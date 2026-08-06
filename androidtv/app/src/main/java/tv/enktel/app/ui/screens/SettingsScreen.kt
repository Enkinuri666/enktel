package tv.enktel.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.BuildConfig
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelTextDim

@Composable
fun SettingsScreen(graph: AppGraph, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val profiles by graph.playlists.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = 0L)
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")
    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    var status by remember { mutableStateOf("") }

    // Padding follows the viewport, not the build flavour.
    //
    // A flat "mobile gets 18 dp, TV gets 28 dp" reads the wrong axis: turn a
    // phone sideways and 18 dp top and bottom is still 10 % of the height,
    // spent before a single row of settings is drawn, on the orientation with
    // the least room. See ScreenShape.
    val isMobile = BuildConfig.FLAVOR == "mobile"
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val hPad = shape.padH
    val vPad = shape.padV
    // One category at a time.
    //
    // Settings was a single 780-line Column inside a verticalScroll: every
    // control, every collectAsStateWithLifecycle subscription and every
    // DataStore flow composed and stayed live at once. On a Fire TV Stick that
    // is both why the page crawls and why finding anything means scrolling past
    // nine sections you didn't want. Rendering one category keeps the tree small
    // and turns navigation into a choice rather than a hunt.
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    // Read by both Parental controls and Kids mode, so it lives above the
    // category split rather than inside whichever section comes first.
    val pinHash by graph.settings.parentalPinHash.collectAsStateWithLifecycle(initialValue = "")

    Column(
        Modifier.fillMaxSize().padding(horizontal = hPad, vertical = vPad),
        verticalArrangement = Arrangement.spacedBy(shape.sectionGap),
    ) {
        SectionTitle("Settings")
        if (status.isNotBlank()) Text(status, color = EnktelOk, fontSize = 13.sp)

        // Above the category tabs on purpose. Expiry and the connection cap are
        // what people open Settings to check, and the v1.38.1 split had filed
        // them under Playlists where you have to know to look.
        AccountBanner(graph, profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull())

        // Quick-actions stay visible in every category — they're the tools
        // people open Settings to reach.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("🩺  Run connection diagnostics", accent = true,
                onClick = { nav.navigate("speedTest") })
            FocusButton("📈  System monitor",
                onClick = { nav.navigate("systemMonitor") })
            FocusButton("🗂  Manage categories",
                onClick = { nav.navigate("manageCategories") })
            // v1.39.0 — companion to the AccountBanner above. Trial or
            // near-expiry users can jump straight to /upgrade (WebView on
            // mobile, QR on TV) without hunting for the button per category.
            FocusButton("💳  Upgrade account",
                onClick = { nav.navigate("upgrade") })
        }
        Text(
            "Diagnostics tests your network, the panel's URL shapes, your connection cap and the HTTP/TLS path — locally, no browser needed.",
            color = EnktelTextDim, fontSize = 11.sp, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CATEGORIES) { c ->
                GlassChip(c, selected = c == category, onClick = { category = c })
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        if (category == "Playlists") {
        Text("PLAYLISTS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        profiles.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton(
                    (if (p.id == activeId) "✓ " else "") + "${p.name} (${p.kind})",
                    accent = p.id == activeId,
                    onClick = { scope.launch { graph.playlists.switchTo(p.id) } },
                )
                FocusButton("Sync now", onClick = {
                    scope.launch {
                        status = "Syncing ${p.name}…"
                        status = runCatching { graph.content.refreshAll(p) }
                            .fold({ "Synced: $it" }, { "Sync failed: ${it.message}" })
                        graph.playlists.markSynced(p)
                    }
                })
                FocusButton("Refresh EPG", onClick = {
                    scope.launch {
                        status = "Downloading EPG…"
                        status = runCatching { graph.epg.refresh(p) }
                            .fold({ "EPG updated: $it programmes" }, { "EPG failed: ${it.message}" })
                    }
                })
                FocusButton("Remove", onClick = { scope.launch { graph.playlists.delete(p.id) } })
            }
            if (p.expiresAt > 0) {
                Text(
                    "Expires ${TimeFormat.format("d MMM yyyy", p.expiresAt)} · max ${p.maxConnections} connection(s)",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("+ Add playlist", onClick = { nav.navigate("onboarding") })
            FocusButton("☰ Manage Categories", accent = true, onClick = { nav.navigate("manageCategories") })
            FocusButton("📶 Network Speed Test", onClick = { nav.navigate("speedTest") })
        }

        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Live stream format")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("HLS (m3u8)", selected = streamFormat == "hls", onClick = { scope.launch { graph.settings.setStreamFormat("hls") } })
            tv.enktel.app.ui.components.GlassChip("MPEG-TS", selected = streamFormat == "ts", onClick = { scope.launch { graph.settings.setStreamFormat("ts") } })
        }
        Text("MPEG-TS starts faster on some panels; HLS adapts quality automatically.", color = EnktelTextDim, fontSize = 11.sp)

        tv.enktel.app.ui.components.ChipRowLabel("Player buffer")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            // v1.31.0 — "Auto" chip now surfaces the auto-per-device-class code
            // path that PlayerEngine already picked up but Settings never showed.
            // Renamed the other three chips to match the user-facing spec.
            tv.enktel.app.ui.components.GlassChip("Auto (recommended)", selected = bufferProfile == "auto",
                onClick = { scope.launch { graph.settings.setBufferProfile("auto") } })
            tv.enktel.app.ui.components.GlassChip("Low latency", selected = bufferProfile == "low",
                onClick = { scope.launch { graph.settings.setBufferProfile("low") } })
            tv.enktel.app.ui.components.GlassChip("Balanced", selected = bufferProfile == "balanced",
                onClick = { scope.launch { graph.settings.setBufferProfile("balanced") } })
            tv.enktel.app.ui.components.GlassChip("High buffer", selected = bufferProfile == "large",
                onClick = { scope.launch { graph.settings.setBufferProfile("large") } })
        }
        Text(
            "Auto = scales by device class (TV keeps a bigger cushion; phones lean lean). " +
                "Low latency ≈ 5 s buffer for live sports. Balanced ≈ 15 s for typical use. " +
                "High buffer ≈ 30 s+ for unstable Wi-Fi or mobile networks. Applies next player open.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Force MP4 fallback (VOD)")
        val vodForceMp4 by graph.settings.vodForceMp4.collectAsStateWithLifecycle(initialValue = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip(
                "Off", selected = !vodForceMp4,
                onClick = { scope.launch { graph.settings.setVodForceMp4(false) } },
            )
            tv.enktel.app.ui.components.GlassChip(
                "On", selected = vodForceMp4,
                onClick = { scope.launch { graph.settings.setVodForceMp4(true) } },
            )
        }
        Text(
            "Bypass container auto-detection and force ExoPlayer to parse every movie / episode strictly as MP4. Turn on if a specific title fails with a codec / container error and the fallback chain doesn't recover.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Player controls auto-hide")
        val hudSec by graph.settings.hudAutoHideSec.collectAsStateWithLifecycle(initialValue = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(0 to "Never", 3 to "3 s", 5 to "5 s", 8 to "8 s", 15 to "15 s", 30 to "30 s").forEach { (v, label) ->
                tv.enktel.app.ui.components.GlassChip(
                    label, selected = hudSec == v,
                    onClick = { scope.launch { graph.settings.setHudAutoHideSec(v) } },
                )
            }
        }
        Text(
            "How long the on-screen info bar / transport controls stay visible after the last input. \"Never\" keeps them up until you dismiss with Back.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Recording") {
        Spacer(Modifier.height(10.dp))
        Text("DVR RECORDING PADDING", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val recPrefix by graph.settings.recPrefixMin.collectAsStateWithLifecycle(initialValue = 2)
        val recSuffix by graph.settings.recSuffixMin.collectAsStateWithLifecycle(initialValue = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("Start ${recPrefix}m early", onClick = {
                scope.launch { graph.settings.setRecPrefixMin(when (recPrefix) { 0 -> 2; 2 -> 5; 5 -> 10; else -> 0 }) }
            })
            FocusButton("End ${recSuffix}m late", onClick = {
                scope.launch { graph.settings.setRecSuffixMin(when (recSuffix) { 0 -> 5; 5 -> 10; 10 -> 15; else -> 0 }) }
            })
        }

        Spacer(Modifier.height(10.dp))
        Text("POWER-USER DECODING", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val decMode by graph.settings.decoderMode.collectAsStateWithLifecycle(initialValue = "hwplus")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("HW+ (default)", selected = decMode == "hwplus",
                onClick = { scope.launch { graph.settings.setDecoderMode("hwplus") } })
            tv.enktel.app.ui.components.GlassChip("HW only", selected = decMode == "hw",
                onClick = { scope.launch { graph.settings.setDecoderMode("hw") } })
        }
        Text(
            "HW+ prefers software extensions (AV1/VP9/FFmpeg) then falls back to SoC hardware — safer for weird codecs. HW-only skips extensions entirely — sharper on strong SoCs (Nvidia Shield, Fire Cube gen 3).",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Minimum buffer override (ms)")
        val minBuf by graph.settings.minBufferMs.collectAsStateWithLifecycle(initialValue = 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(0 to "Off", 2_000 to "2s", 5_000 to "5s", 8_000 to "8s", 12_000 to "12s").forEach { (v, label) ->
                tv.enktel.app.ui.components.GlassChip(label, selected = minBuf == v,
                    onClick = { scope.launch { graph.settings.setMinBufferMs(v) } })
            }
        }
        Text(
            "Force a floor under the LoadControl minimum buffer. Use on jittery ISPs where the profile default (Low/Balanced/Large) still under-buffers.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Dialogue boost")
        val dlg by graph.settings.dialogueBoost.collectAsStateWithLifecycle(initialValue = "off")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf("off" to "Off", "low" to "Low", "medium" to "Medium", "high" to "High").forEach { (v, label) ->
                tv.enktel.app.ui.components.GlassChip(label, selected = dlg == v,
                    onClick = { scope.launch { graph.settings.setDialogueBoost(v) } })
            }
        }
        Text(
            "Lifts the 200–3400 Hz voice band via DynamicsProcessing so whisper-quiet dialogue stops getting drowned by explosion-loud action scenes. Requires Android 9+.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))
        val bgAudio by graph.settings.backgroundAudio.collectAsStateWithLifecycle(initialValue = false)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background audio (live TV)", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Keep live news / sports / podcasts playing when the screen turns off. Applies to live TV only — VOD keeps the screen on.",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            FocusButton(
                text = if (bgAudio) "On" else "Off",
                accent = bgAudio,
                onClick = { scope.launch { graph.settings.setBackgroundAudio(!bgAudio) } },
            )
        }

        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("EPG timezone offset")
        val epgOff by graph.settings.epgOffsetMin.collectAsStateWithLifecycle(initialValue = 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(-180, -120, -60, -30, 0, 30, 60, 120, 180).forEach { m ->
                val label = when {
                    m == 0 -> "0"
                    m > 0 -> "+${m}m"
                    else -> "${m}m"
                }
                tv.enktel.app.ui.components.GlassChip(label, selected = epgOff == m,
                    onClick = { scope.launch { graph.settings.setEpgOffsetMin(m) } })
            }
        }
        Text(
            "Shifts the XMLTV guide times when the panel's clock doesn't match yours. Negative = programmes appear earlier; positive = later.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        // v1.26.0 — Discord Watch Party section.
        }
        if (category == "Network") {
        Spacer(Modifier.height(10.dp))
        Text("DISCORD WATCH PARTY", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val discordWebhook by graph.settings.discordWebhook.collectAsStateWithLifecycle(initialValue = "")
        val discordVoice by graph.settings.discordVoiceChannel.collectAsStateWithLifecycle(initialValue = "Richard's Hangout")
        val companionOn by graph.settings.companionMode.collectAsStateWithLifecycle(initialValue = false)
        Spacer(Modifier.height(4.dp))
        tv.enktel.app.ui.components.TvTextField(
            value = discordWebhook,
            onValueChange = { scope.launch { graph.settings.setDiscordWebhook(it) } },
            label = "Webhook URL (Discord channel → Edit → Integrations → Webhooks)",
        )
        Spacer(Modifier.height(6.dp))
        tv.enktel.app.ui.components.TvTextField(
            value = discordVoice,
            onValueChange = { scope.launch { graph.settings.setDiscordVoiceChannel(it) } },
            label = "Voice channel name (shown in the share message)",
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Streaming Companion Mode", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Locks the top bitrate and raises the min buffer to 30 s so Discord screen-share viewers don't see quality flapping or micro-stalls.",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            FocusButton(
                text = if (companionOn) "On" else "Off",
                accent = companionOn,
                onClick = { scope.launch { graph.settings.setCompanionMode(!companionOn) } },
            )
        }

        // v1.31.0 — surface Backup Gateways in the UI. The setting has been
        // in SettingsStore for a while (setBackupGateways / backupGateways),
        // but it wasn't editable from the app. When the active playlist's
        // origin host fails an HTTP 403 / 502 / 5xx or times out, the
        // fallback resolver walks this list of alternate host prefixes
        // (one URL per line) before giving up.
        }
        if (category == "Network") {
        Spacer(Modifier.height(10.dp))
        Text("BACKUP GATEWAYS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val backupGw by graph.settings.backupGateways.collectAsStateWithLifecycle(initialValue = emptyList())
        var newGw by remember { mutableStateOf("") }
        Spacer(Modifier.height(4.dp))
        tv.enktel.app.ui.components.TvTextField(
            value = newGw,
            onValueChange = { newGw = it },
            label = "Add gateway host (e.g. http://mirror.example.com:8080)",
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("Add", accent = true, onClick = {
                val trimmed = newGw.trim()
                if (trimmed.isNotEmpty() && trimmed !in backupGw) {
                    scope.launch { graph.settings.setBackupGateways(backupGw + trimmed) }
                    newGw = ""
                }
            })
        }
        if (backupGw.isEmpty()) {
            Text(
                "No gateways yet. When your primary panel is unreachable (403 / 502 / timeout), the resolver will fall back to whatever hosts you add here in order.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            backupGw.forEach { host ->
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(host, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    FocusButton("Remove", onClick = {
                        scope.launch { graph.settings.setBackupGateways(backupGw - host) }
                    })
                }
            }
        }

        }
        if (category == "Recording") {
        Spacer(Modifier.height(10.dp))
        Text("DOWNLOADS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val dlEngine by graph.settings.downloadEngine.collectAsStateWithLifecycle(initialValue = "auto")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("Auto (fast)", selected = dlEngine == "auto",
                onClick = { scope.launch { graph.settings.setDownloadEngine("auto") } })
            tv.enktel.app.ui.components.GlassChip("Parallel", selected = dlEngine == "parallel",
                onClick = { scope.launch { graph.settings.setDownloadEngine("parallel") } })
            tv.enktel.app.ui.components.GlassChip("System (OS)", selected = dlEngine == "system",
                onClick = { scope.launch { graph.settings.setDownloadEngine("system") } })
        }
        Text(
            "Auto/Parallel use a ranged OkHttp downloader — genuinely faster on Xtream VOD panels that support ranges. Stream count follows your line's connection limit, always leaving one free for playback. System uses Android's DownloadManager (OS notification, single-stream). Custom folder = always Parallel (OS can't write SAF).",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        val wifiOnly by graph.settings.downloadsWifiOnly.collectAsStateWithLifecycle(
            initialValue = BuildConfig.FLAVOR == "mobile",
        )
        FocusButton(
            "Download on Wi-Fi only: ${if (wifiOnly) "ON" else "off"}",
            accent = wifiOnly,
            onClick = { scope.launch { graph.settings.setDownloadsWifiOnly(!wifiOnly) } },
        )
        Text(
            "Holds downloads back while the connection is metered — a single film can be several GB. " +
                "Queued downloads say \"Waiting for Wi-Fi\" and start on their own once you're back on an " +
                "unmetered network. Tethered hotspots count as metered, since that's still your mobile data.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        val dlFolderUri by graph.settings.downloadFolderUri.collectAsStateWithLifecycle(initialValue = "")
        val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                // Persist the read+write grant across process restarts so the
                // downloader can still write into the chosen folder tomorrow.
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try { ctx.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Throwable) {}
                scope.launch {
                    graph.settings.setDownloadFolderUri(uri.toString())
                    status = "Download folder set — future downloads will land here"
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("📁  Pick download folder", onClick = { folderPicker.launch(null) })
            if (dlFolderUri.isNotBlank()) {
                FocusButton("Reset to default", onClick = {
                    scope.launch {
                        graph.settings.setDownloadFolderUri("")
                        status = "Download folder reset — using default"
                    }
                })
            }
        }
        Text(
            if (dlFolderUri.isBlank()) "Default: the app's private Movies dir (uninstall-cleaned)."
            else "Current: ${dlFolderUri.take(80)}…",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Playlists") {
        Spacer(Modifier.height(10.dp))
        Text("METADATA ENRICHMENT (TMDB)", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val tmdbKey by graph.settings.tmdbApiKey.collectAsStateWithLifecycle(initialValue = "")
        var newTmdb by remember { mutableStateOf(tmdbKey) }
        androidx.compose.runtime.LaunchedEffect(tmdbKey) { if (newTmdb.isBlank()) newTmdb = tmdbKey }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).widthIn(max = 360.dp)) {
                tv.enktel.app.ui.components.TvTextField(
                    newTmdb, { newTmdb = it }, "TMDB v3 key or v4 bearer token", password = true,
                )
            }
            FocusButton("Save", onClick = {
                scope.launch {
                    graph.settings.setTmdbApiKey(newTmdb)
                    status = if (newTmdb.isBlank()) "TMDB enrichment disabled" else "TMDB key saved — enrichment will run after next sync"
                }
            })
            if (tmdbKey.isNotBlank()) {
                FocusButton("Run now", onClick = {
                    scope.launch {
                        val pid = graph.settings.activeProfileIdNow()
                        if (pid > 0) {
                            tv.enktel.app.data.metadata.MetadataEnrichmentWorker.enqueueFor(ctx, pid)
                            status = "Metadata enrichment queued"
                        }
                    }
                })
            }
        }
        Text(
            "Free TMDB account → developer.themoviedb.org → API → request access → paste your v3 key or v4 read-only token here. Blank disables enrichment (the themed home rails still work off titles/genres alone).",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        val autoTrailers by graph.settings.autoTrailersEnabled.collectAsStateWithLifecycle(initialValue = true)
        FocusButton(
            "Auto-play trailers on hover: ${if (autoTrailers) "ON" else "off"}",
            accent = autoTrailers,
            onClick = { scope.launch { graph.settings.setAutoTrailersEnabled(!autoTrailers) } },
        )
        Text(
            if (tmdbKey.isBlank())
                "Rest on a poster in Movies or Series and its trailer plays silently behind the grid. " +
                    "Trailer lookups go through enktel.tv, so no key is needed — adding one above just " +
                    "uses your own TMDB quota instead of the shared one."
            else
                "Rest on a poster in Movies or Series and its trailer plays silently behind the grid. Always muted; move off the poster and it stops.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Parental") {
        Spacer(Modifier.height(10.dp))
        Text("PARENTAL CONTROLS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val lockedCats by graph.settings.lockedCategories.collectAsStateWithLifecycle(initialValue = emptySet())
        var newPin by remember { mutableStateOf("") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).widthIn(max = 180.dp)) {
                tv.enktel.app.ui.components.TvTextField(
                    newPin, { newPin = it.filter(Char::isDigit).take(8) },
                    if (pinHash.isBlank()) "Set PIN (digits)" else "Change PIN", password = true,
                )
            }
            FocusButton("Save PIN", onClick = {
                if (newPin.length >= 4) scope.launch {
                    graph.settings.setParentalPin(tv.enktel.app.util.Pin.hash(newPin))
                    newPin = ""
                    status = "Parental PIN saved"
                } else status = "PIN must be at least 4 digits"
            })
            if (pinHash.isNotBlank()) {
                FocusButton("Remove PIN", onClick = {
                    scope.launch {
                        graph.settings.setParentalPin("")
                        graph.settings.setLockedCategories(emptySet())
                        status = "Parental controls disabled"
                    }
                })
            }
        }
        if (pinHash.isNotBlank()) {
            Text("Tap a category to lock/unlock it (🔒 = PIN required):", color = EnktelTextDim, fontSize = 11.sp)
            val liveCats by graph.content.categories(activeId, "live").collectAsStateWithLifecycle(initialValue = emptyList())
            val vodCats by graph.content.categories(activeId, "vod").collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesCats by graph.content.categories(activeId, "series").collectAsStateWithLifecycle(initialValue = emptyList())
            (liveCats + vodCats + seriesCats).chunked(3).forEach { rowCats ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCats.forEach { cat ->
                        val lockKey = "${cat.kind}:${cat.categoryId}"
                        val locked = lockKey in lockedCats
                        FocusButton(
                            (if (locked) "🔒 " else "") + "[${cat.kind}] ${cat.name}",
                            accent = locked,
                            onClick = {
                                scope.launch {
                                    graph.settings.setLockedCategories(
                                        if (locked) lockedCats - lockKey else lockedCats + lockKey
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        }
        if (category == "Parental") {
        Spacer(Modifier.height(10.dp))
        Text("KIDS MODE", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val kidsModeOn by graph.settings.kidsModeEnabled.collectAsStateWithLifecycle(initialValue = false)
        Text(
            "Simplified, high-contrast Home restricted to family/kids content. " +
                "Requires a parental PIN above to turn back off.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        FocusButton(
            if (kidsModeOn) "🧸 Kids Mode: ON — tap to turn off" else "🧸 Kids Mode: OFF — tap to turn on",
            accent = kidsModeOn,
            onClick = {
                scope.launch {
                    if (kidsModeOn) {
                        if (pinHash.isBlank()) {
                            graph.settings.setKidsModeEnabled(false)
                        } else {
                            status = "Exit Kids Mode from its own lock icon (needs PIN)"
                        }
                    } else {
                        graph.settings.setKidsModeEnabled(true)
                        status = "Kids Mode enabled"
                    }
                }
            },
        )

        }
        if (category == "Playback") {
        Spacer(Modifier.height(10.dp))
        Text("SUBTITLES", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val subColor by graph.settings.subColor.collectAsStateWithLifecycle(initialValue = "white")
        val subEdge by graph.settings.subEdge.collectAsStateWithLifecycle(initialValue = "outline")
        val subBg by graph.settings.subBgAlpha.collectAsStateWithLifecycle(initialValue = 0)
        val extSub by graph.settings.extSubUrl.collectAsStateWithLifecycle(initialValue = "")
        var newSub by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("white", "yellow", "cyan", "green").forEach { c ->
                FocusButton("Color: $c", accent = subColor == c, onClick = {
                    scope.launch { graph.settings.setSubColor(c) }
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("none", "outline", "shadow", "raised", "depressed").forEach { e ->
                FocusButton("Edge: $e", accent = subEdge == e, onClick = { scope.launch { graph.settings.setSubEdge(e) } })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("Background: ${if (subBg == 0) "off" else "${subBg * 100 / 255}%"}", onClick = {
                scope.launch { graph.settings.setSubBgAlpha(when (subBg) { 0 -> 128; 128 -> 200; 200 -> 255; else -> 0 }) }
            })
        }
        if (extSub.isNotBlank()) Text("External subtitle: $extSub", color = EnktelTextDim, fontSize = 11.sp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).widthIn(max = 320.dp)) {
                tv.enktel.app.ui.components.TvTextField(newSub, { newSub = it }, "Load .srt/.vtt/.ass URL")
            }
            FocusButton("Apply", onClick = { scope.launch { graph.settings.setExtSubUrl(newSub.trim()) } })
            FocusButton("Clear", onClick = { scope.launch { graph.settings.setExtSubUrl("") } })
        }

        }
        if (category == "Playback") {
        Spacer(Modifier.height(10.dp))
        Text("AUDIO", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val loud by graph.settings.loudnessOn.collectAsStateWithLifecycle(initialValue = false)
        FocusButton("Loudness normalization: ${if (loud) "ON" else "off"}", accent = loud, onClick = {
            scope.launch { graph.settings.setLoudnessOn(!loud) }
        })

        }
        if (category == "Playback") {
        Spacer(Modifier.height(10.dp))
        Text("PLAYBACK", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val autoplay by graph.settings.autoplayNextEp.collectAsStateWithLifecycle(initialValue = true)
        val skipIntro by graph.settings.skipIntroSec.collectAsStateWithLifecycle(initialValue = 0)
        val pip by graph.settings.pipEnabled.collectAsStateWithLifecycle(initialValue = true)
        val autoPipBack by graph.settings.autoPipOnBack.collectAsStateWithLifecycle(initialValue = true)
        val autoPipHome by graph.settings.autoPipOnHome.collectAsStateWithLifecycle(initialValue = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusButton("Auto-play next episode: ${if (autoplay) "on" else "off"}", accent = autoplay, onClick = {
                    scope.launch { graph.settings.setAutoplayNextEp(!autoplay) }
                })
                FocusButton("Skip intro: ${skipIntro}s", onClick = {
                    scope.launch { graph.settings.setSkipIntroSec(when (skipIntro) { 0 -> 30; 30 -> 60; 60 -> 90; 90 -> 120; else -> 0 }) }
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusButton("Picture-in-Picture: ${if (pip) "on" else "off"}", accent = pip, onClick = {
                    scope.launch { graph.settings.setPipEnabled(!pip) }
                })
                FocusButton(
                    "PiP on back: ${if (autoPipBack) "on" else "off"}",
                    accent = autoPipBack,
                    onClick = { scope.launch { graph.settings.setAutoPipOnBack(!autoPipBack) } },
                )
                FocusButton(
                    "PiP on home: ${if (autoPipHome) "on" else "off"}",
                    accent = autoPipHome,
                    onClick = { scope.launch { graph.settings.setAutoPipOnHome(!autoPipHome) } },
                )
            }
        }
        Text(
            "PiP requires Android 8.0+ and the system \"Picture-in-picture\" app permission " +
                "(Settings → Apps → EnkTel → Advanced → Picture-in-picture). " +
                "\"On back\" hands off when you press the back button; \"On home\" hands off " +
                "when you press Home while a player is on-screen.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Sports & Voice") {
        Spacer(Modifier.height(10.dp))
        Text("VOICE", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val wakeWord by graph.settings.wakeWordEnabled.collectAsStateWithLifecycle(initialValue = false)
        FocusButton(
            "\"Hey Enki\" wake word: ${if (wakeWord) "ON" else "off"}",
            accent = wakeWord,
            onClick = { scope.launch { graph.settings.setWakeWordEnabled(!wakeWord) } },
        )
        Text(
            "When on, EnkTel listens continuously for \"Hey Enki\" (or just \"Enki\") and " +
                "acts on whatever you say next: \"Hey Enki, turn to Nine HD\", \"Hey Enki, pause\", " +
                "\"Hey Enki, what live sports is on\". Uses more battery — off by default.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Sports & Voice") {
        Spacer(Modifier.height(10.dp))
        Text("SPORTS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val sportsKey by graph.settings.sportsDbKey.collectAsStateWithLifecycle(initialValue = "")
        var newSportsKey by remember { mutableStateOf(sportsKey) }
        androidx.compose.runtime.LaunchedEffect(sportsKey) { if (newSportsKey.isBlank()) newSportsKey = sportsKey }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).widthIn(max = 280.dp)) {
                tv.enktel.app.ui.components.TvTextField(
                    newSportsKey, { newSportsKey = it }, "TheSportsDB key (optional)", password = true,
                )
            }
            FocusButton("Save", onClick = {
                scope.launch {
                    graph.settings.setSportsDbKey(newSportsKey)
                    status = if (newSportsKey.isBlank()) "Using TheSportsDB free key"
                        else "TheSportsDB key saved"
                }
            })
        }
        Text(
            "Schedules, highlights and match detail work on the free key. In-play live scores do " +
                "not — that endpoint is Premium-only, which is why turning Live scores on can look " +
                "like nothing happened. A Patreon key at thesportsdb.com unlocks it.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        val scoresOn by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)
        FocusButton("Live scores (TheSportsDB): ${if (scoresOn) "ON" else "off"}", accent = scoresOn, onClick = {
            scope.launch { graph.settings.setScoresEnabled(!scoresOn) }
        })
        val matchCenterOn by graph.settings.matchCenterEnabled.collectAsStateWithLifecycle(initialValue = true)
        FocusButton(
            "Match Centre, broadcast guide + highlights: ${if (matchCenterOn) "ON" else "off"}",
            accent = matchCenterOn,
            onClick = { scope.launch { graph.settings.setMatchCenterEnabled(!matchCenterOn) } },
        )
        Text(
            "Adds in-play stats and timelines for a fixture, the official broadcaster list for it, " +
                "today's published schedule, and highlight packages for matches that have finished. " +
                "Turn off to stop the Sports Hub making these lookups.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("🔎  Live sport finder", onClick = { nav.navigate("sportsFinder") })
            FocusButton("🏟  Sports Hub", onClick = { nav.navigate("sports") })
        }
        val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())
        var newTeam by remember { mutableStateOf("") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).widthIn(max = 260.dp)) {
                tv.enktel.app.ui.components.TvTextField(newTeam, { newTeam = it }, "Follow team or league")
            }
            FocusButton("Add", onClick = {
                if (newTeam.isNotBlank()) scope.launch {
                    graph.db.sportsDao().follow(
                        tv.enktel.app.data.db.FollowedTeam(name = newTeam.lowercase(), displayName = newTeam.trim())
                    )
                    newTeam = ""
                }
            })
        }
        followed.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    FocusButton("★ ${t.displayName}  ✕", onClick = {
                        scope.launch { graph.db.sportsDao().unfollow(t.name) }
                    })
                }
            }
        }

        }
        if (category == "Playback") {
        Spacer(Modifier.height(10.dp))
        Text("BACK BUTTON IN PLAYER", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val backAction by graph.settings.backAction.collectAsStateWithLifecycle(initialValue = "exit")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("Exit player", accent = backAction == "exit",
                onClick = { scope.launch { graph.settings.setBackAction("exit") } })
            FocusButton("⧉ Standard PiP", accent = backAction == "pip",
                onClick = { scope.launch { graph.settings.setBackAction("pip") } })
            FocusButton("▤ Dock & browse", accent = backAction == "dock",
                onClick = { scope.launch { graph.settings.setBackAction("dock") } })
        }
        Text(
            "Dock & browse shrinks playback into a mini window so you can check downloads, " +
                "the guide, Movies or the Sports Hub without losing what you're watching.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        Text(
            "Pressing HOME on your remote / device always tries Picture-in-Picture regardless of this setting.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Playback") {
        Spacer(Modifier.height(10.dp))
        Text("MINI PLAYER", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val dockSize by graph.settings.dockSizeStep.collectAsStateWithLifecycle(initialValue = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Small" to 0, "Medium" to 1, "Large" to 2).forEach { (label, step) ->
                FocusButton(label, accent = dockSize == step,
                    onClick = { scope.launch { graph.settings.setDockSizeStep(step) } })
            }
        }
        val dockCorner by graph.settings.dockCorner.collectAsStateWithLifecycle(initialValue = "BOTTOM_END")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "↖ Top left" to "TOP_START", "↗ Top right" to "TOP_END",
                "↙ Bottom left" to "BOTTOM_START", "↘ Bottom right" to "BOTTOM_END",
            ).forEach { (label, value) ->
                FocusButton(label, accent = dockCorner == value,
                    onClick = { scope.launch { graph.settings.setDockCorner(value) } })
            }
        }
        Text(
            if (tv.enktel.app.BuildConfig.FLAVOR == "mobile")
                "Drag the mini window to move it between corners, or tap it to go full screen."
            else "While docked, ▶ at the top of the side menu returns you to full screen. " +
                "The remote's play/pause and channel keys keep working.",
            color = EnktelTextDim, fontSize = 11.sp,
        )

        }
        if (category == "Appearance") {
        Spacer(Modifier.height(10.dp))
        Text("SCREENSAVER", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val ss by graph.settings.screensaverMin.collectAsStateWithLifecycle(initialValue = 5)
        FocusButton(if (ss == 0) "Screensaver: off" else "Screensaver: ${ss} min", onClick = {
            scope.launch { graph.settings.setScreensaverMin(when (ss) { 0 -> 3; 3 -> 5; 5 -> 10; 10 -> 20; else -> 0 }) }
        })

        }
        if (category == "Appearance") {
        Spacer(Modifier.height(10.dp))
        Text("APPEARANCE", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val themeId by graph.settings.theme.collectAsStateWithLifecycle(initialValue = "enktel_blue")
        val opacityPct by graph.settings.uiOpacityPct.collectAsStateWithLifecycle(initialValue = 92)
        val textPct by graph.settings.textScalePct.collectAsStateWithLifecycle(initialValue = 100)
        tv.enktel.app.ui.theme.ALL_PALETTES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { pal ->
                    FocusButton(
                        (if (themeId == pal.id) "✓ " else "") + pal.label,
                        accent = themeId == pal.id,
                        onClick = { scope.launch { graph.settings.setTheme(pal.id) } },
                    )
                }
            }
        }
        Text("Overlay opacity: ${opacityPct}%", color = EnktelTextDim, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FocusButton("−", onClick = { scope.launch { graph.settings.setUiOpacityPct(opacityPct - 5) } })
            FocusButton("+", onClick = { scope.launch { graph.settings.setUiOpacityPct(opacityPct + 5) } })
            FocusButton("Reset", onClick = { scope.launch { graph.settings.setUiOpacityPct(92) } })
        }
        Text("Text size: ${textPct}%", color = EnktelTextDim, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(85 to "Small", 100 to "Normal", 115 to "Large", 130 to "X-Large").forEach { (v, label) ->
                FocusButton(label, accent = textPct == v, onClick = { scope.launch { graph.settings.setTextScalePct(v) } })
            }
        }

        if (BuildConfig.FLAVOR == "tv") {
        }
        if (category == "Appearance") {
            Spacer(Modifier.height(10.dp))
            Text("STARTUP", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            val sob by graph.settings.startOnBoot.collectAsStateWithLifecycle(initialValue = false)
            FocusButton(
                "Start app on TV boot: ${if (sob) "ON" else "off"}",
                accent = sob,
                onClick = { scope.launch { graph.settings.setStartOnBoot(!sob) } },
            )
            Text(
                "When enabled, EnkTel launches automatically after the TV finishes booting. Some Fire TV builds also require enabling 'Allow launch on boot' in device settings.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }

        }
        if (category == "About") {
        Spacer(Modifier.height(10.dp))
        Text("ABOUT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("EnkTel IPTV · Stream Beyond Limits", color = Color.White, fontSize = 13.sp)
        Text("Android TV & Fire TV · Xtream Codes + M3U · EPG · Catch-up · DVR", color = EnktelTextDim, fontSize = 12.sp)
        Text(
            "Version ${tv.enktel.app.BuildConfig.VERSION_NAME} (${tv.enktel.app.BuildConfig.VERSION_CODE})",
            color = EnktelTextDim, fontSize = 12.sp,
        )
        }
        }
    }
}

private val CATEGORIES = listOf(
    "Playlists", "Playback", "Recording", "Sports & Voice",
    "Parental", "Network", "Appearance", "About",
)

