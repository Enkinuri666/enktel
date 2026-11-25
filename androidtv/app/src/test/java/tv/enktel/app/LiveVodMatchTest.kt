package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.hub.LiveVodMatch
import tv.enktel.app.data.hub.LiveVodMatch.Candidate

/**
 * The EPG→VOD matcher. Scoring is pure, so the cases that would otherwise need
 * a running panel — a messy EPG title against a clean catalogue name, a reboot
 * two years apart, a film that happens to share a show's name — are asserted
 * here.
 */
class LiveVodMatchTest {

    @Test fun `a messy episode title still matches the clean series name`() {
        val c = Candidate("series:1", "Game of Thrones", 2011, isSeries = true)
        val s = LiveVodMatch.score("Game of Thrones S03E05 (New Episode)", 0, true, c)
        assertTrue("messy episode title should match, was $s", s >= LiveVodMatch.ACCEPT_THRESHOLD)
    }

    @Test fun `an unrelated title does not match`() {
        val c = Candidate("movie:1", "The Godfather", 1972, isSeries = false)
        assertEquals(0.0, LiveVodMatch.score("Gardening Australia", 0, false, c), 0.0001)
    }

    @Test fun `the exact year separates a reboot from the original`() {
        val original = Candidate("tv:old", "Battlestar Galactica", 1978, isSeries = true)
        val reboot = Candidate("tv:new", "Battlestar Galactica", 2004, isSeries = true)
        val forReboot = LiveVodMatch.best("Battlestar Galactica", 2004, true, listOf(original, reboot))
        assertEquals("tv:new", forReboot?.candidate?.key)
    }

    @Test fun `a type mismatch is penalised`() {
        // Same name, but the live entry is a series and the candidate is a film.
        val film = Candidate("movie:1", "Fargo", 1996, isSeries = false)
        val show = Candidate("series:1", "Fargo", 2014, isSeries = true)
        val best = LiveVodMatch.best("Fargo S01E01", 0, true, listOf(film, show))
        assertEquals("the series should win for a series listing", "series:1", best?.candidate?.key)
    }

    @Test fun `nothing clearing the threshold returns null`() {
        val weak = Candidate("movie:1", "Completely Different Film", 2000, isSeries = false)
        assertNull(LiveVodMatch.best("The News at Ten", 0, false, listOf(weak)))
    }
}
