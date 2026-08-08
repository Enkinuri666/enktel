package tv.enktel.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import tv.enktel.app.ui.components.dpadScrollable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.BuildConfig
import tv.enktel.app.data.net.StreamHealth
import tv.enktel.app.data.net.SystemMonitor
import tv.enktel.app.data.net.ThermalGuard
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Live system + connection monitor.
 *
 * The Connection Diagnostics screen answers "is my line good enough" once, on
 * demand. This answers "what is happening right now, and why did that stutter
 * happen" continuously — which is the question during playback, not before it.
 *
 * Sampling only runs while this screen is on top (see the [DisposableEffect]
 * below): a monitor that quietly polls forever is exactly the kind of
 * background work that causes the stalls it claims to diagnose.
 *
 * The read across the tiles is deliberate. Dropped frames climbing while the
 * buffer stays deep means the decoder can't keep up — lower the quality or
 * switch decoder mode. The buffer draining while frames hold steady means the
 * link can't keep up — raise the buffer or check the connection. Thermal
 * pressure explains both at once, and is the usual culprit on a stick behind a
 * warm TV.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SystemMonitorScreen(graph: AppGraph, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val sample by SystemMonitor.current.collectAsStateWithLifecycle()
    val history by SystemMonitor.history.collectAsStateWithLifecycle()
    val profile by androidx.compose.runtime.produceState<tv.enktel.app.data.db.Profile?>(initialValue = null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    var ping by remember { mutableStateOf<SystemMonitor.Ping?>(null) }
    var pinging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        SystemMonitor.start()
        onDispose { SystemMonitor.stop() }
    }

    val isMobile = BuildConfig.FLAVOR == "mobile"
    val hPad = if (isMobile) 20.dp else 48.dp

    // Same as Diagnostics: a wall of readings with nothing focusable in it,
    // so the D-pad had no way to move the list.
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(EnktelBg).dpadScrollable(listState, scope),
        contentPadding = PaddingValues(horizontal = hPad, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("System Monitor")
                    Text(
                        "Live connection and device health, sampled while this screen is open.",
                        color = EnktelTextDim, fontSize = 12.sp,
                    )
                }
                FocusButton("🩺 Full diagnostics", onClick = { nav.navigate("speedTest") })
            }
        }

        item {
            val accent = when {
                sample.degraded -> EnktelLive
                sample.quality == StreamHealth.Quality.GOOD -> EnktelOk
                else -> EnktelBlue
            }
            MonitorCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sample.verdict, color = accent, fontSize = 20.sp,
                            fontWeight = FontWeight.Black, modifier = Modifier.weight(1f),
                        )
                        Badge(sample.transport.name, EnktelBlue)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildAdvice(sample),
                        color = EnktelTextDim, fontSize = 12.sp,
                    )
                }
            }
        }

        // Connection block.
        item {
            MonitorCard {
                Column(Modifier.padding(14.dp)) {
                    CardHeader("CONNECTION")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Latency", if (sample.latencyMs > 0) "${sample.latencyMs} ms" else "—",
                            tint = latencyTint(sample.latencyMs), modifier = Modifier.weight(1f))
                        Stat("Link down", kbpsLabel(sample.linkDownKbps), modifier = Modifier.weight(1f))
                        Stat("Timeouts", "${sample.timeouts}",
                            tint = if (sample.timeouts > 0) EnktelLive else null, modifier = Modifier.weight(1f))
                        Stat("Blocked (403)", "${sample.blocked403}",
                            tint = if (sample.blocked403 > 0) EnktelLive else null, modifier = Modifier.weight(1f))
                    }
                    if (sample.activeGateway != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Failed over to backup gateway ${sample.activeGateway}",
                            color = EnktelLive, fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Latency, last ${history.size * 2}s", color = EnktelTextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Sparkline(
                        values = history.map { it.latencyMs.toFloat() },
                        color = EnktelBlue,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FocusButton(
                            if (pinging) "Pinging…" else "Ping panel now",
                            onClick = {
                                val p = profile ?: return@FocusButton
                                if (pinging) return@FocusButton
                                pinging = true
                                scope.launch {
                                    ping = SystemMonitor.probeLatency(graph.http, pingTarget(p))
                                    pinging = false
                                }
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        ping?.let { r ->
                            Text(
                                when {
                                    r.ok && r.error != null -> "${r.ms} ms · ${r.error}"
                                    r.ok -> "Round trip ${r.ms} ms (HTTP ${r.httpCode}, ${r.via})"
                                    else -> "No reply — ${r.error ?: "unknown"}"
                                },
                                color = if (r.ok) EnktelOk else EnktelLive, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }

        // Playback block — only meaningful while something is (or just was) playing.
        item {
            MonitorCard {
                Column(Modifier.padding(14.dp)) {
                    CardHeader("PLAYBACK")
                    Spacer(Modifier.height(8.dp))
                    if (!sample.playbackFresh) {
                        Text(
                            "Nothing playing. Start a channel and come back — these fill in live.",
                            color = EnktelTextDim, fontSize = 12.sp,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat("Stream rate", kbpsLabel(sample.playbackKbps.toInt()), modifier = Modifier.weight(1f))
                            Stat(
                                "Buffer ahead", "${sample.bufferAheadMs / 1000}s",
                                tint = when {
                                    sample.bufferAheadMs in 1..1_500 -> EnktelLive
                                    sample.bufferAheadMs < 4_000 -> null
                                    else -> EnktelOk
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Stat(
                                "Dropped frames", "${sample.droppedFrames}",
                                tint = if (sample.droppedFrames > 50) EnktelLive else null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Buffer depth, last ${history.size * 2}s", color = EnktelTextDim, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Sparkline(
                            values = history.map { (it.bufferAheadMs / 1000f) },
                            color = EnktelOk,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        )
                    }
                }
            }
        }

        // Device block.
        item {
            MonitorCard {
                Column(Modifier.padding(14.dp)) {
                    CardHeader("DEVICE")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(
                            "Thermal", sample.thermal.name,
                            tint = when (sample.thermal) {
                                ThermalGuard.Level.NONE -> EnktelOk
                                ThermalGuard.Level.MILD -> null
                                else -> EnktelLive
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Stat("App CPU", "${sample.appCpuPct}%",
                            tint = if (sample.appCpuPct > 250) EnktelLive else null, modifier = Modifier.weight(1f))
                        Stat(
                            "Memory",
                            if (sample.totalMemMb > 0) "${sample.usedMemMb} / ${sample.totalMemMb} MB" else "—",
                            tint = if (sample.lowMemory) EnktelLive else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Free storage", "${sample.freeStorageMb} MB",
                            tint = if (sample.freeStorageMb in 1..500) EnktelLive else null,
                            modifier = Modifier.weight(1f))
                        Stat(
                            "Battery",
                            if (sample.batteryPct >= 0) "${sample.batteryPct}%${if (sample.charging) " ⚡" else ""}" else "Mains",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton("Clear history", onClick = { SystemMonitor.clearHistory() })
                FocusButton("Back", onClick = { nav.popBackStack() })
            }
        }
    }
}

/** Turns the current sample into the one sentence worth acting on. */
private fun buildAdvice(s: SystemMonitor.Sample): String = when {
    !s.hasInternet ->
        "No internet on the active network. Nothing will play until the connection comes back."
    s.quality == StreamHealth.Quality.BLOCKED ->
        "The panel is returning 403s. That's usually geoblocking or an expired line — try a backup gateway in Settings."
    s.thermal >= ThermalGuard.Level.MODERATE ->
        "The device is throttling to cool down, which shows up as stutter. Improve airflow around the box or drop to a lower stream quality."
    s.lowMemory ->
        "The system is low on memory and may kill background work. Closing other apps usually clears it."
    s.quality == StreamHealth.Quality.POOR ->
        "Requests are timing out or running slow. Raise the player buffer in Settings, or run full diagnostics to find where the delay is."
    s.playbackFresh && s.bufferAheadMs in 1..1_500 ->
        "The buffer is nearly empty — playback is about to stall. A larger buffer profile will ride this out."
    s.playbackFresh && s.droppedFrames > 50 ->
        "Frames are being dropped while the buffer holds, so the decoder is the bottleneck, not the network. Try a lower quality or a different decoder mode."
    s.quality == StreamHealth.Quality.GOOD ->
        "Connection and device both look healthy — playback should be stable."
    else ->
        "Collecting samples. Numbers fill in over the first few seconds."
}

/** Null keeps the default white; a colour means "this number is the story". */
private fun latencyTint(ms: Int): Color? = when {
    ms <= 0 -> null
    ms > 2000 -> Color(0xFFFF5470)
    ms > 900 -> Color(0xFFFFC107)
    else -> null
}

private fun kbpsLabel(kbps: Int): String = when {
    kbps <= 0 -> "—"
    kbps >= 1000 -> "%.1f Mbps".format(kbps / 1000f)
    else -> "$kbps kbps"
}

@Composable
private fun MonitorCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EnktelSurface.copy(0.6f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun CardHeader(text: String) {
    Text(text, color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, tint: Color? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EnktelSurfaceHigh.copy(0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = EnktelTextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(
            value, color = tint ?: Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Minimal trend line. Auto-scales to the window's own maximum, because the
 * useful signal here is the *shape* — a latency spike or a buffer drain — not
 * the absolute value, which the tile above already states.
 */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(EnktelSurfaceHigh.copy(0.4f)),
    ) {
        if (values.size < 2) {
            Text(
                "Collecting…", color = EnktelTextDim, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
            val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            var prev = Offset(0f, size.height - (values[0] / max) * size.height)
            for (i in 1 until values.size) {
                val next = Offset(i * stepX, size.height - (values[i] / max) * size.height)
                drawLine(color = color, start = prev, end = next, strokeWidth = 2f)
                prev = next
            }
        }
    }
}

/**
 * The URL worth pinging for a given line.
 *
 * The bare server root is the wrong target: panels routinely answer it with a
 * redirect to a marketing page, a 403 from the front-end WAF, or nothing at
 * all, none of which says anything about whether *the API* is up. For an
 * Xtream line the login endpoint is the thing the app actually depends on, so
 * that is what gets measured; an M3U line has only its playlist URL.
 */
private fun pingTarget(p: tv.enktel.app.data.db.Profile): String = when {
    p.kind == "xtream" && p.server.isNotBlank() ->
        p.server.trimEnd('/') + "/player_api.php?username=${p.username}&password=${p.password}"
    p.m3uUrl.isNotBlank() -> p.m3uUrl
    else -> p.server
}
