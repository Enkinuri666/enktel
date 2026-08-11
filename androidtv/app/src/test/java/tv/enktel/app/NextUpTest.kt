package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.vod.NextUp

/**
 * A tester reported that the next-episode card appeared, could not be operated,
 * and never rolled over on its own — the episode simply ended and they went
 * back to pick the next one by hand.
 *
 * Two of the three causes were elsewhere (the remote, and a released engine).
 * This one is here: the countdown could not reach zero, so the auto-advance
 * that fires at zero could not fire.
 */
class NextUpTest {

    private val hour = 60L * 60_000

    @Test
    fun `the card stays away until the window opens`() {
        assertNull(NextUp.secondsLeft(durationMs = hour, positionMs = 0))
        assertNull(NextUp.secondsLeft(durationMs = hour, positionMs = hour - NextUp.WINDOW_MS - 1))
    }

    @Test
    fun `the countdown reaches zero`() {
        // The defect, stated as a test. The old arithmetic rounded up from a
        // remainder it also required to be at least 1 ms, so 0 was unreachable
        // and the roll-over never happened.
        assertEquals(0, NextUp.secondsLeft(durationMs = hour, positionMs = hour))
        assertEquals(0, NextUp.secondsLeft(durationMs = hour, positionMs = hour - 999))
        assertEquals(1, NextUp.secondsLeft(durationMs = hour, positionMs = hour - 1_000))
    }

    @Test
    fun `a position past the declared end still reads as over`() {
        // Panels routinely declare a duration a little short of the real file.
        assertEquals(0, NextUp.secondsLeft(durationMs = hour, positionMs = hour + 5_000))
    }

    @Test
    fun `the countdown counts down`() {
        val secs = (0..30).map { NextUp.secondsLeft(hour, hour - it * 1_000L) }
        assertEquals((0..30).toList(), secs)
    }

    @Test
    fun `nothing with no length gets a card`() {
        // Live, and VOD whose panel declares no duration: there is no "end" to
        // count towards, and the card would sit there for the whole programme.
        assertNull(NextUp.secondsLeft(durationMs = 0, positionMs = 500_000))
        assertNull(NextUp.secondsLeft(durationMs = -1, positionMs = 0))
    }

    @Test
    fun `a clip shorter than the window is not offered a successor from its first frame`() {
        assertNull(NextUp.secondsLeft(durationMs = 20_000, positionMs = 0))
        assertNull(NextUp.secondsLeft(durationMs = NextUp.WINDOW_MS, positionMs = 0))
    }

    // ── deciding to roll over ──────────────────────────────────────────

    @Test
    fun `the player reporting the end is enough on its own`() {
        // The clean case, and the only certain one.
        assertTrue(
            NextUp.shouldAdvance(hour, positionMs = 10_000, playbackEnded = true, stalledMs = 0),
        )
    }

    @Test
    fun `a file that stops just short of its declared runtime still counts as finished`() {
        // The reason this feature kept not working. Panels declare a runtime
        // that does not match the file they serve, so a rule that waits for the
        // position to reach the number exactly waits for something that
        // frequently never happens.
        assertTrue(NextUp.shouldAdvance(hour, hour, playbackEnded = false, stalledMs = 0))
        assertTrue(NextUp.shouldAdvance(hour, hour - 1_400, playbackEnded = false, stalledMs = 0))
        assertFalse(NextUp.shouldAdvance(hour, hour - 1_600, playbackEnded = false, stalledMs = 0))
    }

    @Test
    fun `a picture that freezes near the end rolls over once it is clearly over`() {
        // Some panels end a file without ending the stream: the position stops
        // moving and nothing is ever reported. Four seconds of that, inside the
        // closing window, is an episode that has finished.
        val nearEnd = hour - 5_000
        assertFalse(NextUp.shouldAdvance(hour, nearEnd, playbackEnded = false, stalledMs = 3_000))
        assertTrue(NextUp.shouldAdvance(hour, nearEnd, playbackEnded = false, stalledMs = 4_000))
    }

    @Test
    fun `a viewer who paused has not finished the episode`() {
        // The caller only accumulates stall time while playback is wanted, so a
        // deliberate pause twenty seconds from the end reports no stall at all.
        // Rolling them into the next episode would be the worst kind of help.
        val nearEnd = hour - 20_000
        assertFalse(NextUp.shouldAdvance(hour, nearEnd, playbackEnded = false, stalledMs = 0))
    }

    @Test
    fun `a stall in the middle of an episode is buffering, not an ending`() {
        assertFalse(
            NextUp.shouldAdvance(hour, positionMs = 600_000, playbackEnded = false, stalledMs = 30_000),
        )
    }

    @Test
    fun `nothing rolls over on a title with no usable length`() {
        assertFalse(NextUp.shouldAdvance(0, 0, playbackEnded = false, stalledMs = 60_000))
        assertFalse(NextUp.shouldAdvance(20_000, 19_000, playbackEnded = false, stalledMs = 60_000))
        // Except when the player itself says it finished.
        assertTrue(NextUp.shouldAdvance(0, 0, playbackEnded = true, stalledMs = 0))
    }

    @Test
    fun `a real run of ticks reaches the roll-over`() {
        // Replayed as the ticker sees it: 250 ms apart, and the file stops
        // 1.2 s short of its declared runtime with no end-of-stream reported.
        // Under the old rule — a countdown that had to hit exactly zero — this
        // sequence never advanced, which is the reported fault.
        val stopsAt = hour - 1_200
        var advanced = false
        var pos = hour - 40_000
        var stalled = 0L
        repeat(400) {
            pos = (pos + 250).coerceAtMost(stopsAt)
            stalled = if (pos == stopsAt) stalled + 250 else 0
            if (NextUp.shouldAdvance(hour, pos, playbackEnded = false, stalledMs = stalled)) {
                advanced = true
            }
        }
        assertTrue("a run that ends 1.2s short must still roll over", advanced)
    }
}
