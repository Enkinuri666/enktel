package tv.enktel.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tv.enktel.app.data.net.StreamHealth

/**
 * The health chip reported faults that had long since cleared.
 *
 * `timeouts` and `blocked403` were lifetime counters — `resetErrors()` existed
 * but no caller ever invoked it — so three timeouts anywhere in a session
 * pinned the chip to POOR until the process died, and one 403 pinned it to
 * BLOCKED. Every channel tuned afterwards showed the same frozen badge.
 *
 * The latency figure failed the opposite way: it was recomputed only when a
 * request completed, and a live stream holding one connection open makes very
 * few, so a stale mean sat on screen looking current.
 *
 * These pin the window that fixes both. The clock is injected, so "a minute
 * later" is a value rather than a wait.
 */
class StreamHealthTest {

    private var clock = 1_000_000L

    @Before
    fun setUp() {
        StreamHealth.reset()
        StreamHealth.nowMs = { clock }
    }

    @After
    fun tearDown() {
        StreamHealth.reset()
        StreamHealth.nowMs = { System.currentTimeMillis() }
    }

    private fun advance(ms: Long) {
        clock += ms
        StreamHealth.refresh()
    }

    @Test
    fun `three timeouts read as poor`() {
        repeat(3) { StreamHealth.recordTimeout("host", "timeout") }
        assertEquals(StreamHealth.Quality.POOR, StreamHealth.state.value.quality)
    }

    @Test
    fun `timeouts stop counting once they age out`() {
        repeat(3) { StreamHealth.recordTimeout("host", "timeout") }
        assertEquals(StreamHealth.Quality.POOR, StreamHealth.state.value.quality)

        advance(61_000)
        assertEquals(
            "a fault that stopped an hour ago is not a fault now",
            0,
            StreamHealth.state.value.timeouts,
        )
        assertEquals(StreamHealth.Quality.UNKNOWN, StreamHealth.state.value.quality)
    }

    @Test
    fun `a blocked response ages out too`() {
        StreamHealth.recordBlocked("panel.example")
        assertEquals(StreamHealth.Quality.BLOCKED, StreamHealth.state.value.quality)

        advance(61_000)
        assertEquals(0, StreamHealth.state.value.blocked403)
        assertEquals(StreamHealth.Quality.UNKNOWN, StreamHealth.state.value.quality)
    }

    @Test
    fun `a stale latency reading is withdrawn rather than shown as current`() {
        StreamHealth.recordSuccess(120)
        assertEquals(StreamHealth.Quality.GOOD, StreamHealth.state.value.quality)
        assertEquals(120, StreamHealth.state.value.meanLatencyMs)

        advance(61_000)
        assertEquals(
            "no recent reading is not a verdict",
            StreamHealth.Quality.UNKNOWN,
            StreamHealth.state.value.quality,
        )
        assertEquals(0, StreamHealth.state.value.meanLatencyMs)
    }

    @Test
    fun `the mean covers only readings still inside the window`() {
        StreamHealth.recordSuccess(8_000)   // slow, and about to be old news
        advance(50_000)
        StreamHealth.recordSuccess(100)
        StreamHealth.recordSuccess(100)

        // Still inside the window: all three count, so the old slow one drags
        // the mean up and the verdict with it.
        assertEquals(StreamHealth.Quality.POOR, StreamHealth.state.value.quality)

        // Once it ages out, only the two fast readings remain.
        advance(11_000)
        assertEquals(100, StreamHealth.state.value.meanLatencyMs)
        assertEquals(StreamHealth.Quality.GOOD, StreamHealth.state.value.quality)
    }

    @Test
    fun `recovery does not need a fresh request to be observed`() {
        repeat(3) { StreamHealth.recordTimeout("host", "timeout") }
        assertEquals(StreamHealth.Quality.POOR, StreamHealth.state.value.quality)

        // refresh() alone, with no new traffic at all — which is the case that
        // was broken: a healthy long-lived stream makes no new requests, so
        // nothing ever recomputed and the chip stayed on.
        advance(61_000)
        assertEquals(StreamHealth.Quality.UNKNOWN, StreamHealth.state.value.quality)
    }

    @Test
    fun `an active gateway is reported until it is cleared`() {
        StreamHealth.setActiveGateway("backup.example")
        assertEquals("backup.example", StreamHealth.state.value.activeGateway)
        StreamHealth.setActiveGateway(null)
        assertNull(StreamHealth.state.value.activeGateway)
    }
}
