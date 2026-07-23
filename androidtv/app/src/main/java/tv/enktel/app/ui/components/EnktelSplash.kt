package tv.enktel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelTextDim
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen branded splash overlay. Wraps [content]; renders on top of everything for
 * [durationMs] then crossfades out. The visuals are drawn from primitives (no image
 * assets needed) so this works even before the OkHttp / DB layers finish initialising.
 *
 * Anatomy:
 *  - Diagonal EnkTel gradient wash (blue → deep blue → purple).
 *  - Concentric ring "signal" animation pulsing outward from the wordmark.
 *  - Orbiting dot around the wordmark — small premium touch.
 *  - Wordmark: red live dot + ENKTEL / IPTV subtag.
 *  - Bottom tagline that fades in after the rings settle.
 */
@Composable
fun EnktelSplash(durationMs: Int = 1600, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(durationMs.toLong())
        visible = false
    }
    Box(Modifier.fillMaxSize()) {
        content()
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(tween(120)),
            exit = fadeOut(tween(500)),
        ) {
            SplashCanvas()
        }
    }
}

@Composable
private fun SplashCanvas() {
    // Theme colours are @Composable-getter properties; snap them once so the
    // non-composable Canvas draw block can use them.
    val bgColor = EnktelBg
    val blueColor = EnktelBlue
    val liveColor = EnktelLive
    val purpleColor = EnktelPurple
    val textDim = EnktelTextDim

    val infinite = rememberInfiniteTransition(label = "splash")
    val ringPhase = infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    val orbitPhase = infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    0.0f to bgColor,
                    0.5f to blueColor.copy(alpha = 0.15f),
                    1.0f to purpleColor.copy(alpha = 0.2f),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Signal rings — three concentric circles, staggered
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f
                    for (i in 0..2) {
                        val phase = ((ringPhase.value + i / 3f) % 1f)
                        val r = maxR * phase
                        val alpha = (1f - phase).coerceAtLeast(0f)
                        drawCircle(
                            color = blueColor.copy(alpha = 0.55f * alpha),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3f),
                        )
                    }
                    // Orbiting dot
                    val rad = Math.toRadians(orbitPhase.value.toDouble())
                    val ox = cx + cos(rad).toFloat() * maxR * 0.85f
                    val oy = cy + sin(rad).toFloat() * maxR * 0.85f
                    drawCircle(
                        color = liveColor, radius = 6f, center = Offset(ox, oy),
                    )
                }
                // Wordmark centered
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(EnktelLive),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "ENK", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black,
                    )
                    Text(
                        "TEL", color = EnktelBlue, fontSize = 34.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "IPTV", color = EnktelTextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Premium Live TV, Sports, Movies & Series",
                color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
