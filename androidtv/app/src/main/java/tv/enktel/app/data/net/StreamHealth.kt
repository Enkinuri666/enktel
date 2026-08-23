package tv.enktel.app.data.net

import androidx.annotation.VisibleForTesting
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

    /**
     * How far back a reading still counts.
     *
     * Everything here used to be a lifetime total, and that produced the two
     * complaints this window fixes. `timeouts` and `blocked403` only ever went
     * up — `resetErrors()` existed but nothing called it — so three timeouts
     * anywhere in the session pinned quality to POOR until the process died,
     * and a single 403 pinned it to BLOCKED. Every channel the user tuned to
     * afterwards showed the same stuck chip, because the chip was never about
     * the channel: it reports one process-wide snapshot.
     *
     * The latency figure had the matching problem in the other direction. The
     * mean was recomputed only when a request *completed*, and steady playback
     * of a live stream is one long-lived connection making very few new
     * requests — so the number froze at whatever the last burst measured
     * (catalogue load, EPG fetch, artwork) and sat there looking current.
     *
     * A minute is long enough to survive an ad break or a channel change
     * without the chip flickering, short enough that a fault which has cleared
     * stops being reported as present.
     */
    private const val WINDOW_MS = 60_000L

    /** Samples are timestamped so they can age out; see [WINDOW_MS]. */
    private data class Sample(val atMs: Long, val ms: Int)

    private val latencies = ArrayDeque<Sample>()
    private val timeoutsAt = ArrayDeque<Long>()
    private val blockedAt = ArrayDeque<Long>()
    private var lastError: String? = null
    private var lastErrorAtMs = 0L
    private var gateway: String? = null

    /**
     * Wall clock rather than [android.os.SystemClock]: this object is plain
     * Kotlin with no Android dependency, which keeps it unit-testable. A
     * backwards clock jump can only park a sample slightly too long, and the
     * window flushes itself within the minute.
     */
    @VisibleForTesting
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    @Synchronized
    fun recordSuccess(latencyMs: Long) {
        latencies.addLast(Sample(nowMs(), latencyMs.toInt().coerceAtLeast(1)))
        publish()
    }

    @Synchronized
    fun recordBlocked(host: String) {
        blockedAt.addLast(nowMs())
        lastError = "403 from $host"
        lastErrorAtMs = nowMs()
        publish()
    }

    @Synchronized
    fun recordTimeout(host: String, why: String) {
        timeoutsAt.addLast(nowMs())
        lastError = "$why · $host"
        lastErrorAtMs = nowMs()
        publish()
    }

    @Synchronized
    fun setActiveGateway(host: String?) {
        gateway = host
        publish()
    }

    /**
     * Record what a recovery just did, so it is visible somewhere.
     *
     * The interceptor has always described its own failovers through a
     * `notify` callback, and the app never supplied one — so the description
     * went into an empty lambda and a stream that failed over looked exactly
     * like one that never tried. That is a bad property in general and a
     * blocking one when the question is "did the failover run at all", which
     * is the only question a viewer staring at a blocked channel can ask.
     *
     * Stored as [Snapshot.lastError] because that is the field the health chip
     * and the system monitor already show. Not an error, despite the name; the
     * field predates there being anything but errors to put in it.
     */
    @Synchronized
    fun note(message: String) {
        lastError = message
        lastErrorAtMs = nowMs()
        publish()
    }

    /**
     * Re-evaluate without a new reading.
     *
     * The chip is driven by this on a timer. Without it a fault that has
     * stopped recurring never clears, because clearing depends on old readings
     * ageing out and nothing ages them out except another request arriving —
     * which, on a healthy long-lived stream, may be minutes away.
     */
    @Synchronized
    fun refresh() = publish()

    /** Drop everything. For a profile switch, where none of it applies. */
    @Synchronized
    fun reset() {
        latencies.clear()
        timeoutsAt.clear()
        blockedAt.clear()
        lastError = null
        lastErrorAtMs = 0L
        gateway = null
        _state.value = Snapshot()
    }

    private fun prune(now: Long) {
        while (latencies.isNotEmpty() && now - latencies.first().atMs > WINDOW_MS) latencies.removeFirst()
        while (timeoutsAt.isNotEmpty() && now - timeoutsAt.first() > WINDOW_MS) timeoutsAt.removeFirst()
        while (blockedAt.isNotEmpty() && now - blockedAt.first() > WINDOW_MS) blockedAt.removeFirst()
        if (lastError != null && now - lastErrorAtMs > WINDOW_MS) lastError = null
    }

    private fun publish() {
        val now = nowMs()
        prune(now)
        val mean = if (latencies.isEmpty()) 0 else (latencies.sumOf { it.ms.toLong() } / latencies.size).toInt()
        val q = when {
            blockedAt.isNotEmpty() -> Quality.BLOCKED
            timeoutsAt.size >= 3 -> Quality.POOR
            // No recent reading is not a verdict. Reporting the last known
            // mean here is what made a stale number look live.
            latencies.isEmpty() -> Quality.UNKNOWN
            mean > 2000 -> Quality.POOR
            mean > 900 -> Quality.FAIR
            else -> Quality.GOOD
        }
        _state.value = Snapshot(
            quality = q,
            meanLatencyMs = mean,
            blocked403 = blockedAt.size,
            timeouts = timeoutsAt.size,
            lastError = lastError,
            activeGateway = gateway,
        )
    }
}
