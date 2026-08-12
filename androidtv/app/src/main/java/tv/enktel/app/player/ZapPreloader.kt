package tv.enktel.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Rapid channel-zapping latency hider.
 *
 * The player and the app share one OkHttp client, so the connection carrying
 * the channel now playing is already hot — but it is busy, and a zap needs a
 * second one. Paying for DNS, TCP and TLS at the moment the viewer presses the
 * button is routinely 300–800 ms of the delay they feel; opening that second
 * connection early collapses it to whatever the pool's keep-alive window has
 * left. That is the whole of what this does. It is not a media preloader: the
 * zap still resolves and plays its own candidates.
 *
 * ### Why a raw MPEG-TS URL cannot be warmed like a playlist
 *
 * On a `/live` Xtream line, `.m3u8` returns a few hundred bytes of text and
 * ends. A raw `.ts` URL returns video and does not end — the response body is
 * the broadcast, and nothing terminates it but the client hanging up. Reading
 * such a response to completion, which is what a plain `execute().use { }`
 * invites, means holding a panel session open and pulling live video for a
 * channel nobody is watching, for as long as the warm-up is allowed to run.
 * On a metered connection that is somebody's data; on a capped line it is a
 * session that should have been available for the zap.
 *
 * So the request is a HEAD, the client used is a short-timeout clone, and the
 * response is closed without reading the body. Panels that reject HEAD answer
 * 405 — which is fine and even ideal, because the handshake that was the point
 * of the exercise has already happened by the time they say so.
 *
 * ### Everything here is best-effort
 *
 * A failed warm-up — wrong URL shape, 405, timeout, panel refusing a second
 * session — has no effect on the zap that follows. It is cancelled the moment
 * a new one is requested, and [cancel] exists so tuning away stops the work
 * immediately rather than leaving a request in flight against the cap.
 */
class ZapPreloader(http: OkHttpClient) {

    /**
     * A short-tempered clone of the shared client.
     *
     * The app's client waits 45 s to connect and 180 s to read, which is right
     * for a stream that must survive a slow reseller relay and catastrophic for
     * speculative work: a warm-up for a channel the viewer never selects would
     * sit on a panel session for three minutes. Five seconds is longer than any
     * handshake worth waiting for and short enough that a dead host costs
     * nothing.
     *
     * Cloned rather than built fresh, so it shares the connection pool — which
     * is the entire point, since the pooled connection is the thing being
     * warmed — along with the interceptors, User-Agent and TLS settings.
     */
    private val warmHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        // A warm-up that gets redirected has still done its handshake; chasing
        // the redirect only opens a second session somewhere else.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private var job: Job? = null

    /**
     * Warm a connection for each URL in [urls].
     *
     * Callers should pass one URL. See [ZapPlan.target] for why warming both
     * directions costs twice as much to cover a guess, and [ZapPlan.shouldWarm]
     * for when it should not happen at all.
     */
    fun warm(scope: CoroutineScope, urls: List<String>) {
        job?.cancel()
        if (urls.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            for (url in urls) {
                try {
                    val req = Request.Builder().url(url).head().build()
                    // Closed without reading. On a raw .ts URL the body is the
                    // broadcast — see the note above on why that matters.
                    warmHttp.newCall(req).execute().close()
                } catch (_: Throwable) {
                    // Expected and harmless: 405 from a panel that dislikes
                    // HEAD, a timeout, a candidate shape this line does not
                    // serve. The handshake has usually happened regardless,
                    // and the real zap tries the full fallback chain itself.
                }
            }
        }
    }

    fun cancel() { job?.cancel() }
}
