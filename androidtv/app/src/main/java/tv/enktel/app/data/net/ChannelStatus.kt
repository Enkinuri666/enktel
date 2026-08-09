package tv.enktel.app.data.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Profile
import tv.enktel.app.player.PlaybackTelemetry
import java.util.concurrent.TimeUnit

/**
 * Live up/down state per channel, for the guide and the channel browser.
 *
 * ## Why this is deliberately timid
 *
 * The obvious implementation — fan out a request per channel and colour the
 * dots — is actively harmful on an IPTV line, and this codebase has already
 * shipped that mistake once: the connection-cap detector opened eight parallel
 * streams unconditionally, which on a one-connection line is seven guaranteed
 * rejections and, on panels that count failed attempts, a route to getting the
 * line temporarily blocked.
 *
 * A status indicator that knocks the user's stream off the air, or gets them
 * banned, is worse than no indicator. So:
 *
 *  - **Never probe while something is playing.** A live stream holds a
 *    connection; a probe alongside it is one more than the line was sold.
 *  - **Strictly sequential**, with a short timeout and the body closed
 *    immediately: enough bytes to know the server answered, nothing more.
 *  - **Bounded**: only the channels actually on screen, capped at
 *    [MAX_WATCHED], and each answer cached for [TTL_MS].
 *
 * The result is an indicator that is honest about not knowing. [State.UNKNOWN]
 * is the default and stays the default whenever probing would cost more than
 * the information is worth.
 */
object ChannelStatus {

    enum class State { UNKNOWN, UP, DOWN }

    /** Most channels probed for one screenful. */
    const val MAX_WATCHED = 24

    /** How long an answer is trusted before it is worth asking again. */
    private const val TTL_MS = 5 * 60_000L

    /** Gap between probes, so a sweep never looks like a burst to a WAF. */
    private const val SPACING_MS = 400L

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    private val checkedAt = mutableMapOf<String, Long>()

    /**
     * Refresh the status of [channels], skipping anything still within its TTL.
     *
     * Safe to call from a `LaunchedEffect` on every recomposition: it is
     * cancellable, it does nothing when probing would be unsafe, and it does
     * nothing when every entry is still fresh.
     */
    suspend fun watch(graph: AppGraph, profile: Profile, channels: List<Channel>) {
        if (channels.isEmpty()) return
        val now = System.currentTimeMillis()
        val stale = channels.take(MAX_WATCHED)
            .filter { now - (checkedAt[it.key] ?: 0L) > TTL_MS }
        if (stale.isEmpty()) return

        val format = runCatching {
            graph.settings.streamFormat.first()
        }.getOrDefault("hls")

        for (ch in stale) {
            // Re-checked inside the loop, not once up front: the user can
            // start playing at any point during a sweep, and the next probe
            // has to notice.
            if (!safeToProbe()) return
            val url = runCatching { graph.content.liveUrl(profile, ch, format) }.getOrNull()
            if (url.isNullOrBlank()) continue
            // diagHttp, not http: this probe exists to say whether a
            // channel answers. The failover interceptor turns a 403 into a
            // thrown IOException, which would report a channel that is
            // blocked — a real, actionable state — as simply dead.
            val state = probe(graph.diagHttp, url)
            checkedAt[ch.key] = System.currentTimeMillis()
            _states.value = _states.value + (ch.key to state)
            kotlinx.coroutines.delay(SPACING_MS)
        }
    }

    /** Forget everything — on profile switch, where the keys no longer apply. */
    fun reset() {
        checkedAt.clear()
        _states.value = emptyMap()
    }

    /**
     * A probe is only safe when it cannot take a connection the user needs.
     *
     * Playback freshness is the signal: [PlaybackTelemetry] keeps publishing
     * for a few seconds after a stream stops, which is exactly the margin
     * wanted here — a channel change should not be read as "idle".
     */
    private fun safeToProbe(): Boolean = !PlaybackTelemetry.state.value.isFresh()

    /**
     * One byte from [url], with the connection closed immediately.
     *
     * Any HTTP answer at all — including 403 and 404 — proves the *server* is
     * up, but not that the channel is. A live stream that is off air typically
     * answers 404 or 5xx, so those count as DOWN while 2xx/3xx count as UP.
     * A 401/403 is the panel refusing, not the channel failing, so it stays
     * UNKNOWN rather than painting a working channel red.
     */
    private suspend fun probe(http: OkHttpClient, url: String): State = withContext(Dispatchers.IO) {
        val client = http.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
        try {
            val req = Request.Builder().url(url)
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(req).execute().use { resp -> classify(resp.code) }
        } catch (e: Throwable) {
            val msg = e.message.orEmpty()
            // Same OkHttp quirk the live probe and the panel ping hit: a 407
            // received without a configured proxy is thrown, not returned. The
            // server answered, so this is not the channel being down.
            if (msg.contains("HTTP_PROXY_AUTH", true) || msg.contains("407")) State.UNKNOWN
            else State.DOWN
        }
    }

    internal fun classify(code: Int): State = when {
        code in 200..399 -> State.UP
        // The panel refusing us says nothing about the channel.
        code == 401 || code == 403 || code == 407 || code == 429 -> State.UNKNOWN
        else -> State.DOWN
    }
}
