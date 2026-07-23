package tv.enktel.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurface

/**
 * Premium animated buffering loader for the players and any long-running content
 * fetch. Two rings rotate in opposite directions with a gradient sweep — reads
 * as "premium spinner", never as a stock progress wheel.
 *
 * Optional [label] and [progress] (0..1, or negative for indeterminate) render
 * below the ring. Use the branded EnkTel wordmark inside the ring — the app's
 * signature loading vibe.
 */
@Composable
fun BufferingLoader(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    label: String = "Buffering",
    showWordmark: Boolean = true,
) {
    val blueColor = EnktelBlue
    val purpleColor = EnktelPurple
    val liveColor = EnktelLive
    val infinite = rememberInfiniteTransition(label = "buffering")
    val rot1 by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "outer",
    )
    val rot2 by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "inner",
    )

    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val outerR = this.size.minDimension * 0.44f
                val innerR = this.size.minDimension * 0.30f
                val strokeW = this.size.minDimension * 0.05f
                // Outer ring — colour-sweep from blue → purple → transparent
                val outerBrush = Brush.sweepGradient(
                    listOf(
                        blueColor.copy(alpha = 0.05f),
                        blueColor.copy(alpha = 0.95f),
                        purpleColor.copy(alpha = 0.9f),
                        purpleColor.copy(alpha = 0.05f),
                    ),
                    center = Offset(cx, cy),
                )
                withTransform({ rotate(rot1, Offset(cx, cy)) }) {
                    drawArc(
                        brush = outerBrush,
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(cx - outerR, cy - outerR),
                        size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2),
                        style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
                // Inner ring — narrower live-red accent
                withTransform({ rotate(rot2, Offset(cx, cy)) }) {
                    drawArc(
                        color = liveColor.copy(alpha = 0.85f),
                        startAngle = 0f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(cx - innerR, cy - innerR),
                        size = androidx.compose.ui.geometry.Size(innerR * 2, innerR * 2),
                        style = Stroke(width = strokeW * 0.7f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
            if (showWordmark) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ENK", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("TEL", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (label.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

