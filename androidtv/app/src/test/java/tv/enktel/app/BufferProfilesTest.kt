package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.BufferProfiles

class BufferProfilesTest {

    private val profiles = listOf("low", "balanced", "large", "auto", "nonsense")

    /**
     * `DefaultLoadControl.Builder` does not clamp these — it calls
     * `Preconditions` and throws `IllegalArgumentException` from the
     * constructor. Verified against the 1.10.1 bytecode: bufferForPlaybackMs
     * and bufferForPlaybackAfterRebufferMs must be >= 0 and <= minBufferMs,
     * and maxBufferMs must be >= minBufferMs.
     *
     * A violation is a crash on the first frame of playback, reached from a
     * settings slider, so every combination is checked rather than the few
     * that happen to be wired up today.
     */
    @Test
    fun `every combination satisfies ExoPlayer's assertions`() {
        for (p in profiles) for (live in listOf(true, false)) {
            for (tv in listOf(true, false)) for (low in listOf(true, false)) {
                val w = BufferProfiles.window(p, live, tv, low)
                val where = "profile=$p live=$live tv=$tv lowRam=$low -> $w"
                assertTrue(where, w.playMs >= 0)
                assertTrue(where, w.rebufMs >= 0)
                assertTrue(where, w.minMs >= w.playMs)
                assertTrue(where, w.minMs >= w.rebufMs)
                assertTrue(where, w.maxMs >= w.minMs)
            }
        }
    }

    @Test
    fun `a user minimum override cannot produce an illegal window`() {
        // The slider goes higher than some profiles' maximum. Raising minMs
        // above maxMs is the exact shape of the crash above.
        val w = BufferProfiles.window("low", live = true, isTv = true)
        for (override in listOf(1, 500, 30_000, 600_000)) {
            val o = BufferProfiles.withMinOverride(w, override)
            assertTrue("override=$override -> $o", o.maxMs >= o.minMs)
            assertTrue("override=$override -> $o", o.minMs >= o.playMs)
            assertTrue("override=$override -> $o", o.minMs >= o.rebufMs)
        }
    }

    @Test
    fun `zero override leaves the window alone`() {
        val w = BufferProfiles.window("balanced", live = false, isTv = true)
        assertEquals(w, BufferProfiles.withMinOverride(w, 0))
    }

    @Test
    fun `live never buffers past the provider's segment window`() {
        // The whole point. A live HLS server publishes a sliding window of a
        // handful of segments; asking for more than it retains is a request
        // for a deleted file, and the answer is 404 — so a bigger buffer makes
        // live playback less reliable, not more.
        for (p in profiles) for (tv in listOf(true, false)) {
            val w = BufferProfiles.window(p, live = true, isTv = tv)
            assertTrue(
                "profile=$p tv=$tv held ${w.maxMs}ms of a live stream",
                w.maxMs <= BufferProfiles.LIVE_MAX_CEILING_MS,
            )
        }
    }

    @Test
    fun `VOD buffers far more than live on the same profile`() {
        for (p in profiles) {
            val live = BufferProfiles.window(p, live = true, isTv = true)
            val vod = BufferProfiles.window(p, live = false, isTv = true)
            assertTrue("profile=$p", vod.maxMs > live.maxMs * 2)
        }
    }

    @Test
    fun `live starts faster than VOD`() {
        // Time-to-first-frame is what a channel zap feels like. This is the
        // number that has to be small for surfing to feel instant, and it is
        // the one a VOD-shaped configuration gets wrong.
        for (p in profiles) {
            val live = BufferProfiles.window(p, live = true, isTv = true)
            val vod = BufferProfiles.window(p, live = false, isTv = true)
            assertTrue("profile=$p", live.playMs < vod.playMs)
        }
    }

    @Test
    fun `the low profile is the fastest to first frame on live`() {
        val low = BufferProfiles.window("low", live = true, isTv = true)
        val large = BufferProfiles.window("large", live = true, isTv = true)
        assertTrue(low.playMs < large.playMs)
        assertTrue(low.maxMs < large.maxMs)
    }

    @Test
    fun `a low-RAM device holds less`() {
        // A 1 GB Fire TV Stick Lite cannot hold a three-minute 4K window
        // whatever the user picked, and the failure there is an OOM rather
        // than a stall.
        for (p in profiles) {
            val normal = BufferProfiles.window(p, live = false, isTv = true, lowRam = false)
            val lean = BufferProfiles.window(p, live = false, isTv = true, lowRam = true)
            assertTrue("profile=$p", lean.maxMs < normal.maxMs)
        }
    }

    @Test
    fun `an unknown profile behaves like balanced rather than throwing`() {
        // The value comes out of a persisted setting, so an old or corrupt one
        // must not crash playback.
        assertEquals(
            BufferProfiles.window("balanced", live = true, isTv = true),
            BufferProfiles.window("nonsense", live = true, isTv = true),
        )
    }

    @Test
    fun `allocation chunk shrinks on low-RAM devices`() {
        assertTrue(
            BufferProfiles.allocationChunkBytes(lowRam = true) <
                BufferProfiles.allocationChunkBytes(lowRam = false),
        )
    }

    // ── live target offset ─────────────────────────────────────────────
    //
    // Nothing pinned the player to a distance behind the live edge, so it sat
    // wherever the panel's playlist put it — often under a second, which
    // leaves no headroom and turns any late segment into dropped frames.

    @Test
    fun `the live target offset leaves room to start playing`() {
        // Playback cannot begin until playMs is buffered. An offset smaller
        // than that would put the start position past the edge, and the player
        // would spend the first seconds catching up to content that does not
        // exist yet.
        val starts = listOf("low", "balanced", "large").map { p ->
            BufferProfiles.window(p, live = true, isTv = false).playMs
        }
        assertTrue(
            "offset must exceed every live start threshold: $starts",
            starts.all { BufferProfiles.LIVE_TARGET_OFFSET_MS > it },
        )
    }

    @Test
    fun `the live target offset stays inside a typical provider window`() {
        // Same reasoning as LIVE_MAX_CEILING_MS: asking to sit further back
        // than a provider keeps means asking for segments that have rolled
        // off. Media3 clamps rather than fails, but requesting something
        // unservable is not a thing to rely on being rescued from.
        assertTrue(
            BufferProfiles.LIVE_TARGET_OFFSET_MS <= BufferProfiles.LIVE_MAX_CEILING_MS,
        )
    }
}
