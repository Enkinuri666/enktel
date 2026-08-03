package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.diag.EpgOffset

class EpgOffsetTest {

    private val now = 1_784_233_800_000L // 2026-07-16T20:30:00Z
    private val min = 60_000L

    @Test fun `aligned clocks and guide report nothing`() {
        val a = EpgOffset.audit(now, now, listOf((now - 15 * min) to (now + 15 * min)))
        assertTrue(a.measured)
        assertFalse(a.serverNotable)
        assertFalse(a.guideNotable)
        assertNull("nothing to report when it lines up", a.verdict)
    }

    @Test fun `a programme that merely started earlier is not drift`() {
        // Half-way through a 60-minute show is normal, not skew.
        val a = EpgOffset.audit(now, now, listOf((now - 30 * min) to (now + 30 * min)))
        assertFalse(a.guideNotable)
    }

    @Test fun `panel clock ahead is reported as a server skew`() {
        val a = EpgOffset.audit(now + 5 * min, now, listOf((now - 15 * min) to (now + 15 * min)))
        assertEquals(5 * min, a.serverSkewMs)
        assertTrue(a.serverNotable)
        assertTrue(a.verdict!!.contains("Panel clock"))
        assertTrue(a.verdict!!.contains("catch-up"))
    }

    @Test fun `guide shifted an hour is called out as the wrong programme`() {
        val shifted = listOf((now + 45 * min) to (now + 105 * min))
        val a = EpgOffset.audit(now, now, shifted)
        assertTrue(a.guideNotable)
        assertTrue(a.severe)
        assertNotNull(a.verdict)
        assertTrue(a.verdict!!.contains("wrong programme"))
    }

    @Test fun `the median is used so one bad programme does not swing it`() {
        val programmes = listOf(
            (now - 15 * min) to (now + 15 * min),
            (now - 15 * min) to (now + 15 * min),
            (now + 600 * min) to (now + 660 * min), // one nonsense row
        )
        val a = EpgOffset.audit(now, now, programmes)
        assertFalse("median should ignore the outlier", a.guideNotable)
        assertEquals(3, a.programmesChecked)
    }

    @Test fun `an unknown server clock does not fabricate a skew`() {
        val a = EpgOffset.audit(0, now, listOf((now - 5 * min) to (now + 5 * min)))
        assertEquals(0L, a.serverSkewMs)
        assertTrue(a.measured)
    }

    @Test fun `no data at all is reported as unmeasured`() {
        val a = EpgOffset.audit(0, now, emptyList())
        assertFalse(a.measured)
        assertNull(a.verdict)
    }

    @Test fun `malformed programme rows are discarded`() {
        val a = EpgOffset.audit(0, now, listOf(0L to 0L, 100L to 50L))
        assertEquals(0, a.programmesChecked)
    }

    @Test fun `a missing device clock is an error not a zero`() {
        assertEquals("no device clock", EpgOffset.audit(now, 0, emptyList()).error)
    }
}
