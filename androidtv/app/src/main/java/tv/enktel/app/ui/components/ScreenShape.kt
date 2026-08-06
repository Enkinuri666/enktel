package tv.enktel.app.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
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

/** Current viewport shape. Recomposes on rotation and on window resize. */
@Composable
fun rememberScreenShape(): ScreenShape {
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp, cfg.orientation) {
        val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        ScreenShape(
            widthDp = cfg.screenWidthDp,
            heightDp = cfg.screenHeightDp,
            landscape = landscape,
            narrow = cfg.screenWidthDp < 600,
            // The two populations to separate are a landscape phone (~360 dp)
            // and a television (540 dp at 1080p). 460 sits between them with
            // room on both sides; 500 left only 40 dp of margin against a TV,
            // which is close enough to be one odd device from misclassifying
            // the ten-foot layout as cramped.
            short = cfg.screenHeightDp < 460,
        )
    }
}
