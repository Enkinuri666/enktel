package tv.enktel.app.data.net

import java.util.concurrent.atomic.AtomicLong

/**
 * Adds up what several sockets are pulling at once, and says how fast that is.
 *
 * ### Why a speed test cannot be one connection
 *
 * A single TCP stream measures a single TCP stream. IPTV panels rate-limit per
 * request — that is how one account cannot saturate a server — and plenty of
 * ISPs and CDNs shape per flow as well. So a one-socket sample on a 200 Mbps
 * line routinely reports whatever one socket is allowed, which looked exactly
 * like a slow connection and sent the viewer to argue with their ISP about a
 * number that was never about their ISP. Every real speed test opens several
 * connections for this reason; this is the bookkeeping that lets ours.
 *
 * ### The arithmetic is deliberately conservative
 *
 * Elapsed time runs from the *first* byte any worker saw to the *last* byte any
 * worker saw, and every worker's bytes are divided by that one span. A socket
 * that took 300 ms longer to open is therefore charged for the whole window
 * rather than its own, which under-reports slightly. That is the right
 * direction to be wrong in: a speed test that flatters a connection is worse
 * than useless, because the next thing it does is recommend a bitrate.
 *
 * Pure and thread-safe: workers call [record] from several IO threads, the
 * clock is passed in rather than read, and [ThroughputMeterTest] pins it.
 */
class ThroughputMeter(
    /** How long after the first byte the measurement window runs. */
    private val windowNs: Long,
    /** Minimum gap between UI samples, across all workers. */
    private val emitIntervalNs: Long = DEFAULT_EMIT_INTERVAL_NS,
) {

    /** One reading for the meter on screen. */
    data class Sample(
        val elapsedSec: Double,
        val instantMbps: Double,
        val averageMbps: Double,
        val bytes: Long,
    )

    private val total = AtomicLong(0)
    private val lock = Any()

    /**
     * Whether a byte has arrived, held apart from the timestamp.
     *
     * Not `firstByteNs == 0L`, because 0 is a legal reading: `System.nanoTime`
     * counts from an arbitrary origin and is free to return it. Overloading
     * the timestamp as its own "not yet" sentinel is the kind of thing that
     * works for years and then reports a connection as unmeasurable.
     */
    @Volatile private var started = false

    @Volatile private var firstByteNs = 0L
    @Volatile private var lastByteNs = 0L
    private var lastEmitNs = 0L
    private var lastEmitBytes = 0L

    /** Bytes counted so far, across every worker. */
    val bytes: Long get() = total.get()

    /** True once any worker has seen a byte. */
    val hasBytes: Boolean get() = started

    /** Count [n] bytes read at [nowNs]. Safe from any thread. */
    fun record(n: Long, nowNs: Long) {
        if (n <= 0L) return
        if (!started) {
            synchronized(lock) {
                if (!started) {
                    firstByteNs = nowNs
                    lastEmitNs = nowNs
                    started = true
                }
            }
        }
        total.addAndGet(n)
        lastByteNs = nowNs
    }

    /** Has the window closed? Workers stop reading when it has. */
    fun windowExpired(nowNs: Long): Boolean =
        started && nowNs - firstByteNs >= windowNs

    /**
     * A reading, or null when one is not due yet.
     *
     * The interval is global rather than per worker: four sockets each
     * reporting five times a second is twenty readings a second, which is not
     * a meter, it is a flicker.
     */
    fun sampleOrNull(nowNs: Long): Sample? = synchronized(lock) {
        if (!started) return null
        val first = firstByteNs
        if (nowNs - lastEmitNs < emitIntervalNs) return null
        val b = total.get()
        val instantSec = (nowNs - lastEmitNs) / NANOS_PER_SEC
        val totalSec = (nowNs - first) / NANOS_PER_SEC
        val sample = Sample(
            elapsedSec = totalSec,
            instantMbps = if (instantSec > 0) bitsPerSecToMbps(b - lastEmitBytes, instantSec) else 0.0,
            averageMbps = if (totalSec > 0) bitsPerSecToMbps(b, totalSec) else 0.0,
            bytes = b,
        )
        lastEmitNs = nowNs
        lastEmitBytes = b
        sample
    }

    /**
     * The result, in Mbps, or 0.0 when there is not enough to divide by.
     *
     * Zero rather than a guess: half a second of bytes over a connection still
     * in TCP slow-start is not a measurement, and the report says "unable to
     * measure" rather than printing a number the viewer would act on.
     */
    fun mbps(): Double {
        val first = firstByteNs
        val last = lastByteNs
        val b = total.get()
        if (!started || b <= 0L) return 0.0
        val sec = (last - first) / NANOS_PER_SEC
        if (sec < MIN_SAMPLE_SEC) return 0.0
        return bitsPerSecToMbps(b, sec)
    }

    private fun bitsPerSecToMbps(bytes: Long, seconds: Double): Double =
        (bytes * 8.0 / seconds) / 1_000_000.0

    companion object {
        /** ~5 readings a second: live enough to watch, steady enough to read. */
        const val DEFAULT_EMIT_INTERVAL_NS = 200_000_000L

        /** Below this, the sample is slow-start rather than throughput. */
        const val MIN_SAMPLE_SEC = 0.5

        private const val NANOS_PER_SEC = 1_000_000_000.0
    }
}
