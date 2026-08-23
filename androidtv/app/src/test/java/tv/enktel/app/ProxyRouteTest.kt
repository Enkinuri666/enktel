package tv.enktel.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.ProxyRoute

/**
 * The exit of last resort, for a channel served inside one country and
 * published nowhere else.
 *
 * Two properties matter more than the parsing. It must route **only** hosts
 * that have already refused this device, because sending 2,446 American
 * channels through Croatia would be slower for no gain; and it must stop
 * routing a host the detour did not help, or that host pays for an extra hop
 * forever to reach the same refusal.
 */
class ProxyRouteTest {

    @After
    fun tearDown() {
        ProxyRoute.configure(ProxyRoute.Config())
    }

    // ---- what a person actually types ---------------------------------

    @Test
    fun `a bare host and port parses`() {
        val c = ProxyRoute.parse("1.2.3.4:1080")
        assertEquals("1.2.3.4", c.host)
        assertEquals(1080, c.port)
        assertFalse(c.socks)
        assertTrue(c.usable)
    }

    @Test
    fun `a scheme selects the proxy type`() {
        assertTrue(ProxyRoute.parse("socks5://exit.example.hr:1080").socks)
        assertTrue(ProxyRoute.parse("SOCKS://exit.example.hr:1080").socks)
        assertFalse(ProxyRoute.parse("http://exit.example.hr:8080").socks)
        assertEquals("exit.example.hr", ProxyRoute.parse("https://exit.example.hr:8080/").host)
    }

    @Test
    fun `credentials ride along`() {
        val c = ProxyRoute.parse("1.2.3.4:1080", "user", "secret")
        assertEquals("user", c.username)
        assertEquals("secret", c.password)
    }

    /** An unusable entry must read as "no proxy", not as a half-configured one. */
    @Test
    fun `junk is not usable`() {
        assertFalse(ProxyRoute.parse("").usable)
        assertFalse(ProxyRoute.parse("   ").usable)
        assertFalse(ProxyRoute.parse("1.2.3.4").usable)          // no port
        assertFalse(ProxyRoute.parse("1.2.3.4:0").usable)
        assertFalse(ProxyRoute.parse("1.2.3.4:70000").usable)
        assertFalse(ProxyRoute.parse("1.2.3.4:notaport").usable)
    }

    // ---- routing ------------------------------------------------------

    @Test
    fun `nothing is routed when no proxy is configured`() {
        ProxyRoute.configure(ProxyRoute.Config())
        assertFalse(ProxyRoute.routeThroughProxy("webtvstream.bhtelecom.ba"))
        assertFalse(ProxyRoute.isRouted("webtvstream.bhtelecom.ba"))
    }

    @Test
    fun `a refused host is routed, and only that host`() {
        ProxyRoute.configure(ProxyRoute.parse("socks5://exit.example.hr:1080"))
        assertTrue(ProxyRoute.routeThroughProxy("webtvstream.bhtelecom.ba"))
        assertTrue(ProxyRoute.isRouted("webtvstream.bhtelecom.ba"))
        // Everything that has not refused anything stays direct.
        assertFalse(ProxyRoute.isRouted("jmp2.uk"))
        assertFalse(ProxyRoute.isRouted("i.mjh.nz"))
    }

    /**
     * The second call is how the caller learns a retry would repeat itself: the
     * request that just failed was already the proxied one.
     */
    @Test
    fun `routing a host twice reports no new route`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        assertTrue(ProxyRoute.routeThroughProxy("a.example"))
        assertFalse(ProxyRoute.routeThroughProxy("a.example"))
        assertFalse(ProxyRoute.routeThroughProxy("A.EXAMPLE"))
    }

    @Test
    fun `a detour that did not help is abandoned`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.routeThroughProxy("a.example")
        ProxyRoute.stopRouting("a.example")
        assertFalse(ProxyRoute.isRouted("a.example"))
        // And can be tried again later, since the failure may have been transient.
        assertTrue(ProxyRoute.routeThroughProxy("a.example"))
    }

    /**
     * A changed exit invalidates what was learned through the old one — a host
     * that only failed because the previous proxy was refused deserves another
     * direct attempt rather than inheriting the verdict.
     */
    @Test
    fun `changing the proxy forgets what was routed`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.routeThroughProxy("a.example")
        ProxyRoute.configure(ProxyRoute.parse("5.6.7.8:1080"))
        assertFalse(ProxyRoute.isRouted("a.example"))
    }

    @Test
    fun `clearing the proxy stops all routing`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.routeThroughProxy("a.example")
        ProxyRoute.configure(ProxyRoute.Config())
        assertFalse(ProxyRoute.available())
        assertFalse(ProxyRoute.isRouted("a.example"))
    }

    /**
     * The selector is what actually applies the decision — OkHttp asks it per
     * request, which is the only reason this can be a runtime setting on a
     * client built once at startup.
     */
    @Test
    fun `the selector routes only the marked host`() {
        ProxyRoute.configure(ProxyRoute.parse("socks5://exit.example.hr:1080"))
        ProxyRoute.routeThroughProxy("blocked.example")
        val sel = ProxyRoute.selector()

        val direct = sel.select(java.net.URI("https://open.example/live/1.m3u8"))
        assertEquals(listOf(java.net.Proxy.NO_PROXY), direct)

        val detour = sel.select(java.net.URI("https://blocked.example/live/1.m3u8"))
        assertEquals(1, detour.size)
        assertEquals(java.net.Proxy.Type.SOCKS, detour[0].type())
        assertEquals(
            "exit.example.hr:1080",
            (detour[0].address() as java.net.InetSocketAddress).let { "${it.hostString}:${it.port}" },
        )
    }

    /** An http:// endpoint has to produce an HTTP proxy, not a SOCKS one. */
    @Test
    fun `the scheme reaches the selector`() {
        ProxyRoute.configure(ProxyRoute.parse("http://exit.example.hr:8080"))
        ProxyRoute.routeThroughProxy("blocked.example")
        val detour = ProxyRoute.selector().select(java.net.URI("https://blocked.example/1.m3u8"))
        assertEquals(java.net.Proxy.Type.HTTP, detour[0].type())
    }
}
