package tv.enktel.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.dvr.RecordScheduler
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HOUR = 3_600_000L
private val DP_PER_HOUR = 220.dp

@Composable
fun GuideScreen(graph: AppGraph, nav: NavHostController) {
    // Narrow phone viewport (Galaxy S25 Ultra portrait, standard 6" phones): a
    // seven-day multi-column grid is unusable at that width.  Bounce to a
    // single-channel vertical timeline so scrolling stays 1-dimensional.
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val isNarrow = cfg.screenWidthDp < 600
    if (isNarrow) {
        MobileGuideScreen(graph, nav)
        return
    }
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val channels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var dayOffset by remember { mutableIntStateOf(0) }
    var programs by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    var selected by remember { mutableStateOf<Pair<Channel, EpgProgram>?>(null) }
    // What the dock above the grid is describing. Driven by *focus*, not by
    // clicking: on a D-pad the highlight is the cursor, so the detail panel
    // should follow it as you travel. Clicking still opens the full dialog.
    var highlighted by remember { mutableStateOf<Pair<Channel, EpgProgram>?>(null) }

    val dayStart = remember(dayOffset) {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = dayStart + 24 * HOUR

    LaunchedEffect(channels, dayOffset) {
        if (channels.isEmpty()) return@LaunchedEffect
        programs = graph.epg.window(p.id, channels.map { it.epgId }.filter { it.isNotBlank() }.distinct(), dayStart, dayEnd)
    }

    val hScroll = rememberScrollState()
    // Observable read, so the day chips re-label if the device language changes.
    val dayLocale = TimeFormat.currentLocale()
    // Live clock — updates once a minute so the NOW marker on the timeline stays
    // accurate without spinning up a per-second recomposition.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // Under thermal load, stretch the guide's now-tick refresh so
            // recomposition of the timeline doesn't add to CPU pressure.
            val mult = tv.enktel.app.data.net.ThermalGuard.level.value.pollIntervalMultiplier
            val wait = (60_000L * mult).toLong().coerceAtMost(600_000L)
            kotlinx.coroutines.delay(wait)
        }
    }
    suspend fun scrollToNow() {
        val px = ((now - dayStart).toFloat() / HOUR * 220f).toInt() - 300
        hScroll.scrollTo(px.coerceAtLeast(0))
    }
    LaunchedEffect(dayOffset, channels.isEmpty()) {
        if (dayOffset == 0) scrollToNow() else hScroll.scrollTo(0)
    }

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(
            Modifier.padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("TV Guide")
            Spacer(Modifier.weight(1f))
            FocusButton("◀", onClick = { dayOffset-- })
            Text(
                TimeFormat.format("EEEE d MMMM", dayStart),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            FocusButton("▶", onClick = { dayOffset++ })
            Spacer(Modifier.width(6.dp))
            // Snap-back to right now. If you're on a different day it jumps you back
            // to today first. Coloured accent so it's the "safe home" like on an STB.
            FocusButton(
                "⏱  NOW",
                accent = true,
                onClick = { dayOffset = 0; scope.launch { scrollToNow() } },
            )
        }
        Spacer(Modifier.height(10.dp))
        // Quick-jump chip strip: Today, +1, +2, ... makes navigating a week's worth of EPG
        // a single click instead of six D-pad presses.
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                tv.enktel.app.ui.components.GlassChip(
                    "Today", selected = dayOffset == 0, onClick = { dayOffset = 0 },
                )
            }
            items((1..6).toList()) { d ->
                val label = TimeFormat.format(
                    "EEE d", System.currentTimeMillis() + d * 86_400_000L, dayLocale,
                )
                tv.enktel.app.ui.components.GlassChip(
                    label, selected = dayOffset == d, onClick = { dayOffset = d },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        if (channels.isEmpty()) {
            CenterMessage("No channels yet — add a playlist in Settings.")
            return
        }

        // The dock: a live preview and the highlighted programme's detail,
        // above the grid. Without it the guide is a wall of two-line blocks
        // and the only way to read a synopsis is to open a dialog, which
        // means losing your place in the grid to find out whether a
        // programme is worth stopping on.
        GuideDock(
            graph = graph,
            highlighted = highlighted,
            playlistName = p.name,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        Spacer(Modifier.height(14.dp))

        // Timeline header with a live NOW marker: red vertical line at current time
        // if we're viewing today. Scrolls with hScroll so it always aligns.
        val nowOffsetDp = if (dayOffset == 0 && now in dayStart..dayEnd) {
            (DP_PER_HOUR.value * ((now - dayStart).toFloat() / HOUR)).dp
        } else null
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(216.dp))
            Box {
                Row(Modifier.horizontalScroll(hScroll, enabled = false)) {
                    repeat(24) { h ->
                        Box(Modifier.width(DP_PER_HOUR).height(24.dp)) {
                            Text(
                                String.format(Locale.US, "%02d:00", h),
                                color = EnktelTextDim, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                nowOffsetDp?.let { off ->
                    Box(
                        Modifier
                            .horizontalScroll(hScroll, enabled = false)
                            .width(off + 8.dp)
                            .height(24.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(EnktelLive, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            items(channels, key = { it.key }) { ch ->
                Row(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 2.dp)) {
                    Surface(
                        onClick = { nav.navigate("live?ch=${ch.key}") },
                        modifier = Modifier.width(210.dp).fillMaxSize()
                            // Landing on the channel name should describe what
                            // is on it right now, not leave the dock showing
                            // whatever row you came from.
                            .onFocusChanged { f ->
                                if (f.isFocused) {
                                    val nowProg = programs[ch.epgId].orEmpty()
                                        .firstOrNull { it.startMs <= now && it.endMs > now }
                                    if (nowProg != null) highlighted = ch to nowProg
                                }
                            }
                            .tapClick { nav.navigate("live?ch=${ch.key}") },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = EnktelSurface,
                            focusedContainerColor = EnktelBlue,
                            focusedContentColor = Color.White,
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(EnktelSurfaceHigh)) {
                                if (ch.logo.isNotBlank()) AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(ch.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.horizontalScroll(hScroll, enabled = true).fillMaxSize()) {
                        val progs = programs[ch.epgId].orEmpty()
                        if (progs.isEmpty()) {
                            Box(
                                Modifier.width(DP_PER_HOUR * 24).fillMaxSize()
                                    .padding(end = 2.dp)
                                    .background(EnktelSurface.copy(0.4f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text("  No guide data", color = EnktelTextDim.copy(0.6f), fontSize = 11.sp)
                            }
                        } else {
                            var cursor = dayStart
                            progs.forEach { prog ->
                                val start = prog.startMs.coerceAtLeast(dayStart)
                                val end = prog.endMs.coerceAtMost(dayEnd)
                                if (end <= start) return@forEach
                                if (start > cursor) {
                                    Spacer(Modifier.width(DP_PER_HOUR * ((start - cursor).toFloat() / HOUR)))
                                }
                                val w = DP_PER_HOUR * ((end - start).toFloat() / HOUR)
                                val isNow = prog.startMs <= now && prog.endMs > now
                                Surface(
                                    onClick = { selected = ch to prog },
                                    modifier = Modifier.width(w - 2.dp).fillMaxSize().padding(end = 2.dp)
                                        .onFocusChanged { if (it.isFocused) highlighted = ch to prog }
                                        .tapClick { selected = ch to prog },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isNow) EnktelBlue.copy(0.28f) else EnktelSurfaceHigh,
                                        focusedContainerColor = EnktelBlue,
                                        focusedContentColor = Color.White,
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                        Text(prog.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal)
                                        Text(
                                            "${TimeFormat.format("HH:mm", prog.startMs)}–${TimeFormat.format("HH:mm", prog.endMs)}",
                                            fontSize = 10.sp, color = EnktelTextDim, maxLines = 1,
                                        )
                                    }
                                }
                                cursor = end
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { (ch, prog) ->
        ProgramDialog(
            channel = ch, prog = prog, profile = p,
            onWatchLive = { selected = null; nav.navigate("live?ch=${ch.key}") },
            onCatchup = {
                selected = null
                val mins = (prog.endMs - prog.startMs) / 60000
                val url = XtreamClient.timeshiftUrl(p, ch.streamId, prog.startMs, mins)
                nav.navigate(vodPlayerRoute(url, "${ch.name} · ${prog.title}"))
            },
            onRecord = {
                selected = null
                scope.launch {
                    val url = if (p.kind == "m3u") ch.url else XtreamClient.liveUrl(p, ch.streamId, hls = false)
                    RecordScheduler.schedule(context, p.id, prog.title, ch.name, url, prog.startMs, prog.endMs, channelLogo = ch.logo)
                }
            },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun ProgramDialog(
    channel: Channel,
    prog: EpgProgram,
    profile: Profile,
    onWatchLive: () -> Unit,
    onCatchup: () -> Unit,
    onRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val isPast = prog.endMs <= now
    val isFuture = prog.startMs > now
    val isNow = !isPast && !isFuture
    // Not "is this an Xtream profile" — an M3U line that declares a catch-up
    // scheme has working catch-up, and the old gate refused it on paperwork.
    val canCatchup = isPast &&
        tv.enktel.app.data.catchup.CatchupUrls.isSupported(profile, channel) &&
        tv.enktel.app.data.catchup.CatchupUrls.isWithinWindow(channel, prog.startMs, now)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 480.dp).fillMaxWidth()
                .background(EnktelSurface, RoundedCornerShape(12.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(prog.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isNow) Badge("ON AIR", EnktelLive)
                if (canCatchup) Badge("CATCH-UP", EnktelOk)
            }
            Text(
                "${channel.name} · ${TimeFormat.format("EEE d MMM HH:mm", prog.startMs)}–${TimeFormat.format("HH:mm", prog.endMs)}",
                color = EnktelTextDim, fontSize = 12.sp,
            )
            if (prog.desc.isNotBlank()) {
                Text(prog.desc, color = Color.White.copy(0.85f), fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            if (isNow) FocusButton("▶ Watch live", accent = true, onClick = onWatchLive, modifier = Modifier.fillMaxWidth())
            if (canCatchup) FocusButton("⏪ Play from start (catch-up)", onClick = onCatchup, modifier = Modifier.fillMaxWidth())
            if (isFuture || isNow) FocusButton("● Record${if (isFuture) " (scheduled)" else ""}", onClick = onRecord, modifier = Modifier.fillMaxWidth())
            FocusButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Live preview plus the highlighted programme's detail, docked above the grid.
 *
 * The preview binds the shared [tv.enktel.app.player.PlaybackSession] surface
 * rather than starting a second player. There is only ever one ExoPlayer in
 * the process and only one view may hold its surface, so the session hands it
 * between whoever is on screen — the fullscreen player, the floating mini
 * window, and now this. Binding here takes the picture over while the guide is
 * open and `unbind` returns it on the way out; starting an independent player
 * would double the decode load and fight the dock for the same stream.
 *
 * When nothing is playing there is no surface to show, so the panel falls back
 * to the highlighted channel's own artwork.
 */
// PlaybackSession is @UnstableApi (it owns an ExoPlayer), and lint flags the
// property read, not just the construction — the same rule that caught
// AppGraph.playback during the AGP 9 move. Opting in here rather than
// baselining: this really is the thing consuming media3's unstable surface.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun GuideDock(
    graph: AppGraph,
    highlighted: Pair<Channel, EpgProgram>?,
    playlistName: String,
    modifier: Modifier = Modifier,
) {
    val nowPlaying by graph.playback.now.collectAsStateWithLifecycle()
    val ch = highlighted?.first
    val prog = highlighted?.second

    // Claim the picture for as long as this dock is composed, so the floating
    // mini window does not draw a second copy over the corner of the guide.
    // Released on the way out, which hands the surface back to the mini window
    // for whatever screen comes next.
    DisposableEffect(Unit) {
        graph.playback.setInlinePreview(true)
        onDispose { graph.playback.setInlinePreview(false) }
    }
    Row(modifier.fillMaxWidth().height(184.dp)) {
        Box(
            Modifier
                .width(320.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (nowPlaying != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setKeepContentOnPlayerReset(true)
                        }
                    },
                    update = { view -> graph.playback.bind(view) },
                    onRelease = { view -> graph.playback.unbind(view) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (ch != null && ch.logo.isNotBlank()) {
                AsyncImage(
                    model = ch.logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(34.dp),
                )
            } else {
                Text("Nothing playing", color = EnktelTextDim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (prog == null || ch == null) {
                Text(
                    "Move through the guide to see programme details here.",
                    color = EnktelTextDim, fontSize = 13.sp,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prog.title, color = Color.White, fontSize = 23.sp,
                    fontWeight = FontWeight.Black, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    TimeFormat.now("HH:mm") + "  |  " + TimeFormat.now("EEE, d MMM yyyy"),
                    color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${TimeFormat.format("HH:mm", prog.startMs)} – ${TimeFormat.format("HH:mm", prog.endMs)}" +
                    "   ·   ${ch.name}" +
                    if (ch.categoryName.isNotBlank()) "   ·   $playlistName, Group: ${ch.categoryName}" else "",
                color = EnktelTextDim, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            // Progress only means anything for something currently airing;
            // on a programme three days out a half-full bar would be a lie.
            val nowMs = System.currentTimeMillis()
            if (prog.startMs <= nowMs && prog.endMs > nowMs) {
                val frac = ((nowMs - prog.startMs).toFloat() /
                    (prog.endMs - prog.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(260.dp).height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(0.18f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(frac).height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(EnktelBlue),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${((prog.endMs - nowMs) / 60_000).coerceAtLeast(0)} Minutes Left",
                        color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(9.dp))
            }
            if (prog.desc.isNotBlank()) {
                Text(
                    prog.desc, color = Color.White.copy(0.78f), fontSize = 12.5.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
