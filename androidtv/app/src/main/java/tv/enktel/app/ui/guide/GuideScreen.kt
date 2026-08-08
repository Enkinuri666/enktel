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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
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
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextFaint
import tv.enktel.app.vodPlayerRoute
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import tv.enktel.app.ui.components.tvRailFocus

private val HOUR = 3_600_000L
private val DP_PER_HOUR = 220.dp

/**
 * A faint genre wash behind each programme cell.
 *
 * The grid was one flat surface colour for every cell, so a wall of a hundred
 * programmes carried no information until you read each title. A tint lets the
 * eye find the sport or the film before the text resolves — and at these
 * alphas it stays subordinate to the live/past/future states, which are what
 * actually matter.
 *
 * Derived from the *channel's* category, not the programme's: EpgProgram has no
 * category column, and adding one means an XMLTV parser change plus a schema
 * migration. The channel group is a decent proxy on an IPTV line — a channel in
 * "Sports" is showing sport — and it is available today with no migration. If
 * per-programme genres are wanted later, this function is where they plug in.
 *
 * Anything unrecognised gets no tint at all, rather than a wrong one.
 */
internal fun genreTint(category: String): Color? {
    val c = category.lowercase()
    return when {
        c.isBlank() -> null
        "sport" in c || "football" in c || "soccer" in c -> Color(0xFF00E5A0)
        "news" in c || "weather" in c || "current affairs" in c -> Color(0xFF29B6FF)
        "movie" in c || "film" in c || "cinema" in c -> Color(0xFFB14DFF)
        "kid" in c || "child" in c || "cartoon" in c || "animation" in c -> Color(0xFFFFC44D)
        "music" in c || "concert" in c -> Color(0xFFFF6FD8)
        "document" in c || "nature" in c || "science" in c || "history" in c -> Color(0xFF7FD1AE)
        "comedy" in c || "sitcom" in c -> Color(0xFFFFA24D)
        "drama" in c || "series" in c || "soap" in c -> Color(0xFF6E8BFF)
        else -> null
    }
}

@Composable
fun GuideScreen(graph: AppGraph, nav: NavHostController) {
    // Narrow phone viewport (Galaxy S25 Ultra portrait, standard 6" phones): a
    // seven-day multi-column grid is unusable at that width.  Bounce to a
    // single-channel vertical timeline so scrolling stays 1-dimensional.
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val isNarrow = shape.narrow
    if (isNarrow) {
        MobileGuideScreen(graph, nav)
        return
    }
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current

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

    Column(Modifier.fillMaxSize().padding(top = shape.padV)) {
        Row(
            Modifier.padding(horizontal = shape.padH),
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
        Spacer(Modifier.height(shape.headerGap))
        // Quick-jump chip strip: Today, +1, +2, ... makes navigating a week's worth of EPG
        // a single click instead of six D-pad presses.
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.tvRailFocus(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = shape.padH),
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
            modifier = Modifier.padding(horizontal = shape.padH),
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
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(6.dp))
                                    .background(EnktelSurfaceHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (ch.logo.isNotBlank()) {
                                    AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp))
                                } else {
                                    Text(
                                        ch.name.take(2).uppercase(), fontSize = 11.sp,
                                        fontWeight = FontWeight.Black, color = EnktelTextDim,
                                    )
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                // The channel number was nowhere in the guide,
                                // so the one identifier people actually navigate
                                // by — and type on the remote — was missing from
                                // the screen built for navigating.
                                if (ch.num > 0) {
                                    Text(
                                        "${ch.num}", fontSize = 11.sp, fontWeight = FontWeight.Black,
                                        color = EnktelBlue, letterSpacing = 0.6.sp,
                                    )
                                }
                                Text(
                                    ch.name, fontSize = 14.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp,
                                )
                            }
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
                                // Three states, not two.
                                //
                                // The grid drew everything the same except for
                                // a tint on the live cell, so a programme that
                                // finished four hours ago was as visually loud
                                // as one starting next — the eye had nothing to
                                // anchor to and the whole grid read as an
                                // undifferentiated wall of grey rectangles.
                                val isPast = prog.endMs <= now
                                Surface(
                                    onClick = { selected = ch to prog },
                                    modifier = Modifier.width(w - 3.dp).fillMaxSize().padding(end = 3.dp)
                                        .onFocusChanged { if (it.isFocused) highlighted = ch to prog }
                                        .tapClick { selected = ch to prog },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(7.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = when {
                                            isNow -> EnktelBlue.copy(0.30f)
                                            isPast -> EnktelSurface.copy(0.55f)
                                            // A whole grid of one surface colour
                                            // carried no information until you
                                            // read every title. The wash is
                                            // deliberately faint: it must stay
                                            // subordinate to live/past/future,
                                            // which are the states that matter.
                                            else -> genreTint(ch.categoryName)
                                                ?.copy(alpha = 0.13f)
                                                ?: EnktelSurfaceHigh
                                        },
                                        focusedContainerColor = EnktelBlue,
                                        focusedContentColor = Color.White,
                                        contentColor = if (isPast) EnktelTextDim else Color.White,
                                    ),
                                    // Full-width-ish cells have nowhere to grow;
                                    // scaling one inside a tight grid overlaps
                                    // its neighbours. Focus is the fill.
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                ) {
                                    Row(Modifier.fillMaxSize()) {
                                        // Live marker on the leading edge, so
                                        // "what's on now" is findable by shape
                                        // rather than by reading every cell.
                                        if (isNow) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight()
                                                    .background(EnktelLive),
                                            )
                                        }
                                        Column(
                                            Modifier.padding(
                                                start = if (isNow) 7.dp else 9.dp,
                                                end = 6.dp, top = 6.dp, bottom = 6.dp,
                                            ),
                                        ) {
                                            Text(
                                                // 14 sp, not 12. A viewer is
                                                // three metres away; 12 sp on a
                                                // 540 dp-tall layout is about
                                                // 8 pt at that distance, and
                                                // washes out entirely on the
                                                // cheaper panels this runs on.
                                                prog.title, fontSize = 14.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                            )
                                            // A 15-minute programme is ~55 dp
                                            // wide. Both lines used to render
                                            // regardless, so short cells showed
                                            // two clipped fragments stacked on
                                            // each other and read as noise.
                                            if (w >= 96.dp) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    "${TimeFormat.format("HH:mm", prog.startMs)} – ${TimeFormat.format("HH:mm", prog.endMs)}",
                                                    fontSize = 12.sp,
                                                    color = if (isPast) EnktelTextFaint else EnktelTextDim,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
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
                // Was a single guessed Xtream timeshift URL — the last copy of
                // the bug fixed everywhere else. Goes through the resolver now,
                // which walks the shapes the panel might serve and says so when
                // none of them answer.
                scope.launch {
                    val url = tv.enktel.app.data.catchup.CatchupUrls.resolve(
                        graph.http, p, ch, prog.startMs, prog.endMs,
                    )
                    if (url == null) {
                        toaster.error("The provider has no recording of that programme")
                    } else {
                        nav.navigate(vodPlayerRoute(url, "${ch.name} · ${prog.title}"))
                    }
                }
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
    // The dock is a fixed 184 dp tall over a grid that also wants room. On a
    // landscape phone that is half the viewport spent on a preview, leaving
    // two channel rows underneath — the "docked TV guide is cramped" report.
    // It scales with the height available instead.
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val dockHeight = if (shape.short) 116.dp else 184.dp
    val previewWidth = if (shape.short) 202.dp else 320.dp
    Row(modifier.fillMaxWidth().height(dockHeight)) {
        Box(
            Modifier
                .width(previewWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val engine = graph.playback.engineOrNull()
            if (nowPlaying != null && engine != null) {
                // First migrated surface that shares the session's engine.
                //
                // No bind/unbind here any more. Those exist because a
                // PlayerView has to be handed the player explicitly, and two
                // hosts mounting in an unpredictable order could leave the
                // wrong one holding it. PlayerSurface attaches and detaches
                // with the composition instead, so the ordering problem the
                // hand-off was solving does not arise for this host.
                //
                // What still matters is that only one surface targets the
                // engine at a time — two would fight and one would go black.
                // That is already guaranteed: this dock claims inlinePreview
                // for as long as it is composed (see the DisposableEffect
                // above) and the floating mini window stands down while
                // anything is claiming.
                //
                // TEXTURE_VIEW, despite this being a single static panel where
                // SURFACE_VIEW would be cheaper: the dock is clipped to a
                // 10 dp rounded rectangle, and a SurfaceView is a separate
                // window layer that ignores its parent's clip. It would draw
                // square corners over a rounded frame. A TextureView is an
                // ordinary view and clips normally.
                //
                // ContentFrame rather than a bare PlayerSurface because the
                // PlayerView this replaces letterboxed (RESIZE_MODE_FIT, its
                // default) and kept the last frame across a player reset.
                // PlayerSurface on its own does neither: it fills whatever box
                // it is given, so a 4:3 channel would come out stretched, and
                // it goes black the moment the engine re-prepares. ContentFrame
                // is the composable that carries both behaviours.
                ContentFrame(
                    player = engine.player,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    contentScale = ContentScale.Fit,
                    keepContentOnReset = true,
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
