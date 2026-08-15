package tv.enktel.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's corner radii, and the spacing rhythm they sit in.
 *
 * ## Why this exists
 *
 * There were 199 `RoundedCornerShape(N.dp)` call sites using **eighteen**
 * distinct radii: 2, 3, 4, 6, 7, 8, 10, 12, 14, 15, 16, 18, 19, 20, 24, 28, 32
 * and 50. Nothing chose those numbers. They accumulated one component at a
 * time, so a card was 14 dp on one screen and 12 dp on the next, a chip was 8
 * dp here and 6 dp there, and a dialog was 18, 19 or 20 depending on which week
 * it was written.
 *
 * No single one of those is wrong. The absence of a repeating figure is what
 * makes a dense interface read as assembled rather than designed — the eye
 * notices that two things which do the same job are not shaped the same, long
 * before it could tell you the difference is two device-independent pixels.
 *
 * Six steps, on a 4 dp rhythm up to [xl]. Reach for the nearest one rather
 * than adding a seventh.
 *
 * ## What the steps are for
 *
 * | step | dp | used by |
 * | :--- | :--- | :--- |
 * | [xs] | 4 | hairlines, progress bars, the smallest pills |
 * | [sm] | 8 | chips, badges, inline controls |
 * | [md] | 12 | buttons, list rows, compact cards |
 * | [lg] | 16 | posters, panels, the dominant card radius |
 * | [xl] | 24 | dialogs, sheets, the large overlay surfaces |
 * | [shapePill] | 50% | anything that should read as fully round |
 *
 * ## A note on the migration
 *
 * The existing call sites were snapped to these values rather than rewritten to
 * reference the tokens, because 199 edits that each add an import are 199
 * chances to break a build that cannot be compiled locally. Changing a number
 * literal cannot. New code should use the tokens; old code now at least agrees
 * with them, and `DesignTokensTest` pins the scale so it cannot quietly grow a
 * seventh step.
 *
 * Snapping is safe everywhere except one case, and that case had to be found by
 * hand: a radius chosen as *half of a known height* is not a corner, it is a
 * request for a pill, and rounding it to the nearest step silently un-rounds the
 * shape. A 64 dp box at radius 32 is a circle; the same box at 24 is a squircle,
 * and nothing in the diff says so. Those nine sites now say `percent = 50`,
 * which states the intent and survives any later change to the height.
 */
object EnktelRadius {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    /** The shapes, for call sites that want one directly. */
    val shapeXs = RoundedCornerShape(xs)
    val shapeSm = RoundedCornerShape(sm)
    val shapeMd = RoundedCornerShape(md)
    val shapeLg = RoundedCornerShape(lg)
    val shapeXl = RoundedCornerShape(xl)

    /**
     * Fully round, whatever the height turns out to be.
     *
     * Written as a percentage rather than a large Dp deliberately. `999.dp`
     * would render the same — Compose clamps a radius to half the shorter side —
     * but it only *happens* to be a pill, and it reads as a mistake next to a
     * scale that stops at 24. `percent = 50` is the thing itself.
     *
     * Note the two spellings are not interchangeable: `RoundedCornerShape(50)`
     * is the Int overload and means 50 percent, while
     * `RoundedCornerShape(50.dp)` is a 50 dp corner. They are one character
     * apart in a diff and mean different shapes, which is reason enough to name
     * the argument at every call site.
     */
    val shapePill = RoundedCornerShape(percent = 50)

    /** Every step, smallest first — for tests and for pickers. */
    val all: List<Dp> = listOf(xs, sm, md, lg, xl)
}

/**
 * The spacing rhythm.
 *
 * Same argument as [EnktelRadius], same 4 dp step. These are offered rather
 * than enforced: padding is far more layout-specific than a corner radius, and
 * a value chosen to make one row line up with another above it is a reason, not
 * drift. The scale exists so that the *unreasoned* cases have somewhere
 * obvious to land.
 */
object EnktelSpace {
    /** Between a label and the thing it labels. */
    val xs: Dp = 4.dp
    /** Between items in a row. */
    val sm: Dp = 8.dp
    /** Inside a control. */
    val md: Dp = 12.dp
    /** Between grouped blocks. */
    val lg: Dp = 16.dp
    /** Between sections. */
    val xl: Dp = 24.dp
    /** Between a section and an unrelated one. */
    val xxl: Dp = 32.dp

    val all: List<Dp> = listOf(xs, sm, md, lg, xl, xxl)
}
