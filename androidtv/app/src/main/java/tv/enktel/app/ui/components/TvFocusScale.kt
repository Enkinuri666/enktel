package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer

/**
 * v1.27.0 TV cinematic refactor — hardware-accelerated focus-scale
 * modifier that mirrors the brief's "1.05x over 150 ms with a decelerate
 * curve, translationZ raised over neighbours" spec.
 *
 * Compose is our implementation vehicle here (not the View system), so
 * "decelerate 150 ms" maps to `tween(150, easing = FastOutSlowInEasing)`
 * — the tween-decelerate pairing runs on the render thread via
 * `graphicsLayer`, hitting the same 60 fps target the brief calls for
 * on Fire TV Stick / Nexus Player-class hardware.
 *
 * Two entry points:
 *   - [tvFocusScale] — attach to any focusable modifier chain and it'll
 *     grow the composable to 1.05× while focused, and lift z by 8 dp so
 *     it renders over neighbouring rail items.
 *   - [tvFocusScaleByFlag] — same animation but driven by a Boolean the
 *     caller already tracks (used inside Surface/onClick composables that
 *     don't expose their own interactionSource).
 *
 * Both keep the underlying view laid out at 1× — only the visual scale
 * changes, so scrolling and focus target rects stay stable.
 */
private const val TARGET_SCALE = 1.05f
private const val TARGET_Z_DP = 8f
private const val ANIM_MS = 150

@Composable
fun Modifier.tvFocusScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return tvFocusScaleByFlag(focused)
}

fun Modifier.tvFocusScaleByFlag(focused: Boolean): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (focused) TARGET_SCALE else 1f,
        animationSpec = tween(durationMillis = ANIM_MS),
        label = "tv-focus-scale",
    )
    val z by animateFloatAsState(
        targetValue = if (focused) TARGET_Z_DP else 0f,
        animationSpec = tween(durationMillis = ANIM_MS),
        label = "tv-focus-z",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationZ = z * density
    }
}

/**
 * Attach directly to a Composable that owns its own focus. Useful when
 * you can't drive a MutableInteractionSource (e.g. wrapping a plain Box
 * that becomes focusable via `.focusable()`). Reports focus back through
 * [onFocusedChanged] so the caller can drive backdrop crossfades.
 */
fun Modifier.tvFocusScaleReporting(onFocusedChanged: (Boolean) -> Unit): Modifier = composed {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    onFocusChanged {
        if (focused != it.isFocused) {
            focused = it.isFocused
            onFocusedChanged(it.isFocused)
        }
    }.then(tvFocusScaleByFlag(focused))
}
