package tv.enktel.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Rapid channel-zapping latency hider.
 *
 * ExoPlayer's own preloading infra is built around scheduled playlists,
 * not "the user might flip up or down at any moment" live TV browsing.
 * The cheap, high-value win here is smaller: fire a background HEAD
 * request at the adjacent channels' stream URLs so DNS resolution, the
 * TCP handshake, and (for HLS) TLS negotiation are already warm in the
 * shared OkHttp connection pool by the time the user actually zaps.
 * A cold connection to an IPTV panel is routinely 300-800 ms of the
 * channel-change delay; warming it ahead of time collapses that to
 * whatever's left in the connection pool's keep-alive window.
 *
 * Deliberately fire-and-forget: a failed warm-up (wrong URL shape, panel
 * rejects HEAD, timeout) has zero effect on the actual zap — [zap] in
 * LivePlayerScreen always resolves and plays fresh candidates itself.
 */
class ZapPreloader(private val http: OkHttpClient) {
    private var job: Job? = null

    /** Warm connections to every URL in [urls] (adjacent-channel candidates,
     *  first-choice format only — no need to warm the whole fallback chain). */
    fun warm(scope: CoroutineScope, urls: List<String>) {
        job?.cancel()
        if (urls.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            for (url in urls) {
                try {
                    val req = Request.Builder().url(url).head().build()
                    http.newCall(req).execute().use { /* discard body, keep the socket alive */ }
                } catch (_: Throwable) {
                    // Expected for panels that reject HEAD or don't have this
                    // exact candidate — the real play() call still tries the
                    // full fallback chain when the user actually zaps.
                }
            }
        }
    }

    fun cancel() { job?.cancel() }
}
