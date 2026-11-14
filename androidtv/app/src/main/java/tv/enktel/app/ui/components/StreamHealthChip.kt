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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelWarn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.enktel.app.data.net.StreamHealth
import tv.enktel.app.player.PlaybackTelemetry

/**
 * Small floating chip surfacing the current stream-health snapshot on
 * player screens.  Hidden when everything's healthy; slides in when the
 * user's ISP/VPN/CDN is misbehaving so they know why playback is
 * degrading before assuming the app is broken.
 *
 * Legend:
 *   POOR       — sustained high latency or ≥3 timeouts (yellow)
 *   BLOCKED    — 403s in the current window *and* nothing is playing (red)
 *   FAIL-OVER  — an active backup gateway is in use (blue)
 *
 * ## Why BLOCKED is gated on playback
 *
 * `StreamHealth` counts 403s process-wide, and a 403 is routine even on a
 * working line: tuning a channel often tries a couple of alternate URLs that
 * refuse before it lands on the one that plays, and background probes and
 * artwork fetches draw their own. A single such 403 used to flip the chip to
 * "BLOCKED" and paint it over a stream that was rendering perfectly — the exact
 * thing a viewer reads as "the app says this is blocked but it's clearly on".
 *
 * A stream that is putting frames on the screen is, by definition, not blocked.
 * So BLOCKED is suppressed while [PlaybackTelemetry] is fresh (the player has
 * pushed stats within the last few seconds). If playback actually stalls, the
 * freshness lapses within ~15 s and a real block surfaces then.
 */
@Composable
fun StreamHealthChip(modifier: Modifier = Modifier) {
    val snap by StreamHealth.state.collectAsStateWithLifecycle(
        initialValue = StreamHealth.Snapshot(),
    )
    val playback by PlaybackTelemetry.state.collectAsStateWithLifecycle(
        initialValue = PlaybackTelemetry.Snapshot(),
    )
    // Age the window while the chip is on screen.
    //
    // Readings expire on a clock, but nothing was turning that clock: the
    // snapshot was only recomputed when a *new* request completed, and a live
    // stream holding one connection open can go minutes without making one. So
    // a fault that had already cleared kept its chip, and the latency beside it
    // was whatever the last burst of requests happened to measure. Ticking here
    // costs one recomputation a second over a handful of samples, and only
    // while a player screen is actually showing.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            StreamHealth.refresh()
        }
    }
    val label: String
    val color: Color
    when {
        snap.activeGateway != null -> {
            label = "BACKUP · ${snap.activeGateway}"
            color = EnktelBlue
        }
        // A playing stream cannot be blocked, whatever the 403 counter says —
        // those 403s came from tune-in fallbacks or background fetches, not
        // from the picture that is currently on screen.
        snap.quality == StreamHealth.Quality.BLOCKED && !playback.isFresh() -> {
            label = "BLOCKED · ${snap.blocked403} × 403"
            color = EnktelLive
        }
        snap.quality == StreamHealth.Quality.POOR -> {
            label = "POOR · ${snap.meanLatencyMs} ms"
            color = EnktelWarn
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
                .glassChip(alpha = 0.68f)
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
