package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer

/**
 * v1.27.0 TV cinematic refactor — hardware-accelerated focus-scale
 * modifier that mirrors the brief's "1.05x over 150 ms with a decelerate
 * curve, raised over neighbours" spec.
 *
 * Compose is our implementation vehicle here (not the View system), so
 * "decelerate 150 ms" maps to a tween on `graphicsLayer` running on the
 * render thread — hitting the same 60 fps target the brief calls for on
 * Fire TV Stick / Nexus Player-class hardware. `shadowElevation` is the
 * Compose-native way to lift the focused card over its neighbours (the
 * View system's `translationZ` isn't part of `GraphicsLayerScope`).
 *
 * Three entry points:
 *   - [tvFocusScale] — attach to any focusable modifier chain and it'll
 *     grow the composable to 1.05× while focused and elevate it 8 dp so
 *     it renders over neighbouring rail items.
 *   - [tvFocusScaleByFlag] — same animation but driven by a Boolean the
 *     caller already tracks (used inside Surface/onClick composables that
 *     don't expose their own interactionSource).
 *   - [tvFocusScaleReporting] — same animation, plus a callback so the
 *     caller can react to focus (used by the backdrop-crossfade rig in
 *     phase 2).
 *
 * Each keeps the underlying view laid out at 1× — only the visual scale
 * changes, so scrolling and focus target rects stay stable.
 */
// v1.35.0 — the scale target now comes from the active palette
// (EnktelFocusScale) rather than a constant here, so a theme owns its own
// focus language and this modifier can't drift away from PosterCard's ring.
// Elevation and duration stay fixed: 8 dp is what lifts a card clear of its
// neighbours, and 150 ms is the brief's decelerate window.
private const val TARGET_ELEVATION_DP = 8f
private const val ANIM_MS = 150

@Composable
fun Modifier.tvFocusScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return tvFocusScaleByFlag(focused)
}

fun Modifier.tvFocusScaleByFlag(focused: Boolean): Modifier = composed {
    val focusScale = tv.enktel.app.ui.theme.EnktelFocusScale
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = tween(durationMillis = ANIM_MS),
        label = "tv-focus-scale",
    )
    val elevationDp by animateFloatAsState(
        targetValue = if (focused) TARGET_ELEVATION_DP else 0f,
        animationSpec = tween(durationMillis = ANIM_MS),
        label = "tv-focus-elevation",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        shadowElevation = elevationDp * density
    }
}

fun Modifier.tvFocusScaleReporting(onFocusedChanged: (Boolean) -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    // Chain the two extensions on `this` directly. Wrapping the factory
    // in `.then(tvFocusScaleByFlag(...))` would add `this` to the chain
    // twice (SuspiciousModifierThen lint) since the factory internally
    // calls this.then().
    onFocusChanged {
        if (focused != it.isFocused) {
            focused = it.isFocused
            onFocusedChanged(it.isFocused)
        }
    }.tvFocusScaleByFlag(focused)
}
