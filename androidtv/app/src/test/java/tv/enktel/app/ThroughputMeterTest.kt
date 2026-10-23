package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.SpeedTestEngine
import tv.enktel.app.data.net.ThroughputMeter

/**
 * What a speed test is allowed to claim.
 *
 * The figure drives a recommendation — "your line is best suited to SD" — so
 * the arithmetic matters more than it looks. A single socket measures a single
 * socket, and an IPTV panel rate-limits per request, which is how a 200 Mbps
 * line was being reported at the speed of one allowance and the viewer sent to
 * argue with their ISP about it.
 */
class ThroughputMeterTest {

    private val second = 1_000_000_000L
    private val window = 17 * second
    private val mb = 1024L * 1024L

    private fun meter() = ThroughputMeter(window)

    @Test
    fun `four sockets count as one line`() {
        // The whole point: bytes from every worker land in one total, so the
        // answer is the line's capacity rather than one request's allowance.
        val m = meter()
        val t0 = 1_000L
        repeat(4) { m.record(8 * mb, t0) }
        m.record(1, t0 + second)
        // 32 MB in one second ≈ 268 Mbps.
        assertEquals(268.4, m.mbps(), 1.0)
    }

    @Test
    fun `elapsed runs from the first byte to the last, not from the call`() {
        // Connecting is not transferring. Starting the clock at the request
        // charges the sample for DNS, TCP and TLS, which on a distant panel is
        // most of a second of "zero throughput" folded into the average.
        val m = meter()
        val firstByte = 5 * second
        m.record(10 * mb, firstByte)
        m.record(10 * mb, firstByte + 2 * second)
        // 20 MB over 2 s, not over 7.
        assertEquals(83.9, m.mbps(), 1.0)
    }

    @Test
    fun `a worker that opens late is charged for the whole window`() {
        // Deliberately conservative: every worker's bytes are divided by the
        // span from the earliest first byte to the latest last byte. A speed
        // test that flatters a connection is worse than useless, because the
        // next thing it does is recommend a bitrate.
        val m = meter()
        m.record(10 * mb, second)
        m.record(10 * mb, 3 * second)
        val generous = (20 * mb * 8.0 / 1.0) / 1_000_000.0
        assertTrue("must not report the optimistic figure", m.mbps() < generous)
    }

    @Test
    fun `nothing measured is zero, not a number`() {
        // "Unable to measure" is a usable answer. A figure invented from a
        // fraction of a second of slow-start is not.
        assertEquals(0.0, meter().mbps(), 0.0001)
        val m = meter()
        m.record(mb, second)
        m.record(mb, second + 100_000_000L) // 0.1 s of samples
        assertEquals(0.0, m.mbps(), 0.0001)
    }

    @Test
    fun `the window closes on the first byte, not on the first worker`() {
        val m = meter()
        assertFalse("nothing has arrived yet", m.windowExpired(50 * second))
        m.record(mb, 10 * second)
        assertFalse(m.windowExpired(10 * second + window - 1))
        assertTrue(m.windowExpired(10 * second + window))
    }

    @Test
    fun `readings are paced across all workers, not per worker`() {
        // Four sockets each reporting five times a second is twenty readings a
        // second, which is not a meter, it is a flicker.
        val m = meter()
        m.record(mb, 0)
        assertNull("too soon after the first byte", m.sampleOrNull(100_000_000L))
        assertNotNull(m.sampleOrNull(ThroughputMeter.DEFAULT_EMIT_INTERVAL_NS))
        assertNull(
            "a second worker must not emit straight after the first",
            m.sampleOrNull(ThroughputMeter.DEFAULT_EMIT_INTERVAL_NS + 1),
        )
    }

    @Test
    fun `a reading reports the stretch since the last one, and the whole run`() {
        val m = meter()
        m.record(mb, 0)
        m.sampleOrNull(second)
        m.record(9 * mb, second)
        val s = m.sampleOrNull(2 * second)
        assertNotNull(s)
        // Instant covers the 9 MB of the second second; average covers 10 MB
        // over two.
        assertEquals(75.5, s!!.instantMbps, 1.0)
        assertEquals(41.9, s.averageMbps, 1.0)
        assertEquals(10 * mb, s.bytes)
    }

    @Test
    fun `a zero-length read never starts the clock`() {
        // A socket that connects and delivers nothing would otherwise latch
        // the first-byte timestamp and have its siblings' bytes divided by a
        // span that began before any of them arrived.
        val m = meter()
        m.record(0, second)
        assertFalse(m.hasBytes)
        assertEquals(0.0, m.mbps(), 0.0001)
    }

    @Test
    fun `a clock reading of zero is a time, not an absence`() {
        // System.nanoTime counts from an arbitrary origin and may return 0.
        // Using the timestamp as its own "nothing yet" sentinel is the kind of
        // bug that works for years and then reports a healthy line as
        // unmeasurable on the one device whose clock starts there.
        val m = meter()
        m.record(mb, 0)
        assertTrue(m.hasBytes)
        assertEquals("one instant is not a span", 0.0, m.mbps(), 0.0001)
        m.record(mb, second)
        // 2 MB over the second that followed.
        assertEquals(16.8, m.mbps(), 0.5)
    }

    // ---- how many sockets to open ------------------------------------------

    @Test
    fun `a one-connection line is measured over one connection`() {
        // Seven guaranteed rejections is not a measurement, and on panels that
        // count failed attempts it can get the line blocked by the very test
        // meant to diagnose it.
        assertEquals(1, SpeedTestEngine.measurementStreams(1))
    }

    @Test
    fun `the sample never exceeds the cap the line published`() {
        // Unlike the connection-cap probe, which deliberately goes one past
        // the boundary to find it, this has no business discovering it.
        assertEquals(2, SpeedTestEngine.measurementStreams(2))
        assertEquals(3, SpeedTestEngine.measurementStreams(3))
        assertEquals(
            SpeedTestEngine.MAX_MEASURE_STREAMS,
            SpeedTestEngine.measurementStreams(64),
        )
    }

    @Test
    fun `an unpublished cap is measured cautiously, but not with one socket`() {
        // Plain M3U lines publish nothing. Two is enough to show a line gives
        // more than one request's worth, and is a measurement no 1-connection
        // line can be badly hurt by.
        assertEquals(SpeedTestEngine.UNKNOWN_CAP_STREAMS, SpeedTestEngine.measurementStreams(0))
        assertEquals(SpeedTestEngine.UNKNOWN_CAP_STREAMS, SpeedTestEngine.measurementStreams(-5))
        assertTrue(SpeedTestEngine.UNKNOWN_CAP_STREAMS in 2..SpeedTestEngine.MAX_MEASURE_STREAMS)
    }
}
