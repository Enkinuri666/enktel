package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurfaceHigh

/**
 * Foundation-only pull-to-refresh.
 *
 * The app builds on `androidx.tv.material3`, which has no PullToRefresh, and
 * does not depend on the phone Material3 that does — so the one gesture every
 * phone user reaches for to get the latest (a new guide, a re-synced catalogue)
 * was simply absent. This wraps a scrollable child, reads its over-scroll at the
 * top through a [NestedScrollConnection], and fires [onRefresh] once the pull
 * passes the threshold.
 *
 * Inert on television: a D-pad produces no over-scroll drag, so [pull] never
 * leaves zero and the indicator never shows. Safe to wrap a shared screen.
 */
@Composable
fun PullRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { PULL_THRESHOLD_DP.dp.toPx() }
    // Resisted pull distance, in pixels. Written only from the gesture.
    var pull by remember { mutableFloatStateOf(0f) }

    // While a refresh runs, park the indicator at the threshold; otherwise it
    // follows the finger. Animated so release and completion both glide.
    val indicator by animateFloatAsState(
        targetValue = if (refreshing) thresholdPx else pull,
        label = "pullIndicator",
    )

    val connection = remember(enabled, refreshing, thresholdPx, onRefresh) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Finger moving up while a pull is showing: retract it first,
                // before the list itself scrolls.
                if (!enabled || refreshing) return Offset.Zero
                if (available.y < 0 && pull > 0f) {
                    val take = (-pull).coerceAtLeast(available.y)
                    pull = (pull + take).coerceAtLeast(0f)
                    return Offset(0f, take)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // The child left downward drag unconsumed — it is at the top.
                if (!enabled || refreshing) return Offset.Zero
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = pullOffset(pull, available.y, thresholdPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!refreshing && pull >= thresholdPx) onRefresh()
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        // The indicator rides down from the top edge on the resisted distance.
        if (indicator > 0.5f) {
            val progress = (indicator / thresholdPx).coerceIn(0f, 1.5f)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (indicator - thresholdPx).toInt()) }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(EnktelSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Pull to refresh",
                    tint = EnktelBlue,
                    modifier = Modifier
                        .size(22.dp)
                        // Spin while refreshing is driven by the caller flipping
                        // `refreshing`; before release the arrow turns with the
                        // pull so it reads as "keep going".
                        .rotate(if (refreshing) 0f else progress * 270f),
                )
            }
        }
    }
}

/** 72 dp of travel past the top edge triggers a refresh. */
const val PULL_THRESHOLD_DP = 72

/**
 * New resisted pull distance after [delta] px of raw over-scroll.
 *
 * Rubber-band: the pull follows the finger at half speed and eases to a stop as
 * it passes the threshold, so it never tracks 1:1 and cannot be yanked
 * arbitrarily far. Pure, so the feel is unit-testable without a gesture.
 */
fun pullOffset(current: Float, delta: Float, thresholdPx: Float): Float {
    if (delta <= 0f) return current
    // Past the threshold, add only a fraction so it asymptotes rather than
    // following the finger for ever.
    val factor = if (current < thresholdPx) 0.5f else 0.25f
    return (current + delta * factor).coerceAtMost(thresholdPx * 1.6f)
}

/** True once the pull is far enough that releasing will refresh. */
fun pullWouldTrigger(pull: Float, thresholdPx: Float): Boolean = pull >= thresholdPx
