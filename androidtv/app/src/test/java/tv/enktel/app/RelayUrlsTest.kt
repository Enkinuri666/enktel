package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.RelayUrls

class RelayUrlsTest {

    private val base = "https://enktel.tv/api/stream"

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
            "https://enktel.tv/api/stream?u=http%3A%2F%2Fapi.elg-26.com%2Flive%2Fu%2Fp%2F1.m3u8",
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

    /**
     * The constant, not a base the test supplies.
     *
     * Every case above passes `base` in, which is why none of them noticed
     * that the default pointed at watch.enktel.tv — a separate property that
     * serves no /api/stream at all. `/api/stream` is a route in this
     * repository and deploys with this site, so the relay has to name this
     * origin. Nothing else in the build can catch that: a wrong host compiles,
     * lints and ships, and fails only as a channel that will not play.
     */
    @Test
    fun `the default base is the origin that serves the route`() {
        assertEquals("https://enktel.tv/api/stream", RelayUrls.DEFAULT_BASE)
    }

    @Test
    fun `a failed request relays through the endpoint`() {
        assertEquals(
            "https://enktel.tv/api/stream?u=http%3A%2F%2Fcdn.example%2Flive%2F1.m3u8",
            RelayUrls.fallbackFor("http://cdn.example/live/1.m3u8"),
        )
    }

    /**
     * The guide, the playlist and both metadata proxies live on the relay's
     * own origin. A 403 from one of those is the service refusing, and asking
     * it again through itself would neither change the answer nor terminate.
     */
    @Test
    fun `our own origin is never relayed through itself`() {
        assertNull(RelayUrls.fallbackFor("https://enktel.tv/api/guide"))
        assertNull(RelayUrls.fallbackFor("https://enktel.tv/playlists/enktel-lineup.m3u"))
        assertNull(RelayUrls.fallbackFor("https://enktel.tv/api/tmdb/movie/550"))
        assertNull(
            RelayUrls.fallbackFor("https://enktel.tv/api/stream?u=http%3A%2F%2Fa.example%2F1.ts"),
        )
    }

    /** An imported playlist has no upstream to fetch. */
    @Test
    fun `a local file is not relayable`() {
        assertNull(RelayUrls.fallbackFor("file:///data/user/0/tv.enktel.app/files/playlists/1.m3u"))
        assertNull(RelayUrls.fallbackFor(""))
    }

    @Test
    fun `no endpoint means no fallback`() {
        assertNull(RelayUrls.fallbackFor("http://cdn.example/live/1.m3u8", base = ""))
        assertNull(RelayUrls.fallbackFor("http://cdn.example/live/1.m3u8", base = "   "))
    }

    /** Credentials ride along untouched — the relay grants no access of its own. */
    @Test
    fun `a panel URL keeps its credentials through the relay`() {
        val relayed = RelayUrls.fallbackFor("http://api.elg-26.com/live/user/pass/42.ts")
        assertEquals(
            "https://enktel.tv/api/stream?u=http%3A%2F%2Fapi.elg-26.com%2Flive%2Fuser%2Fpass%2F42.ts",
            relayed,
        )
    }

    // ---- country-pinned endpoints -------------------------------------

    /**
     * US first because the lineup is 83.7% American, GB second at 307
     * channels. Order is the whole point: a blocked stream pays one extra
     * request per country tried, so the likely one goes first.
     */
    @Test
    fun `the chain tries the US then the UK`() {
        assertEquals(
            listOf("https://enktel.tv/api/stream/us", "https://enktel.tv/api/stream/gb"),
            RelayUrls.regionBases(),
        )

        val chain = RelayUrls.fallbackChain("http://cdn.example/live/1.m3u8")
        assertEquals(2, chain.size)
        assertTrue(chain[0].startsWith("https://enktel.tv/api/stream/us?u="))
        assertTrue(chain[1].startsWith("https://enktel.tv/api/stream/gb?u="))
    }

    /**
     * No Croatian entry, and not by omission: no serverless region exists in
     * Croatia, so no endpoint here can present a Croatian address. A test that
     * asserted one would be asserting a lie.
     */
    @Test
    fun `there is no croatian endpoint to try`() {
        assertTrue(RelayUrls.regionBases().none { it.endsWith("/hr") })
    }

    /** Whatever [fallbackFor] refuses, the chain refuses for the same reason. */
    @Test
    fun `the chain is empty where relaying is wrong`() {
        assertTrue(RelayUrls.fallbackChain("https://enktel.tv/api/guide").isEmpty())
        assertTrue(RelayUrls.fallbackChain("file:///data/playlists/1.m3u").isEmpty())
        assertTrue(RelayUrls.fallbackChain("http://cdn.example/1.m3u8", base = "").isEmpty())
    }

    @Test
    fun `each region names itself for the on-screen note`() {
        val chain = RelayUrls.fallbackChain("http://cdn.example/live/1.m3u8")
        assertEquals("the US relay", RelayUrls.regionOf(chain[0]))
        assertEquals("the UK relay", RelayUrls.regionOf(chain[1]))
        assertEquals("the relay", RelayUrls.regionOf("https://enktel.tv/api/stream?u=x"))
    }
}
