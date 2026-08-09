package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import tv.enktel.app.player.FrameRates
import org.junit.Test

/**
 * A live channel played, cut out, recovered and cut out again — on a full
 * 15 s buffer with zero dropped frames, which is not a player short of data.
 *
 * The cause was here. A measured frame rate was published every second to
 * RefreshRateMatcher, which sets `preferredDisplayModeId` and makes the
 * television resync. 24 and 25 fps are one frame apart and the snap tolerance
 * was ±1.5, so the boundary between them fell at 24.5 and ordinary sampling
 * noise crossed it repeatedly. Each crossing read as a new frame rate and
 * triggered another blank screen.
 *
 * These pin both halves of the fix: a tolerance that can actually tell 24 from
 * 25, and a latch that publishes once and then stops.
 */
class FrameRatesTest {

    // ── snapping ───────────────────────────────────────────────────────

    @Test
    fun `a measurement near a real rate is called that rate`() {
        assertEquals(25f, FrameRates.snap(24.8f))
        assertEquals(25f, FrameRates.snap(25.3f))
        assertEquals(50f, FrameRates.snap(49.7f))
        assertEquals(60f, FrameRates.snap(60.4f))
    }

    @Test
    fun `an ambiguous measurement between 24 and 25 is not guessed at`() {
        // The old tolerance answered this confidently and wrongly, alternating
        // between 24 and 25 as the noise moved. Refusing to name it is what
        // keeps it out of the latch, and so out of the display.
        val snapped = FrameRates.snap(24.5f)
        assertFalse(
            "24.5 is not evidence for either rate: $snapped",
            FrameRates.isKnown(snapped),
        )
    }

    @Test
    fun `the NTSC pulldowns stay distinct from their whole-number rates`() {
        assertEquals(23.976f, FrameRates.snap(23.98f))
        assertEquals(29.97f, FrameRates.snap(29.95f))
    }

    // ── the latch ──────────────────────────────────────────────────────

    @Test
    fun `a steady rate settles and is published once`() {
        val latch = FrameRates.Latch()
        repeat(FrameRates.AGREE_SAMPLES - 1) {
            assertEquals("must not act before it is sure", 0f, latch.offer(25.1f))
        }
        assertEquals(25f, latch.offer(24.9f))
        assertTrue(latch.settled)
    }

    @Test
    fun `noise across the 24-25 boundary never settles`() {
        // The exact reported failure, replayed: a stream whose measurement
        // wanders either side of 24.5. Previously every crossing published a
        // new rate and blanked the screen. Now nothing is published at all.
        val latch = FrameRates.Latch()
        val wobble = listOf(24.4f, 24.6f, 24.45f, 24.55f, 24.5f, 24.6f, 24.4f, 24.52f)
        wobble.forEach {
            assertEquals(
                "an unsettled measurement must never reach the display: $it",
                0f,
                latch.offer(it),
            )
        }
        assertFalse(latch.settled)
    }

    @Test
    fun `a burst after a decoder flush is not mistaken for a new rate`() {
        val latch = FrameRates.Latch()
        repeat(FrameRates.AGREE_SAMPLES) { latch.offer(25f) }
        assertTrue(latch.settled)
        // A flush releases a pile of buffers at once and the next sample is
        // nonsense. The caller has already latched and stops asking, but the
        // latch itself must not report a *new* rate if it is asked anyway.
        assertEquals(0f, latch.offer(180f))
        assertFalse(latch.settled)
    }

    @Test
    fun `a reset clears the rate so the next channel starts fresh`() {
        val latch = FrameRates.Latch()
        repeat(FrameRates.AGREE_SAMPLES) { latch.offer(50f) }
        assertTrue(latch.settled)
        latch.reset()
        assertFalse(latch.settled)
        assertEquals(0f, latch.offer(25f))
    }

    @Test
    fun `the tolerance cannot be widened past the closest pair of real rates`() {
        // A guard on the constant itself, because this is the thing that was
        // wrong: any tolerance at or above half the 24-to-25 gap makes the two
        // rates indistinguishable and brings the whole fault back.
        assertTrue(
            "tolerance ${FrameRates.SNAP_TOLERANCE} must stay below half the 24→25 gap",
            FrameRates.SNAP_TOLERANCE < 0.5f * (25f - 24f),
        )
    }
}
