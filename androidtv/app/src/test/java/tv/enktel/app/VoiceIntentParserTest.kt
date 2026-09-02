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
    fun `search phrasings reach search`() {
        assertEquals(VoiceIntent.Search("batman"), parse("search for the batman"))
        assertEquals(VoiceIntent.Search("comedy"), parse("find me a comedy"))
    }
}
