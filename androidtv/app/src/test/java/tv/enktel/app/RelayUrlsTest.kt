package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.net.RelayUrls

class RelayUrlsTest {

    private val base = "https://watch.enktel.tv/api/stream"

    @Test
    fun `off is a no-op`() {
        val url = "http://api.elg-26.com/live/u/p/1.m3u8"
        assertEquals(url, RelayUrls.wrap(url, base, enabled = false))
        assertEquals(listOf(url), RelayUrls.wrapAll(listOf(url), base, enabled = false))
    }

    @Test
    fun `on wraps with the target percent-encoded`() {
        val wrapped = RelayUrls.wrap("http://api.elg-26.com/live/u/p/1.m3u8", base)
        assertEquals(
            "https://watch.enktel.tv/api/stream?u=http%3A%2F%2Fapi.elg-26.com%2Flive%2Fu%2Fp%2F1.m3u8",
            wrapped,
        )
    }

    /**
     * A rewritten HLS manifest comes back with its segments already pointing at
     * the relay. Wrapping those again would nest the relay inside itself and
     * fetch our own endpoint as though it were an upstream.
     */
    @Test
    fun `an already-relayed URL is left alone`() {
        val once = RelayUrls.wrap("http://api.elg-26.com/live/u/p/1.ts", base)
        assertEquals(once, RelayUrls.wrap(once, base))
    }

    /** Nothing to fetch upstream, so nothing to relay. */
    @Test
    fun `non-http URLs pass through`() {
        assertEquals("file:///sdcard/a.mp4", RelayUrls.wrap("file:///sdcard/a.mp4", base))
        assertEquals("", RelayUrls.wrap("", base))
    }

    @Test
    fun `a blank base means direct`() {
        val url = "http://api.elg-26.com/live/u/p/1.ts"
        assertEquals(url, RelayUrls.wrap(url, base = "", enabled = true))
    }

    /** A trailing slash on the configured base must not produce a double slash. */
    @Test
    fun `base is normalised`() {
        assertEquals(
            RelayUrls.wrap("http://h/x.ts", base),
            RelayUrls.wrap("http://h/x.ts", "$base/"),
        )
    }

    /** The fallback chain's order is what makes it a fallback chain. */
    @Test
    fun `wrapAll preserves candidate order`() {
        val urls = listOf("http://h/1.m3u8", "http://h/1.ts", "http://h/1")
        val wrapped = RelayUrls.wrapAll(urls, base)
        assertEquals(3, wrapped.size)
        assertEquals(urls.map { RelayUrls.wrap(it, base) }, wrapped)
    }
}
