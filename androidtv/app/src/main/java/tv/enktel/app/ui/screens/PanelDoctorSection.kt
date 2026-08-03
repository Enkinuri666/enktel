package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.diag.ContainerFacts
import tv.enktel.app.data.diag.DiagnosticsCache
import tv.enktel.app.data.diag.PanelDoctor
import tv.enktel.app.data.diag.PanelReport
import tv.enktel.app.data.diag.PlaybackSettings
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Panel Doctor — what the line actually serves, and what to set because of it.
 *
 * The connection test above answers "is the network fast enough". This answers
 * the harder question: given this specific panel, are the app's playback
 * settings the right ones? It probes the containers, the byte-range behaviour
 * that seeking depends on, the Matroska seek index, and the catch-up endpoint,
 * then offers to apply whatever it found and re-measure so the change can be
 * checked rather than believed.
 *
 * State lives in [DiagnosticsCache] rather than here so navigating away and
 * back does not re-probe the line — a full pass costs several round trips
 * against the user's own connection cap.
 */
@Composable
fun PanelDoctorSection(graph: AppGraph, profile: Profile, scope: CoroutineScope) {
    val report by DiagnosticsCache.last.collectAsStateWithLifecycle()
    val previous by DiagnosticsCache.previous.collectAsStateWithLifecycle()
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    // The spec's explicit consent step: nothing is written to settings until
    // the user says so, and the prompt lists exactly what will change.
    var confirmApply by remember { mutableStateOf(false) }

    suspend fun currentSettings() = PlaybackSettings(
        streamFormat = graph.settings.streamFormat.first(),
        bufferProfile = graph.settings.bufferProfile.first(),
        decoderMode = graph.settings.decoderMode.first(),
        vodForceMp4 = graph.settings.vodForceMp4.first(),
        liveShiftEnabled = graph.settings.liveShiftEnabled.first(),
        customUserAgent = graph.settings.customUserAgent.first(),
    )

    fun run(force: Boolean) {
        if (running) return
        running = true
        status = "Starting…"
        scope.launch {
            try {
                val settings = currentSettings()
                val channels = runCatching { graph.content.channels(profile.id).first() }
                    .getOrDefault(emptyList())
                val archiveChannels = channels.filter { it.archiveDays > 0 }
                val liveUrl = runCatching {
                    channels.firstOrNull()?.let {
                        graph.content.liveUrl(profile, it, settings.streamFormat)
                    }
                }.getOrNull()
                val vodUrl = runCatching {
                    graph.content.movies(profile.id).first().firstOrNull()
                        ?.let { graph.content.vodUrl(profile, it) }
                }.getOrNull()
                val catchupUrl = runCatching {
                    archiveChannels.firstOrNull()?.let { ch ->
                        tv.enktel.app.data.xtream.XtreamClient.timeshiftUrl(
                            profile,
                            ch.streamId,
                            System.currentTimeMillis() - 30 * 60_000L,
                            5,
                        )
                    }
                }.getOrNull()

                // Cache is keyed on the line actually being probed, so it is
                // only consultable once the sample URLs are known.
                val hash = DiagnosticsCache.lineHash(
                    profile.server, profile.username, liveUrl, vodUrl, catchupUrl,
                )
                if (!force && DiagnosticsCache.cached(hash, settings) != null) {
                    status = ""
                    return@launch
                }

                // Sample what the guide claims is on air right now across a
                // handful of channels — enough for a median, cheap enough not
                // to matter. A single channel could be legitimately odd.
                val nowMs = System.currentTimeMillis()
                val nowProgrammes = mutableListOf<Pair<Long, Long>>()
                runCatching {
                    // Plain loop rather than a sequence: nowNext suspends, and
                    // a suspending call cannot cross a Sequence boundary.
                    channels.filter { it.epgId.isNotBlank() }.take(8).forEach { ch ->
                        graph.db.epgDao().nowNext(profile.id, ch.epgId, nowMs, 1)
                            .forEach { nowProgrammes += it.startMs to it.endMs }
                    }
                }

                val r = PanelDoctor.run(
                    http = graph.http,
                    profile = profile,
                    xtream = graph.xtream,
                    liveUrl = liveUrl,
                    vodUrl = vodUrl,
                    catchupUrl = catchupUrl,
                    channelsWithArchive = archiveChannels.size,
                    nowProgrammes = nowProgrammes,
                    settings = settings,
                    onProgress = { status = it },
                )
                DiagnosticsCache.store(r, hash, settings)
            } catch (e: Exception) {
                // A diagnostic that throws when the thing it diagnoses is
                // broken is useless exactly when it is needed.
                DiagnosticsCache.store(
                    PanelReport(
                        profileId = profile.id,
                        ranAtMs = System.currentTimeMillis(),
                        error = e.message ?: e.javaClass.simpleName,
                    ),
                    DiagnosticsCache.lineHash(profile.server, profile.username),
                    runCatching { currentSettings() }.getOrDefault(PlaybackSettings()),
                )
            } finally {
                running = false
                status = ""
            }
        }
    }

    fun applyAndRerun() {
        val r = report ?: return
        if (applying || running) return
        applying = true
        scope.launch {
            try {
                r.suggestedChanges.forEach { c ->
                    when (c.key) {
                        "streamFormat" -> graph.settings.setStreamFormat(c.suggested)
                        "bufferProfile" -> graph.settings.setBufferProfile(c.suggested)
                        "decoderMode" -> graph.settings.setDecoderMode(c.suggested)
                        "vodForceMp4" -> graph.settings.setVodForceMp4(c.suggested == "on")
                        "liveShiftEnabled" -> graph.settings.setLiveShiftEnabled(c.suggested == "on")
                        "customUserAgent" -> graph.settings.setCustomUserAgent(c.suggested)
                    }
                }
            } catch (_: Exception) {
                // Falling through to the re-run is right: the comparison will
                // show which settings actually took.
            } finally {
                applying = false
            }
            run(force = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupHeaderPublic("PANEL DOCTOR")
            Spacer(Modifier.weight(1f))
            FocusButton(
                when {
                    running -> "Running…"
                    report == null -> "▶ Diagnose panel"
                    else -> "↻ Re-run"
                },
                accent = report == null,
                onClick = { run(force = report != null) },
            )
        }
        Text(
            "Reads what this line actually serves — containers, byte-range support, " +
                "the Matroska seek index and catch-up — then recommends settings to match it.",
            color = EnktelTextDim, fontSize = 12.sp,
        )
        if (running && status.isNotBlank()) {
            Text(status, color = EnktelBlue, fontSize = 12.sp)
        }

        val r = report
        if (r != null) {
            r.error?.let {
                Text("Diagnostics failed: $it", color = EnktelLive, fontSize = 12.sp)
            }
            if (r.structure.queried) StructureCard(r)
            if (r.epg.measured) EpgCard(r)
            r.live?.let { ContainerCard("LIVE", it) }
            r.vod?.let { ContainerCard("VOD", it) }
            CatchupCard(r)

            if (r.notes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.notes.forEach { n ->
                        Text("• $n", color = EnktelTextDim, fontSize = 11.sp)
                    }
                }
            }

            SettingsComparison(r)

            if (r.suggestedChanges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton(
                        if (applying) "Applying…" else
                            "✓ Apply ${r.suggestedChanges.size} suggested & re-test",
                        accent = true,
                        onClick = { confirmApply = true },
                    )
                }
            } else if (r.error == null) {
                Text(
                    "Settings already match what this panel serves — nothing to change.",
                    color = EnktelOk, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }

            previous?.let { prev -> ComparisonCard(prev, r) }
        }
    }

    if (confirmApply && r_of(report) != null) {
        val rr = r_of(report)!!
        tv.enktel.app.ui.components.ConfirmDialog(
            title = "Apply suggested compatibility settings now and re-run diagnostics?",
            message = buildString {
                appendLine("These will change:")
                rr.suggestedChanges.forEach { c ->
                    appendLine("  • ${c.label}: ${c.current} → ${c.suggested}")
                }
                append("\nThe diagnostics will then re-run so you can compare before and after.")
            },
            confirmLabel = "Apply & re-test",
            onConfirm = { confirmApply = false; applyAndRerun() },
            onDismiss = { confirmApply = false },
        )
    }
}

/** Local helper so the dialog can read the collected state without recomposing scope games. */
private fun r_of(r: PanelReport?): PanelReport? = r

@Composable
private fun ContainerCard(label: String, f: ContainerFacts) {
    Card {
        Text("$label — ${f.detected}", color = Color_White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        f.error?.let { Text(it, color = EnktelLive, fontSize = 11.sp) }
        if (f.declaredContentType.isNotBlank()) {
            Row2(
                "Content-Type",
                if (f.mimeCorrect) f.declaredContentType
                else "${f.declaredContentType}  (expected ${f.mimeExpected})",
                if (f.mimeCorrect) Color_White else EnktelLive,
            )
        }
        if (f.chunked) Row2("Transfer", "chunked — no length advertised", EnktelLive)
        if (f.mismatch) {
            Row2("Extension", "disagrees with the bytes on the wire", EnktelLive)
        }
        val rg = f.range
        if (rg.tested) {
            Row2(
                "HEAD",
                when {
                    !rg.headSupported -> "rejected — GET is the authoritative test"
                    rg.headAcceptsRanges -> "Accept-Ranges: bytes"
                    else -> "no Accept-Ranges advertised"
                },
                if (rg.headAcceptsRanges) EnktelOk else EnktelTextDim,
            )
            Row2(
                "Byte ranges",
                when {
                    !rg.partialContent -> "not supported (HTTP ${rg.httpCode})"
                    rg.totalBytes <= 0 -> "supported, length unknown"
                    rg.midFileSeekOk -> "supported, mid-file verified"
                    else -> "start only — mid-file range refused"
                },
                if (rg.usable) EnktelOk else EnktelLive,
            )
            if (rg.totalBytes > 0) {
                Row2("Size", "%.1f MB".format(rg.totalBytes / 1_048_576.0))
            }
        }
        f.hls?.let { pl ->
            Row2("Playlist", pl.kind.name.lowercase() + (if (pl.isLive) " (live)" else ""))
            if (pl.variants.isNotEmpty()) Row2("Variants", pl.variants.size.toString())
            if (pl.targetDurationSec > 0) Row2("Target duration", "${pl.targetDurationSec}s")
            if (pl.discontinuities > 0) {
                Row2("Discontinuities", pl.discontinuities.toString(), EnktelLive)
            }
            if (pl.danglingAudioGroups.isNotEmpty()) {
                Row2("Missing audio groups", pl.danglingAudioGroups.joinToString(), EnktelLive)
            }
        }
        if (f.ttfbMs > 0) Row2("Time to first byte", "${f.ttfbMs} ms")
        f.matroska?.let { m ->
            Row2("Matroska DocType", m.docType.ifBlank { "unknown" })
            Row2(
                "Seek index (Cues)",
                when (m.seekable) {
                    true -> "present — seeking supported"
                    false -> "absent — seeking unsupported for this file"
                    null -> "not determinable from the file head"
                },
                when (m.seekable) {
                    true -> EnktelOk
                    false -> EnktelLive
                    null -> EnktelTextDim
                },
            )
            if (m.indexed.isNotEmpty()) Row2("Indexed elements", m.indexed.joinToString(", "))
        }
    }
}

@Composable
private fun StructureCard(r: PanelReport) {
    val st = r.structure
    Card {
        Text("LINE STRUCTURE (Xtream API)", color = Color_White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        st.error?.let { Text(it, color = EnktelLive, fontSize = 11.sp) }
        Row2("Live streams", st.liveCount.toString())
        Row2("VOD titles", st.vodCount.toString())
        Row2("Channels with archive", st.archiveCount.toString())
        if (st.vodContainers.isNotEmpty()) {
            Row2(
                "Declared containers",
                st.vodContainers.entries.sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key} ×${it.value}" },
            )
        }
        // The panel's own declaration next to what we actually pulled. When
        // these disagree it explains a whole class of playback failure.
        val declared = st.dominantVodContainer
        val actual = r.vod?.detected
        if (declared != null && actual != null && actual != "UNKNOWN") {
            val agrees = actual.equals(declared, true) ||
                (declared == "mkv" && actual == "MATROSKA") ||
                (declared == "mp4" && actual == "MP4")
            Row2(
                "Declared vs actual",
                if (agrees) "$declared matches the wire" else "says $declared, serves $actual",
                if (agrees) EnktelOk else EnktelLive,
            )
        }
    }
}

@Composable
private fun EpgCard(r: PanelReport) {
    val a = r.epg
    Card {
        Text("GUIDE ALIGNMENT", color = Color_White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row2("Programmes sampled", a.programmesChecked.toString())
        Row2(
            "Panel clock",
            if (a.serverSkewMs == 0L) "matches device" else "${a.serverSkewMs / 1000}s vs device",
            if (a.serverNotable) EnktelLive else EnktelOk,
        )
        Row2(
            "EPG data",
            if (a.guideSkewMs == 0L) "aligned" else "${a.guideSkewMs / 60_000}m vs now",
            if (a.guideNotable) EnktelLive else EnktelOk,
        )
        a.verdict?.let { Text(it, color = EnktelLive, fontSize = 11.sp) }
    }
}

@Composable
private fun CatchupCard(r: PanelReport) {
    val c = r.catchup
    Card {
        Text("CATCH-UP / TIME-SHIFT", color = Color_White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row2("Channels with archive", c.channelsWithArchive.toString())
        if (c.tested) Row2("Scheme", c.scheme.name.lowercase().replace('_', ' '))
        if (!c.tested) {
            Row2("Endpoint", c.error ?: "not tested", EnktelTextDim)
        } else {
            Row2(
                "Endpoint",
                if (c.available) "answers (HTTP ${c.httpCode})"
                else c.error ?: "no (HTTP ${c.httpCode})",
                if (c.available) EnktelOk else EnktelLive,
            )
        }
    }
}

@Composable
private fun SettingsComparison(r: PanelReport) {
    Card {
        Text("CURRENT vs SUGGESTED", color = Color_White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (r.changes.isEmpty()) {
            Text("No setting is implicated by these results.", color = EnktelTextDim, fontSize = 11.sp)
            return@Card
        }
        r.changes.forEach { c ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(c.label, color = EnktelTextDim, fontSize = 11.sp, modifier = Modifier.width(120.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.differs) "${c.current}  →  ${c.suggested}" else "${c.current}  (already correct)",
                        color = if (c.differs) EnktelBlue else EnktelOk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(c.reason, color = EnktelTextDim, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Before vs after, side by side.
 *
 * The point of re-testing is to make the change checkable rather than
 * believed, so each measured signal is shown for both passes with the
 * direction of travel — not just an overall verdict.
 */
@Composable
private fun ComparisonCard(prev: PanelReport, now: PanelReport) {
    val delta = now.score - prev.score
    Card {
        Text("BEFORE  vs  AFTER", color = Color_White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            Text("", color = EnktelTextDim, fontSize = 10.sp, modifier = Modifier.width(150.dp))
            Text("before", color = EnktelTextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("after", color = EnktelTextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
        }
        Compare("Live container", prev.live?.detected, now.live?.detected)
        Compare("VOD container", prev.vod?.detected, now.vod?.detected)
        Compare(
            "Byte ranges",
            prev.vod?.range?.let { if (it.usable) "usable" else "limited" },
            now.vod?.range?.let { if (it.usable) "usable" else "limited" },
        )
        Compare(
            "MKV seek index",
            prev.vod?.matroska?.seekable?.let { if (it) "present" else "absent" },
            now.vod?.matroska?.seekable?.let { if (it) "present" else "absent" },
        )
        Compare(
            "Catch-up",
            prev.catchup.takeIf { it.tested }?.let { if (it.available) "ok" else "no" },
            now.catchup.takeIf { it.tested }?.let { if (it.available) "ok" else "no" },
        )
        Compare(
            "Outstanding changes",
            prev.suggestedChanges.size.toString(),
            now.suggestedChanges.size.toString(),
        )
        Spacer(Modifier.height(4.dp))
        Row2(
            "Verdict",
            when {
                delta > 0 -> "improved"
                delta < 0 -> "worse — consider reverting"
                else -> "no measurable change"
            },
            when {
                delta > 0 -> EnktelOk
                delta < 0 -> EnktelLive
                else -> EnktelTextDim
            },
        )
    }
}

@Composable
private fun Compare(label: String, before: String?, after: String?) {
    val changed = before != after
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = EnktelTextDim, fontSize = 11.sp, modifier = Modifier.width(150.dp))
        Text(before ?: "—", color = EnktelTextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
            after ?: "—",
            color = if (changed) EnktelBlue else Color_White,
            fontSize = 11.sp,
            fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EnktelSurfaceHigh.copy(alpha = 0.5f))
            .border(1.dp, EnktelBlue.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

@Composable
private fun Row2(label: String, value: String, color: androidx.compose.ui.graphics.Color = Color_White) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = EnktelTextDim, fontSize = 11.sp, modifier = Modifier.width(150.dp))
        Text(value, color = color, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GroupHeaderPublic(text: String) {
    Text(text, color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
}

private val Color_White = androidx.compose.ui.graphics.Color.White
