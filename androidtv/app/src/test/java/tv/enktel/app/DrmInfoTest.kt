package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.m3u.DrmInfo

/**
 * Reading what a playlist says about encryption.
 *
 * `license_key` is one string carrying up to four different things, in a format
 * borrowed from Kodi because M3U has nothing of its own for DRM. Getting it
 * wrong is quiet: a licence request goes out with a percent-encoded header or
 * the wrong endpoint, the server refuses it, and the channel reports a generic
 * playback error indistinguishable from a dead stream.
 */
class DrmInfoTest {

    // ---- what the list calls the scheme ---------------------------------

    @Test
    fun `the system ids and the short names both read`() {
        assertEquals(DrmInfo.WIDEVINE, DrmInfo.scheme("com.widevine.alpha"))
        assertEquals(DrmInfo.WIDEVINE, DrmInfo.scheme("widevine"))
        assertEquals(DrmInfo.PLAYREADY, DrmInfo.scheme("com.microsoft.playready"))
        assertEquals(DrmInfo.CLEARKEY, DrmInfo.scheme("org.w3.clearkey"))
        assertEquals(DrmInfo.CLEARKEY, DrmInfo.scheme("  ClearKey "))
    }

    /** An unrecognised scheme reads as "no DRM", never as a half-configured one. */
    @Test
    fun `anything else is not a scheme`() {
        assertEquals("", DrmInfo.scheme("com.apple.fps"))
        assertEquals("", DrmInfo.scheme(""))
    }

    // ---- the KODIPROP line ----------------------------------------------

    @Test
    fun `a property line splits into name and value`() {
        val p = DrmInfo.kodiProp("#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha")
        assertEquals("license_type" to "com.widevine.alpha", p)
    }

    /** Both the qualified and the bare spelling occur in real lists. */
    @Test
    fun `the property prefix is optional`() {
        assertEquals("license_key" to "x", DrmInfo.kodiProp("#KODIPROP:license_key=x"))
        assertEquals("license_key" to "x", DrmInfo.kodiProp("#KODIPROP:inputstream.adaptive.license_key=x"))
    }

    /** A licence URL is full of colons and equals signs; only the first splits. */
    @Test
    fun `a value keeps everything after the first equals`() {
        val p = DrmInfo.kodiProp("#KODIPROP:inputstream.adaptive.license_key=https://a.example/wv?t=1&u=2")
        assertEquals("https://a.example/wv?t=1&u=2", p!!.second)
    }

    @Test
    fun `a line with nothing in it is not a property`() {
        assertEquals(null, DrmInfo.kodiProp("#KODIPROP:"))
        assertEquals(null, DrmInfo.kodiProp("#KODIPROP:license_key="))
        assertEquals(null, DrmInfo.kodiProp("#KODIPROP:nonsense"))
    }

    // ---- the licence endpoint -------------------------------------------

    @Test
    fun `a bare url is the endpoint`() {
        val d = DrmInfo(DrmInfo.WIDEVINE, "https://lic.example/wv")
        assertEquals("https://lic.example/wv", d.licenseUrl)
        assertTrue(d.licenseHeaders.isEmpty())
        assertTrue(d.needsLicenseServer)
        assertFalse(d.isInlineClearKey)
    }

    /**
     * The trailing parts describe how to wrap the challenge — `R{SSM}` and
     * friends — which ExoPlayer does itself. Only the first two are ours.
     */
    @Test
    fun `the endpoint stops at the first pipe`() {
        val d = DrmInfo(
            DrmInfo.WIDEVINE,
            "https://lic.example/wv|Content-Type=application/octet-stream|R{SSM}|",
        )
        assertEquals("https://lic.example/wv", d.licenseUrl)
        assertEquals(mapOf("Content-Type" to "application/octet-stream"), d.licenseHeaders)
    }

    @Test
    fun `several headers are separated by ampersands`() {
        val d = DrmInfo(DrmInfo.WIDEVINE, "https://l.example|X-A=1&X-B=2|R{SSM}|")
        assertEquals(mapOf("X-A" to "1", "X-B" to "2"), d.licenseHeaders)
    }

    /**
     * Values are percent-encoded precisely because they contain the characters
     * that separate them. Sending the encoded form is sending a header the
     * server was not given.
     */
    @Test
    fun `header values are decoded`() {
        val d = DrmInfo(DrmInfo.WIDEVINE, "https://l.example|Authorization=Bearer%20abc%3D%3D&X-Tenant=a%26b|")
        assertEquals("Bearer abc==", d.licenseHeaders["Authorization"])
        assertEquals("a&b", d.licenseHeaders["X-Tenant"])
    }

    @Test
    fun `an empty header section yields no headers`() {
        assertTrue(DrmInfo(DrmInfo.WIDEVINE, "https://l.example||R{SSM}|").licenseHeaders.isEmpty())
    }

    // ---- ClearKey --------------------------------------------------------

    /**
     * The shorthand is the common spelling and needs converting: a JWK carries
     * the same bytes in base64url, not hex.
     */
    @Test
    fun `the kid colon key shorthand becomes a jwk set`() {
        val d = DrmInfo(DrmInfo.CLEARKEY, "0123456789abcdef0123456789abcdef:ffffffffffffffffffffffffffffffff")
        assertEquals(
            """{"keys":[{"kty":"oct","kid":"ASNFZ4mrze8BI0VniavN7w","k":"_____________________w"}],"type":"temporary"}""",
            d.clearKeyJson,
        )
        assertTrue(d.isInlineClearKey)
        // Inline keys have no server to ask.
        assertEquals("", d.licenseUrl)
        assertFalse(d.needsLicenseServer)
    }

    @Test
    fun `several key pairs are all carried`() {
        val d = DrmInfo(
            DrmInfo.CLEARKEY,
            "0123456789abcdef0123456789abcdef:ffffffffffffffffffffffffffffffff," +
                "abcdefabcdefabcdefabcdefabcdefab:00000000000000000000000000000000",
        )
        assertEquals(2, Regex("\"kty\"").findAll(d.clearKeyJson).count())
    }

    /** A list that already wrote the JSON is passed through untouched. */
    @Test
    fun `json is taken as written`() {
        val json = """{"keys":[{"kty":"oct","kid":"a","k":"b"}],"type":"temporary"}"""
        assertEquals(json, DrmInfo(DrmInfo.CLEARKEY, json).clearKeyJson)
    }

    /** Hex is fixed-width; anything else is not a key and must not be guessed at. */
    @Test
    fun `malformed keys produce nothing rather than a broken jwk`() {
        assertEquals("", DrmInfo(DrmInfo.CLEARKEY, "abc:def").clearKeyJson)
        assertEquals("", DrmInfo(DrmInfo.CLEARKEY, "0123456789abcdef0123456789abcdef").clearKeyJson)
        assertEquals("", DrmInfo(DrmInfo.CLEARKEY, "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz:0123456789abcdef0123456789abcdef").clearKeyJson)
    }

    /** ClearKey can also come from a server, in which case there is nothing local. */
    @Test
    fun `clearkey from a url is not inline`() {
        val d = DrmInfo(DrmInfo.CLEARKEY, "https://lic.example/ck")
        assertEquals("", d.clearKeyJson)
        assertFalse(d.isInlineClearKey)
    }

    // ---- nothing at all --------------------------------------------------

    /**
     * The common case by a wide margin. A blank scheme is what keeps a stream
     * in the clear from opening a DRM session, which fails outright on the
     * cheap boxes that have no provisioned Widevine.
     */
    @Test
    fun `an unencrypted channel is empty on every reading`() {
        for (d in listOf(DrmInfo.NONE, DrmInfo("", "x"), DrmInfo(DrmInfo.WIDEVINE, ""))) {
            assertTrue(d.isEmpty)
            assertFalse(d.needsLicenseServer)
            assertFalse(d.isInlineClearKey)
        }
    }
}
