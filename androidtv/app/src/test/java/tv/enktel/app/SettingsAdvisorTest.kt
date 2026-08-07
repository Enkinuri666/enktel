package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.diag.CatchupFacts
import tv.enktel.app.data.diag.ContainerFacts
import tv.enktel.app.data.diag.Ebml
import tv.enktel.app.data.diag.PlaybackSettings
import tv.enktel.app.data.diag.RangeSupport
import tv.enktel.app.data.diag.SettingsAdvisor

/**
 * The advisor is the only part of the diagnostics that holds an opinion, so it
 * is the only part that can be confidently wrong. Each case pins both the
 * recommendation and, where it matters, the absence of one.
 */
class SettingsAdvisorTest {

    private val defaults = PlaybackSettings()

    private fun facts(
        detected: String,
        partial: Boolean = true,
        total: Long = 1_000_000,
        midOk: Boolean = true,
        mkv: Ebml.Head? = null,
    ) = ContainerFacts(
        url = "http://x-api.cc/movie/u/p/1",
        detected = detected,
        matroska = mkv,
        range = RangeSupport(tested = true, partialContent = partial, totalBytes = total, midFileSeekOk = midOk),
    )

    @Test fun `stream format follows what the panel actually serves`() {
        val ts = SettingsAdvisor.advise(
            defaults.copy(streamFormat = "hls"), facts("MPEG-TS"), null, CatchupFacts(),
        ).single { it.key == "streamFormat" }
        assertEquals("ts", ts.suggested)
        assertTrue(ts.differs)
    }

    @Test fun `no format change is suggested when it already matches`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(streamFormat = "hls"), facts("HLS"), null, CatchupFacts(),
        ).single { it.key == "streamFormat" }
        assertEquals("hls", c.suggested)
        assertTrue("already correct, so nothing to apply", !c.differs)
    }

    @Test fun `force MP4 is turned off when VOD is matroska`() {
        // Forcing MP4 on a Matroska file mislabels the container to the
        // extractor, which is how you get audio and a black frame.
        val c = SettingsAdvisor.advise(
            defaults.copy(vodForceMp4 = true), null, facts("MATROSKA"), CatchupFacts(),
        ).single { it.key == "vodForceMp4" }
        assertEquals("off", c.suggested)
    }

    @Test fun `force MP4 is turned on when VOD really is MP4`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(vodForceMp4 = false), null, facts("MP4"), CatchupFacts(),
        ).single { it.key == "vodForceMp4" }
        assertEquals("on", c.suggested)
    }

    @Test fun `an unknown container produces no force-MP4 opinion`() {
        assertTrue(
            SettingsAdvisor.advise(defaults, null, facts("UNKNOWN"), CatchupFacts())
                .none { it.key == "vodForceMp4" },
        )
    }

    @Test fun `a panel that refuses ranges gets a larger buffer`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(bufferProfile = "balanced"),
            null,
            facts("MP4", partial = false, midOk = false),
            CatchupFacts(),
        ).single { it.key == "bufferProfile" }
        assertEquals("large", c.suggested)
    }

    @Test fun `working ranges leave the buffer profile alone`() {
        assertTrue(
            SettingsAdvisor.advise(defaults, null, facts("MP4"), CatchupFacts())
                .none { it.key == "bufferProfile" },
        )
    }

    @Test fun `time-shift is turned off when catch-up does not answer`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(liveShiftEnabled = true), null, null,
            CatchupFacts(tested = true, available = false, httpCode = 404),
        ).single { it.key == "liveShiftEnabled" }
        assertEquals("off", c.suggested)
        assertTrue(c.reason.contains("404"))
    }

    @Test fun `time-shift is offered when catch-up works and channels have archive`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(liveShiftEnabled = false), null, null,
            CatchupFacts(tested = true, available = true, httpCode = 200, channelsWithArchive = 12),
        ).single { it.key == "liveShiftEnabled" }
        assertEquals("on", c.suggested)
    }

    @Test fun `an untested catch-up yields no time-shift opinion`() {
        assertTrue(
            SettingsAdvisor.advise(defaults, null, null, CatchupFacts(tested = false))
                .none { it.key == "liveShiftEnabled" },
        )
    }

    @Test fun `matroska without cues is called out as a file problem not an app bug`() {
        val notes = SettingsAdvisor.notes(
            null,
            facts("MATROSKA", mkv = Ebml.Head(isMatroska = true, hasSeekHead = true, hasCues = false)),
        )
        assertTrue(notes.any { it.contains("Cues") && it.contains("not the app") })
    }

    @Test fun `matroska with cues is reported as seekable`() {
        val notes = SettingsAdvisor.notes(
            null,
            facts("MATROSKA", mkv = Ebml.Head(isMatroska = true, hasSeekHead = true, hasCues = true)),
        )
        assertTrue(notes.any { it.contains("seeking is supported") })
    }

    @Test fun `ranges that work only at offset zero are called out`() {
        val notes = SettingsAdvisor.notes(null, facts("MP4", partial = true, midOk = false))
        assertTrue(notes.any { it.contains("not mid-file") })
    }

    @Test fun `a healthy panel produces no changes at all`() {
        val changes = SettingsAdvisor.advise(
            defaults.copy(
                streamFormat = "hls",
                vodForceMp4 = false,
                liveShiftEnabled = true,
                allocatorSizeKb = 2048,
            ),
            facts("HLS"),
            facts("MATROSKA"),
            CatchupFacts(tested = true, available = true, httpCode = 200, channelsWithArchive = 4),
        )
        assertTrue("nothing should need applying", changes.none { it.differs })
    }

    // ── v1.50.0 per-type buffer advice ─────────────────────────────────

    @Test fun `broken ranges suggest deeper vod min buffer when custom`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(vodBufferProfile = "custom", vodMinBufferMs = 10_000),
            null,
            facts("MP4", partial = false, midOk = false),
            CatchupFacts(),
        ).single { it.key == "vodMinBufferMs" }
        assertEquals("30s", c.suggested)
    }

    @Test fun `auto vod buffer profile does not produce a min-buffer suggestion`() {
        assertTrue(
            SettingsAdvisor.advise(
                defaults.copy(vodBufferProfile = "auto"),
                null,
                facts("MP4", partial = false, midOk = false),
                CatchupFacts(),
            ).none { it.key == "vodMinBufferMs" },
        )
    }

    @Test fun `matroska vod suggests 2 MB allocator`() {
        val c = SettingsAdvisor.advise(
            defaults.copy(allocatorSizeKb = 0),
            null,
            facts("MATROSKA", mkv = Ebml.Head(isMatroska = true, hasSeekHead = true, hasCues = true)),
            CatchupFacts(),
        ).single { it.key == "allocatorSizeKb" }
        assertEquals("2048 KB", c.suggested)
    }

    @Test fun `allocator already at 2 MB produces no suggestion for matroska`() {
        assertTrue(
            SettingsAdvisor.advise(
                defaults.copy(allocatorSizeKb = 2048),
                null,
                facts("MATROSKA", mkv = Ebml.Head(isMatroska = true, hasSeekHead = true, hasCues = true)),
                CatchupFacts(),
            ).none { it.key == "allocatorSizeKb" },
        )
    }

    @Test fun `dangling audio groups suggest deeper live min buffer when custom`() {
        val hls = tv.enktel.app.data.diag.HlsInspector.Playlist(
            kind = tv.enktel.app.data.diag.HlsInspector.Kind.MASTER,
            danglingAudioGroups = listOf("aac"),
        )
        val live = ContainerFacts(detected = "HLS", hls = hls)
        val c = SettingsAdvisor.advise(
            defaults.copy(liveBufferProfile = "custom", liveMinBufferMs = 2_000),
            live, null, CatchupFacts(),
        ).single { it.key == "liveMinBufferMs" }
        assertEquals("4s", c.suggested)
    }
}
