package tv.enktel.app

import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.HttpProfiles

/**
 * The cap nobody configured.
 *
 * One panel serves the streams, the catalogue, the guide and the artwork, so
 * all of it is one host — and an OkHttp client allows five concurrent requests
 * per host by default. Both libraries that do the work here use `enqueue`
 * (Media3's OkHttpDataSource blocks on a future; Coil awaits a Callback), so
 * both are metered by that limit. A poster grid could hold every slot while
 * the player's next segment waited in a queue, with no call timeout to end the
 * wait: a stall that looks exactly like a slow line and is not one.
 *
 * These pin the budgets and, more importantly, the isolation — because the way
 * this fix silently fails is a derived client that shares the thing it was
 * supposed to replace.
 */
class HttpProfilesTest {

    private val marker = Interceptor { chain: Interceptor.Chain -> chain.proceed(chain.request()) }

    private fun base(): OkHttpClient = HttpProfiles
        .tuneShared(OkHttpClient.Builder().addInterceptor(marker))
        .build()

    @Test
    fun `OkHttp's own default is the five-per-host cap this exists to replace`() {
        // The premise of the whole change, pinned rather than remembered: if a
        // future OkHttp ships a different default, this test says so instead
        // of the comments quietly becoming wrong.
        assertEquals(5, Dispatcher().maxRequestsPerHost)
        assertEquals(64, Dispatcher().maxRequests)
    }

    @Test
    fun `the shared client no longer runs on OkHttp's default budget`() {
        assertEquals(HttpProfiles.SHARED_PER_HOST, base().dispatcher.maxRequestsPerHost)
        assertEquals(HttpProfiles.SHARED_TOTAL, base().dispatcher.maxRequests)
        assertTrue(HttpProfiles.SHARED_PER_HOST > 5)
    }

    @Test
    fun `playback gets a dispatcher of its own, not a share of one`() {
        // newBuilder copies the dispatcher by reference. A profile that did
        // not replace it would inherit the parent's queue, which is the exact
        // failure this is meant to prevent and would look like it worked.
        val b = base()
        val stream = HttpProfiles.forStreaming(b)
        assertNotSame(b.dispatcher, stream.dispatcher)
        assertEquals(HttpProfiles.STREAM_PER_HOST, stream.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun `playback gets a connection pool of its own too`() {
        // The pool is copied by reference as well, and it holds five idle
        // sockets for the whole client. Sharing it means artwork evicting the
        // keep-alive connection the stream is riding on, which costs a fresh
        // handshake and another TCP slow-start per segment.
        val b = base()
        val stream = HttpProfiles.forStreaming(b)
        assertNotSame(b.connectionPool, stream.connectionPool)
        assertNotSame(
            HttpProfiles.forImages(b).connectionPool,
            stream.connectionPool,
        )
    }

    @Test
    fun `artwork and playback cannot take each other's slots`() {
        val b = base()
        assertNotSame(
            HttpProfiles.forImages(b).dispatcher,
            HttpProfiles.forStreaming(b).dispatcher,
        )
    }

    @Test
    fun `a stream can run more requests at once than a single rendition needs`() {
        // Video, audio and subtitles can be separate renditions, the playlist
        // refreshes beside them, Multi-View runs two engines and the zap
        // preloader warms a connection. Five did not cover that.
        assertTrue(HttpProfiles.STREAM_PER_HOST >= 8)
        assertTrue(HttpProfiles.STREAM_TOTAL >= HttpProfiles.STREAM_PER_HOST)
    }

    @Test
    fun `a poster never holds a connection for as long as a film`() {
        // The app-wide client waits three minutes for a read and has no call
        // timeout at all, which is right for a multi-gigabyte VOD body and
        // absurd for a logo.
        val images = HttpProfiles.forImages(base())
        assertTrue("an image call must have a deadline", images.callTimeoutMillis > 0)
        assertTrue(images.callTimeoutMillis <= 60_000)
        assertTrue(images.readTimeoutMillis < 60_000)
    }

    @Test
    fun `every profile keeps the user agent, failover and TLS policy`() {
        // Derived rather than assembled, deliberately: a stream must differ
        // from any other request in nothing but its budget. If a profile ever
        // starts building its own client, the panel whitelisting and the
        // API 23 cipher list go with it.
        val b = base()
        for (client in listOf(HttpProfiles.forStreaming(b), HttpProfiles.forImages(b))) {
            assertTrue(
                "profile dropped the base client's interceptors",
                client.interceptors.contains(marker),
            )
        }
    }

    @Test
    fun `the budgets stay within what a panel will tolerate from one address`() {
        // Ceilings, not targets — but a panel that sees dozens of sockets from
        // one address may throttle the address, which would be this fix
        // causing the problem it set out to solve.
        assertTrue(HttpProfiles.STREAM_PER_HOST <= 16)
        assertTrue(HttpProfiles.IMAGE_PER_HOST <= 16)
        assertTrue(HttpProfiles.SHARED_PER_HOST <= 16)
    }
}
