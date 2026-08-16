package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelWarn

/**
 * Account state for the active line, pinned to the top of Settings.
 *
 * The expiry and connection cap were previously a grey sub-line under the
 * profile row, which the v1.38.1 category split pushed behind a tab. They are
 * the things people open Settings to check — "how long have I got" and "why
 * won't a second device play" — so they belong above the tabs, visible without
 * a click, not filed under Playlists.
 *
 * Live figures come from one `player_api` call. The panel is the only place
 * `active_cons` exists — it isn't stored locally and it changes minute to
 * minute — so the banner refreshes it on mount and falls back to the values
 * cached on the profile when the panel can't be reached.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
// PlaybackSession is @UnstableApi, because everything that touches media3 is,
// and "free this device's connection" has to reach it. Same opt-in MainActivity
// already carries for the same reason — this is the annotation for media3's
// marker, which is androidx.annotation.OptIn rather than Kotlin's.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AccountBanner(graph: AppGraph, profile: Profile?, modifier: Modifier = Modifier) {
    val p = profile ?: return

    var activeConns by remember(p.id) { mutableIntStateOf(-1) }
    var maxConns by remember(p.id) { mutableIntStateOf(p.maxConnections) }
    var expiresAt by remember(p.id) { mutableLongStateOf(p.expiresAt) }
    var trial by remember(p.id) { mutableStateOf(false) }
    var reachable by remember(p.id) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(p.id) {
        if (p.kind != "xtream") { reachable = null; return@LaunchedEffect }
        runCatching {
            val ui = graph.xtream.login(p).get("user_info")
            activeConns = ui.int("active_cons") ?: -1
            maxConns = ui.int("max_connections") ?: p.maxConnections
            expiresAt = (ui.long("exp_date") ?: 0L) * 1000L
            trial = (ui.int("is_trial") ?: 0) == 1
            reachable = (ui.int("auth") ?: 0) == 1
        }.onFailure { reachable = false }
    }

    val now = System.currentTimeMillis()
    val daysLeft = if (expiresAt > 0) ((expiresAt - now) / 86_400_000L).toInt() else Int.MAX_VALUE
    val expired = expiresAt in 1 until now

    // One accent drives the whole banner, so its state reads at a glance rather
    // than needing the text parsed.
    val accent = when {
        expired || reachable == false -> EnktelLive
        daysLeft <= 7 -> EnktelWarn
        else -> EnktelOk
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.16f), EnktelSurfaceHigh.copy(alpha = 0.55f)),
                )
            )
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    when {
                        reachable == false -> "UNREACHABLE"
                        expired -> "EXPIRED"
                        trial -> "TRIAL"
                        else -> "ACTIVE"
                    },
                    color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black,
                )
            }
            Text(
                p.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (p.username.isNotBlank()) {
                Text(p.username, color = EnktelTextDim, fontSize = 12.sp, maxLines = 1)
            }
        }

        // FlowRow, not Row: three fixed columns needed ~500 dp and a phone in
        // portrait has ~330 dp to give, so the last stat used to run off the
        // edge. Here they wrap to a second line on a narrow screen and stay on
        // one on a TV.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Stat(
                label = "EXPIRES",
                value = when {
                    expiresAt <= 0 -> "—"
                    expired -> "Expired"
                    else -> TimeFormat.format("d MMM yyyy", expiresAt)
                },
                note = when {
                    expiresAt <= 0 || expired -> ""
                    daysLeft <= 0 -> "today"
                    daysLeft == 1 -> "1 day left"
                    else -> "$daysLeft days left"
                },
                accent = if (expired || daysLeft <= 7) accent else Color.White,
            )
            Stat(
                label = "CONNECTIONS",
                value = when {
                    maxConns <= 0 -> "—"
                    activeConns >= 0 -> "$activeConns / $maxConns"
                    else -> "max $maxConns"
                },
                // The cap is why a second device gets kicked mid-programme, and
                // why the downloader holds a stream back — worth spelling out
                // rather than leaving as a bare number.
                note = when {
                    maxConns <= 0 -> ""
                    activeConns >= maxConns -> "at the limit"
                    maxConns == 1 -> "one device at a time"
                    else -> "in use now"
                },
                accent = if (maxConns in 1..activeConns) accent else Color.White,
            )
            Stat(
                label = "LAST SYNC",
                value = if (p.lastSync <= 0) "Never" else relativeTime(now - p.lastSync),
                note = if (p.lastSync <= 0) "run a sync below" else "",
                accent = if (p.lastSync <= 0) accent else Color.White,
            )
        }

        // Free the session this device is holding.
        //
        // Offered whenever the line is capped, not only when it is currently at
        // the limit, because the moment it is useful is *before* picking up the
        // other device — you free the television, then walk away with the
        // phone. Waiting for "at the limit" would mean the button only appears
        // once you are already stuck on the device you are trying to leave.
        //
        // What it honestly does is hang up this device's stream and its socket.
        // It cannot end a session on another device: no customer-facing panel
        // call does that, and pretending otherwise would be the one thing worse
        // than not having the button. So the wording promises this device only,
        // and the result afterwards says which case actually applied.
        if (maxConns in 1..2) {
            var freeing by remember(p.id) { mutableStateOf(false) }
            var freedNote by remember(p.id) { mutableStateOf("") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tv.enktel.app.ui.components.FocusButton(
                    if (freeing) "Freeing…" else "Free this device's connection",
                    onClick = {
                        freeing = true
                        scope.launch {
                            val wasPlaying = graph.playback.releaseConnectionSlot()
                            // Re-read rather than assume. The panel's own
                            // bookkeeping lags the socket by a moment, so the
                            // grace wait is the difference between showing the
                            // viewer the new figure and showing them the old
                            // one and looking like it did nothing.
                            kotlinx.coroutines.delay(tv.enktel.app.player.ZapPlan.RELEASE_GRACE_MS * 4)
                            val after = runCatching {
                                graph.xtream.login(p).get("user_info").int("active_cons") ?: -1
                            }.getOrDefault(-1)
                            if (after >= 0) activeConns = after
                            freedNote = when {
                                wasPlaying -> "Released. Start the other device now."
                                after > 0 -> "Nothing was playing here — the line is in use on another device."
                                else -> "Nothing was playing here."
                            }
                            freeing = false
                        }
                    },
                )
                if (freedNote.isNotBlank()) {
                    Text(freedNote, color = EnktelTextDim, fontSize = 11.sp)
                }
            }
        }

        if (reachable == false) {
            Text(
                "Couldn't reach the panel just now — the figures above are the last known values.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, note: String, accent: Color) {
    Column(Modifier.widthIn(min = 132.dp)) {
        Text(label, color = EnktelTextDim, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (note.isNotBlank()) {
            Text(note, color = EnktelBlue, fontSize = 10.sp, maxLines = 1)
        }
    }
}

private fun relativeTime(ageMs: Long): String {
    val mins = ageMs / 60_000
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}
