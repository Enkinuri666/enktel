package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.BufferProfiles
import tv.enktel.app.player.LiveBufferPacer
import tv.enktel.app.player.LiveRecovery

/**
 * Rebuilding a live cushion that cannot be rebuilt any other way.
 *
 * > Live TV keeps buffering.
 *
 * The channel: 720p50 AVC in MPEG-TS, 15 Mbps of network estimated, 4.6 s of
 * buffer, three dropped frames. Three times the bandwidth the feed needs, a
 * decoder with nothing to complain about, and a buffer that will not grow —
 * because a raw `.ts` URL is the broadcast being written into a socket at the
 * rate it happens. One second of video arrives per second however much spare
 * bandwidth there is, so the margin only ever ratchets downwards and every dip
 * is permanent.
 *
 * Media3 solves this on HLS by easing playback below 1× to earn the margin
 * back. On a progressive stream its live speed control never runs at all —
 * `shouldUseLivePlaybackSpeedControl` wants a window that is live *and*
 * dynamic *and* wall-clock anchored, and `ProgressiveMediaSource` gives it the
 * first only. [LiveBufferPacer] is the same idea driven by the buffer level,
 * which is the one signal a progressive live stream does have.
 */
class LiveBufferPacerTest {

    private val balanced = BufferProfiles.window("balanced", live = true, isTv = false)
    private val low = BufferProfiles.window("low", live = true, isTv = true)
    private val large = BufferProfiles.window("large", live = true, isTv = true)

    private fun speed(bufferedMs: Long, from: Float = LiveBufferPacer.NORMAL_SPEED) =
        LiveBufferPacer.speedFor(bufferedMs, from, balanced)

    // ---- who is driving ----------------------------------------------------

    @Test
    fun `an HLS channel is Media3's to pace, not ours`() {
        // A live, dynamic, wall-clock-anchored window is what HlsMediaSource
        // publishes, and Media3's own control is the better one there: it can
        // measure the distance to the live edge, which the buffer level only
        // approximates.
        assertTrue(LiveBufferPacer.media3PacesThis(isLive = true, isDynamic = true, windowStartTimeMs = 1_700_000_000_000L))
    }

    @Test
    fun `a raw TS channel is nobody's until this object exists`() {
        // The bug. ProgressiveMediaSource reports live — it is a stream of
        // unknown duration — so the MediaItem's LiveConfiguration rides along
        // on the window and looks applied. It is not: the window is not
        // dynamic and has no wall-clock start, so Media3 skips its live speed
        // control entirely and the six-second target offset the app asks for
        // is read and discarded.
        assertFalse(
            LiveBufferPacer.media3PacesThis(
                isLive = true,
                isDynamic = false,
                windowStartTimeMs = LiveBufferPacer.UNSET_TIME,
            ),
        )
        // Each condition alone is enough to disqualify it.
        assertFalse(LiveBufferPacer.media3PacesThis(true, true, LiveBufferPacer.UNSET_TIME))
        assertFalse(LiveBufferPacer.media3PacesThis(true, false, 1L))
        assertFalse(LiveBufferPacer.media3PacesThis(false, true, 1L))
    }

    @Test
    fun `the unset sentinel is the one Media3 actually uses`() {
        // C.TIME_UNSET, written out rather than imported so this object stays
        // free of Media3. If the two ever disagree, every progressive stream
        // silently looks wall-clock anchored and the pacer stands down on the
        // streams it exists for.
        assertEquals(androidx.media3.common.C.TIME_UNSET, LiveBufferPacer.UNSET_TIME)
    }

    // ---- the decision ------------------------------------------------------

    @Test
    fun `a thin buffer eases off`() {
        // Balanced mobile: 4 s healthy, so 2 s is the floor.
        assertEquals(LiveBufferPacer.REBUILD_SPEED, speed(1_200), 0.0001f)
        assertEquals(LiveBufferPacer.REBUILD_SPEED, speed(0), 0.0001f)
    }

    @Test
    fun `a healthy buffer plays at one`() {
        assertEquals(LiveBufferPacer.NORMAL_SPEED, speed(6_000), 0.0001f)
        assertEquals(LiveBufferPacer.NORMAL_SPEED, speed(12_000), 0.0001f)
        // The reported case: 4.6 s on a 4 s window is healthy, and nothing
        // should be done about it. The pacer is for the dip, not for the
        // steady state.
        assertEquals(LiveBufferPacer.NORMAL_SPEED, speed(4_600), 0.0001f)
    }

    @Test
    fun `between the marks, whatever it was doing continues`() {
        // The hysteresis, and the whole reason there are two marks. A single
        // threshold flips the speed on every sample that lands near it, and a
        // playback speed oscillating once a second is audible in a way that
        // holding 0.97 for a minute is not.
        val mid = 3_000L
        assertEquals(LiveBufferPacer.REBUILD_SPEED, speed(mid, from = LiveBufferPacer.REBUILD_SPEED), 0.0001f)
        assertEquals(LiveBufferPacer.NORMAL_SPEED, speed(mid, from = LiveBufferPacer.NORMAL_SPEED), 0.0001f)
    }

    @Test
    fun `a dip and a recovery is one slow-down, not a flutter`() {
        // Walked as it actually happens: healthy, a dip past the floor, the
        // slow climb back at 0.97, and normal speed only once the cushion is
        // whole again.
        var s = LiveBufferPacer.NORMAL_SPEED
        val trace = listOf(5_000L, 4_200, 1_800, 2_100, 2_600, 3_400, 3_900, 4_000, 4_500)
        val speeds = trace.map { buffered ->
            s = LiveBufferPacer.speedFor(buffered, s, balanced)
            s
        }
        assertEquals(
            listOf(1f, 1f, 0.97f, 0.97f, 0.97f, 0.97f, 0.97f, 1f, 1f),
            speeds.map { "%.2f".format(it).toFloat() },
        )
    }

    // ---- the marks themselves ---------------------------------------------

    @Test
    fun `every profile has room between its marks`() {
        for (w in listOf(balanced, low, large)) {
            val lo = LiveBufferPacer.lowWatermarkMs(w)
            val hi = LiveBufferPacer.highWatermarkMs(w)
            assertTrue("$w: low $lo must be under high $hi", lo < hi)
            // A mark above the ceiling would leave the pacer permanently slow,
            // drifting further behind the broadcast every minute.
            assertTrue("$w: high $hi must be reachable under max ${w.maxMs}", hi < w.maxMs)
        }
    }

    @Test
    fun `the low-latency profile keeps a floor worth having`() {
        // Its minimum is 2 s, so half of it is a second — which is not a
        // cushion, it is the tail end of one. The floor stops a profile
        // chosen for latency from disabling the mechanism that keeps it
        // playing.
        assertEquals(LiveBufferPacer.MIN_FLOOR_MS, LiveBufferPacer.lowWatermarkMs(low))
        assertTrue(LiveBufferPacer.lowWatermarkMs(low) <= low.minMs)
    }

    @Test
    fun `the rebuild speed is slow enough to help and small enough to ignore`() {
        assertTrue(LiveBufferPacer.REBUILD_SPEED < LiveBufferPacer.NORMAL_SPEED)
        // Media3's own live control uses 3 %, and the app already asks for
        // exactly that range on HLS. Going further would rebuild faster and
        // start to be heard.
        assertTrue(LiveBufferPacer.NORMAL_SPEED - LiveBufferPacer.REBUILD_SPEED <= 0.05f)
        assertTrue(LiveBufferPacer.isRebuilding(LiveBufferPacer.REBUILD_SPEED))
        assertFalse(LiveBufferPacer.isRebuilding(LiveBufferPacer.NORMAL_SPEED))
    }

    // ---- what it costs to resume -------------------------------------------

    @Test
    fun `live resumes from a stall with the cushion it needs to survive the next dip`() {
        // A player that resumes with less than the load control's own minimum
        // resumes with less than it can ever earn back, because the stream
        // arrives at playback rate. Those are the repeat stalls the report is
        // about: five short pauses instead of one.
        for (p in listOf("low", "balanced", "large")) {
            for (tv in listOf(true, false)) {
                val w = BufferProfiles.window(p, live = true, isTv = tv)
                assertEquals("$p/tv=$tv resumes short", w.minMs, w.rebufMs)
            }
        }
    }

    @Test
    fun `resuming never takes so long the watchdog reconnects instead`() {
        // The trap in raising rebufMs. PlayerEngine arms a stall check on
        // every STATE_BUFFERING, and if the player is still waiting for its
        // cushion when that fires, the fix becomes the fault: a reconnect
        // throws the buffer away and starts from nothing, which is the loop
        // this change exists to break, now driven by the app itself.
        for (p in listOf("low", "balanced", "large")) {
            for (tv in listOf(true, false)) {
                val w = BufferProfiles.window(p, live = true, isTv = tv)
                assertTrue(
                    "$p/tv=$tv waits ${w.rebufMs}ms against a ${tv.let { LiveRecovery.LIVE_STALL_MS }}ms watchdog",
                    w.rebufMs * 2L < LiveRecovery.LIVE_STALL_MS,
                )
            }
        }
    }

    @Test
    fun `VOD keeps resuming as soon as it can`() {
        // The rule is about streams delivered at playback rate. A file is not
        // one: the bytes are all there, the buffer refills as fast as the line
        // allows, and waiting for the full minimum before resuming would be a
        // longer pause bought for nothing.
        val vod = BufferProfiles.window("balanced", live = false, isTv = true)
        assertTrue("VOD should resume well before its minimum", vod.rebufMs < vod.minMs)
    }

    @Test
    fun `and none of that slows down a channel change`() {
        // Time-to-first-frame is playMs, which this does not touch. Zapping is
        // the thing most easily spoiled by a bigger live buffer, so it is
        // worth a test rather than a comment.
        for (p in listOf("low", "balanced", "large")) {
            val w = BufferProfiles.window(p, live = true, isTv = true)
            assertTrue("$p starts slowly: ${w.playMs}ms", w.playMs <= 1_500)
            assertTrue("$p starts later than it resumes", w.playMs <= w.rebufMs)
        }
    }
}
