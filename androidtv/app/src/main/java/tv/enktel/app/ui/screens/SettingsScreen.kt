package tv.enktel.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelText
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextFaint
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.components.tvRailFocus

@Composable
fun SettingsScreen(graph: AppGraph, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val profiles by graph.playlists.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = 0L)
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")
    val relayPlayback by graph.settings.relayPlayback.collectAsStateWithLifecycle(initialValue = false)
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

        // Sections down the side, not along the top.
        //
        // Eight categories in a horizontal chip rail meant scrolling sideways
        // to reach the last three, on the axis a television has most of and a
        // remote traverses worst — and the rail showed only names, so choosing
        // between "Network" and "Playback" for a buffer setting was a guess
        // with nothing on screen to settle it. A side list shows all eight at
        // once with a line each saying what is inside, which is the difference
        // between navigating and hunting.
        //
        // Narrow portrait keeps the rail: there is no room for two panes on a
        // phone held upright, and a full-width list would push the actual
        // settings off the bottom.
        val twoPane = !shape.narrow

        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(if (twoPane) 18.dp else 0.dp),
        ) {
            if (twoPane) {
                Column(
                    Modifier
                        .width(236.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SETTINGS_CATEGORIES.forEach { c ->
                        SettingsNavItem(
                            category = c,
                            selected = c.name == category,
                            onClick = { category = c.name },
                        )
                    }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!twoPane) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.tvRailFocus(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(CATEGORIES) { c ->
                            GlassChip(c, selected = c == category, onClick = { category = c })
                        }
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
                        // A re-sync is the user asking for everything to be
                        // brought up to date, published feeds included.
                        runCatching { graph.feed.invalidate() }
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
            ProviderUserAgent(graph, p, scope)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("+ Add playlist", onClick = { nav.navigate("onboarding") })
            FocusButton("\u2b06 Import file", onClick = { playlistPicker.launch(arrayOf("*/*")) })
            FocusButton("\u2b07 Export", onClick = {
                val p = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
                val stem = p?.name?.replace(Regex("[^A-Za-z0-9 _-]"), "")?.trim().orEmpty()
                exportPicker.launch("${stem.ifBlank { "enktel" }}.m3u")
            })
            FocusButton("☰ Manage Categories", accent = true, onClick = { nav.navigate("manageCategories") })
            FocusButton("📶 Network Speed Test", onClick = { nav.navigate("speedTest") })
        }

        Spacer(Modifier.height(10.dp))
        // Import accepts anything the picker will show. These files arrive as
        // .m3u, .m3u8, .dat, .txt and with no extension at all, and the name
        // says nothing about the contents — the parser is what decides, so
        // filtering by MIME type here would only hide valid playlists.
        val playlistPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) scope.launch {
                status = "Importing\u2026"
                val imported = graph.playlists.importM3u(ctx, uri)
                status = imported.fold({ p ->
                    // Sync straight away. An imported profile that sits empty
                    // until something else triggers a refresh reads as a failed
                    // import, and the parse is the only real check that the
                    // file was a playlist at all.
                    runCatching { graph.content.refreshAll(p) }.fold(
                        { summary ->
                            runCatching { graph.playlists.markSynced(p) }
                            "Imported ${p.name} \u2014 $summary"
                        },
                        { e -> "Imported, but sync failed: ${e.message ?: "unknown"}" },
                    )
                }, { e -> "Import failed: ${e.message ?: "unknown"}" })
            }
        }

        val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
        ) { uri ->
            if (uri != null) scope.launch {
                status = runCatching {
                    val p = graph.playlists.activeProfile() ?: error("No playlist selected")
                    val channels = kotlinx.coroutines.flow.first(graph.content.channels(p.id))
                    val text = tv.enktel.app.data.m3u.M3uWriter.write(
                        channels,
                        epgUrl = p.epgUrl,
                        // An M3U profile stores a URL per row. An Xtream line
                        // builds them, which means an exported Xtream playlist
                        // necessarily contains the line's username and
                        // password — it is a working key to the account, so
                        // treat the file as one.
                        urlOf = { ch ->
                            if (p.kind == "m3u") ch.url
                            else tv.enktel.app.data.xtream.StreamUrlResolver
                                .forChannel(p, ch, preferHls = true).firstOrNull().orEmpty()
                        },
                    )
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("Could not open the file for writing")
                    val warning = if (p.kind == "xtream") " \u2014 contains your line's credentials" else ""
                    "Exported ${channels.size} channels$warning"
                }.getOrElse { e -> "Export failed: ${e.message ?: "unknown"}" }
            }
        }

        tv.enktel.app.ui.components.ChipRowLabel("Live stream format")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("HLS (m3u8)", selected = streamFormat == "hls", onClick = { scope.launch { graph.settings.setStreamFormat("hls") } })
            tv.enktel.app.ui.components.GlassChip("MPEG-TS", selected = streamFormat == "ts", onClick = { scope.launch { graph.settings.setStreamFormat("ts") } })
        }
        Text("MPEG-TS starts faster on some panels; HLS adapts quality automatically.", color = EnktelTextDim, fontSize = 11.sp)


        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Playback route")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("Direct", selected = !relayPlayback, onClick = { scope.launch { graph.settings.setRelayPlayback(false) } })
            tv.enktel.app.ui.components.GlassChip("Relay", selected = relayPlayback, onClick = { scope.launch { graph.settings.setRelayPlayback(true) } })
        }
        Text(
            "Direct opens the stream host from this device — fewest hops, lowest latency, " +
                "and right whenever it works. Relay fetches through enktel.tv instead, for when " +
                "this network cannot reach the host. It does not grant access to anything your " +
                "line is not already entitled to.",
            color = EnktelTextDim,
            fontSize = 11.sp,
        )

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
            "Live and on-demand get opposite buffers from the same profile, because they want " +
                "opposite things: live has to stay near the broadcast edge and inside the " +
                "provider's segment window, on-demand wants as much runway as it can hold. " +
                "Auto scales by device class. Applies next time a player opens.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        // The numbers actually in force, not the ones the profile is named
        // after. A profile is adjusted for live-vs-VOD, device class and
        // available memory before it reaches the player, so "High buffer"
        // meaning 180 s on a film and 15 s on a live channel is exactly the
        // kind of thing a user should be able to read rather than infer.
        run {
            // LocalConfiguration, not ctx.resources.configuration: the
            // context's copy is not invalidated on a configuration change, so
            // this line would keep reporting the mode it saw at first
            // composition.
            val uiMode = androidx.compose.ui.platform.LocalConfiguration.current.uiMode
            val isTvUi = (uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            val lowRam = (ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager)?.isLowRamDevice == true
            val resolved = if (bufferProfile == "auto") {
                tv.enktel.app.data.net.NetworkClass.suggestedBufferProfile
            } else {
                bufferProfile
            }
            val liveW = tv.enktel.app.player.BufferProfiles.window(resolved, true, isTvUi, lowRam)
            val vodW = tv.enktel.app.player.BufferProfiles.window(resolved, false, isTvUi, lowRam)
            Text(
                "On this device: live holds up to ${liveW.maxMs / 1000}s and starts after " +
                    "${liveW.playMs}ms · on-demand holds up to ${vodW.maxMs / 1000}s and starts " +
                    "after ${vodW.playMs}ms" +
                    if (lowRam) " (halved — this device reports low RAM)" else "",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("VOD / LIVE BUFFER TUNING", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(
            "Separate buffer windows for VOD (stability-first) and Live IPTV (latency-first). " +
                "Auto uses best-practice defaults. Custom lets you set each value. Applies next player open.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        val vodBufProf by graph.settings.vodBufferProfile.collectAsStateWithLifecycle(initialValue = "auto")
        val liveBufProf by graph.settings.liveBufferProfile.collectAsStateWithLifecycle(initialValue = "auto")
        tv.enktel.app.ui.components.ChipRowLabel("VOD buffer profile")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("Auto (recommended)", selected = vodBufProf == "auto",
                onClick = { scope.launch { graph.settings.setVodBufferProfile("auto") } })
            tv.enktel.app.ui.components.GlassChip("Custom", selected = vodBufProf == "custom",
                onClick = { scope.launch { graph.settings.setVodBufferProfile("custom") } })
        }
        if (vodBufProf == "custom") {
            val vMin by graph.settings.vodMinBufferMs.collectAsStateWithLifecycle(initialValue = 25_000)
            val vMax by graph.settings.vodMaxBufferMs.collectAsStateWithLifecycle(initialValue = 120_000)
            val vPlay by graph.settings.vodPlaybackMs.collectAsStateWithLifecycle(initialValue = 2_000)
            val vRebuf by graph.settings.vodRebufferMs.collectAsStateWithLifecycle(initialValue = 5_000)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Min buffer")
                listOf(10_000 to "10s", 15_000 to "15s", 20_000 to "20s", 25_000 to "25s", 30_000 to "30s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = vMin == v,
                        onClick = { scope.launch { graph.settings.setVodMinBufferMs(v) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Max buffer")
                listOf(60_000 to "60s", 90_000 to "90s", 120_000 to "120s", 180_000 to "180s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = vMax == v,
                        onClick = { scope.launch { graph.settings.setVodMaxBufferMs(v) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Playback start")
                listOf(1_000 to "1s", 1_500 to "1.5s", 2_000 to "2s", 3_000 to "3s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = vPlay == v,
                        onClick = { scope.launch { graph.settings.setVodPlaybackMs(v) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Rebuffer")
                listOf(3_000 to "3s", 4_000 to "4s", 5_000 to "5s", 7_000 to "7s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = vRebuf == v,
                        onClick = { scope.launch { graph.settings.setVodRebufferMs(v) } })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Live IPTV buffer profile")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            tv.enktel.app.ui.components.GlassChip("Auto (recommended)", selected = liveBufProf == "auto",
                onClick = { scope.launch { graph.settings.setLiveBufferProfile("auto") } })
            tv.enktel.app.ui.components.GlassChip("Custom", selected = liveBufProf == "custom",
                onClick = { scope.launch { graph.settings.setLiveBufferProfile("custom") } })
        }
        if (liveBufProf == "custom") {
            val lMin by graph.settings.liveMinBufferMs.collectAsStateWithLifecycle(initialValue = 2_000)
            val lMax by graph.settings.liveMaxBufferMs.collectAsStateWithLifecycle(initialValue = 8_000)
            val lPlay by graph.settings.livePlaybackMs.collectAsStateWithLifecycle(initialValue = 500)
            val lRebuf by graph.settings.liveRebufferMs.collectAsStateWithLifecycle(initialValue = 1_500)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Min buffer")
                listOf(1_000 to "1s", 1_500 to "1.5s", 2_000 to "2s", 3_000 to "3s", 5_000 to "5s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = lMin == v,
                        onClick = { scope.launch { graph.settings.setLiveMinBufferMs(v) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Max buffer")
                listOf(5_000 to "5s", 8_000 to "8s", 10_000 to "10s", 15_000 to "15s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = lMax == v,
                        onClick = { scope.launch { graph.settings.setLiveMaxBufferMs(v) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Playback start")
                listOf(300 to "300ms", 500 to "500ms", 800 to "800ms", 1_000 to "1s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = lPlay == v,
                        onClick = { scope.launch { graph.settings.setLivePlaybackMs(v) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.ChipRowLabel("Rebuffer")
                listOf(1_000 to "1s", 1_500 to "1.5s", 2_000 to "2s", 3_000 to "3s").forEach { (v, l) ->
                    tv.enktel.app.ui.components.GlassChip(l, selected = lRebuf == v,
                        onClick = { scope.launch { graph.settings.setLiveRebufferMs(v) } })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        tv.enktel.app.ui.components.ChipRowLabel("Memory allocator chunk size")
        val allocKb by graph.settings.allocatorSizeKb.collectAsStateWithLifecycle(initialValue = 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(0 to "Default (16 KB)", 256 to "256 KB", 1024 to "1 MB", 2048 to "2 MB").forEach { (v, l) ->
                tv.enktel.app.ui.components.GlassChip(l, selected = allocKb == v,
                    onClick = { scope.launch { graph.settings.setAllocatorSizeKb(v) } })
            }
        }
        Text(
            "Larger chunks reduce allocator overhead for 4K and large MKV files. " +
                "2 MB recommended for 4K content. Leave at default for standard streams.",
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
            tv.enktel.app.ui.components.GlassChip("Software audio", selected = decMode == "sw",
                onClick = { scope.launch { graph.settings.setDecoderMode("sw") } })
        }
        Text(
            "HW+ uses the SoC's own audio decoders and keeps the bundled FFmpeg decoder behind them, so it only steps in for what the box genuinely cannot decode — AC-3, E-AC-3, DTS and TrueHD on hardware that ships without them, the usual cause of a channel with picture but no sound. HW-only drops FFmpeg entirely. Software audio puts FFmpeg first: try it only if a track plays silent on HW+, which means the box advertises a decoder it does not really have. Video decoding is unaffected by all three.",
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
            "Force a floor under the LoadControl minimum buffer. Use on jittery ISPs where the " +
                "profile default still under-buffers. Clamped so it can never exceed the maximum " +
                "for the stream being played — ExoPlayer throws rather than clamps, and that " +
                "would be a crash on the first frame.",
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
        if (category == "Playback" && BuildConfig.FLAVOR == "tv") {
            Spacer(Modifier.height(10.dp))
            Text("HARDWARE", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            val tunneling by graph.settings.tunneling.collectAsStateWithLifecycle(initialValue = true)
            FocusButton(
                "Tunneled playback: ${if (tunneling) "ON" else "off"}",
                accent = tunneling,
                onClick = { scope.launch { graph.settings.setTunneling(!tunneling) } },
            )
            Text(
                "Feeds video straight to the television's decoder, bypassing part of Android's " +
                    "graphics path. Usually smoother on 4K. Turn it off if a title plays cleanly " +
                    "on the phone app but stutters here — tunnelling is one of only two things " +
                    "that differ between the two builds, and whether a given box handles it well " +
                    "for a given codec is not something the app can detect. Takes effect on the " +
                    "next thing you play.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }
        if (category == "Playback") {
        Spacer(Modifier.height(10.dp))
        Text("SUBTITLES", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)

        // ---- Live TV closed captions ----------------------------------------
        val captionMode by graph.settings.captionMode
            .collectAsStateWithLifecycle(initialValue = tv.enktel.app.player.ClosedCaptions.OFF)
        Text(
            "Live TV closed captions",
            color = EnktelText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tv.enktel.app.player.ClosedCaptions.MODES.forEach { m ->
                FocusButton(
                    tv.enktel.app.player.ClosedCaptions.label(m),
                    accent = captionMode == m,
                    onClick = { scope.launch { graph.settings.setCaptionMode(m) } },
                )
            }
        }
        Text(
            "Live channels usually do carry captions — inside the video as CEA-608/708, or as " +
                "DVB subtitles — but they are not subtitle tracks. When a provider's remux drops " +
                "the descriptor that names them, the player extracts none of them at all, which " +
                "is what makes a fully captioned channel look like it has no subtitles. This " +
                "reads them anyway, and lets an untagged caption track be selected " +
                "automatically. Croatian is matched on hr, hrv and the withdrawn scr code that " +
                "ex-Yugoslav muxes still emit.\n\n" +
                "It cannot create captions. A channel that transmits none will still show none — " +
                "that would need speech recognition, which is a different feature. Takes effect " +
                "on the next channel you open.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))

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
        Spacer(Modifier.height(14.dp))
        Text("FOLLOWING", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        // What it does, said once and plainly. Without this the field was a box
        // that swallowed text: no confirmation, no effect anyone could point
        // at, and no statement anywhere of what following was for.
        Text(
            "A followed team or league is matched against programme titles and channel names. " +
                "Its fixtures pin to the top of the Live sport finder and the Sports Hub, and the " +
                "Hub's \"my teams\" filter shows only those. Teams and leagues work; player names " +
                "do not — nothing broadcasts under a player's name.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
        val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())
        var newTeam by remember { mutableStateOf("") }
        var checking by remember { mutableStateOf(false) }
        var suggestions by remember {
            mutableStateOf<List<tv.enktel.app.data.repo.ScoresRepository.Followable>>(emptyList())
        }
        var followNote by remember { mutableStateOf("") }

        /** Store one, under the spelling the sports database uses. */
        fun addFollow(displayName: String, kind: String) {
            scope.launch {
                graph.db.sportsDao().follow(
                    tv.enktel.app.data.db.FollowedTeam(
                        name = displayName.lowercase().trim(),
                        displayName = displayName.trim(),
                        kind = kind,
                    )
                )
                newTeam = ""
                suggestions = emptyList()
                followNote = "Following $displayName"
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).widthIn(max = 260.dp)) {
                tv.enktel.app.ui.components.TvTextField(newTeam, { newTeam = it }, "Team or league name")
            }
            FocusButton(if (checking) "Checking…" else "Look up", accent = true, onClick = {
                val q = newTeam.trim()
                if (q.isBlank() || checking) return@FocusButton
                scope.launch {
                    checking = true
                    followNote = ""
                    val found = try {
                        graph.scores.searchFollowable(q)
                    } catch (_: Throwable) { null }
                    checking = false
                    suggestions = found.orEmpty()
                    followNote = when {
                        // A failed lookup and a genuine miss are different
                        // answers, and telling somebody their team does not
                        // exist because a request timed out is the worse one.
                        found == null -> "Could not reach the sports database — you can still add it as typed."
                        found.isEmpty() -> "No team or league found for \"$q\". Add it as typed if your guide spells it that way."
                        else -> "Pick the one you meant:"
                    }
                }
            })
            FocusButton("Add as typed", onClick = {
                if (newTeam.isNotBlank()) addFollow(newTeam.trim(), "team")
            })
        }
        if (followNote.isNotBlank()) {
            Text(followNote, color = EnktelTextDim, fontSize = 11.sp)
        }
        // Confirmed candidates, with enough detail to tell two Uniteds apart.
        suggestions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    FocusButton(
                        "＋ ${s.name}" + if (s.detail.isNotBlank()) "  ·  ${s.detail}" else "",
                        onClick = { addFollow(s.name, s.kind) },
                    )
                }
            }
        }
        if (followed.isEmpty()) {
            Text(
                "Not following anything yet.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        } else {
            Text(
                "Following ${followed.size} — press one to stop:",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }
        followed.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    FocusButton("★ ${t.displayName}  ✕", onClick = {
                        scope.launch {
                            graph.db.sportsDao().unfollow(t.name)
                            followNote = "Stopped following ${t.displayName}"
                        }
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

        // Startup steps that failed and were survived.
        //
        // Shown above the crash report because it is the more common case: the
        // app is running, so nobody thinks to look, and meanwhile something
        // like the guide's refresh schedule is quietly not happening.
        val startupIssues = remember { tv.enktel.app.data.diag.CrashLog.startupFailures() }
        if (startupIssues.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "STARTUP WARNINGS",
                color = tv.enktel.app.ui.theme.EnktelLive,
                fontSize = 12.sp, fontWeight = FontWeight.Black,
            )
            Text(
                "The app started, but ${startupIssues.size} optional component" +
                    (if (startupIssues.size == 1) "" else "s") +
                    " did not. The app is usable; the affected feature is not. " +
                    "Please send these lines to t.me/EnkTel.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            startupIssues.forEach { line ->
                Text(
                    "• $line",
                    color = Color.White, fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 14.sp,
                )
            }
        }

        // Last crash, if there was one.
        //
        // Shown rather than only written to a file because the file lives at
        // Android/data/<package>/files, which is reachable but not obvious. A
        // tester who can open Settings can read the first lines here and paste
        // them straight into Telegram; one who cannot open the app at all still
        // has the file. Between them that covers every case we have hit.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var crash by remember {
            mutableStateOf(tv.enktel.app.data.diag.CrashLog.read(ctx))
        }
        crash?.let { report ->
            Spacer(Modifier.height(14.dp))
            Text(
                "LAST CRASH",
                color = tv.enktel.app.ui.theme.EnktelLive,
                fontSize = 12.sp, fontWeight = FontWeight.Black,
            )
            Text(
                "The app closed unexpectedly. Send these lines to t.me/EnkTel — " +
                    "the full report is also saved to " +
                    (tv.enktel.app.data.diag.CrashLog.externalPath(ctx) ?: "the app's files folder"),
                color = EnktelTextDim, fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                report.lineSequence().take(14).joinToString("\n"),
                color = Color.White, fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            FocusButton("Clear crash report", onClick = {
                tv.enktel.app.data.diag.CrashLog.clear(ctx)
                crash = null
            })
        }
        }
            } // content column
        } // two-pane Row
        }
    }
}

/**
 * One row of the settings side list.
 *
 * Selection is carried by a filled accent bar down the leading edge rather
 * than by tinting the whole row. On a ten-foot display the selected section
 * and the focused section are two different things that are both on screen at
 * once — you can be reading Playback while the remote sits over Network — and
 * a full-row tint for one leaves nothing distinct for the other. An edge bar
 * says "this is the section you are in" while the focus ring keeps saying
 * "this is what OK will do".
 */
@Composable
private fun SettingsNavItem(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            RoundedCornerShape(12.dp),
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) EnktelSurfaceHigh else Color.Transparent,
            focusedContainerColor = EnktelBlue,
            contentColor = if (selected) EnktelText else EnktelTextDim,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .background(
                        if (selected) EnktelBlue else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    ),
            )
            Text(category.glyph, fontSize = 15.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    category.name,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    category.blurb,
                    fontSize = 10.sp,
                    color = EnktelTextFaint,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A settings section: what it is called, what it holds, and its glyph.
 *
 * The name alone was doing all the work in an eight-item chip rail, and names
 * are a poor index — "Network" and "Playback" both plausibly hold the buffer
 * settings, "Sports & Voice" reads as two unrelated things bolted together,
 * and nothing on screen resolved either guess. The subtitle is what turns the
 * list from a set of labels into a table of contents.
 */
internal data class SettingsCategory(
    val name: String,
    val glyph: String,
    val blurb: String,
)

internal val SETTINGS_CATEGORIES = listOf(
    SettingsCategory("Playlists", "📺", "Accounts, sources, catalogue sync"),
    SettingsCategory("Playback", "▶", "Buffering, decoder, subtitles, audio"),
    SettingsCategory("Recording", "⏺", "DVR, storage, padding, downloads"),
    SettingsCategory("Sports & Voice", "⚽", "Scores, teams, Match Center, Enki"),
    SettingsCategory("Parental", "🔒", "PIN, kids mode, locked categories"),
    SettingsCategory("Network", "🌐", "Guide offset, user agent, gateways"),
    SettingsCategory("Appearance", "🎨", "Theme, text size, startup"),
    SettingsCategory("About", "ⓘ", "Version, diagnostics, support"),
)

internal val CATEGORIES = SETTINGS_CATEGORIES.map { it.name }


/**
 * The User-Agent a single provider is served with.
 *
 * A panel that answers 403 to a request carrying valid credentials is
 * filtering on agent rather than rejecting the login, and changing it is the
 * highest-yield fix there is. Until now the only way to reach that was for the
 * Panel Doctor to happen to suggest it, and the value it set was global — so a
 * viewer with two lines had one provider's workaround applied to both.
 *
 * Presented as suggestions rather than a text field on purpose. These strings
 * are long and unforgiving: a User-Agent with one character wrong does not
 * fail loudly, it just carries on getting 403 while looking correct on screen.
 * Picking from a list of known-good values is most of what makes this usable
 * from a sofa with a remote.
 */
@Composable
private fun ProviderUserAgent(
    graph: AppGraph,
    profile: tv.enktel.app.data.db.Profile,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var open by remember(profile.id) { mutableStateOf(false) }
    val current = profile.userAgent
    val chosen = tv.enktel.app.data.net.UserAgents.suggestionFor(current)
    val summary = when {
        current.isBlank() -> "App default"
        chosen != null -> chosen.label
        else -> "Custom"
    }

    FocusButton(
        "🕶  User-Agent: $summary",
        accent = current.isNotBlank(),
        onClick = { open = !open },
    )
    if (!open) {
        // Say which way round it is even when collapsed. "Set to Smart TV" and
        // "set to Smart TV by the global override rather than by this
        // provider" are different facts, and only the second explains why
        // changing this row can appear to do nothing.
        if (current.isBlank()) {
            Text(
                "Sent as the app default unless a global override is set. Open to change it for this provider only.",
                color = EnktelTextFaint, fontSize = 10.sp,
            )
        }
        return
    }

    Text(
        "Sent with every request to this provider. Change it when a line answers 403 " +
            "with credentials that are otherwise fine — that is agent filtering, not a bad password.",
        color = EnktelTextDim, fontSize = 11.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tv.enktel.app.data.net.UserAgents.SUGGESTIONS.forEach { s ->
            val selected = current == s.value
            FocusButton(
                (if (selected) "✓ " else "") + s.label,
                accent = selected,
                onClick = { scope.launch { graph.playlists.setUserAgent(profile, s.value) } },
            )
            Text(s.hint, color = EnktelTextFaint, fontSize = 10.sp)
        }
        FocusButton(
            (if (current.isBlank()) "✓ " else "") + "App default",
            accent = current.isBlank(),
            onClick = { scope.launch { graph.playlists.setUserAgent(profile, "") } },
        )
        Text(
            "Clears the provider's own agent. The global override, if any, applies instead.",
            color = EnktelTextFaint, fontSize = 10.sp,
        )
    }
    if (current.isNotBlank() && chosen == null) {
        Text("Currently: $current", color = EnktelTextDim, fontSize = 10.sp)
    }
    Text(
        "Changing this takes effect on the next request — no re-sync needed. " +
            "If it makes no difference, run Diagnostics: the Panel Doctor reports what the panel " +
            "actually answered for each agent it tried.",
        color = EnktelTextFaint, fontSize = 10.sp,
    )
}
