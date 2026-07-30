package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusable
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * A two-pane split whose divider the user can drag to re-balance.
 *
 * Every split in the player used to be a hardcoded weight — 60/40 for
 * video-versus-dock in landscape, a locked 16:9 video in portrait, 62/38 for
 * channel-list-versus-guide. Those numbers can't be right for everyone: a
 * 21:9 phone in landscape wants a different balance from a tablet, and the
 * guide column at 38 % truncated almost every programme title. Making the
 * divider draggable moves that decision to the person looking at the screen.
 *
 * Works for both axes:
 *  - [Orientation.Horizontal] — panes side by side, divider drags left/right.
 *  - [Orientation.Vertical] — panes stacked, divider drags up/down.
 *
 * ### Remotes as well as fingers
 *
 * A drag handle is useless on a TV remote, so the divider is also focusable
 * and responds to the D-pad: left/right (or up/down) nudge it by
 * [NUDGE_FRACTION] per press. Without that this feature would silently not
 * exist on the platform the app is primarily built for.
 *
 * [fraction] is the share given to the first pane. It's clamped to
 * [minFraction]..[maxFraction] so neither pane can be dragged to nothing —
 * a pane collapsed to zero can't be dragged back, since its edge is what you
 * grab. The caller owns the value, so it can be persisted.
 */
private const val NUDGE_FRACTION = 0.04f

@Composable
fun ResizableSplit(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    orientation: Orientation,
    modifier: Modifier = Modifier,
    minFraction: Float = 0.25f,
    maxFraction: Float = 0.8f,
    /** Thickness of the grabbable divider. Deliberately larger than the
     *  1 dp hairline it draws, so it's a realistic touch target. */
    handleThickness: androidx.compose.ui.unit.Dp = 20.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val safeFraction = fraction.coerceIn(minFraction, maxFraction)
    var handleFocused by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        // Total extent along the drag axis, in px, so a drag delta can be
        // converted into a fraction of the whole.
        val totalPx = with(density) {
            if (orientation == Orientation.Horizontal) maxWidth.toPx() else maxHeight.toPx()
        }.coerceAtLeast(1f)

        fun applyDelta(deltaPx: Float) {
            onFractionChange((safeFraction + deltaPx / totalPx).coerceIn(minFraction, maxFraction))
        }

        fun nudge(forward: Boolean) {
            val step = if (forward) NUDGE_FRACTION else -NUDGE_FRACTION
            onFractionChange((safeFraction + step).coerceIn(minFraction, maxFraction))
        }

        val dragState = rememberDraggableState { delta -> applyDelta(delta) }

        val handleModifier = Modifier
            .draggable(state = dragState, orientation = orientation)
            .onFocusChanged { handleFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val backward = if (orientation == Orientation.Horizontal) Key.DirectionLeft else Key.DirectionUp
                val forward = if (orientation == Orientation.Horizontal) Key.DirectionRight else Key.DirectionDown
                when (event.key) {
                    backward -> { nudge(false); true }
                    forward -> { nudge(true); true }
                    else -> false
                }
            }

        if (orientation == Orientation.Horizontal) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().weight(safeFraction)) { first() }
                Box(
                    handleModifier.fillMaxHeight().width(handleThickness),
                    contentAlignment = Alignment.Center,
                ) { Grip(orientation, handleFocused) }
                Box(Modifier.fillMaxHeight().weight(1f - safeFraction)) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(safeFraction)) { first() }
                Box(
                    handleModifier.fillMaxWidth().height(handleThickness),
                    contentAlignment = Alignment.Center,
                ) { Grip(orientation, handleFocused) }
                Box(Modifier.fillMaxWidth().weight(1f - safeFraction)) { second() }
            }
        }
    }
}

/**
 * The visible part of the divider: a short rounded pill, brightened while the
 * handle holds D-pad focus so a remote user can see what they're about to
 * move. Intentionally understated — it should read as an affordance, not a
 * piece of chrome competing with the video.
 */
@Composable
private fun Grip(orientation: Orientation, focused: Boolean) {
    val color = if (focused) EnktelBlue else EnktelTextDim.copy(alpha = 0.5f)
    val length = if (focused) 44.dp else 32.dp
    Box(
        Modifier
            .size(
                width = if (orientation == Orientation.Horizontal) 4.dp else length,
                height = if (orientation == Orientation.Horizontal) length else 4.dp,
            )
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}
