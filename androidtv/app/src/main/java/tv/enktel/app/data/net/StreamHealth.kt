package tv.enktel.app.data.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rolling health snapshot for the shared OkHttp client.  [StreamHealthInterceptor]
 * feeds it as requests complete; UI reads it via [state] to render the network
 * quality chip on player screens.
 *
 * Kept intentionally coarse — the goal isn't a full-blown APM tool, just enough
 * signal to tell the user "your VPN / ISP is throttling us" without them having
 * to open a terminal.  The interceptor also uses the counters to decide when
 * automatic failover to a backup gateway kicks in.
 */
object StreamHealth {

    /** How the health chip renders. */
    enum class Quality { UNKNOWN, GOOD, FAIR, POOR, BLOCKED }

    data class Snapshot(
        val quality: Quality = Quality.UNKNOWN,
        /** Rolling mean fetch latency, ms.  0 while still warming up. */
        val meanLatencyMs: Int = 0,
        /** Number of 403 responses observed in the current window — the classic
         *  "geoblocked" signal on IPTV.  Anything >0 flips quality to BLOCKED. */
        val blocked403: Int = 0,
        /** Timeouts / IO errors in the current window. */
        val timeouts: Int = 0,
        /** Human-readable last-error string, if any.  Shown in the toast when
         *  auto-failover trips. */
        val lastError: String? = null,
        /** Backup gateway currently in use (host only), or null if the primary. */
        val activeGateway: String? = null,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    // Ring buffer of latencies — cheap rolling average without a coroutine.
    private const val WINDOW = 16
    private val ring = IntArray(WINDOW)
    private var ringIdx = 0
    private var ringFilled = 0

    @Synchronized
    fun recordSuccess(latencyMs: Long) {
        ring[ringIdx] = latencyMs.toInt().coerceAtLeast(1)
        ringIdx = (ringIdx + 1) % WINDOW
        if (ringFilled < WINDOW) ringFilled++
        publish()
    }

    @Synchronized
    fun recordBlocked(host: String) {
        val cur = _state.value
        _state.value = cur.copy(
            blocked403 = cur.blocked403 + 1,
            lastError = "403 from $host",
            quality = Quality.BLOCKED,
        )
    }

    @Synchronized
    fun recordTimeout(host: String, why: String) {
        val cur = _state.value
        _state.value = cur.copy(
            timeouts = cur.timeouts + 1,
            lastError = "$why · $host",
            quality = if (cur.timeouts + 1 >= 3) Quality.POOR else cur.quality,
        )
    }

    @Synchronized
    fun setActiveGateway(host: String?) {
        _state.value = _state.value.copy(activeGateway = host)
    }

    @Synchronized
    fun resetErrors() {
        val cur = _state.value
        _state.value = cur.copy(
            blocked403 = 0, timeouts = 0, lastError = null,
        )
    }

    private fun publish() {
        if (ringFilled == 0) return
        var sum = 0L
        for (i in 0 until ringFilled) sum += ring[i]
        val mean = (sum / ringFilled).toInt()
        val q = when {
            _state.value.blocked403 > 0 -> Quality.BLOCKED
            _state.value.timeouts >= 3 -> Quality.POOR
            mean > 2000 -> Quality.POOR
            mean > 900 -> Quality.FAIR
            else -> Quality.GOOD
        }
        _state.value = _state.value.copy(quality = q, meanLatencyMs = mean)
    }
}
