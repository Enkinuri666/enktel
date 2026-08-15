package tv.enktel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Full-screen premium overlay shown while EnkTel refreshes the playlist / EPG.
 * Not a stock progress bar — a branded card with the animated BufferingLoader,
 * a live status line, and a slim progress bar. Fades in/out.
 *
 * The card is width-capped rather than width-fixed: 400 dp is right on a TV,
 * but a phone in portrait is narrower than that and a fixed width would push
 * the card off both edges.
 */
@Composable
fun RefreshSplash(
    visible: Boolean,
    status: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(320)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to EnktelBg.copy(alpha = 0.90f),
                        1.0f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(EnktelSurfaceHigh)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ENKTEL IPTV", color = EnktelBlue, fontSize = 11.sp,
                     fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(16.dp))
                BufferingLoader(label = "", showWordmark = true)
                Spacer(Modifier.height(18.dp))
                Text(
                    if (status.isBlank()) "Refreshing your library…" else status,
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your playlist and TV guide are being synced with the panel. " +
                        "Please keep the app open.",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (progress != null && progress in 0f..1f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(EnktelBlue, EnktelPurple)),
                                ),
                        )
                    }
                }
            }
        }
    }
}
