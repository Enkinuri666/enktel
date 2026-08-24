package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.m3u.DrmInfo
import tv.enktel.app.data.m3u.M3uParser

/**
 * `#KODIPROP` lines sit between the `#EXTINF` and the URL, like `#EXTVLCOPT`,
 * so the parser has to carry them across iterations — and clear them once
 * consumed.
 *
 * Failing to clear is the expensive half. A leaked User-Agent gives the wrong
 * header to channels below; a leaked licence tells them all to decrypt a stream
 * that is not encrypted, and on a box with no provisioned Widevine that fails
 * outright rather than degrading.
 */
class M3uDrmParseTest {

    private fun parse(text: String) = M3uParser.parse(text.reader().buffered()).entries

    @Test
    fun `a widevine licence is read onto its channel`() {
        val e = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="a.hr",Channel A
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://lic.example/wv|X-Auth=t|R{SSM}|
            https://cdn.example/a/index.mpd
            """.trimIndent(),
        ).single()

        assertEquals(DrmInfo.WIDEVINE, e.drmScheme)
        assertEquals("https://lic.example/wv|X-Auth=t|R{SSM}|", e.drmLicense)
        val drm = DrmInfo(e.drmScheme, e.drmLicense)
        assertEquals("https://lic.example/wv", drm.licenseUrl)
        assertEquals(mapOf("X-Auth" to "t"), drm.licenseHeaders)
    }

    @Test
    fun `inline clearkey keys are read onto their channel`() {
        val e = parse(
            """
            #EXTM3U
            #EXTINF:-1,Channel A
            #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
            #KODIPROP:inputstream.adaptive.license_key=0123456789abcdef0123456789abcdef:ffffffffffffffffffffffffffffffff
            https://cdn.example/a/index.mpd
            """.trimIndent(),
        ).single()

        assertEquals(DrmInfo.CLEARKEY, e.drmScheme)
        assertEquals(
            """{"keys":[{"kty":"oct","kid":"ASNFZ4mrze8BI0VniavN7w","k":"_____________________w"}],"type":"temporary"}""",
            DrmInfo(e.drmScheme, e.drmLicense).clearKeyJson,
        )
    }

    /** The one that matters: an encrypted channel must not infect the next. */
    @Test
    fun `a licence does not leak onto the channel below`() {
        val entries = parse(
            """
            #EXTM3U
            #EXTINF:-1,Encrypted
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://lic.example/wv
            https://cdn.example/a/index.mpd
            #EXTINF:-1,In the clear
            https://cdn.example/b/index.m3u8
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        assertEquals(DrmInfo.WIDEVINE, entries[0].drmScheme)
        assertEquals("", entries[1].drmScheme)
        assertEquals("", entries[1].drmLicense)
        // And so the second opens no session at all.
        assertEquals(true, DrmInfo(entries[1].drmScheme, entries[1].drmLicense).isEmpty)
    }

    /** An ordinary playlist declares nothing, which is what almost all of them do. */
    @Test
    fun `a channel with no properties has no drm`() {
        val e = parse(
            """
            #EXTM3U
            #EXTINF:-1,Plain
            #EXTVLCOPT:http-user-agent=okhttp/4.11.0
            https://cdn.example/a/index.m3u8
            """.trimIndent(),
        ).single()

        assertEquals("", e.drmScheme)
        assertEquals("", e.drmLicense)
        // The agent still works; these two live side by side.
        assertEquals("okhttp/4.11.0", e.userAgent)
    }

    /** A scheme this player cannot handle reads as no DRM, not as broken DRM. */
    @Test
    fun `an unsupported scheme is ignored`() {
        val e = parse(
            """
            #EXTM3U
            #EXTINF:-1,FairPlay
            #KODIPROP:inputstream.adaptive.license_type=com.apple.fps
            #KODIPROP:inputstream.adaptive.license_key=https://lic.example/fps
            https://cdn.example/a/index.m3u8
            """.trimIndent(),
        ).single()

        assertEquals("", e.drmScheme)
        assertEquals(true, DrmInfo(e.drmScheme, e.drmLicense).isEmpty)
    }
}
