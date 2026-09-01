package tv.enktel.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.enktel.app.ui.components.ScreenShape

class ScreenShapeTest {

    /**
     * A 1080p television as Android reports it: 960x540 dp, not 1920x1080.
     * The panel is xhdpi, so the layout size is half the pixel size.
     */
    private fun tv() = ScreenShape(
        widthDp = 960, heightDp = 540, landscape = true, narrow = false, short = false,
    )

    private fun phonePortrait() = ScreenShape(
        widthDp = 411, heightDp = 891, landscape = false, narrow = true, short = false,
    )

    private fun phoneLandscape() = ScreenShape(
        widthDp = 891, heightDp = 411, landscape = true, narrow = false, short = true,
    )

    @Test
    fun `TV page padding stays outside the overscan safe zone`() {
        // The rule this exists for. A television may crop the edges of the
        // signal, and Android TV's guidance is to keep content 5% in from each
        // edge — 48 dp horizontally and 27 dp vertically on a 960x540 dp
        // viewport. Anything less puts the first and last rows of a screen
        // where a cropping panel eats them, and the symptom is menu items that
        // appear to continue below the bottom of the picture.
        //
        // Four screens had drifted to 20 dp and 48 dp because nothing checked.
        assumeTrue("ten-foot layout only", BuildConfig.FLAVOR != "mobile")
        val s = tv()
        assertTrue("padH ${s.padH} is inside the 5% overscan band", s.padH >= 48.dp)
        assertTrue("padV ${s.padV} is inside the 5% overscan band", s.padV >= 27.dp)
    }

    @Test
    fun `a television is never treated as a cramped screen`() {
        // 540 dp of height is a TV, not a landscape phone. Classing it short
        // would strip the vertical padding this same test insists on.
        assumeTrue("ten-foot layout only", BuildConfig.FLAVOR != "mobile")
        assertTrue(tv().padV >= 27.dp)
    }

    @Test
    fun `a phone does not pay the television's margins`() {
        // The safe zone is a property of televisions. Spending 58 dp a side on
        // a 411 dp phone would leave less than half the width for content.
        assumeTrue("handset layout only", BuildConfig.FLAVOR == "mobile")
        assertTrue(phonePortrait().padH < 48.dp)
    }

    @Test
    fun `a short viewport spends less height on margin`() {
        // A landscape phone has roughly 411 dp of height. The complaint that
        // produced ScreenShape was vertical padding tuned for portrait being
        // applied to a viewport with half the room.
        assumeTrue("handset layout only", BuildConfig.FLAVOR == "mobile")
        assertTrue(phoneLandscape().padV < phonePortrait().padV)
    }

    @Test
    fun `section and header gaps shrink on a short viewport`() {
        assertTrue(phoneLandscape().sectionGap <= phonePortrait().sectionGap)
        assertTrue(phoneLandscape().headerGap <= phonePortrait().headerGap)
    }
}
