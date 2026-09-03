package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.Subscribe

class SubscribeTest {

    @Test fun `a renewal link carries the line it is renewing`() {
        // Without this the site issues a second line and the viewer has paid
        // twice, which is refunded by hand.
        assertEquals(
            "https://enktel.tv/checkout?renew=enkteluser",
            Subscribe.renewUrl("enkteluser"),
        )
    }

    @Test fun `a username with awkward characters survives the trip`() {
        val url = Subscribe.renewUrl("a b+c&d")
        val value = url.substringAfter("renew=")
        // A raw & would start a second query parameter and truncate the name;
        // a raw + would arrive at the server as a space.
        assertTrue(url, !value.contains("&"))
        assertTrue(url, !value.contains(" "))
        assertEquals("a b+c&d", java.net.URLDecoder.decode(value, "UTF-8"))
    }

    @Test fun `no username means the plain pricing page`() {
        // An M3U profile has no account to extend.
        assertEquals(Subscribe.PRICING, Subscribe.renewUrl(""))
        assertEquals(Subscribe.PRICING, Subscribe.renewUrl("   "))
    }

    @Test fun `the prompt is silent until the end is in sight`() {
        assertNull(Subscribe.expiryNotice(90, expired = false))
        assertNull(Subscribe.expiryNotice(Subscribe.NOTICE_DAYS + 1, expired = false))
        assertTrue(Subscribe.expiryNotice(Subscribe.NOTICE_DAYS, expired = false)!!.contains("14 days"))
    }

    @Test fun `the last few days read as English, not as arithmetic`() {
        assertTrue(Subscribe.expiryNotice(0, expired = false)!!.contains("today"))
        assertTrue(Subscribe.expiryNotice(1, expired = false)!!.contains("tomorrow"))
        assertTrue(Subscribe.expiryNotice(2, expired = false)!!.contains("2 days"))
    }

    @Test fun `an expired line says so whatever the day count is`() {
        // A line past its date can arrive with a negative count, and "expires
        // in -3 days" is what that looks like when the flag is ignored.
        assertTrue(Subscribe.expiryNotice(-3, expired = true)!!.contains("expired"))
        assertNull(Subscribe.expiryNotice(-3, expired = false))
    }

    @Test fun `no price is quoted anywhere in here`() {
        // An APK is updated rarely; a price compiled into one keeps being
        // advertised after it changes. The web page is the only correctable
        // copy, so this object must never grow one.
        val text = listOf(
            Subscribe.PRICING, Subscribe.TRIAL, Subscribe.SHORT_PRICING,
            Subscribe.SHORT_TRIAL, Subscribe.renewUrl("x"),
            Subscribe.expiryNotice(0, false).orEmpty(),
            Subscribe.expiryNotice(0, true).orEmpty(),
        ).joinToString(" ")
        assertTrue(text, !text.contains("$"))
        assertTrue(text, !Regex("\\d+\\.\\d{2}").containsMatchIn(text))
    }
}
