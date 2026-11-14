package tv.enktel.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.R
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelBrandGradient
import tv.enktel.app.ui.theme.EnktelText
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Full-screen branded splash overlay. Wraps [content]; renders on top of
 * everything for [durationMs] then crossfades out.
 *
 * ## v1.84 — the wolf leads
 *
 * The splash used to be a text wordmark with a decorative orbit. The brand is
 * the EnkTel wolf, so the wolf is now the hero: it sits in the centre on the
 * new spectrum gradient wash, lit by a soft glow and ringed by the signal
 * animation pulsing outward from it, with the wordmark and tagline beneath.
 *
 * It still draws almost entirely from primitives — the one asset is the logo
 * drawable, which is bundled in the APK, so this works before the OkHttp / DB
 * layers finish initialising, exactly as before.
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
    val gradient = EnktelBrandGradient
    val ringStart = gradient.first()
    val ringEnd = gradient.last()

    val infinite = rememberInfiniteTransition(label = "splash")
    val ringPhase = infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    val shimmer = infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer",
    )
    // The wolf settles in rather than snapping on: a short scale + fade the
    // first time the splash draws.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { landed = true }
    val logoScale by animateFloatAsState(
        targetValue = if (landed) 1f else 0.82f,
        animationSpec = tween(620, easing = FastOutSlowInEasing),
        label = "logoScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
            // Spectrum wash — the brand gradient, kept low so the wolf and
            // wordmark stay the brightest things on screen.
            .background(
                Brush.linearGradient(
                    0.0f to ringStart.copy(alpha = 0.16f),
                    0.5f to bgColor.copy(alpha = 0f),
                    1.0f to ringEnd.copy(alpha = 0.22f),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Signal rings — three concentric circles pulsing outward,
                // tinted along the brand gradient, plus a soft seating glow.
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f
                    // Seating glow behind the wolf.
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to ringStart.copy(alpha = 0.28f + 0.12f * shimmer.value),
                            1f to ringStart.copy(alpha = 0f),
                            center = Offset(cx, cy),
                            radius = maxR * 0.75f,
                        ),
                        radius = maxR * 0.75f,
                        center = Offset(cx, cy),
                    )
                    for (i in 0..2) {
                        val phase = ((ringPhase.value + i / 3f) % 1f)
                        val r = maxR * (0.45f + 0.55f * phase)
                        val alpha = (1f - phase).coerceAtLeast(0f)
                        val c = lerpColor(ringStart, ringEnd, phase)
                        drawCircle(
                            color = c.copy(alpha = 0.5f * alpha),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3f),
                        )
                    }
                }
                // The EnkTel wolf — the hero of the splash.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_logo),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp).scale(logoScale),
                )
            }
            Spacer(Modifier.height(18.dp))
            // Wordmark kept: ENK / TEL / IPTV.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ENK", color = EnktelText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("TEL", color = EnktelBlue, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("IPTV", color = EnktelTextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Premium Live TV, Sports, Movies & Series",
                color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Straight RGB lerp between two opaque colours, for the gradient-tinted rings. */
private fun lerpColor(a: Color, b: Color, t: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )
