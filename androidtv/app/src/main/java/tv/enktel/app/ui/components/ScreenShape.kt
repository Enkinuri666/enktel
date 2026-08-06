package tv.enktel.app.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
     * A television is never short: 1080p at the standard TV density is
     * ~648 dp tall, and 720p is ~720 dp, both comfortably past the threshold.
     */
    val short: Boolean,
) {
    /** Outer horizontal page padding. */
    val padH: Dp
        get() = when {
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
            // 500 dp, not 600: a 1080p television reports ~648 dp and must
            // never be treated as short, while a landscape phone (~360 dp) and
            // a small landscape tablet (~450 dp) both must be.
            short = cfg.screenHeightDp < 500,
        )
    }
}
