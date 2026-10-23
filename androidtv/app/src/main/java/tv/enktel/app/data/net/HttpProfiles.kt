package tv.enktel.app.data.net

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One HTTP client per kind of traffic, because OkHttp meters requests by host.
 *
 * ### The cap nobody configured
 *
 * An `OkHttpClient` carries a [Dispatcher], and a default `Dispatcher` allows
 * **five concurrent requests per host** (64 overall). That limit applies to
 * every call made with `enqueue`, and the two libraries that do most of this
 * app's work both enqueue:
 *
 *  - `androidx.media3.datasource.okhttp.OkHttpDataSource` enqueues and blocks
 *    on the future — so every segment, every playlist refresh and every byte
 *    of a progressive MP4 is an async call.
 *  - Coil's OkHttp fetcher awaits a `Callback`, so every poster and channel
 *    logo is one too.
 *
 * One panel serves the streams, the catalogue, the guide and the artwork, so
 * all of that is **one host**. Five slots, shared. A catalogue screen pulling
 * posters, an EPG refresh and a channel-status sweep could hold every one of
 * them while the player's next segment sat in the dispatcher queue — not slow
 * to download, not downloading at all. And because the app-wide client sets no
 * call timeout (deliberately: a VOD body is measured in gigabytes) a queued
 * segment had no deadline either; it waited behind whatever was in front of
 * it, for as long as that took.
 *
 * That is the "capped speed" with no cap in sight: the line is fine, the panel
 * is fine, and the request has not been sent yet.
 *
 * [tv.enktel.app.data.download.ParallelDownloader] already found this the hard
 * way and built itself a private dispatcher and pool. This generalises what it
 * did rather than leaving the fix in one corner of the app.
 *
 * ### Why each profile needs *both* a dispatcher and a pool
 *
 * `newBuilder()` copies the dispatcher and the connection pool **by
 * reference** — a derived client shares the parent's limits and the parent's
 * sockets unless it replaces them. So a profile that set only a dispatcher
 * would still have its keep-alive connections evicted by artwork (the default
 * pool holds five idle connections for the *whole client*), and a profile that
 * set only a pool would still queue behind it. Every profile here sets both,
 * which is also why [ProxyRoute] and [ParallelDownloader] say what they say
 * about the pool: half this fix was already learned once.
 *
 * ### Why these numbers
 *
 * They are ceilings, not targets — nothing opens a connection it does not need
 * — so the cost of being generous is zero and the cost of being tight is a
 * queued request. They are still bounded, because a panel that sees forty
 * sockets from one address may well throttle the address.
 */
object HttpProfiles {

    /**
     * Catalogue, guide, metadata and probes.
     *
     * Eight rather than five because a first sync fans out: categories, then
     * channels, then VOD, then series, while the EPG downloads beside them.
     * This is the one profile applied to the app-wide client itself, so it is
     * also the floor every derived client inherits if it forgets to set its
     * own.
     */
    const val SHARED_PER_HOST = 8
    const val SHARED_TOTAL = 48

    /**
     * Playback.
     *
     * Twelve, because the concurrent requests of a *single* viewer add up
     * faster than they look: an HLS stream can be fetching a video segment, a
     * separate audio rendition and a subtitle track while refreshing the media
     * playlist; Multi-View runs two engines at once; the zap preloader warms a
     * second connection; a DVR recording can be writing while someone watches.
     * Five covered none of those combinations, and the one that loses is
     * whichever request arrives last — usually the one on screen.
     */
    const val STREAM_PER_HOST = 12
    const val STREAM_TOTAL = 24

    /**
     * Artwork.
     *
     * A poster grid asks for thirty images at once and gets them eight at a
     * time, which is what a grid filling in should look like. The point of the
     * separate client is less the number than the isolation: artwork can no
     * longer take a slot the player needs, whatever it does.
     */
    const val IMAGE_PER_HOST = 8
    const val IMAGE_TOTAL = 32

    /** Idle sockets each profile keeps warm. The default is five per client. */
    const val STREAM_IDLE_CONNECTIONS = 8
    const val IMAGE_IDLE_CONNECTIONS = 8

    /** How long an idle stream socket is worth keeping, in minutes. */
    const val STREAM_KEEPALIVE_MIN = 5L

    /**
     * Artwork timeouts, in seconds.
     *
     * The app-wide client waits three minutes for a read and has no overall
     * deadline, which is right for a film and absurd for a 40 KB logo: a stalled
     * poster used to hold a connection — and, before this, a dispatcher slot —
     * for the full three minutes. A picture that cannot arrive inside half a
     * minute is not worth a socket.
     */
    const val IMAGE_CONNECT_TIMEOUT_SEC = 15L
    const val IMAGE_READ_TIMEOUT_SEC = 20L

    /**
     * The overall deadline for one image.
     *
     * Forty-five seconds rather than something tighter because the read
     * timeout already covers a dead socket: this one has to be loose enough
     * that a megabyte backdrop still arrives on a slow line, and its job is
     * only to stop a half-open connection being held indefinitely.
     */
    const val IMAGE_CALL_TIMEOUT_SEC = 45L

    /**
     * The limits for the app-wide client, applied while it is being built.
     *
     * Everything derived from that client with `newBuilder()` and no dispatcher
     * of its own — the diagnostics client, a status probe, a proxied request —
     * inherits these, which is the intent: none of them should be able to queue
     * a stream, and none of them is the stream.
     */
    fun tuneShared(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .dispatcher(dispatcher(SHARED_TOTAL, SHARED_PER_HOST))

    /**
     * The client the player, the preloader and the recorder use.
     *
     * Derived so it keeps the user agent, the stream-health failover, the TLS
     * fallbacks and the NO_PROXY policy — a stream must differ from any other
     * request in nothing but its budget.
     */
    fun forStreaming(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .dispatcher(dispatcher(STREAM_TOTAL, STREAM_PER_HOST))
        .connectionPool(
            ConnectionPool(STREAM_IDLE_CONNECTIONS, STREAM_KEEPALIVE_MIN, TimeUnit.MINUTES),
        )
        .build()

    /** The client Coil draws every poster and channel logo through. */
    fun forImages(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .dispatcher(dispatcher(IMAGE_TOTAL, IMAGE_PER_HOST))
        .connectionPool(
            ConnectionPool(IMAGE_IDLE_CONNECTIONS, STREAM_KEEPALIVE_MIN, TimeUnit.MINUTES),
        )
        .connectTimeout(IMAGE_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(IMAGE_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(IMAGE_CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    /** A dispatcher with explicit limits rather than OkHttp's defaults. */
    fun dispatcher(maxRequests: Int, maxPerHost: Int): Dispatcher = Dispatcher().apply {
        this.maxRequests = maxRequests
        this.maxRequestsPerHost = maxPerHost
    }
}
