package tv.enktel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.SpeedTestEngine

/**
 * The diagnostics panel used to report two contradictory things in the same
 * run: "Unable to measure throughput — check server address and network
 * connection" in the recommendation, and "Everything looks healthy" in the
 * suggestions, on a report where every latency probe and all eight URL shapes
 * had come back 200. These pin both halves of that fix.
 */
class SpeedTestAdviceTest {

    private val noServer = SpeedTestEngine.ServerInfo()

    @Test
    fun `zero throughput with working latency does not blame the server address`() {
        val text = SpeedTestEngine.recommend(mbps = 0.0, pingMs = 42, lossPct = 0)
        assertFalse(
            "must not send the user after a connectivity problem that isn't there: $text",
            text.contains("check server address", ignoreCase = true),
        )
        assertTrue(text.contains("did not complete", ignoreCase = true))
    }

    @Test
    fun `zero throughput with failed latency still blames connectivity`() {
        val text = SpeedTestEngine.recommend(mbps = 0.0, pingMs = -1, lossPct = 0)
        assertTrue(text.contains("too unstable", ignoreCase = true))
    }

    @Test
    fun `unmeasured throughput is never reported as healthy`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 42, jitterMs = 8, lossPct = 0, mbps = 0.0,
            server = noServer, live = null, vod = null,
        )
        assertFalse(
            "a missing headline measurement is not a clean bill of health: $out",
            out.any { it.contains("Everything looks healthy") },
        )
        assertTrue(out.any { it.contains("could not be measured", ignoreCase = true) })
    }

    @Test
    fun `a genuinely clean run still reports healthy`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 180.0,
            server = noServer, live = null, vod = null,
        )
        assertTrue(out.any { it.contains("Everything looks healthy") })
    }

    @Test
    fun `throughput window stays inside the promised 15-20 second range`() {
        assertTrue(SpeedTestEngine.WINDOW_SEC in 15L..20L)
    }
}
