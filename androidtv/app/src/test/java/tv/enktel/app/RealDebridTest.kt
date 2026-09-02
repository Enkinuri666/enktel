package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.debrid.RealDebrid
import tv.enktel.app.data.repo.EnktelFeed

class RealDebridTest {

    private val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRST"

    // ── token ──────────────────────────────────────────────────────────

    @Test
    fun `a token pasted with whitespace still works`() {
        // Copying from a web page brings newlines and spaces with it, and a
        // token split across two lines by the browser is still the token.
        assertEquals(token, RealDebrid.normaliseToken("  $token \n"))
        assertEquals(token, RealDebrid.normaliseToken(token.chunked(20).joinToString("\n")))
        // ...including when the browser indents the continuation lines.
        assertEquals(token, RealDebrid.normaliseToken(token.chunked(20).joinToString("\n   ")))
    }

    @Test
    fun `something that is not a token is refused rather than stored`() {
        // Storing the wrong text produces a 401, which reads as "your account
        // is wrong" when the real problem is that a URL was copied instead.
        assertEquals("", RealDebrid.normaliseToken(""))
        assertEquals("", RealDebrid.normaliseToken("   "))
        assertEquals("", RealDebrid.normaliseToken("https://real-debrid.com/apitoken"))
        assertEquals("", RealDebrid.normaliseToken("my token is $token"))
        assertEquals("", RealDebrid.normaliseToken("short"))
    }

    // ── failures ───────────────────────────────────────────────────────

    @Test
    fun `a rate limit never suggests trying again now`() {
        // The published limit counts refused requests too, and the API's own
        // documentation warns that hammering it blocks the account for an
        // undefined period. A message that invites an immediate retry would
        // make the situation worse rather than better.
        val msg = RealDebrid.describeFailure(429)
        assertTrue(msg, msg.contains("Wait"))
        assertTrue(msg, msg.contains("longer"))
    }

    @Test
    fun `each documented failure says something different and specific`() {
        val codes = listOf(401, 403, 404, 429, 503)
        val messages = codes.map { RealDebrid.describeFailure(it) }
        assertEquals("each code needs its own explanation", codes.size, messages.distinct().size)
        assertTrue(messages.none { it.isBlank() })
        // 401 is the one a viewer can actually fix, so it must point at where.
        assertTrue(RealDebrid.describeFailure(401).contains("Settings"))
    }

    @Test
    fun `an unmapped code still produces something usable`() {
        val msg = RealDebrid.describeFailure(418)
        assertTrue(msg, msg.contains("418"))
    }

    @Test
    fun `the API's own words are added, never shown alone`() {
        // The service's error string separates cases this cannot, but it is
        // written for someone reading a response body, not for a television.
        val msg = RealDebrid.describeFailure(503, "hoster_unavailable")
        assertTrue(msg, msg.contains("hoster_unavailable"))
        assertTrue(msg, msg.length > "hoster_unavailable".length + 20)
    }

    // ── account state ──────────────────────────────────────────────────

    private fun day(iso: String) = EnktelFeed.parseIsoDateToEpochDay(iso)!!

    @Test
    fun `days left counts from the expiry date`() {
        val today = day("2026-09-02")
        assertEquals(30, RealDebrid.daysLeft("2026-10-02T23:59:59.000Z", today))
        assertEquals(0, RealDebrid.daysLeft("2026-09-02T23:59:59.000Z", today))
        assertEquals(-1, RealDebrid.daysLeft("2026-09-01T23:59:59.000Z", today))
    }

    @Test
    fun `an unreadable expiry is null rather than zero`() {
        // Zero means "expires today". Reporting that for a date this simply
        // could not read would tell someone with a healthy account that it is
        // about to end.
        assertNull(RealDebrid.daysLeft("", day("2026-09-02")))
        assertNull(RealDebrid.daysLeft("never", day("2026-09-02")))
    }

    @Test
    fun `premium is both the account type and a date that has not passed`() {
        assertTrue(RealDebrid.isPremium("premium", 30))
        assertTrue(RealDebrid.isPremium("premium", 0))
        assertFalse("an expired premium account cannot unrestrict", RealDebrid.isPremium("premium", -1))
        assertFalse(RealDebrid.isPremium("free", 30))
    }

    @Test
    fun `the account line leads with the state, not the name`() {
        val today = day("2026-09-02")
        assertEquals(
            "enki · premium, 30 days left",
            RealDebrid.accountLine("enki", "premium", "2026-10-02T00:00:00.000Z", today),
        )
        assertEquals(
            "enki · premium, expires today",
            RealDebrid.accountLine("enki", "premium", "2026-09-02T00:00:00.000Z", today),
        )
        assertEquals(
            "enki · premium, 1 day left",
            RealDebrid.accountLine("enki", "premium", "2026-09-03T00:00:00.000Z", today),
        )
        assertEquals(
            "enki · premium expired",
            RealDebrid.accountLine("enki", "premium", "2026-08-02T00:00:00.000Z", today),
        )
    }

    @Test
    fun `a free account is told why it will not work`() {
        // The commonest support question this avoids: the token is valid, the
        // account is real, and unrestricting still fails.
        val line = RealDebrid.accountLine("enki", "free", "2026-10-02T00:00:00.000Z", day("2026-09-02"))
        assertTrue(line, line.contains("free"))
        assertTrue(line, line.contains("premium"))
    }

    @Test
    fun `a missing username does not produce a leading separator`() {
        val line = RealDebrid.accountLine("", "premium", "2026-10-02T00:00:00.000Z", day("2026-09-02"))
        assertFalse(line, line.startsWith("·"))
        assertTrue(line, line.startsWith("Real-Debrid"))
    }
}
