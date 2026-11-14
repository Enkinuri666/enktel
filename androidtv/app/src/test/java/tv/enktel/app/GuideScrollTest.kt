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

/**
 * The guide opens describing something.
 *
 * Its top band — a fifth of the screen — said "Nothing playing" beside one
 * line of instructions until focus happened to land on a programme.
 */
class GuideOpeningTest {
    private val now = 1_800_000_000_000L
    private fun ch(n: Int, epg: String = "e$n") = tv.enktel.app.data.db.Channel(
        key = "1:$n", profileId = 1, streamId = n.toLong(), name = "Channel $n", epgId = epg,
    )
    private fun prog(epg: String, from: Long, to: Long, title: String) = tv.enktel.app.data.db.EpgProgram(
        profileId = 1, epgId = epg, startMs = now + from, endMs = now + to, title = title,
    )
    private val channels = listOf(ch(1), ch(2), ch(3))
    private val programs = mapOf(
        "e1" to listOf(prog("e1", -7_200_000, -3_600_000, "Earlier"), prog("e1", 600_000, 4_200_000, "Later")),
        "e2" to listOf(prog("e2", -600_000, 1_800_000, "On Two")),
        "e3" to listOf(prog("e3", -60_000, 3_000_000, "On Three")),
    )
    private fun open(playing: String?) =
        tv.enktel.app.ui.guide.guideDefaultHighlight(channels, programs, now, playing)

    @Test
    fun `it opens on the first channel with something airing`() {
        // Channel 1 has a gap right now; it is skipped rather than shown empty.
        assertEquals("On Two", open(null)?.second?.title)
        assertEquals("1:2", open(null)?.first?.key)
    }

    @Test
    fun `it opens on the channel being watched`() {
        assertEquals("On Three", open("1:3")?.second?.title)
    }

    @Test
    fun `a watched channel with nothing listed falls back to the first that has`() {
        assertEquals("On Two", open("1:1")?.second?.title)
        assertEquals("On Two", open("9:99")?.second?.title)
    }

    @Test
    fun `no guide data is still no answer, not a crash`() {
        assertEquals(null, tv.enktel.app.ui.guide.guideDefaultHighlight(channels, emptyMap(), now, null))
        assertEquals(null, tv.enktel.app.ui.guide.guideDefaultHighlight(emptyList(), programs, now, "1:1"))
    }
}
