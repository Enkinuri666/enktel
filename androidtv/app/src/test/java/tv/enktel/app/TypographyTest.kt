package tv.enktel.app

import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.theme.EnktelMaterialTypography

/**
 * Pins the fix for the double-spaced text.
 *
 * `EnktelTheme` passed no typography to `MaterialTheme`, so every `Text` that
 * set only a font size — five hundred of them — inherited tv-material's
 * default: a 24 sp line and 0.5 sp of tracking, whatever size it was drawn at.
 * Wrapped captions came out double-spaced and wrapped headlines had their
 * second line riding into the first.
 *
 * The fix is one object, which makes it one object's worth of easy to undo:
 * swap an `em` for an `sp` while tidying, and the bug is back on every screen
 * with nothing failing. These are the properties that must hold.
 */
class TypographyTest {

    private val styles = with(EnktelMaterialTypography) {
        mapOf(
            "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
        )
    }

    @Test
    fun `every line height is a ratio`() {
        // The whole point. An sp line height is right for exactly one font
        // size; an em one follows whatever size the call site asks for.
        styles.forEach { (name, style) ->
            assertEquals(
                "$name has a fixed line height, so a Text that overrides fontSize keeps the wrong leading",
                TextUnitType.Em,
                style.lineHeight.type,
            )
        }
    }

    @Test
    fun `leading is in the range text is actually set at`() {
        styles.forEach { (name, style) ->
            val em = style.lineHeight.value
            assertTrue("$name leading $em is tight enough for lines to collide", em >= 1.1f)
            assertTrue("$name leading $em is loose enough to read as double-spaced", em <= 1.5f)
        }
    }

    @Test
    fun `the default text style is led for interface copy`() {
        // bodyLarge is what a bare Text inherits. 24 sp on 12 sp was 2.0.
        val em = EnktelMaterialTypography.bodyLarge.lineHeight.value
        assertTrue("default leading is $em", em in 1.25f..1.45f)
    }

    @Test
    fun `nothing is tracked out by default`() {
        // Half an sp on a 12 sp face is four percent of the em. Tracking is a
        // decision a style makes (see EnktelType.overline), not a default.
        styles.forEach { (name, style) ->
            assertEquals("$name carries default tracking", 0f, style.letterSpacing.value, 0.0001f)
        }
    }

    @Test
    fun `large type is led tighter than small type`() {
        // Leading that suits a caption leaves a two-line headline looking like
        // two headlines.
        with(EnktelMaterialTypography) {
            assertTrue(displayLarge.lineHeight.value < bodyLarge.lineHeight.value)
            assertTrue(headlineMedium.lineHeight.value < bodyLarge.lineHeight.value)
        }
    }
}
