package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.DeviceProbe
import tv.enktel.app.data.net.SpeedTestEngine
import tv.enktel.app.data.net.expiryLabel

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

    // --- device-side limits ------------------------------------------------

    private fun device(
        decoders: List<DeviceProbe.Decoder> = emptyList(),
        displayW: Int = 3840,
        displayH: Int = 2160,
        ramMb: Long = 4096,
        freeMb: Long = 8192,
        linkKbps: Int = 0,
        hdr: List<String> = listOf("HDR10"),
    ) = DeviceProbe.Info(
        decoders = decoders,
        displayWidth = displayW, displayHeight = displayH, hdrTypes = hdr,
        totalRamMb = ramMb, availRamMb = ramMb / 2,
        totalStorageMb = 16384, freeStorageMb = freeMb,
        linkDownKbps = linkKbps,
    )

    private fun decoder(label: String, hardware: Boolean, w: Int = 3840, h: Int = 2160) =
        DeviceProbe.Decoder(label, "video/$label", hardware, w, h, 60)

    @Test
    fun `a box with no HEVC decoder is told so plainly`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 90.0,
            server = noServer, live = null, vod = null,
            device = device(decoders = listOf(decoder("H.264", hardware = true))),
        )
        assertTrue(out.toString(), out.any { it.contains("no HEVC decoder", ignoreCase = true) })
    }

    @Test
    fun `software HEVC is called out rather than blamed on the buffer`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 90.0,
            server = noServer, live = null, vod = null,
            device = device(
                decoders = listOf(decoder("H.264", true), decoder("HEVC", hardware = false)),
            ),
        )
        val hit = out.single { it.contains("HEVC decodes in software") }
        assertTrue("a bigger buffer must not be the advice: $hit", hit.contains("not a bigger buffer"))
    }

    @Test
    fun `a fast line behind a slow panel points past the users wifi`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 5.0,
            server = noServer, live = null, vod = null,
            device = device(
                decoders = listOf(decoder("H.264", true), decoder("HEVC", true)),
                linkKbps = 300_000,
            ),
        )
        assertTrue(out.toString(), out.any { it.contains("bottleneck is between the panel and you") })
    }

    @Test
    fun `a healthy device adds no device advice at all`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 180.0,
            server = noServer, live = null, vod = null,
            device = device(
                decoders = listOf(decoder("H.264", true), decoder("HEVC", true), decoder("AV1", true)),
                linkKbps = 400_000,
            ),
        )
        assertTrue(out.toString(), out.any { it.contains("Everything looks healthy") })
    }

    // --- catalogue gaps ----------------------------------------------------

    @Test
    fun `an empty guide is reported as a sync problem not a playback one`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 90.0,
            server = noServer, live = null, vod = null,
            catalogue = SpeedTestEngine.Catalogue(channels = 900, epgProgrammes = 0),
        )
        assertTrue(out.toString(), out.any { it.contains("guide is empty", ignoreCase = true) })
    }

    @Test
    fun `partial guide coverage names the real percentage`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 90.0,
            server = noServer, live = null, vod = null,
            catalogue = SpeedTestEngine.Catalogue(
                channels = 900, epgProgrammes = 40_000, epgChannels = 90,
                epgHorizonMs = System.currentTimeMillis() + 3 * 86_400_000L,
            ),
        )
        assertTrue(out.toString(), out.any { it.contains("covers only 10%") })
    }

    @Test
    fun `a line with no catch-up channels says so instead of leaving an empty screen`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 90.0,
            server = noServer, live = null, vod = null,
            catalogue = SpeedTestEngine.Catalogue(
                channels = 900, epgProgrammes = 40_000, epgChannels = 880,
                epgHorizonMs = System.currentTimeMillis() + 3 * 86_400_000L,
                catchupChannels = 0,
            ),
        )
        assertTrue(out.toString(), out.any { it.contains("catch-up archive", ignoreCase = true) })
    }

    // --- subscription expiry ----------------------------------------------

    @Test
    fun `an expired line is named as the whole fault`() {
        val out = SpeedTestEngine.buildSuggestions(
            pingMs = 20, jitterMs = 5, lossPct = 0, mbps = 90.0,
            server = SpeedTestEngine.ServerInfo(
                url = "panel.example",
                expiresAt = System.currentTimeMillis() - 2 * 86_400_000L,
            ),
            live = null, vod = null,
        )
        assertTrue(out.toString(), out.any { it.contains("EXPIRED") })
    }

    @Test
    fun `expiry labels read in plain english on both sides of now`() {
        val now = 1_700_000_000_000L
        assertEquals("27 days left", expiryLabel(now + 27 * 86_400_000L + 1000, now))
        assertEquals("1 day left", expiryLabel(now + 86_400_000L + 1000, now))
        assertEquals("expires today", expiryLabel(now + 3_600_000L, now))
        assertEquals("expired today", expiryLabel(now - 3_600_000L, now))
        assertEquals("expired 3 days ago", expiryLabel(now - 3 * 86_400_000L, now))
    }
}
