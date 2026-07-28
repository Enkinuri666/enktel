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
 * v1.28.1 — TV Cinematic glass card. Matches the design brief tokens
 * (14 dp corners, `#A012141D` tint, 1 dp `#1AFFFFFF` stroke) but skips
 * the RenderEffect blur: `RenderEffect.createBlurEffect` on a Compose
 * Box blurs the composable's *own* content, not the backdrop behind
 * it, so wrapping the player action bar in a blurred GlassPanel
 * (v1.27.0) rendered every button as an unreadable smear. The 70 %
 * alpha tint + hairline stroke reads as a floating card without the
 * live blur, which is how most iOS / Apple TV+ "glass" cards actually
 * ship today.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xB012141D), shape)
            .border(1.dp, Color(0x1AFFFFFF), shape),
    ) {
        content()
    }
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
