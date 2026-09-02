package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.debrid.MagnetFlow
import tv.enktel.app.data.debrid.RealDebrid

class MagnetFlowTest {

    @Test fun `the whole wait costs a fraction of the rate limit`() {
        // Real-Debrid counts refused requests toward the same 250-per-minute
        // limit that refused them, so a poll that is merely "not too bad" is
        // not good enough — this asserts the schedule stays far under it.
        var waited = 0L
        var requests = 0
        var attempt = 0
        while (waited < MagnetFlow.MAX_WAIT_MS) {
            requests++
            waited += MagnetFlow.pollDelayMs(attempt++)
        }
        val perMinute = requests * 60_000.0 / MagnetFlow.MAX_WAIT_MS
        assertTrue("$perMinute requests/min", perMinute < RealDebrid.REQUESTS_PER_MINUTE / 5.0)
    }

    @Test fun `the first polls are quick so a cached torrent feels instant`() {
        assertEquals(1_500L, MagnetFlow.pollDelayMs(0))
        assertTrue(MagnetFlow.pollDelayMs(0) < MagnetFlow.pollDelayMs(10))
        assertTrue(MagnetFlow.pollDelayMs(10) < MagnetFlow.pollDelayMs(30))
    }

    @Test fun `terminal states are the ones nothing more happens to`() {
        assertTrue(MagnetFlow.isFailed("error"))
        assertTrue(MagnetFlow.isFailed("magnet_error"))
        assertTrue(MagnetFlow.isFailed("virus"))
        assertTrue(MagnetFlow.isFailed("dead"))
        assertFalse(MagnetFlow.isFailed("downloading"))
        assertFalse(MagnetFlow.isFailed("waiting_files_selection"))
        assertFalse(MagnetFlow.isFailed("downloaded"))
    }

    @Test fun `no status word reaches the screen raw`() {
        val statuses = listOf(
            "magnet_conversion", "waiting_files_selection", "queued", "downloading",
            "compressing", "uploading", "downloaded", "error", "magnet_error",
            "virus", "dead", "something_new",
        )
        for (s in statuses) {
            val line = MagnetFlow.progressLine(s, 42)
            assertFalse("$s leaked", line.contains("_"))
            assertTrue("$s empty", line.isNotBlank())
        }
    }

    @Test fun `progress is shown while downloading and clamped`() {
        assertTrue(MagnetFlow.progressLine("downloading", 42).contains("42%"))
        assertTrue(MagnetFlow.progressLine("downloading", 140).contains("100%"))
        assertTrue(MagnetFlow.progressLine("downloading", -5).contains("0%"))
    }

    @Test fun `running out of patience does not read as a failure`() {
        val line = MagnetFlow.stillGoingLine("Some Film 2019")
        assertTrue(line.startsWith("Some Film 2019"))
        assertTrue(line.contains("still downloading"))
        assertFalse(line.contains("fail"))
        assertFalse(line.contains("error"))
        // A magnet with no display name still produces a sentence.
        assertTrue(MagnetFlow.stillGoingLine("  ").startsWith("This torrent"))
    }
}
