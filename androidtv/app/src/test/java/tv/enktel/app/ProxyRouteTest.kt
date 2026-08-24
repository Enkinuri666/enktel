package tv.enktel.app

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.ProxyRoute

/**
 * The exit of last resort, for a channel served inside one country and
 * published nowhere else.
 *
 * These used to assert on a `ProxySelector`, which is how the feature shipped
 * doing nothing at all. A geo-block is a healthy HTTP exchange that leaves a
 * pooled connection behind; the retry's `Address` compares equal, so OkHttp
 * reuses the pooled **direct** connection and never asks the selector. The
 * selector was correct in isolation and unreachable in practice — which is
 * precisely what a unit test of it could not show.
 *
 * So what is pinned now is the thing that makes the detour real: a client with
 * a **pinned** proxy and a connection pool of its own.
 */
class ProxyRouteTest {

    private val base = OkHttpClient.Builder().build()

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

    // ---- the client that actually takes the detour ----------------------

    @Test
    fun `there is no client until something is configured`() {
        ProxyRoute.attach(base)
        ProxyRoute.configure(ProxyRoute.Config())
        assertNull(ProxyRoute.clientOrNull())
    }

    /** Attaching supplies the timeouts, TLS fallbacks and agent to copy. */
    @Test
    fun `an attached base and a configured exit yield a client`() {
        ProxyRoute.attach(OkHttpClient.Builder().build())
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        assertNotNull(ProxyRoute.clientOrNull())
    }

    /**
     * The proxy is **pinned**, not selected. A selected proxy is consulted only
     * when a new connection is opened, and the whole point of this path is that
     * a usable connection already exists.
     */
    @Test
    fun `the client pins the proxy rather than selecting it`() {
        ProxyRoute.attach(base)
        ProxyRoute.configure(ProxyRoute.parse("socks5://exit.example.hr:1080"))
        val proxy = ProxyRoute.clientOrNull()!!.proxy!!
        assertEquals(java.net.Proxy.Type.SOCKS, proxy.type())
        assertEquals(
            "exit.example.hr:1080",
            (proxy.address() as java.net.InetSocketAddress).let { "${it.hostString}:${it.port}" },
        )
    }

    @Test
    fun `an http endpoint produces an http proxy`() {
        ProxyRoute.attach(base)
        ProxyRoute.configure(ProxyRoute.parse("http://exit.example.hr:8080"))
        assertEquals(java.net.Proxy.Type.HTTP, ProxyRoute.clientOrNull()!!.proxy!!.type())
    }

    /**
     * Its own pool, not the base client's. Sharing the pool is exactly how a
     * proxied request ends up on a connection that was opened direct.
     */
    @Test
    fun `the client does not share the direct connection pool`() {
        ProxyRoute.attach(base)
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        assertFalse(ProxyRoute.clientOrNull()!!.connectionPool === base.connectionPool)
    }

    @Test
    fun `the client is built once and reused`() {
        ProxyRoute.attach(base)
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        assertSame(ProxyRoute.clientOrNull(), ProxyRoute.clientOrNull())
    }

    @Test
    fun `changing the exit builds a new client`() {
        ProxyRoute.attach(base)
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        val first = ProxyRoute.clientOrNull()
        ProxyRoute.configure(ProxyRoute.parse("5.6.7.8:1080"))
        assertFalse(first === ProxyRoute.clientOrNull())
    }

    // ---- which hosts are worth the detour ------------------------------

    @Test
    fun `nothing is worth trying when no proxy is configured`() {
        ProxyRoute.configure(ProxyRoute.Config())
        assertFalse(ProxyRoute.shouldTry("webtvstream.bhtelecom.ba"))
        assertFalse(ProxyRoute.isKnownGood("webtvstream.bhtelecom.ba"))
    }

    @Test
    fun `an untried host is worth trying, and is not yet known good`() {
        ProxyRoute.configure(ProxyRoute.parse("socks5://exit.example.hr:1080"))
        assertTrue(ProxyRoute.shouldTry("webtvstream.bhtelecom.ba"))
        assertFalse(ProxyRoute.isKnownGood("webtvstream.bhtelecom.ba"))
    }

    /**
     * A stream fetches a segment every few seconds. Retrying a detour that did
     * not help would double the requests for every one of them, forever, to
     * arrive at the same refusal more slowly.
     */
    @Test
    fun `a detour that did not help is not tried again`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.noteFailed("a.example")
        assertFalse(ProxyRoute.shouldTry("a.example"))
        assertFalse(ProxyRoute.shouldTry("A.EXAMPLE"))
    }

    /** Once proven, the exit is used from the start rather than after a refusal. */
    @Test
    fun `a host the exit served is remembered`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.noteWorked("a.example")
        assertTrue(ProxyRoute.isKnownGood("a.example"))
        assertTrue(ProxyRoute.isKnownGood("A.EXAMPLE"))
        // And only that host.
        assertFalse(ProxyRoute.isKnownGood("b.example"))
    }

    /** An exit that starts working again must not stay written off. */
    @Test
    fun `working supersedes a previous failure and the reverse`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.noteFailed("a.example")
        ProxyRoute.noteWorked("a.example")
        assertTrue(ProxyRoute.isKnownGood("a.example"))
        assertTrue(ProxyRoute.shouldTry("a.example"))

        ProxyRoute.noteFailed("a.example")
        assertFalse(ProxyRoute.isKnownGood("a.example"))
        assertFalse(ProxyRoute.shouldTry("a.example"))
    }

    /**
     * A changed exit invalidates what was learned through the old one — a host
     * that only failed because the previous proxy was refused deserves another
     * attempt rather than inheriting the verdict.
     */
    @Test
    fun `changing the proxy forgets what was learned`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.noteFailed("a.example")
        ProxyRoute.noteWorked("b.example")
        ProxyRoute.configure(ProxyRoute.parse("5.6.7.8:1080"))
        assertTrue(ProxyRoute.shouldTry("a.example"))
        assertFalse(ProxyRoute.isKnownGood("b.example"))
    }

    @Test
    fun `clearing the proxy stops everything`() {
        ProxyRoute.configure(ProxyRoute.parse("1.2.3.4:1080"))
        ProxyRoute.noteWorked("a.example")
        ProxyRoute.configure(ProxyRoute.Config())
        assertFalse(ProxyRoute.available())
        assertFalse(ProxyRoute.isKnownGood("a.example"))
        assertFalse(ProxyRoute.shouldTry("a.example"))
    }
}
