package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.epg.NowPlaying
import tv.enktel.app.data.repo.ChannelFilters

/**
 * What a live channel card says about the programme on it.
 *
 * The grid already fetched a now-playing programme per channel and printed
 * its title. What it could not say was where in it you are — and on a live
 * grid that is most of the information, because "Arsenal v Chelsea" does not
 * tell you whether you have missed it.
 *
 * All of the arithmetic here is edge cases, and each one has an obvious
 * implementation that is wrong: a guide that publishes a programme ending
 * before it starts (a provider with broken timezone handling, which is
 * common), a clock past the end, a zero-length entry, a panel that publishes
 * one twelve-hour block per channel per day.
 */
class NowPlayingTest {

    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun `progress is the fraction of the way through`() {
        val end = start + 60 * minute
        assertEquals(0f, NowPlaying.progress(start, end, start), 0.001f)
        assertEquals(0.5f, NowPlaying.progress(start, end, start + 30 * minute), 0.001f)
        assertEquals(1f, NowPlaying.progress(start, end, end), 0.001f)
    }

    @Test
    fun `a bar never runs backwards out of its track`() {
        // The naive (now - start) / (end - start) returns a negative fraction
        // for a clock that has not reached the start yet, and fillMaxWidth
        // throws on a negative.
        val end = start + 60 * minute
        assertEquals(0f, NowPlaying.progress(start, end, start - 10 * minute), 0.001f)
    }

    @Test
    fun `a bar never runs past the end of its track`() {
        val end = start + 60 * minute
        assertEquals(1f, NowPlaying.progress(start, end, end + 10 * minute), 0.001f)
    }

    @Test
    fun `an inverted or empty programme reads as not started`() {
        // A guide that says 20:00 to 19:00 is what a provider publishes when
        // its timezone handling is wrong. Dividing by that span is a negative
        // fraction; dividing by a zero-length one is a crash.
        assertEquals(0f, NowPlaying.progress(start, start - minute, start), 0.001f)
        assertEquals(0f, NowPlaying.progress(start, start, start), 0.001f)
    }

    @Test
    fun `minutes left round up, so a programme never reads as over while it runs`() {
        // 30 seconds left is "1 min left", not "0 min left". Truncating here
        // is how a card says a programme has finished while it is on screen.
        assertEquals("1 min left", NowPlaying.remaining(start + 30_000, start))
        assertEquals("1 min left", NowPlaying.remaining(start + minute, start))
        assertEquals("32 min left", NowPlaying.remaining(start + 32 * minute, start))
    }

    @Test
    fun `an hour or more reads in hours and minutes`() {
        assertEquals("1h left", NowPlaying.remaining(start + 60 * minute, start))
        assertEquals("1h 05m left", NowPlaying.remaining(start + 65 * minute, start))
        assertEquals("2h 30m left", NowPlaying.remaining(start + 150 * minute, start))
    }

    @Test
    fun `a finished programme says nothing rather than counting down from zero`() {
        assertNull(NowPlaying.remaining(start, start))
        assertNull(NowPlaying.remaining(start - minute, start))
    }

    @Test
    fun `a day-long guide entry says nothing rather than nine hours left`() {
        // A line with no real EPG publishes one block per channel per day.
        // Every card reading "9h 42m left" is not information, and it would
        // crowd out the title it sits under.
        assertNull(NowPlaying.remaining(start + 13 * 60 * minute, start))
        assertTrue(NowPlaying.remaining(start + 11 * 60 * minute, start) != null)
    }

    // ---- the name on the card ---------------------------------------------

    @Test
    fun `the provider's country marker comes off the front`() {
        // Five characters every card in the grid repeated, on a 120 dp cell,
        // with the actual name taking the ellipsis: "AU | Fox Cricke…".
        assertEquals("Fox Cricket HD", ChannelFilters.displayName("AU | Fox Cricket HD"))
        assertEquals("Sky Sports Main Event", ChannelFilters.displayName("UK: Sky Sports Main Event"))
        assertEquals("HBO HD", ChannelFilters.displayName("[US] HBO HD"))
        assertEquals("Nickelodeon", ChannelFilters.displayName("AU - Nickelodeon"))
    }

    @Test
    fun `a name with no marker is left exactly as it is`() {
        assertEquals("BBC One HD", ChannelFilters.displayName("BBC One HD"))
        assertEquals("Arena Sport 1", ChannelFilters.displayName("Arena Sport 1"))
    }

    @Test
    fun `stripping never leaves too little to identify a channel`() {
        // "UK: 4" is Channel 4. Reduced to "4" it is a number in a grid that
        // already shows numbers, so the prefix stays.
        assertEquals("UK: 4", ChannelFilters.displayName("UK: 4"))
        assertEquals("AU | 7", ChannelFilters.displayName("AU | 7"))
    }

    @Test
    fun `the card and the player overlay word the same time the same way`() {
        // Both print a countdown for the same programme, and a viewer sees
        // them within a second of each other: the card in the grid, the
        // overlay after pressing it. Two formatters drifted apart would show
        // the same programme with two different amounts of time left.
        val end = start + 95 * minute
        assertEquals(
            tv.enktel.app.ui.live.remainingLabel(end, start),
            NowPlaying.remaining(end, start),
        )
        assertEquals("1h 35m left", NowPlaying.remaining(end, start))
    }

    @Test
    fun `a channel with no logo gets initials rather than its prefix`() {
        // The plate drew name.take(2), so fifteen Australian channels in a
        // row all showed "AU" — the one thing the name underneath already
        // said, on the only part of the card that had room to say something
        // else.
        assertEquals("FC", ChannelFilters.initials("AU | Fox Cricket HD"))
        assertEquals("C7", ChannelFilters.initials("AU | Channel 7 HD"))
        assertEquals("C10", ChannelFilters.initials("AU | Channel 10 HD"))
        assertEquals("ES", ChannelFilters.initials("US | ESPN HD"))
        assertEquals("NI", ChannelFilters.initials("AU | Nickelodeon"))
    }

    @Test
    fun `initials never come out blank or longer than the plate`() {
        // A name that is punctuation, or empty, still has to draw something
        // that fits: the plate is a fixed box, not a text field.
        for (name in listOf("", "   ", "---", "UK: 4", "HD", "A", "AU | Channel 501")) {
            val initials = ChannelFilters.initials(name)
            assertTrue("'$name' gave '$initials'", initials.length <= 3)
        }
    }

    @Test
    fun `display and search agree about what a prefix is`() {
        // They share PREFIX_RE deliberately. If display stripped something
        // search still indexed, a viewer could read a name off a card and
        // fail to find it by typing exactly that.
        val raw = "AU | Fox Cricket HD"
        val shown = ChannelFilters.displayName(raw)
        assertTrue(
            "a channel must be findable by the name its card shows",
            ChannelFilters.tokens(raw).containsAll(ChannelFilters.tokens(shown)),
        )
    }
}
