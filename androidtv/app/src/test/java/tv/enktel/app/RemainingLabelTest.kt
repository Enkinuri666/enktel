package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.ui.live.remainingLabel

/**
 * The live overlay's countdown used to be `"$minsLeft Minutes Left"`, which
 * capitalised mid-phrase like a system dialog and read "1 Minutes Left" and
 * "0 Minutes Left" at the two moments a viewer is most likely to be looking
 * at it. These pin the wording at the boundaries.
 */
class RemainingLabelTest {

    private val now = 1_710_000_000_000L
    private fun inMinutes(m: Long) = remainingLabel(now + m * 60_000, now)

    @Test
    fun `plural and singular are both grammatical`() {
        assertEquals("24 min left", inMinutes(24))
        assertEquals("1 min left", inMinutes(1))
    }

    @Test
    fun `the last minute does not read as zero`() {
        assertEquals("Ends in under a minute", remainingLabel(now + 30_000, now))
        assertEquals("Ends in under a minute", remainingLabel(now + 1, now))
    }

    @Test
    fun `a programme that has ended says so`() {
        assertEquals("Just finished", remainingLabel(now - 60_000, now))
    }

    @Test
    fun `long programmes read in hours rather than a large minute count`() {
        assertEquals("2h left", inMinutes(120))
        assertEquals("1h 35m left", inMinutes(95))
        assertEquals("59 min left", inMinutes(59))
    }
}
