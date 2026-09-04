package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.NetworkClass
import tv.enktel.app.data.share.LanShare

class LanShareTest {

    // ── the PIN ────────────────────────────────────────────────────────

    @Test fun `a pin is six digits and not the same twice`() {
        val pins = (1..200).map { LanShare.newPin() }
        pins.forEach { assertTrue(it, it.matches(Regex("\\d{6}"))) }
        // Not a randomness proof — a check that it is not a constant, which
        // is the failure that would actually ship.
        assertTrue("all ${pins.size} pins identical", pins.toSet().size > 1)
    }

    @Test fun `pin comparison accepts only the real pin`() {
        assertTrue(LanShare.pinMatches("123456", "123456"))
        assertFalse(LanShare.pinMatches("123457", "123456"))
        assertFalse(LanShare.pinMatches("", "123456"))
        assertFalse(LanShare.pinMatches("12345", "123456"))
        assertFalse(LanShare.pinMatches("1234567", "123456"))
    }

    @Test fun `a wrong pin costs the same whether it is wrong early or late`() {
        // Guards the constant-time property against someone "simplifying"
        // this to ==. A first-character mismatch and a last-character
        // mismatch must both walk the whole PIN.
        val actual = "123456"
        val early = measure { LanShare.pinMatches("923456", actual) }
        val late = measure { LanShare.pinMatches("123459", actual) }
        // Wall-clock on a shared CI box is noisy, so this asserts only that
        // neither is wildly cheaper — an early-exit implementation is orders
        // of magnitude apart, not a few percent.
        val ratio = maxOf(early, late).toDouble() / minOf(early, late).coerceAtLeast(1)
        assertTrue("early=$early late=$late ratio=$ratio", ratio < 50.0)
    }

    private fun measure(times: Int = 20_000, body: () -> Unit): Long {
        repeat(1_000) { body() }
        val t0 = System.nanoTime()
        repeat(times) { body() }
        return System.nanoTime() - t0
    }

    @Test fun `tokens are long, hex, and unique`() {
        val tokens = (1..500).map { LanShare.newToken() }
        tokens.forEach { assertTrue(it, it.matches(Regex("[0-9a-f]{32}"))) }
        assertEquals(tokens.size, tokens.toSet().size)
    }

    // ── ranges ─────────────────────────────────────────────────────────

    @Test fun `a plain range is read as written`() {
        assertEquals(0L..499L, LanShare.parseRange("bytes=0-499", 1_000))
        assertEquals(500L..999L, LanShare.parseRange("bytes=500-999", 1_000))
    }

    @Test fun `an open-ended range runs to the last byte`() {
        assertEquals(500L..999L, LanShare.parseRange("bytes=500-", 1_000))
        assertEquals(0L..999L, LanShare.parseRange("bytes=0-", 1_000))
    }

    @Test fun `a suffix range counts back from the end`() {
        assertEquals(500L..999L, LanShare.parseRange("bytes=-500", 1_000))
        // Asking for more than exists yields the whole file, not a negative start.
        assertEquals(0L..999L, LanShare.parseRange("bytes=-5000", 1_000))
    }

    @Test fun `an end past the file is clamped rather than trusted`() {
        assertEquals(500L..999L, LanShare.parseRange("bytes=500-99999", 1_000))
    }

    @Test fun `a start past the end is refused, not silently restarted`() {
        // The important one: answering this with the whole file is how a
        // resume produces a corrupt copy that looks complete.
        assertNull(LanShare.parseRange("bytes=1000-", 1_000))
        assertNull(LanShare.parseRange("bytes=5000-6000", 1_000))
    }

    @Test fun `nonsense is refused`() {
        assertNull(LanShare.parseRange(null, 1_000))
        assertNull(LanShare.parseRange("", 1_000))
        assertNull(LanShare.parseRange("items=0-10", 1_000))
        assertNull(LanShare.parseRange("bytes=", 1_000))
        assertNull(LanShare.parseRange("bytes=abc-def", 1_000))
        assertNull(LanShare.parseRange("bytes=900-100", 1_000))
        assertNull(LanShare.parseRange("bytes=0-499", 0))
    }

    @Test fun `only the first range of several is honoured`() {
        assertEquals(0L..99L, LanShare.parseRange("bytes=0-99,200-299", 1_000))
    }

    // ── names and types ────────────────────────────────────────────────

    @Test fun `a filename cannot escape into a path`() {
        assertEquals("etcpasswd.mkv", LanShare.safeFilename("../../etc/passwd", "mkv"))
        assertEquals("film.mkv", LanShare.safeFilename("film", ".MKV"))
        assertEquals("a b.mp4", LanShare.safeFilename("  a\t\tb  ", "mp4"))
        assertEquals("download.mkv", LanShare.safeFilename("   ", "mkv"))
    }

    @Test fun `nothing is ever served as html`() {
        // A file served as text/html runs script on this server's origin.
        val names = listOf("x.html", "x.htm", "x.svg", "x.js", "x", "x.mkv", "x.MP4")
        names.forEach {
            val t = LanShare.contentType(it)
            assertFalse("$it -> $t", t.contains("html"))
            assertFalse("$it -> $t", t.contains("javascript"))
        }
        assertEquals("video/mp4", LanShare.contentType("A Film.MP4"))
        assertEquals("application/octet-stream", LanShare.contentType("x.html"))
    }

    // ── where it may run ───────────────────────────────────────────────

    @Test fun `a listening socket is opened on wifi and wired, never on mobile`() {
        assertTrue(LanShare.allowedOn(NetworkClass.Kind.WIFI))
        assertTrue(LanShare.allowedOn(NetworkClass.Kind.WIRED))
        assertFalse(LanShare.allowedOn(NetworkClass.Kind.MOBILE))
        assertFalse(LanShare.allowedOn(NetworkClass.Kind.UNKNOWN))
    }

    @Test fun `the refusal says why, in terms of the carrier's network`() {
        assertNull(LanShare.blockedReason(NetworkClass.Kind.WIFI))
        val why = LanShare.blockedReason(NetworkClass.Kind.MOBILE)
        assertTrue(why, why!!.contains("Wi-Fi"))
        assertTrue(why, why.contains("carrier"))
    }

    @Test fun `the url is what a person types`() {
        assertEquals("http://192.168.1.44:8787", LanShare.shareUrl("192.168.1.44", 8787))
    }
}
