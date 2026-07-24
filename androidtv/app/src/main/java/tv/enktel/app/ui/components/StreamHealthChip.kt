package tv.enktel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.enktel.app.data.net.StreamHealth

/**
 * Small floating chip surfacing the current stream-health snapshot on
 * player screens.  Hidden when everything's healthy; slides in when the
 * user's ISP/VPN/CDN is misbehaving so they know why playback is
 * degrading before assuming the app is broken.
 *
 * Legend:
 *   POOR       — sustained high latency or ≥3 timeouts (yellow)
 *   BLOCKED    — one or more 403s in the current window (red)
 *   FAIL-OVER  — an active backup gateway is in use (blue)
 */
@Composable
fun StreamHealthChip(modifier: Modifier = Modifier) {
    val snap by StreamHealth.state.collectAsStateWithLifecycle(
        initialValue = StreamHealth.Snapshot(),
    )
    val label: String
    val color: Color
    when {
        snap.activeGateway != null -> {
            label = "BACKUP · ${snap.activeGateway}"
            color = Color(0xFF3B9DFF)
        }
        snap.quality == StreamHealth.Quality.BLOCKED -> {
            label = "BLOCKED · ${snap.blocked403} × 403"
            color = Color(0xFFEF4444)
        }
        snap.quality == StreamHealth.Quality.POOR -> {
            label = "POOR · ${snap.meanLatencyMs} ms"
            color = Color(0xFFFBBF24)
        }
        else -> {
            label = ""
            color = Color.Transparent
        }
    }
    AnimatedVisibility(
        visible = label.isNotBlank(),
        modifier = modifier,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
