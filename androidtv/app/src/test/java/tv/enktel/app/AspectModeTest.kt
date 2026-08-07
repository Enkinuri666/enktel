package tv.enktel.app

import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.player.AspectMode

class AspectModeTest {

    @Test
    fun `cycles through every mode and returns to the start`() {
        var m = AspectMode.FIT
        val seen = mutableListOf(m)
        repeat(AspectMode.entries.size - 1) {
            m = m.next()
            seen += m
        }
        assertEquals(AspectMode.entries.toList(), seen)
        // The button is a cycle, not a ratchet: pressing it once more must come
        // back to where it started rather than sticking on the last mode.
        assertEquals(AspectMode.FIT, m.next())
    }

    @Test
    fun `maps to the ContentScale that matches the old resize mode`() {
        // These three pairings are the whole contract with ContentFrame, and
        // getting FILL and ZOOM the wrong way round is invisible in review:
        // both fill the screen, but only one of them distorts.
        assertEquals(ContentScale.Fit, AspectMode.FIT.scale)          // RESIZE_MODE_FIT
        assertEquals(ContentScale.FillBounds, AspectMode.FILL.scale)  // RESIZE_MODE_FILL
        assertEquals(ContentScale.Crop, AspectMode.ZOOM.scale)        // RESIZE_MODE_ZOOM
    }

    @Test
    fun `every mode has a distinct non-blank label`() {
        // The label is the only thing telling the user which mode they landed
        // in, so a blank or duplicated one makes the toast useless.
        val labels = AspectMode.entries.map { it.label }
        assertTrue(labels.none { it.isBlank() })
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `no mode maps to the same scale as another`() {
        val scales = AspectMode.entries.map { it.scale }
        assertEquals(scales.size, scales.distinct().size)
        assertNotEquals(AspectMode.FILL.scale, AspectMode.ZOOM.scale)
    }
}
