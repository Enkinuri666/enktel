package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.repo.EpgShift

/**
 * Settings offers an "EPG timezone offset" for guides whose clock does not
 * match the viewer's. The chosen value was written to preferences and read by
 * nothing: the chip highlighted, and the guide sat exactly where it was.
 *
 * Now that it is applied, the arithmetic is the thing worth pinning. It has to
 * go in opposite directions on the two sides of a query — bounds into the
 * database's frame, results into the viewer's — and a sign error either way
 * produces a guide that is wrong by twice the correction, which is a worse
 * bug than the one being fixed.
 */
class EpgShiftTest {

    private val hour = 3_600_000L

    private fun prog(start: Long, end: Long) = EpgProgram(
        profileId = 1, epgId = "bbc1", startMs = start, endMs = end,
        title = "Match of the Day", desc = "",
    )

    @Test
    fun `a positive offset makes programmes appear later`() {
        // The caption promises "positive = later". A programme the panel
        // stored at 19:00 shows at 20:00 with +60.
        assertEquals(20 * hour, EpgShift.toWall(19 * hour, 60))
    }

    @Test
    fun `a negative offset makes programmes appear earlier`() {
        assertEquals(18 * hour, EpgShift.toWall(19 * hour, -60))
    }

    @Test
    fun `query bounds move the opposite way to results`() {
        // This is the half that is easy to forget. Asking "what is on at 20:00"
        // with a +1h correction means asking the database about 19:00. Shift
        // only the results and the right programmes come back labelled
        // correctly but selected by the wrong window — an hour of guide simply
        // missing at the edges, which reads as patchy EPG rather than a bug.
        assertEquals(19 * hour, EpgShift.toStored(20 * hour, 60))
        assertEquals(21 * hour, EpgShift.toStored(20 * hour, -60))
    }

    @Test
    fun `the two directions undo each other`() {
        val wall = 1_700_000_000_000L
        listOf(-180, -60, -30, 0, 30, 60, 180).forEach { off ->
            assertEquals(wall, EpgShift.toWall(EpgShift.toStored(wall, off), off))
        }
    }

    @Test
    fun `a shifted programme keeps its duration`() {
        val p = prog(19 * hour, 20 * hour)
        val s = EpgShift.shift(p, 90)
        assertEquals(p.endMs - p.startMs, s.endMs - s.startMs)
        assertEquals(19 * hour + 90 * 60_000L, s.startMs)
        assertEquals("Match of the Day", s.title)
    }

    @Test
    fun `zero returns the very same instance`() {
        // The default, on a list of hundreds redrawn every tick. Allocating a
        // copy per row to add zero would be a real cost on a Fire Stick.
        val p = prog(1, 2)
        assertSame(p, EpgShift.shift(p, 0))
        val list = listOf(p, prog(3, 4))
        assertSame(list, EpgShift.shift(list, 0))
    }

    @Test
    fun `every offset the chips offer is handled`() {
        // Settings offers exactly these. None of them should be a special case.
        val p = prog(12 * hour, 13 * hour)
        listOf(-180, -120, -60, -30, 0, 30, 60, 120, 180).forEach { off ->
            val s = EpgShift.shift(p, off)
            assertEquals(12 * hour + off * 60_000L, s.startMs)
            assertEquals(13 * hour + off * 60_000L, s.endMs)
        }
    }

    @Test
    fun `a whole list shifts together`() {
        val list = listOf(prog(hour, 2 * hour), prog(2 * hour, 3 * hour))
        val s = EpgShift.shift(list, 30)
        assertEquals(hour + 30 * 60_000L, s[0].startMs)
        assertEquals(2 * hour + 30 * 60_000L, s[1].startMs)
        // Still back to back — a gap opening between adjacent programmes would
        // put a hole in the guide grid.
        assertEquals(s[0].endMs, s[1].startMs)
    }
}
