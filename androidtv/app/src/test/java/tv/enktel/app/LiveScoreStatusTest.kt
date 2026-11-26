package tv.enktel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import tv.enktel.app.data.repo.LiveScore
import org.junit.Test

/**
 * The scoreboard's live/finished/upcoming state, which drives the pulsing LIVE
 * badge and the sort order.
 *
 * The current TheSportsDB livescore.php returns strStatus as null and carries
 * the real state in strProgress ("Final", "1st Half"), so the state words that
 * actually arrive are the progress words, not the FT/AET/PEN abbreviations.
 */
class LiveScoreStatusTest {

    private fun score(status: String) = LiveScore(
        eventId = "1", home = "A", away = "B", homeScore = "1", awayScore = "0",
        minute = "", league = "L", sport = "Soccer", status = status,
    )

    @Test fun `a finished match reported as Final is not live`() {
        val s = score("Final")
        assertTrue(s.finished)
        assertFalse("a finished match must not pulse LIVE", s.inPlay)
    }

    @Test fun `the FT abbreviation still counts as finished`() {
        assertTrue(score("FT").finished)
        assertTrue(score("AET").finished)
    }

    @Test fun `a match in progress is live`() {
        val s = score("2nd Half")
        assertTrue(s.inPlay)
        assertFalse(s.finished)
        assertFalse(s.notStarted)
    }

    @Test fun `NS and blank are not live`() {
        assertTrue(score("NS").notStarted)
        assertFalse(score("NS").inPlay)
        // A row with no state word at all is not something to pulse LIVE.
        assertFalse(score("").inPlay)
    }
}
