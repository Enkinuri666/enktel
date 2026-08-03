package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.net.SpeedTestEngine
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Connection Diagnostics.  Combines the older "Speed Test" workflow with
 * real Xtream server-info detection, HEAD probes on live + VOD stream URLs
 * (to identify container / codec / transcoder actually in the path), a
 * plain-language suggestion list built from the observed numbers, and
 * inline troubleshooting toggles for the settings that most often help —
 * stream format (HLS vs TS), buffer profile, and live time-shift.
 */
@Composable
fun SpeedTestScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SpeedTestEngine.Result?>(null) }

    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")
    val liveShift by graph.settings.liveShiftEnabled.collectAsStateWithLifecycle(initialValue = true)
    val loudness by graph.settings.loudnessOn.collectAsStateWithLifecycle(initialValue = false)

    fun runTest() {
        if (running) return
        running = true
        result = null
        scope.launch {
            // Pick a real live channel + real VOD asset so the probes actually
            // reflect what the panel serves — not the panel API JSON. When the
            // profile hasn't finished syncing yet, this returns null and the
            // engine gracefully falls back to a server-URL throughput test.
            val liveUrl = pickLiveUrl(graph, p)
            val vodUrl = pickVodUrl(graph, p)
            val r = SpeedTestEngine.run(
                http = graph.http,
                serverUrl = p.server,
                streamSampleUrl = liveUrl,
                vodSampleUrl = vodUrl,
                profile = p,
                xtream = graph.xtream,
                onProgress = { status = it },
            )
            result = r
            running = false
            status = ""
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Connection Diagnostics")
                Spacer(Modifier.weight(1f))
                FocusButton(
                    if (running) "Testing…" else "▶ Run diagnostics",
                    accent = true,
                    onClick = { runTest() },
                )
            }
        }
        item {
            val kindLabel = if (p.kind == "xtream") "Xtream Codes API" else "M3U playlist"
            Text(
                "Tests your live connection against your $kindLabel server — panel host, DNS, latency, real throughput, plus the container + codec the panel actually serves.",
                color = EnktelTextDim, fontSize = 12.sp,
            )
        }
        if (running) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnktelSurface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(status.ifBlank { "Starting…" }, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        result?.let { r ->
            item { GroupHeader("Network") }
            item { MetricRow("Resolved IP", r.resolvedIp ?: "unresolved") }
            item { MetricRow("DNS lookup", "${r.dnsLookupMs} ms") }
            item {
                MetricRow(
                    "Ping (TCP connect)",
                    if (r.pingMs >= 0) "${r.pingMs} ms" else "failed",
                    color = when {
                        r.pingMs < 0 -> EnktelLive
                        r.pingMs < 80 -> EnktelOk
                        r.pingMs < 200 -> Color(0xFFFBBF24)
                        else -> EnktelLive
                    },
                )
            }
            item { MetricRow("Jitter", "${r.jitterMs} ms") }
            item {
                MetricRow(
                    "Packet loss", "${r.packetLossPct}%",
                    color = if (r.packetLossPct >= 20) EnktelLive else if (r.packetLossPct > 0) Color(0xFFFBBF24) else EnktelOk,
                )
            }
            item {
                MetricRow(
                    "Download throughput", "%.2f Mbps".format(r.downloadMbps),
                    color = when {
                        r.downloadMbps >= 15 -> EnktelOk
                        r.downloadMbps >= 5 -> Color(0xFFFBBF24)
                        else -> EnktelLive
                    },
                )
            }
            item { MetricRow("Connection type", r.connectionType.name) }

            if (!r.server.isEmpty()) {
                item { GroupHeader("Panel · Xtream Codes API") }
                if (r.server.url.isNotBlank()) item { MetricRow("URL", r.server.url) }
                if (r.server.protocol.isNotBlank() || r.server.port.isNotBlank()) {
                    item {
                        MetricRow(
                            "Protocol / port",
                            "${r.server.protocol.uppercase()}  ${r.server.port}${if (r.server.httpsPort.isNotBlank()) " · https ${r.server.httpsPort}" else ""}",
                        )
                    }
                }
                if (r.server.serverSoftware.isNotBlank()) item { MetricRow("Server software", r.server.serverSoftware) }
                if (r.server.transcoderProcess.isNotBlank()) item { MetricRow("Transcoder", r.server.transcoderProcess) }
                if (r.server.timezone.isNotBlank()) item { MetricRow("Timezone / clock", "${r.server.timezone} · ${r.server.timeNow}") }
                if (r.server.maxConnections > 0) {
                    item {
                        MetricRow(
                            "Connections used",
                            "${r.server.activeConnections} / ${r.server.maxConnections}${if (r.server.trial) " (trial)" else ""}",
                            color = if (r.server.activeConnections >= r.server.maxConnections && r.server.maxConnections > 0) EnktelLive else Color.White,
                        )
                    }
                }
            }

            r.liveProbe?.let { probe ->
                item { GroupHeader("Live stream probe") }
                item { MetricRow("HTTP", "${probe.httpCode}", color = if (probe.ok) EnktelOk else EnktelLive) }
                item { MetricRow("Container", probe.container) }
                if (probe.contentType.isNotBlank()) item { MetricRow("Content-Type", probe.contentType) }
                if (probe.codecHint.isNotBlank()) item { MetricRow("Codec (hint)", probe.codecHint) }
                if (probe.serverHeader.isNotBlank()) item { MetricRow("Server", probe.serverHeader) }
                item { MetricRow("Transcoder in path", probe.transcoderHint.ifBlank { "direct / unknown" }) }
                probe.error?.let { item { MetricRow("Error", it, color = EnktelLive) } }
            }
            r.vodProbe?.let { probe ->
                item { GroupHeader("VOD stream probe") }
                item { MetricRow("HTTP", "${probe.httpCode}", color = if (probe.ok) EnktelOk else EnktelLive) }
                item { MetricRow("Container", probe.container) }
                if (probe.contentType.isNotBlank()) item { MetricRow("Content-Type", probe.contentType) }
                if (probe.codecHint.isNotBlank()) item { MetricRow("Codec (hint)", probe.codecHint) }
            }

            // v1.23 additions — protocol / TLS + URL-shape simulation + cap
            if (r.protocol.httpVersion.isNotBlank() || r.protocol.tlsVersion.isNotBlank()) {
                item { GroupHeader("HTTP + TLS") }
                if (r.protocol.httpVersion.isNotBlank()) item { MetricRow("HTTP version", r.protocol.httpVersion) }
                if (r.protocol.tlsVersion.isNotBlank()) item { MetricRow("TLS version", r.protocol.tlsVersion) }
                if (r.protocol.handshakeMs > 0) item { MetricRow("Handshake", "${r.protocol.handshakeMs} ms") }
                if (r.protocol.redirectChain.isNotEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(EnktelSurface)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text("Redirect chain", color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
                            r.protocol.redirectChain.forEach {
                                Text(it, color = Color.White.copy(0.9f), fontSize = 12.sp,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (r.urlShapes.liveShapes.isNotEmpty()) {
                item { GroupHeader("URL shape simulation") }
                r.urlShapes.bestLive?.let { best ->
                    item {
                        Text(
                            "Best-matching shape for live: ${best.url.substringAfterLast('/')}",
                            color = EnktelOk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
                items(r.urlShapes.liveShapes) { s ->
                    val statusColor = when {
                        s.ok -> EnktelOk
                        s.code == 0 -> EnktelLive
                        s.code in 400..499 -> Color(0xFFFBBF24)
                        else -> EnktelLive
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EnktelSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            val label = s.url.substringAfter(r.host).ifBlank { s.url }
                            Text(label, color = Color.White, fontSize = 12.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (s.error != null) {
                                Text(s.error.take(80), color = EnktelTextDim, fontSize = 10.sp,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        Text(
                            if (s.code == 0) "err" else "${s.code} · ${s.ms}ms",
                            color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (r.connectionCap.attempted > 0) {
                item { GroupHeader("Concurrent connection cap") }
                val cap = r.connectionCap
                item {
                    MetricRow(
                        "Parallel probes",
                        "${cap.succeeded} / ${cap.attempted} succeeded" + if (cap.rejectedAt > 0) " · panel rejected at slot ${cap.rejectedAt}" else "",
                        color = when {
                            cap.succeeded == cap.attempted -> EnktelOk
                            cap.succeeded >= 4 -> Color(0xFFFBBF24)
                            else -> EnktelLive
                        },
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnktelSurfaceHigh)
                        .padding(16.dp),
                ) {
                    Text("Recommendation", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(r.recommendation, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Buffer health", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(r.bufferProjection, color = Color.White, fontSize = 14.sp)
                }
            }

            if (r.suggestions.isNotEmpty()) {
                item { GroupHeader("Suggestions") }
                items(r.suggestions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EnktelSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("•", color = EnktelBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp))
                        Text(s, color = Color.White.copy(0.92f), fontSize = 13.sp)
                    }
                }
            }

            item { GroupHeader("Troubleshooting toggles") }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnktelSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToggleGroup("Stream format", options = listOf("hls" to "HLS", "ts" to "MPEG-TS"),
                        current = streamFormat,
                        onPick = { scope.launch { graph.settings.setStreamFormat(it) } })
                    ToggleGroup("Buffer profile",
                        options = listOf("low" to "Low", "balanced" to "Balanced", "large" to "Large", "auto" to "Auto"),
                        current = bufferProfile,
                        onPick = { scope.launch { graph.settings.setBufferProfile(it) } })
                    SwitchRow(
                        label = "Live time-shift",
                        subLabel = "Turn off if seek-back on live channels stalls playback.",
                        checked = liveShift,
                        onToggle = { scope.launch { graph.settings.setLiveShiftEnabled(it) } },
                    )
                    SwitchRow(
                        label = "Loudness normalization",
                        subLabel = "Only turn on if audio is very quiet on your setup — some AVRs dislike it.",
                        checked = loudness,
                        onToggle = { scope.launch { graph.settings.setLoudnessOn(it) } },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton(
                        "📋 Copy report",
                        // LocalClipboard's setter suspends, so it goes through
                        // the screen scope rather than being called inline.
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(android.content.ClipData.newPlainText("EnkTel speed test", r.toReport())),
                                )
                            }
                        },
                    )
                    FocusButton("↻ Re-run", onClick = { runTest() })
                }
            }
        }
    }
}

private suspend fun pickLiveUrl(graph: AppGraph, p: Profile): String? = try {
    val list = graph.content.channels(p.id).first()
    list.firstOrNull()?.let { c -> graph.content.liveUrl(p, c, graph.settings.streamFormat.first()) }
} catch (_: Throwable) { null }

private suspend fun pickVodUrl(graph: AppGraph, p: Profile): String? = try {
    val list = graph.content.movies(p.id).first()
    list.firstOrNull()?.let { m -> graph.content.vodUrl(p, m) }
} catch (_: Throwable) { null }

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(), color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun MetricRow(label: String, value: String, color: Color = Color.White) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EnktelSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = EnktelTextDim, fontSize = 13.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleGroup(
    label: String,
    options: List<Pair<String, String>>,
    current: String,
    onPick: (String) -> Unit,
) {
    Column {
        Text(label, color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, display) ->
                GlassChip(display, selected = current == key, onClick = { onPick(key) })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, subLabel: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subLabel, color = EnktelTextDim, fontSize = 11.sp)
        }
        FocusButton(
            text = if (checked) "On" else "Off",
            accent = checked,
            onClick = { onToggle(!checked) },
        )
    }
}
