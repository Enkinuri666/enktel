package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
