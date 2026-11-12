package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.voice.VoiceIntent
import tv.enktel.app.voice.VoiceIntentParser

/**
 * The phrase parser, which had no tests at all.
 *
 * It is 500 lines of ordered pattern matching where every rule can shadow the
 * ones below it, which is the shape of code that goes subtly wrong and stays
 * that way — nothing crashes, a command simply does something else.
 */
class VoiceIntentParserTest {

    private fun parse(s: String) = VoiceIntentParser.parse(s)

    // ── the bug that motivated this ────────────────────────────────────

    @Test
    fun `saying play with a title does not just resume what was already on`() {
        // "play" was in the resume list, and the matcher treats a listed word
        // as a match anywhere in the phrase — so every "play something"
        // command resumed the previous programme instead. The whole "play X"
        // family was dead, silently, because resuming looks like it worked.
        for (phrase in listOf(
            "play squid game",
            "play the batman",
            "play the arsenal game",
            "play some comedy",
        )) {
            assertTrue("$phrase became Resume", parse(phrase) !is VoiceIntent.Resume)
        }
    }

    @Test
    fun `continue with a title is not resume either`() {
        assertTrue(parse("continue watching the bear") !is VoiceIntent.Resume)
    }

    @Test
    fun `the bare transport words still work`() {
        // The fix must not cost the plain commands, which are the common case.
        assertEquals(VoiceIntent.Resume, parse("play"))
        assertEquals(VoiceIntent.Resume, parse("resume"))
        assertEquals(VoiceIntent.Resume, parse("continue"))
        assertEquals(VoiceIntent.Resume, parse("unpause"))
        assertEquals(VoiceIntent.Pause, parse("pause"))
    }

    @Test
    fun `politeness does not stop a command being understood`() {
        // People talk to a television the way they talk to a person.
        assertEquals(VoiceIntent.Pause, parse("please pause"))
        assertEquals(VoiceIntent.Resume, parse("can you play"))
        assertEquals(VoiceIntent.Resume, parse("ok resume"))
    }

    @Test
    fun `punctuation and casing from the recogniser are ignored`() {
        assertEquals(VoiceIntent.Pause, parse("Pause."))
        assertEquals(VoiceIntent.Mute, parse("  MUTE!  "))
    }

    // ── things that must keep working ──────────────────────────────────

    @Test
    fun `a bare channel number tunes`() {
        assertEquals(VoiceIntent.TuneChannel("402"), parse("channel 402"))
        assertEquals(VoiceIntent.TuneChannel("42"), parse("jump to channel 42"))
    }

    @Test
    fun `volume takes a percentage`() {
        val v = parse("set volume to 40 percent")
        assertTrue(v is VoiceIntent.SetVolume)
        assertEquals(0.4f, (v as VoiceIntent.SetVolume).fraction, 0.001f)
    }

    @Test
    fun `an out of range volume is clamped rather than rejected`() {
        val v = parse("set volume to 500") as VoiceIntent.SetVolume
        assertEquals(1.0f, v.fraction, 0.001f)
    }

    @Test
    fun `watching a named match still finds the game`() {
        val i = parse("watch the arsenal game")
        assertTrue("$i", i is VoiceIntent.PlayTeamGame)
        assertEquals("arsenal", (i as VoiceIntent.PlayTeamGame).team)
    }

    @Test
    fun `an empty transcription is unknown rather than a wrong guess`() {
        assertTrue(parse("") is VoiceIntent.Unknown)
        assertTrue(parse("   ") is VoiceIntent.Unknown)
    }

    // ── the tune catch-all ─────────────────────────────────────────────

    @Test
    fun `an ordinary sentence containing a tune verb is not a channel change`() {
        // The tune rule searched the whole utterance rather than its start,
        // so any sentence merely containing one of its verbs became a tune:
        // "what should I watch tonight" changed to a channel called
        // "tonight". Both of these are English, not commands, and both
        // reached the tune rule before any discovery rule could see them.
        assertTrue(
            "${parse("what should i watch tonight")}",
            parse("what should i watch tonight") !is VoiceIntent.TuneChannel,
        )
        assertTrue(
            "${parse("i want to go to bed")}",
            parse("i want to go to bed") !is VoiceIntent.TuneChannel,
        )
    }

    @Test
    fun `a statement of intent is not stripped like politeness`() {
        // "please" carries nothing and can go. "I want to" carries the
        // difference between an instruction and a remark: dropping it turned
        // "I want to go to bed" into "go to bed", which opens with a tune
        // verb and changed the channel to one called "bed".
        assertTrue(parse("i want to go to bed") is VoiceIntent.Unknown)
    }

    @Test
    fun `tuning still works when the phrase actually opens with the verb`() {
        assertEquals(VoiceIntent.TuneChannel("bbc one"), parse("switch to bbc one"))
        assertEquals(VoiceIntent.TuneChannel("the office"), parse("watch the office"))
        assertEquals(VoiceIntent.TuneChannel("fox news"), parse("go to fox news"))
    }

    @Test
    fun `asking for a position seeks to it, whichever verb is used`() {
        // Two of these three never worked. The seek rule sat below the tune
        // rule, which read "go to 45 minutes" as a channel called
        // "45 minutes", and below the skip rule, which read "jump to 12" as
        // "jump" and skipped thirty seconds.
        assertEquals(VoiceIntent.SeekTo(90), parse("seek to 90"))
        assertEquals(VoiceIntent.SeekTo(45), parse("go to 45 minutes"))
        assertEquals(VoiceIntent.SeekTo(12), parse("jump to 12"))
        assertEquals(VoiceIntent.SeekTo(12), parse("jump to 12 minutes"))
        assertEquals(VoiceIntent.SeekTo(5), parse("go to 5 min"))
    }

    @Test
    fun `a bare number after go to is still a channel`() {
        // "go to 45" is how people ask for channel 45. Only a unit makes it a time.
        assertEquals(VoiceIntent.TuneChannel("45"), parse("go to 45"))
        assertEquals(VoiceIntent.TuneChannel("fox news"), parse("go to fox news"))
    }

    // ── skipping and rewinding ─────────────────────────────────────────

    @Test
    fun `skipping forward by an amount`() {
        assertEquals(VoiceIntent.SeekForward(30), parse("skip forward 30 seconds"))
        assertEquals(VoiceIntent.SeekForward(120), parse("jump ahead 2 minutes"))
        assertEquals(VoiceIntent.SeekForward(15), parse("forward 15"))
        assertEquals(VoiceIntent.SeekForward(300), parse("move ahead 5 minutes"))
        assertEquals(VoiceIntent.SeekForward(30), parse("go forward 30 seconds"))
        assertEquals(VoiceIntent.SeekForward(10), parse("skip ten seconds"))
        assertEquals(VoiceIntent.SeekForward(60), parse("skip ahead a minute"))
        assertEquals(VoiceIntent.SeekForward(3600), parse("fast forward 1 hour"))
        // Thirty seconds, because "skip" matched and stopped before the number.
        assertEquals(VoiceIntent.SeekForward(120), parse("skip forward 2 minutes"))
    }

    @Test
    fun `skipping forward with no amount is a thirty second nudge`() {
        for (phrase in listOf("skip", "skip forward", "jump ahead", "fast forward", "ff", "go forward", "skip ahead a bit")) {
            assertEquals(phrase, VoiceIntent.SeekForward(30), parse(phrase))
        }
    }

    @Test
    fun `going back goes back`() {
        assertEquals(VoiceIntent.SeekBack(120), parse("rewind 2 minutes"))
        assertEquals(VoiceIntent.SeekBack(600), parse("go back 10 minutes"))
        assertEquals(VoiceIntent.SeekBack(10), parse("rewind by 10"))
        for (phrase in listOf("rewind", "back", "go back", "jump back", "skip back", "go back a little")) {
            assertEquals(phrase, VoiceIntent.SeekBack(30), parse(phrase))
        }
    }

    @Test
    fun `skip back does not skip forward`() {
        // The forward rule ran first and knew the word "skip". It took the
        // phrase, found no number directly after the verb, and skipped thirty
        // seconds the wrong way.
        assertEquals(VoiceIntent.SeekBack(20), parse("skip back 20 seconds"))
        assertEquals(VoiceIntent.SeekBack(300), parse("jump back 5 minutes"))
    }

    @Test
    fun `a title with ff or back in it is not a seek`() {
        // "ff" is in the forward list. It is also in "office", "coffee",
        // "traffic" and "off", and the old rule searched for it anywhere.
        for (phrase in listOf(
            "the office", "play the office", "open the office", "coffee break", "traffic",
            "different strokes", "stuff you should know", "sleep timer off",
        )) {
            assertTrue("$phrase skipped forward", parse(phrase) !is VoiceIntent.SeekForward)
        }
        for (phrase in listOf("feedback", "playback speed", "back to the future", "play back to the future")) {
            assertTrue("$phrase rewound", parse(phrase) !is VoiceIntent.SeekBack)
        }
        assertEquals(VoiceIntent.TuneChannel("back to the future"), parse("watch back to the future"))
    }

    @Test
    fun `named playback commands are not read as skips`() {
        // Each contains a verb the seek rules know, and each used to move the
        // player thirty seconds instead of doing what it says.
        assertEquals(VoiceIntent.SkipIntro, parse("skip intro"))
        assertEquals(VoiceIntent.SkipIntro, parse("skip the intro"))
        assertEquals(VoiceIntent.SkipIntro, parse("skip credits"))
        assertEquals(VoiceIntent.PreviousEpisode, parse("go back an episode"))
        assertEquals(VoiceIntent.NextEpisode, parse("skip to the next episode"))
        assertEquals(VoiceIntent.NextEpisode, parse("jump to next episode"))
        assertEquals(VoiceIntent.Restart, parse("go back to the beginning"))
    }

    @Test
    fun `an absurd amount is clamped rather than overflowing`() {
        assertEquals(VoiceIntent.SeekForward(24 * 60 * 60), parse("skip forward 999999 hours"))
        // More digits than an Int holds: not an amount at all, and not a crash.
        assertTrue(runCatching { parse("skip forward 99999999999999 seconds") }.isSuccess)
    }

    @Test
    fun `a number too big to be a minute is not a crash`() {
        // What a recogniser makes of a phone number or a date. toInt() threw
        // NumberFormatException here, inside the speech callback. Whatever
        // these become instead, they must become something.
        for (phrase in listOf("go to 5551234567890 minutes", "seek to 99999999999 minutes", "jump to 20261112093000")) {
            val intent = runCatching { parse(phrase) }
            assertTrue("$phrase threw ${intent.exceptionOrNull()}", intent.isSuccess)
            assertTrue("$phrase became a seek", intent.getOrNull() !is VoiceIntent.SeekTo)
        }
    }

    @Test
    fun `search phrasings reach search`() {
        assertEquals(VoiceIntent.Search("batman"), parse("search for the batman"))
        assertEquals(VoiceIntent.Search("comedy"), parse("find me a comedy"))
    }
}
