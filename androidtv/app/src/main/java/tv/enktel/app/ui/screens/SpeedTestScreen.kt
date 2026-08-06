package tv.enktel.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.net.DeviceProbe
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
    val ctx = LocalContext.current
    val shape = tv.enktel.app.ui.components.rememberScreenShape()

    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    // Live readings during the throughput phase. Kept as a rolling window so
    // the sparkline shows the speed settling rather than one averaged number
    // appearing at the end.
    var liveSample by remember { mutableStateOf<SpeedTestEngine.SpeedSample?>(null) }
    val speedHistory = remember { mutableStateListOf<Float>() }
    var result by remember { mutableStateOf<SpeedTestEngine.Result?>(null) }

    // Leaving the screen discards the run.
    //
    // Compose keeps `remember` state alive across a navigation that does not
    // destroy the entry, so coming back to Diagnostics used to show the
    // previous run's numbers, sitting there as if they had just been taken.
    // A diagnostic that reports the past as the present is worse than one
    // that reports nothing.
    DisposableEffect(Unit) {
        onDispose {
            result = null
            liveSample = null
            speedHistory.clear()
        }
    }

    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")
    val liveShift by graph.settings.liveShiftEnabled.collectAsStateWithLifecycle(initialValue = true)
    val loudness by graph.settings.loudnessOn.collectAsStateWithLifecycle(initialValue = false)

    fun runTest() {
        if (running) return
        running = true
        result = null
        liveSample = null
        speedHistory.clear()
        scope.launch {
            // Pick a real live channel + real VOD asset so the probes actually
            // reflect what the panel serves — not the panel API JSON. When the
            // profile hasn't finished syncing yet, this returns null and the
            // engine gracefully falls back to a server-URL throughput test.
            val liveUrl = pickLiveUrl(graph, p)
            val vodUrl = pickVodUrl(graph, p)
            status = "Reading device capabilities…"
            val device = withContext(Dispatchers.IO) { DeviceProbe.snapshot(ctx) }
            val catalogue = withContext(Dispatchers.IO) { readCatalogue(graph, p) }
            val r = SpeedTestEngine.run(
                http = graph.http,
                serverUrl = p.server,
                streamSampleUrl = liveUrl,
                vodSampleUrl = vodUrl,
                profile = p,
                xtream = graph.xtream,
                device = device,
                catalogue = catalogue,
                onProgress = { status = it },
                onSpeedSample = { sample ->
                    liveSample = sample
                    speedHistory += sample.instantMbps.toFloat()
                    // ~85 points at 5 Hz covers the whole window; trimming keeps
                    // the sparkline from compressing into a smear.
                    if (speedHistory.size > 85) speedHistory.removeAt(0)
                },
            )
            result = r
            running = false
            status = ""
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = shape.padH, vertical = shape.padV),
        verticalArrangement = Arrangement.spacedBy(shape.sectionGap),
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
            PanelDoctorSection(graph = graph, profile = p, scope = scope)
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
            // Only appears once the download phase starts producing readings —
            // before that there is nothing to show but an empty graph.
            if (liveSample != null) {
                item { SpeedMeter(liveSample, speedHistory) }
            }
        }
        result?.let { r ->
            // The verdict, before any of the detail. Forty individual metric
            // rows do not answer "is my connection the problem?" — this does,
            // and everything below it is the working.
            item { VerdictBanner(r) }
            item {
                // When these numbers were taken, and what took them.
                //
                // Results used to sit on screen undated for as long as the
                // screen lived, so readings from a run twenty minutes ago
                // looked identical to ones from twenty seconds ago — and got
                // pasted into reports as though they described the problem
                // being reported. The device and app line is here because
                // every bug report needs it and asking for it afterwards
                // costs a round trip.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Measured ${TimeFormat.format("HH:mm:ss", r.measuredAtMs)}" +
                            "  ·  ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" +
                            "  ·  Android ${android.os.Build.VERSION.RELEASE}" +
                            "  ·  EnkTel ${tv.enktel.app.BuildConfig.VERSION_NAME}",
                        color = EnktelTextDim, fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    FocusButton("✕ Clear", onClick = {
                        result = null
                        speedHistory.clear()
                        liveSample = null
                        status = ""
                    })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(
                        "Download", "%.1f".format(r.downloadMbps), "Mbps",
                        rate(r.downloadMbps, good = 15.0, fair = 5.0),
                        Modifier.weight(1f),
                    )
                    ScoreTile(
                        "Ping", if (r.pingMs >= 0) "${r.pingMs}" else "—", "ms",
                        if (r.pingMs < 0) Grade.BAD else rateLower(r.pingMs.toDouble(), good = 80.0, fair = 200.0),
                        Modifier.weight(1f),
                    )
                    ScoreTile(
                        "Jitter", "${r.jitterMs}", "ms",
                        rateLower(r.jitterMs.toDouble(), good = 20.0, fair = 60.0),
                        Modifier.weight(1f),
                    )
                    ScoreTile(
                        "Loss", "${r.packetLossPct}", "%",
                        rateLower(r.packetLossPct.toDouble(), good = 0.5, fair = 5.0),
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                DiagCard("Network") {
                    MetricRow("Resolved IP", r.resolvedIp ?: "unresolved")
                    MetricRow("DNS lookup", "${r.dnsLookupMs} ms")
                    MetricRow("Connection type", r.connectionType.name)
                    if (r.device.linkDownKbps > 0) {
                        // Side by side with the measured figure on purpose:
                        // a link rated far above what the panel delivered
                        // puts the bottleneck past the user's own network.
                        MetricRow(
                            "Link estimate (OS)",
                            "%.0f Mbps".format(r.device.linkDownKbps / 1000.0),
                        )
                    }
                }
            }
            if (!r.device.isEmpty()) {
                item {
                    DiagCard("This device") {
                        MetricRow("Model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        MetricRow("Display", r.device.displayLabel)
                        MetricRow("HDR", r.device.hdrTypes.joinToString(", ").ifBlank { "SDR only" })
                        if (r.device.totalRamMb > 0) {
                            MetricRow(
                                "Memory free",
                                "${r.device.availRamMb} MB of ${r.device.totalRamMb} MB",
                                color = if (r.device.totalRamMb in 1..1200) EnktelLive else Color.White,
                            )
                        }
                        if (r.device.totalStorageMb > 0) {
                            MetricRow(
                                "Storage free",
                                "%.1f GB of %.1f GB".format(
                                    r.device.freeStorageMb / 1024.0, r.device.totalStorageMb / 1024.0,
                                ),
                                color = if (r.device.freeStorageMb in 1..500) EnktelLive else Color.White,
                            )
                        }
                        if (r.device.abi.isNotBlank()) MetricRow("Architecture", r.device.abi)
                    }
                }
                item {
                    // The half of "why does this stutter" that a throughput
                    // test cannot see. A software HEVC path judders on a
                    // gigabit line and nothing else in this report says so.
                    DiagCard("Video decoders") {
                        listOf("H.264", "HEVC", "AV1", "VP9").forEach { label ->
                            val d = r.device.decoder(label)
                            MetricRow(
                                label,
                                when {
                                    d == null -> "not supported"
                                    else -> (if (d.hardware) "hardware" else "software") +
                                        d.resolutionLabel.let { if (it.isBlank()) "" else " · up to $it" }
                                },
                                color = when {
                                    d == null -> EnktelTextDim
                                    !d.hardware -> Color(0xFFFBBF24)
                                    else -> EnktelOk
                                },
                            )
                        }
                    }
                }
            }
            if (!r.catalogue.isEmpty()) {
                val c = r.catalogue
                item {
                    DiagCard("Catalogue on this device") {
                        MetricRow("Channels", "${c.channels}")
                        MetricRow("Movies / series", "${c.movies} / ${c.series}")
                        if (c.lastSyncMs > 0) {
                            MetricRow("Last synced", TimeFormat.format("d MMM, HH:mm", c.lastSyncMs))
                        }
                        MetricRow(
                            "Guide loaded",
                            if (c.epgProgrammes == 0) "empty" else
                                "${c.epgProgrammes} programmes · ${c.epgChannels} channels (${c.epgCoveragePct}%)",
                            color = when {
                                c.epgProgrammes == 0 -> EnktelLive
                                c.epgCoveragePct < 50 -> Color(0xFFFBBF24)
                                else -> EnktelOk
                            },
                        )
                        MetricRow(
                            "Guide depth",
                            if (c.epgDaysAhead <= 0) "does not reach past today" else "${c.epgDaysAhead} days ahead",
                            color = if (c.epgDaysAhead <= 0) Color(0xFFFBBF24) else Color.White,
                        )
                        MetricRow(
                            "Catch-Up channels",
                            if (c.catchupChannels == 0) "none on this line" else
                                "${c.catchupChannels}" + if (c.catchupDays > 0) " · up to ${c.catchupDays} days back" else "",
                            color = if (c.catchupChannels == 0) Color(0xFFFBBF24) else EnktelOk,
                        )
                    }
                }
            }
            if (speedHistory.size >= 2) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EnktelSurface)
                            .padding(14.dp),
                    ) {
                        Text(
                            "Throughput over the ${SpeedTestEngine.WINDOW_SEC}s sample",
                            color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Sparkline(speedHistory, EnktelBlue)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            // A line that ramps and holds is healthy; one that
                            // spikes and collapses is being shaped or contended.
                            "Peak %.1f Mbps · low %.1f Mbps".format(
                                speedHistory.maxOrNull() ?: 0f, speedHistory.minOrNull() ?: 0f,
                            ),
                            color = Color.White.copy(0.85f), fontSize = 12.sp,
                        )
                    }
                }
            }
            if (!r.server.isEmpty()) {
                item {
                    DiagCard("Panel · Xtream Codes API") {
                        if (r.server.url.isNotBlank()) MetricRow("URL", r.server.url)
                        if (r.server.protocol.isNotBlank() || r.server.port.isNotBlank()) {
                            MetricRow(
                                "Protocol / port",
                                "${r.server.protocol.uppercase()}  ${r.server.port}${if (r.server.httpsPort.isNotBlank()) " · https ${r.server.httpsPort}" else ""}",
                            )
                        }
                        if (r.server.serverSoftware.isNotBlank()) MetricRow("Server software", r.server.serverSoftware)
                        if (r.server.transcoderProcess.isNotBlank()) MetricRow("Transcoder", r.server.transcoderProcess)
                        if (r.server.timezone.isNotBlank()) MetricRow("Timezone / clock", "${r.server.timezone} · ${r.server.timeNow}")
                        if (r.server.maxConnections > 0) {
                            MetricRow(
                                "Connections used",
                                "${r.server.activeConnections} / ${r.server.maxConnections}${if (r.server.trial) " (trial)" else ""}",
                                color = if (r.server.activeConnections >= r.server.maxConnections && r.server.maxConnections > 0) EnktelLive else Color.White,
                            )
                        }
                        if (r.server.expiresAt > 0) {
                            // Fetched since v1.23 and never shown, so an
                            // expired line has been reading as a network fault.
                            val daysLeft = (r.server.expiresAt - System.currentTimeMillis()) / 86_400_000L
                            MetricRow(
                                "Subscription",
                                TimeFormat.format("d MMM yyyy", r.server.expiresAt) +
                                    " · " + tv.enktel.app.data.net.expiryLabel(r.server.expiresAt),
                                color = when {
                                    daysLeft < 0 -> EnktelLive
                                    daysLeft <= 3 -> Color(0xFFFBBF24)
                                    else -> EnktelOk
                                },
                            )
                        }
                    }
                }
            }

            r.liveProbe?.let { probe ->
                item {
                    DiagCard("Live stream probe", ok = probe.ok) {
                        MetricRow("HTTP", "${probe.httpCode}", color = if (probe.ok) EnktelOk else EnktelLive)
                        MetricRow("Container", probe.container)
                        if (probe.contentType.isNotBlank()) MetricRow("Content-Type", probe.contentType)
                        if (probe.codecHint.isNotBlank()) MetricRow("Codec (hint)", probe.codecHint)
                        if (probe.serverHeader.isNotBlank()) MetricRow("Server", probe.serverHeader)
                        MetricRow("Transcoder in path", probe.transcoderHint.ifBlank { "direct / unknown" })
                        probe.error?.let { MetricRow("Error", it, color = EnktelLive) }
                    }
                }
            }
            r.vodProbe?.let { probe ->
                item {
                    DiagCard("VOD stream probe", ok = probe.ok) {
                        MetricRow("HTTP", "${probe.httpCode}", color = if (probe.ok) EnktelOk else EnktelLive)
                        MetricRow("Container", probe.container)
                        if (probe.contentType.isNotBlank()) MetricRow("Content-Type", probe.contentType)
                        if (probe.codecHint.isNotBlank()) MetricRow("Codec (hint)", probe.codecHint)
                    }
                }
            }

            // v1.23 additions — protocol / TLS + URL-shape simulation + cap
            if (r.protocol.httpVersion.isNotBlank() || r.protocol.tlsVersion.isNotBlank()) {
                item {
                    DiagCard("HTTP + TLS") {
                        if (r.protocol.httpVersion.isNotBlank()) MetricRow("HTTP version", r.protocol.httpVersion)
                        if (r.protocol.tlsVersion.isNotBlank()) MetricRow("TLS version", r.protocol.tlsVersion)
                        if (r.protocol.handshakeMs > 0) MetricRow("Handshake", "${r.protocol.handshakeMs} ms")
                        if (r.protocol.redirectChain.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Redirect chain", color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Black)
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
                val cap = r.connectionCap
                item {
                    DiagCard("Concurrent connection cap") {
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

/**
 * How a single reading scored.
 *
 * The colour is resolved by [gradeColor] rather than held here: the palette
 * colours are theme-aware composable properties, so they cannot be read from
 * an enum constructor.
 */
private enum class Grade(val label: String) {
    GOOD("Good"),
    FAIR("Fair"),
    BAD("Poor"),
}

@Composable
private fun gradeColor(g: Grade): Color = when (g) {
    Grade.GOOD -> EnktelOk
    Grade.FAIR -> Color(0xFFFBBF24)
    Grade.BAD -> EnktelLive
}

/** Higher is better (throughput). */
private fun rate(v: Double, good: Double, fair: Double): Grade =
    if (v >= good) Grade.GOOD else if (v >= fair) Grade.FAIR else Grade.BAD

/** Lower is better (latency, jitter, loss). */
private fun rateLower(v: Double, good: Double, fair: Double): Grade =
    if (v <= good) Grade.GOOD else if (v <= fair) Grade.FAIR else Grade.BAD

/**
 * One-line answer to the only question this screen is really asked: is the
 * connection good enough to stream, and if not, which part is failing?
 *
 * The worst of the four headline readings decides it. A line that is fast but
 * dropping packets is not "good" — averaging would say it was, which is how a
 * screen full of green numbers can sit above a channel that keeps buffering.
 */
@Composable
private fun VerdictBanner(r: SpeedTestEngine.Result) {
    val grades = listOf(
        rate(r.downloadMbps, 15.0, 5.0),
        if (r.pingMs < 0) Grade.BAD else rateLower(r.pingMs.toDouble(), 80.0, 200.0),
        rateLower(r.jitterMs.toDouble(), 20.0, 60.0),
        rateLower(r.packetLossPct.toDouble(), 0.5, 5.0),
    )
    val worst = when {
        grades.any { it == Grade.BAD } -> Grade.BAD
        grades.any { it == Grade.FAIR } -> Grade.FAIR
        else -> Grade.GOOD
    }
    val worstColor = gradeColor(worst)
    val headline = when (worst) {
        Grade.GOOD -> "Your connection is in good shape"
        Grade.FAIR -> "Workable, but there is a weak link"
        Grade.BAD -> "This connection will struggle"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(worstColor.copy(alpha = 0.22f), worstColor.copy(alpha = 0.05f)),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(worstColor),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                worst.label.uppercase(),
                color = worstColor, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(headline, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** One headline reading: big number, unit, and its grade. */
@Composable
private fun ScoreTile(
    label: String,
    value: String,
    unit: String,
    grade: Grade,
    modifier: Modifier = Modifier,
) {
    val c = gradeColor(grade)
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label.uppercase(),
            color = EnktelTextDim, fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = c, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(3.dp))
            Text(
                unit, color = c.copy(alpha = 0.75f), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(grade.label, color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A titled group of metric rows.
 *
 * This screen used to emit every reading as its own item in one flat list —
 * roughly forty rows under bare headings, with nothing to say where one
 * section ended and the next began. Grouping them into cards is the whole
 * difference between a report and a dump.
 */
@Composable
private fun DiagCard(
    title: String,
    ok: Boolean? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (ok == false) EnktelLive else EnktelBlue),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(),
                color = if (ok == false) EnktelLive else EnktelBlue,
                fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private suspend fun pickLiveUrl(graph: AppGraph, p: Profile): String? = try {
    val list = graph.content.channels(p.id).first()
    list.firstOrNull()?.let { c -> graph.content.liveUrl(p, c, graph.settings.streamFormat.first()) }
} catch (_: Throwable) { null }

/**
 * Counts what the app has actually cached for this profile.
 *
 * Every one of these numbers answers a complaint that arrives as a playback
 * fault and is not one: an empty guide, a Catch-Up screen with nothing in it,
 * channels the user was promised that never synced. Best-effort — a database
 * that will not answer should not sink the whole diagnostic run.
 */
private suspend fun readCatalogue(graph: AppGraph, p: Profile): SpeedTestEngine.Catalogue = try {
    val content = graph.db.contentDao()
    val epg = graph.db.epgDao()
    SpeedTestEngine.Catalogue(
        channels = content.channelCount(p.id),
        movies = content.movieCount(p.id),
        series = content.seriesCount(p.id),
        catchupChannels = content.catchupChannelCount(p.id),
        catchupDays = content.maxCatchupDays(p.id) ?: 0,
        epgProgrammes = epg.count(p.id),
        epgChannels = epg.coveredChannelCount(p.id),
        epgHorizonMs = epg.horizonMs(p.id) ?: 0L,
        lastSyncMs = p.lastSync,
    )
} catch (_: Throwable) { SpeedTestEngine.Catalogue() }

private suspend fun pickVodUrl(graph: AppGraph, p: Profile): String? = try {
    val list = graph.content.movies(p.id).first()
    list.firstOrNull()?.let { m -> graph.content.vodUrl(p, m) }
} catch (_: Throwable) { null }

/**
 * Live throughput meter, shown while the download phase runs.
 *
 * The engine emits roughly five readings a second. This draws the
 * instantaneous figure large, the running average and transferred volume
 * underneath, and a sparkline of the whole window — so a line that starts
 * fast and then collapses looks visibly different from one that is simply
 * slow. That distinction is the whole reason a single averaged number at the
 * end was not enough to diagnose buffering.
 */
@Composable
private fun SpeedMeter(sample: SpeedTestEngine.SpeedSample?, history: List<Float>) {
    val instant = (sample?.instantMbps ?: 0.0).toFloat()
    // Smoothing the displayed figure only — the sparkline plots raw readings,
    // so nothing is hidden, the headline number just stops flickering.
    val shown by animateFloatAsState(targetValue = instant, label = "instantMbps")
    val elapsed = sample?.elapsedSec ?: 0.0
    val progress = (elapsed / SpeedTestEngine.WINDOW_SEC).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "speedProgress")
    val accent = EnktelBlue
    val peak = history.maxOrNull() ?: 0f

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurfaceHigh)
            .padding(18.dp),
    ) {
        Text(
            "MEASURING DOWNLOAD SPEED",
            color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.1f".format(shown),
                color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Mbps",
                color = EnktelTextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "avg %.1f · peak %.1f Mbps".format(sample?.averageMbps ?: 0.0, peak),
                    color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.1f MB in %.0fs of %ds".format(
                        (sample?.bytes ?: 0L) / 1_000_000.0, elapsed, SpeedTestEngine.WINDOW_SEC,
                    ),
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Sparkline(history, accent)
        Spacer(Modifier.height(12.dp))
        // Elapsed-time bar, so the user knows how much longer this takes.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(accent, EnktelOk))),
            )
        }
    }
}

/**
 * Plots the readings as a filled line, scaled to the highest reading seen so
 * far. The vertical scale is deliberately relative rather than absolute: on a
 * 400 Mbps line a 0-1000 axis would render every real fluctuation as a flat
 * line, and the shape of the curve is the diagnostic signal here.
 */
@Composable
private fun Sparkline(history: List<Float>, accent: Color) {
    val faint = Color.White.copy(0.08f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(0.25f)),
    ) {
        // Three horizontal guides so the eye has something to judge against.
        repeat(3) { i ->
            val y = size.height * (i + 1) / 4f
            drawLine(faint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (history.size < 2) return@Canvas

        val peak = (history.maxOrNull() ?: 0f).coerceAtLeast(0.001f)
        val stepX = size.width / (history.size - 1).toFloat()
        // Keep the peak just off the top edge so the line never clips.
        fun yFor(v: Float) = size.height - (v / peak).coerceIn(0f, 1f) * size.height * 0.88f

        val line = Path().apply {
            moveTo(0f, yFor(history[0]))
            for (i in 1 until history.size) lineTo(i * stepX, yFor(history[i]))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo((history.size - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(listOf(accent.copy(0.35f), Color.Transparent)),
        )
        drawPath(line, accent, style = Stroke(width = 2.5f))
    }
}

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
