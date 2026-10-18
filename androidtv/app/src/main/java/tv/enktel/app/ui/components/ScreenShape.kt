package tv.enktel.app.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.enktel.app.BuildConfig

/**
 * The shape of the viewport, as a layout decision rather than a device class.
 *
 * Every responsive choice in this app was a `screenWidthDp < 600` test and
 * nothing anywhere looked at height — which is precisely backwards for the
 * complaint it produced. Turn a phone sideways and the width goes *up*, past
 * 600, so each of those tests concluded "roomy" and applied the widest, most
 * generous spacing available. Meanwhile the height collapsed to around 360 dp.
 * The screens with the most vertical padding, the tallest headers and the
 * biggest section gaps were the ones with the least room to put them in, and
 * the result was Live TV, the guide, the docked guide, VOD and Settings all
 * feeling crowded in landscape and fine in portrait.
 *
 * So: measure both, and let the short axis have a say.
 */
data class ScreenShape(
    val widthDp: Int,
    val heightDp: Int,
    val landscape: Boolean,
    /** Phone-width portrait: a multi-column grid does not fit. */
    val narrow: Boolean,
    /**
     * Not enough vertical room for full-height chrome — a phone or small
     * tablet in landscape, and a foldable half-open.
     *
     * A television must never be classed short, and the margin is thinner than
     * it looks: a 1080p Android TV reports 960×540 dp, not 1080 — the panel is
     * 1080 physical pixels at xhdpi, so the layout height is half of it. (An
     * earlier version of this comment claimed ~648 dp, which is simply wrong;
     * the nav rail overflowing its 540 dp column is what proved it.)
     */
    val short: Boolean,
) {
    /**
     * True on the ten-foot build, where the edge rules are different: a television
     * may crop the edges of the signal, so nothing important can sit in the
     * outer band.
     */
    private val tenFoot: Boolean get() = BuildConfig.FLAVOR != "mobile"

    /**
     * Outer horizontal page padding.
     *
     * On TV this is the overscan safe zone, not a taste decision. Android TV's
     * guidance is 5 % of the width — 48 dp on a 960 dp layout — and 58 dp is
     * the figure that survives the older panels that still crop. The app was
     * sitting at 48 dp on wide screens and dropping to 32 dp on the rest, which
     * put content inside the band that a cropping TV eats.
     */
    val padH: Dp
        get() = when {
            tenFoot -> 58.dp
            narrow -> 16.dp
            landscape && short -> 28.dp
            widthDp >= 1200 -> 48.dp
            else -> 32.dp
        }

    /**
     * Outer vertical page padding.
     *
     * This is the number that hurt most: 28 dp top and bottom is 15 % of a
     * landscape phone's height spent on margin before anything is drawn.
     */
    val padV: Dp
        get() = when {
            // Vertical overscan safe zone, same reasoning as padH.
            tenFoot -> 27.dp
            short -> 10.dp
            narrow -> 16.dp
            else -> 24.dp
        }

    /** Gap between stacked sections on a page. */
    val sectionGap: Dp get() = if (short) 8.dp else 14.dp

    /** Height for a screen header block (title + subtitle row). */
    val headerGap: Dp get() = if (short) 6.dp else 16.dp

    /**
     * How many poster columns fit, given a target tile width.
     *
     * Landscape phones were still being handed the portrait column count,
     * so a grid that had four across in portrait had four across in
     * landscape too — same tiles, twice the gutters, half the rows visible.
     */
    fun columns(targetTileDp: Int, min: Int = 2, max: Int = 10): Int =
        ((widthDp - padH.value.toInt() * 2) / targetTileDp).coerceIn(min, max)
}

/**
 * Current viewport shape. Recomposes on rotation and on window resize.
 *
 * Measured from the window this app is actually drawn in, not from
 * [Configuration]. `screenWidthDp` and `screenHeightDp` describe the *screen*
 * — they exclude system bars inconsistently across versions, and in
 * split-screen, freeform or a desktop window they keep reporting the whole
 * display while the app has half of it. Every layout decision in this app
 * branches on these numbers, so a screen that is wrong here is wrong
 * everywhere at once: a phone in split-screen was being handed the two-pane
 * Settings layout and the 48 dp rail gutters for a pane 300 dp wide.
 *
 * `LocalWindowInfo.containerSize` is the window, in pixels, which is the
 * thing layout has to fit inside.
 */
@Composable
// The only place in the app allowed to read Configuration's screen size, and
// only as the fallback below. Suppressed with a reason rather than left as a
// standing warning: a category that always has four hits in it is a category
// people stop reading, and this is the file that exists so nowhere else has
// to make this mistake.
@Suppress("ConfigurationScreenWidthHeight")
fun rememberScreenShape(): ScreenShape {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    return remember(container, cfg.screenWidthDp, cfg.screenHeightDp, density.density) {
        // Before the first measure the container is reported as zero, and a
        // 0 x 0 window classifies as narrow *and* short — which would hand
        // every screen the phone layout for one frame, and the whole app the
        // phone layout for good on any surface that never reports a size.
        // Configuration is the fallback precisely because it is always
        // populated, even when it is a little wrong.
        val widthDp = if (container.width > 0) {
            (container.width / density.density).toInt()
        } else {
            cfg.screenWidthDp
        }
        val heightDp = if (container.height > 0) {
            (container.height / density.density).toInt()
        } else {
            cfg.screenHeightDp
        }
        ScreenShape(
            widthDp = widthDp,
            heightDp = heightDp,
            // From the window's own proportions rather than the device's
            // orientation. A phone held sideways with the app in the top half
            // of a split screen is a *tall* pane on a landscape device, and it
            // is the pane that has to be laid out.
            landscape = widthDp > heightDp,
            narrow = widthDp < 600,
            // The two populations to separate are a landscape phone (~360 dp)
            // and a television (540 dp at 1080p). 460 sits between them with
            // room on both sides; 500 left only 40 dp of margin against a TV,
            // which is close enough to be one odd device from misclassifying
            // the ten-foot layout as cramped.
            short = heightDp < 460,
        )
    }
}
