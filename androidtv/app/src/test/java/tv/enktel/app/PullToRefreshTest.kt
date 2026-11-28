package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.components.pullOffset
import tv.enktel.app.ui.components.pullWouldTrigger

/**
 * The feel of the pull — rubber-banding and the trigger threshold — kept pure so
 * it can be checked without a touch screen.
 */
class PullToRefreshTest {

    private val threshold = 200f

    @Test fun `pull follows the finger at half speed below the threshold`() {
        assertEquals(50f, pullOffset(0f, 100f, threshold), 0.01f)
    }

    @Test fun `pull eases once past the threshold instead of tracking 1 to 1`() {
        // Already at the threshold: a further 100 px of drag adds only a quarter.
        assertEquals(threshold + 25f, pullOffset(threshold, 100f, threshold), 0.01f)
    }

    @Test fun `pull is capped so it cannot be yanked arbitrarily far`() {
        assertEquals(threshold * 1.6f, pullOffset(threshold * 1.5f, 10_000f, threshold), 0.01f)
    }

    @Test fun `an upward or zero delta never grows the pull`() {
        assertEquals(40f, pullOffset(40f, 0f, threshold), 0.01f)
        assertEquals(40f, pullOffset(40f, -500f, threshold), 0.01f)
    }

    @Test fun `releasing only refreshes past the threshold`() {
        assertFalse(pullWouldTrigger(threshold - 1f, threshold))
        assertTrue(pullWouldTrigger(threshold, threshold))
    }
}
