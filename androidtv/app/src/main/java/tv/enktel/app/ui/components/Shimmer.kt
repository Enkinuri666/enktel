package tv.enktel.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A drop-in shimmer modifier that overlays an animated diagonal light
 * sweep on whatever it's applied to.  Use it on a Box that already has
 * the target card's shape + a muted base fill: the effect reads like a
 * skeleton placeholder.
 *
 * Kept intentionally cheap (no infinite recomposition — the animation
 * drives a single Float that's read inside drawWithContent) so long
 * shimmer grids scroll without dropping frames on budget hardware.
 */
fun Modifier.shimmer(
    baseColor: Color = Color(0xFF1F2937),
    highlightColor: Color = Color(0xFF374151),
    durationMs: Int = 1400,
): Modifier = composedShimmer(baseColor, highlightColor, durationMs)

@Composable
private fun Modifier.composedShimmer(
    baseColor: Color,
    highlightColor: Color,
    durationMs: Int,
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerP",
    )
    return this
        .background(baseColor)
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            val sweep = progress * (w + h) - h
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor.copy(alpha = 0.35f),
                    Color.Transparent,
                ),
                start = Offset(sweep, 0f),
                end = Offset(sweep + h, h),
            )
            drawRect(brush = brush)
        }
}

/**
 * Convenience skeleton card matching the standard PosterCard shape used
 * across the Home / Movies / Series grids.  Callers render N of these
 * inside a rail while waiting for real data.
 */
@Composable
fun PosterSkeleton(wide: Boolean = false) {
    val w = if (wide) 220.dp else 140.dp
    val h = 190.dp
    Box(
        Modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(10.dp))
            .shimmer(),
    )
}

/** Skeleton for a Guide row while EPG loads. */
@Composable
fun EpgRowSkeleton() {
    Box(
        Modifier
            .height(56.dp)
            .width(600.dp)
            .clip(RoundedCornerShape(6.dp))
            .shimmer(),
    )
}
