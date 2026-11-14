package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.enktel.app.data.repo.ChannelFilters

/**
 * The colours a name is drawn in when it has no artwork.
 *
 * Kept apart from the composable so the two things that must hold can be
 * tested without drawing anything: the same name always gets the same colours,
 * and white letters are readable on every one of them.
 */
object MonogramArt {

    /** 0 until 360, the same for a name every time, on every device. */
    fun hueOf(name: String): Float {
        // Not String.hashCode: nothing guarantees it, and "the tile changed
        // colour after an update" is a bug report nobody could act on. FNV-1a
        // over the characters, which is ten lines and fixed for ever.
        var h = 0x811C9DC5.toInt()
        for (c in name.trim().lowercase()) {
            h = h xor c.code
            h *= 0x01000193
        }
        return ((h ushr 8) % 360).toFloat()
    }

    /**
     * Top-left and bottom-right of the plate.
     *
     * Dark on purpose. The plate sits on a near-black page beside real
     * artwork, and a bright block of colour where a logo is missing would
     * draw the eye to exactly the tiles that have least to show. Lightness
     * stays at or under 0.30, which also keeps white letters above 7:1.
     */
    fun colors(name: String): Pair<Color, Color> {
        val hue = hueOf(name)
        return top(hue) to Color.hsl((hue + 28f) % 360f, 0.52f, 0.13f)
    }

    /**
     * The lighter corner, as light as this hue can be with white still
     * readable on it.
     *
     * One lightness for every hue does not work: the eye finds green and
     * yellow far brighter than blue at the same figure, and at 0.30 a green
     * plate left white letters at 6:1. So each hue starts there and steps
     * down until it clears the target — blues stay where they are, greens
     * settle a few points darker.
     */
    internal fun top(hue: Float): Color {
        var lightness = 0.30f
        var color = Color.hsl(hue, 0.46f, lightness)
        while (lightness > 0.10f && contrastWithWhite(color) < 7.5) {
            lightness -= 0.01f
            color = Color.hsl(hue, 0.46f, lightness)
        }
        return color
    }

    private fun contrastWithWhite(c: Color): Double = 1.05 / (c.luminance() + 0.05)
}

/**
 * What stands in for a logo or a poster that is not there.
 *
 * There were nine of these, each written where it was needed: black with dim
 * letters on the Live TV grid, one blue-to-purple wash on every poster, grey
 * with `take(2)` of the raw name in lists — so fifteen Australian channels in
 * a row all read "AU" on identical grey squares. On a line whose provider
 * sends few logos that is most of the screen.
 *
 * One plate now. Its colour comes from the name, so a row of them can be told
 * apart at a glance and a channel is the same colour wherever it turns up;
 * its letters are the initials the grid already used, sized to the plate
 * rather than to whichever screen it happens to be on.
 *
 * Fills whatever it is put in. The caller owns the shape and the size.
 */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier) {
    val (top, bottom) = remember(name) { MonogramArt.colors(name) }
    val letters = remember(name) { ChannelFilters.initials(name) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center,
    ) {
        // A soft light in the top corner, so the plate reads as a surface
        // rather than a fill.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset.Zero,
                    radius = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).toPx() },
                ),
            ),
        )
        // A third of the short side, so two letters fit a square plate and
        // three ("C10") still do; never smaller than can be read.
        val side = minOf(maxWidth, maxHeight).value
        val size = (side * (if (letters.length > 2) 0.28f else 0.34f)).coerceIn(10f, 44f)
        Text(letters, color = Color.White.copy(alpha = 0.92f), fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
