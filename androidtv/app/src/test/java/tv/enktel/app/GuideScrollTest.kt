package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.guide.guideNowPx
import tv.enktel.app.ui.guide.guideScrollPx

/**
 * The TV Guide opens on now, on every screen density.
 *
 * It did not. The timeline is laid out in dp and the scroll that parks it on
 * the current time was computed in dp too — then handed to `scrollTo`, which
 * takes pixels. On a 1080p television, where a dp is two pixels, the guide
 * scrolled half as far as it meant to: opened at 16:00 it showed 08:00. The
 * NOW button called the same function, so it could not fix it either.
 *
 * Nobody caught it because the arithmetic is right at density 1.0, which is
 * what a calculation looks like when you read it — and wrong at every density
 * a real device has.
 */
class GuideScrollTest {

    private val hour = 3_600_000L
    private val dayStart = 1_700_000_000_000L

    /** Distance from the left edge of the viewport to the now-line, in dp. */
    private fun leadDp(nowMs: Long, density: Float): Float {
        val scroll = guideScrollPx(nowMs, dayStart, density)
        return (guideNowPx(nowMs, dayStart, density) - scroll) / density
    }

    @Test
    fun `now sits the same distance in on every density`() {
        val evening = dayStart + 20 * hour
        // mdpi, a 720p stick, a 1080p television, a 4K panel, a phone.
        listOf(1.0f, 1.5f, 2.0f, 3.0f, 2.625f).forEach { density ->
            assertEquals(
                "at density $density the now-line is not where it was put",
                150f, leadDp(evening, density), 1f,
            )
        }
    }

    @Test
    fun `a 1080p television opens on the current hour, not half of it`() {
        // The reported shape of the bug, as a number. 16:00 at 220 dp an hour
        // is 3520 dp along; on a density-2 screen that is 7040 px. The old sum
        // scrolled to 16 * 220 - 300 = 3220 px, which is 07:19.
        val fourPm = dayStart + 16 * hour
        val scroll = guideScrollPx(fourPm, dayStart, density = 2f)
        assertTrue("scrolled to $scroll px — that is still the morning", scroll > 6_500)
        val hourShownAtLeftEdge = scroll / 2f / 220f
        assertTrue(
            "left edge of the guide is at hour $hourShownAtLeftEdge",
            hourShownAtLeftEdge in 15.0f..16.0f,
        )
    }

    @Test
    fun `just after midnight does not scroll before the start of the day`() {
        // Ten past midnight is 36 dp along — less than the 150 dp lead. The
        // scroll must stop at zero rather than go negative.
        assertEquals(0, guideScrollPx(dayStart + hour / 6, dayStart, density = 2f))
        assertEquals(0, guideScrollPx(dayStart, dayStart, density = 3f))
    }

    @Test
    fun `later in the day is further along`() {
        val a = guideScrollPx(dayStart + 9 * hour, dayStart, 2f)
        val b = guideScrollPx(dayStart + 21 * hour, dayStart, 2f)
        assertTrue(b > a)
        // Twelve hours apart is twelve hours of timeline, in this screen's pixels.
        assertEquals(12 * 220 * 2, b - a)
    }
}
