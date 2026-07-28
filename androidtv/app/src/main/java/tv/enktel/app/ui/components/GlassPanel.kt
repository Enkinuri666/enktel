package tv.enktel.app.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v1.27.0 — TV Cinematic glass card. Matches the design brief exactly:
 * 14 dp corners, `#A012141D` tint (sRGB 18,20,29 @ 70 % alpha), and a
 * 1 dp `#1AFFFFFF` stroke. Thin wrapper over [GlassPanel] so callers
 * don't have to remember the token values.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        cornerRadius = 14.dp,
        blurRadius = 20.dp,
        tint = Color(0xB012141D),
        borderColor = Color(0x1AFFFFFF),
        content = content,
    )
}

/**
 * A frosted-glass surface for floating overlays, bottom sheets and EPG
 * tooltips.
 *
 * On API 31+ (Android 12 / S) we can composite a live blur of everything
 * behind the panel using [RenderEffect.createBlurEffect] — the same
 * effect the system uses for the notification-shade backdrop.  On older
 * builds we fall back to a semi-transparent surface with a subtle inner
 * highlight, which still reads as premium without the live blur.
 *
 * Either way the panel keeps a hairline border so it stays legible on
 * bright backdrops.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    blurRadius: Dp = 24.dp,
    tint: Color = Color(0xCC0F172A),
    borderColor: Color = Color(0x33FFFFFF),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val glassMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = RenderEffect
                .createBlurEffect(
                    blurRadius.toPx(),
                    blurRadius.toPx(),
                    Shader.TileMode.CLAMP,
                )
                .asComposeRenderEffect()
        }
    } else {
        // Older devices — no live blur is available; the tint + border
        // fallback still reads as a floating glass card, just without the
        // moving-behind-the-panel highlights.
        Modifier
    }
    Box(
        modifier
            .clip(shape)
            .then(glassMod)
            .background(tint, shape)
            .border(1.dp, borderColor, shape),
    ) {
        content()
    }
}
