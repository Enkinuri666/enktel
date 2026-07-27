package tv.enktel.app.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.flow.first
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.repo.SportsEvent
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.dvr.RecordScheduler
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SportsHubScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current

    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<Map<String, List<SportsEvent>>>(emptyMap()) }
    var sportFilter by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var teamFilterOn by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scoresEnabled by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)
    var liveScores by remember { mutableStateOf<List<tv.enktel.app.data.repo.LiveScore>>(emptyList()) }
    val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(refreshTick, sportFilter) {
        loading = true; loadError = null
        try {
            events = graph.sports.load(p.id, sportFilter.orEmpty())
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (t: Throwable) {
            loadError = t.message ?: "Could not load sports events"
            events = emptyMap()
        }
        loading = false
    }
    LaunchedEffect(scoresEnabled, refreshTick) {
        liveScores = try { if (scoresEnabled) graph.scores.live() else emptyList() }
        catch (ce: kotlinx.coroutines.CancellationException) { throw ce }
        catch (_: Throwable) { emptyList() }
    }

    val allSports = remember(events) {
        try { graph.sports.sportsInSet(events) } catch (_: Throwable) { emptyList() }
    }
    fun matchesTeam(ev: SportsEvent): Boolean =
        try { followed.any { it.name in ev.title.lowercase() } } catch (_: Throwable) { false }
    val live = events["LIVE"].orEmpty().let { if (teamFilterOn) it.filter(::matchesTeam) else it }
    val upcoming = events["UPCOMING"].orEmpty().let { if (teamFilterOn) it.filter(::matchesTeam) else it }
    val finished = events["FINISHED"].orEmpty().let { if (teamFilterOn) it.filter(::matchesTeam) else it }

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val padHoriz = if (isMobile) 16.dp else 48.dp

    LazyColumn(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(EnktelSurface.copy(0.4f), EnktelSurface.copy(0.0f)))
        ),
        contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            GlassCard(padHoriz = padHoriz) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sports Hub", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge("● ${live.size} LIVE", EnktelLive)
                            Badge("${upcoming.size} UPCOMING", EnktelBlue)
                            Badge("${finished.size} REPLAYS", EnktelOk)
                        }
                    }
                    if (followed.isNotEmpty()) {
                        FocusButton(
                            if (teamFilterOn) "★ mine" else "★",
                            accent = teamFilterOn,
                            onClick = { teamFilterOn = !teamFilterOn },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    FocusButton("↻", onClick = { refreshTick++ })
                }
            }
        }
        if (allSports.isNotEmpty()) {
            item {
                tv.enktel.app.ui.components.ChipRowLabel(
                    "Sport",
                    modifier = Modifier.padding(start = padHoriz, bottom = 4.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        tv.enktel.app.ui.components.GlassChip(
                            "All", selected = sportFilter == null,
                            accent = EnktelBlue,
                            onClick = { sportFilter = null },
                        )
                    }
                    items(allSports, key = { it }) { sport ->
                        tv.enktel.app.ui.components.GlassChip(
                            sport, selected = sportFilter == sport,
                            accent = EnktelBlue,
                            onClick = { sportFilter = if (sportFilter == sport) null else sport },
                        )
                    }
                }
            }
        }
        when {
            loadError != null -> item {
                GlassCard(padHoriz = padHoriz) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Couldn't load sports", color = EnktelLive, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(loadError!!, color = EnktelTextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        FocusButton("Try again", accent = true, onClick = { refreshTick++ })
                    }
                }
            }
            loading && events.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("Scanning EPG for sports events…", color = EnktelTextDim, fontSize = 14.sp)
                }
            }
            live.isEmpty() && upcoming.isEmpty() && finished.isEmpty() -> item {
                GlassCard(padHoriz = padHoriz) {
                    Column(Modifier.padding(20.dp)) {
                        Text("No sports events found yet.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "If you just added the playlist, wait for the EPG download to finish, or refresh it manually.",
                            color = EnktelTextDim, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusButton("Refresh EPG", accent = true, onClick = {
                                scope.launch {
                                    try { graph.epg.refresh(p) } catch (_: Throwable) {}
                                    refreshTick++
                                }
                            })
                            FocusButton("Rescan", onClick = { refreshTick++ })
                        }
                    }
                }
            }
        }
        // Live-scores ticker: shows every match currently in progress across all channels,
        // even ones our EPG scan missed. Tap → jump straight to whichever channel is
        // carrying that match if we recognise the team names in a live channel title.
        if (scoresEnabled && liveScores.isNotEmpty()) {
            item { SectionHeader("⚡ LIVE SCORES", EnktelOk, padHoriz) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(liveScores, key = { "${it.home}-${it.away}-${it.league}" }) { s ->
                        LiveScoreChip(
                            score = s,
                            onTap = {
                                // Fuzzy channel match by team names appearing in channel titles.
                                scope.launch {
                                    val matchChannel = try {
                                        graph.content.channels(p.id).first().firstOrNull { ch ->
                                            val title = ch.name.lowercase()
                                            s.home.lowercase() in title || s.away.lowercase() in title
                                        }
                                    } catch (_: Throwable) { null }
                                    if (matchChannel != null) {
                                        toaster.info("Tuning to ${matchChannel.name}")
                                        nav.navigate("live?ch=${matchChannel.key}")
                                    } else {
                                        toaster.info("No live channel matched for that match")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        if (live.isNotEmpty()) {
            item { SectionHeader("🔴 LIVE NOW", EnktelLive, padHoriz) }
            items(live, key = { "L-${it.channel.key}-${it.program.id}" }) { ev ->
                val matchedScore = try { if (scoresEnabled) graph.scores.matchByTitle(ev.title, liveScores) else null } catch (_: Throwable) { null }
                LiveEventCard(ev, score = matchedScore, padHoriz = padHoriz, onTap = {
                    toaster.info("Tuning to ${ev.channel.name}")
                    nav.navigate("live?ch=${ev.channel.key}")
                })
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("📅 UPCOMING", EnktelBlue, padHoriz) }
            items(upcoming, key = { "U-${it.channel.key}-${it.program.id}" }) { ev ->
                UpcomingEventCard(
                    ev, padHoriz = padHoriz,
                    onSchedule = {
                        scope.launch {
                            try {
                                val url = if (p.kind == "m3u") ev.channel.url
                                    else XtreamClient.liveUrl(p, ev.channel.streamId, hls = false)
                                RecordScheduler.schedule(context, p.id, ev.title, ev.channel.name, url, ev.startMs, ev.endMs)
                                toaster.success("Recording scheduled")
                            } catch (t: Throwable) { toaster.error(t.message ?: "Schedule failed") }
                        }
                    },
                    onRemind = {
                        scope.launch {
                            try {
                                tv.enktel.app.dvr.MatchReminderScheduler.schedule(
                                    context, ev.channel.key, ev.channel.name, ev.title, ev.startMs, ev.endMs,
                                )
                                toaster.success("Reminder set")
                            } catch (t: Throwable) { toaster.error(t.message ?: "Reminder failed") }
                        }
                    },
                    onOpen = { nav.navigate("live?ch=${ev.channel.key}") },
                )
            }
        }
        // Split "finished" into fresh Highlights (last 6h — likely still trending) and
        // older Replays (>6h ago). Same tap behaviour: pull from catch-up if the channel
        // supports it.
        val nowMs = System.currentTimeMillis()
        val highlightWindow = 6 * 60 * 60_000L
        val highlights = finished.filter { nowMs - it.endMs <= highlightWindow }
        val olderReplays = finished.filter { nowMs - it.endMs > highlightWindow }
        if (highlights.isNotEmpty()) {
            item { SectionHeader("🎬 HIGHLIGHTS — LAST 6 HOURS", EnktelOk, padHoriz) }
            items(highlights, key = { "H-${it.channel.key}-${it.program.id}" }) { ev ->
                FinishedEventCard(
                    ev, padHoriz = padHoriz,
                    onReplay = {
                        if (ev.channel.hasArchive && p.kind == "xtream") {
                            val mins = (ev.endMs - ev.startMs) / 60_000
                            val url = XtreamClient.timeshiftUrl(p, ev.channel.streamId, ev.startMs, mins)
                            nav.navigate(vodPlayerRoute(url, "${ev.channel.name} · ${ev.title}"))
                        } else toaster.error("No catch-up archive")
                    },
                )
            }
        }
        if (olderReplays.isNotEmpty()) {
            item { SectionHeader("📼 REPLAYS", EnktelBlue, padHoriz) }
            items(olderReplays, key = { "F-${it.channel.key}-${it.program.id}" }) { ev ->
                FinishedEventCard(
                    ev, padHoriz = padHoriz,
                    onReplay = {
                        if (ev.channel.hasArchive && p.kind == "xtream") {
                            val mins = (ev.endMs - ev.startMs) / 60_000
                            val url = XtreamClient.timeshiftUrl(p, ev.channel.streamId, ev.startMs, mins)
                            nav.navigate(vodPlayerRoute(url, "${ev.channel.name} · ${ev.title}"))
                        } else toaster.error("No catch-up archive")
                    },
                )
            }
        }
    }
}

@Composable
private fun GlassCard(padHoriz: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = padHoriz)
            .clip(RoundedCornerShape(14.dp))
            .background(EnktelSurface.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun SectionHeader(text: String, color: Color, padHoriz: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = padHoriz, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 20.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiveEventCard(
    ev: SportsEvent,
    score: tv.enktel.app.data.repo.LiveScore?,
    padHoriz: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val frac = ((now - ev.startMs).toFloat() / (ev.endMs - ev.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    Surface(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz).tapClick(onTap),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelLive.copy(0.14f),
            focusedContainerColor = EnktelLive.copy(0.4f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ev.channel.logo, ev.channel.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge("● LIVE", EnktelLive)
                        Badge(ev.sport, EnktelBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(ev.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(ev.channel.name, color = EnktelTextDim, fontSize = 11.sp)
                }
                Text("▶", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            if (score != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${score.home} ${score.homeScore} — ${score.awayScore} ${score.away}   ${score.minute}",
                    color = EnktelOk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBarThin(frac, Modifier.fillMaxWidth())
            Text(
                "${hhmm(ev.startMs)} — ${hhmm(ev.endMs)}  ·  ${(now - ev.startMs) / 60_000}m in",
                color = EnktelTextDim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun UpcomingEventCard(
    ev: SportsEvent,
    padHoriz: androidx.compose.ui.unit.Dp,
    onSchedule: () -> Unit,
    onRemind: () -> Unit,
    onOpen: () -> Unit,
) {
    val inMs = ev.startMs - System.currentTimeMillis()
    val eta = when {
        inMs < 60 * 60_000L -> "in ${(inMs / 60_000L).coerceAtLeast(0)}m"
        inMs < 24 * 3600_000L -> "in ${inMs / 3600_000L}h ${inMs / 60_000L % 60}m"
        else -> "in ${inMs / 86_400_000L}d"
    }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz).tapClick(onOpen),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue.copy(0.4f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(ev.sport, EnktelBlue)
                    Badge(eta, EnktelBlue)
                }
                Spacer(Modifier.height(3.dp))
                Text(ev.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ev.channel.name}  ·  ${SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(ev.startMs))}",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                FocusButton("🔔", onClick = onRemind)
                Spacer(Modifier.height(4.dp))
                FocusButton("●", onClick = onSchedule)
            }
        }
    }
}

@Composable
private fun FinishedEventCard(ev: SportsEvent, padHoriz: androidx.compose.ui.unit.Dp, onReplay: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Surface(
        onClick = onReplay,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz).tapClick(onReplay),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface.copy(0.5f),
            focusedContainerColor = EnktelOk.copy(0.4f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge("FT", EnktelOk)
                    Badge(ev.sport, EnktelTextDim)
                    if (ev.channel.hasArchive) Badge("CATCH-UP", EnktelOk)
                }
                Spacer(Modifier.height(3.dp))
                Text(ev.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ev.channel.name} · ${SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(ev.startMs))}",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                // Highlights link-out — YouTube search intent using the event
                // title + "highlights". Free, keyless, and opens the native
                // YouTube app on TV / phones when available.
                FocusButton("🎬 Highlights", onClick = {
                    val query = "${ev.title} highlights"
                    val webUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                    runCatching { ctx.startActivity(intent) }
                })
            }
            Text(
                if (ev.channel.hasArchive) "⏪" else "—",
                color = if (ev.channel.hasArchive) EnktelOk else EnktelTextDim,
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Compact live-score chip for the top-of-Sports ticker: HOME 2 - 1 AWAY · 78'. */
@Composable
private fun LiveScoreChip(score: tv.enktel.app.data.repo.LiveScore, onTap: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(EnktelSurface.copy(0.7f))
            .border(1.dp, EnktelOk.copy(0.5f), RoundedCornerShape(20.dp))
            .tapClick(onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pulsing live dot so the eye finds live scores instantly in the strip.
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(EnktelLive),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            score.home, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${score.homeScore}", color = EnktelOk, fontSize = 15.sp, fontWeight = FontWeight.Black,
        )
        Text(" – ", color = EnktelTextDim, fontSize = 12.sp)
        Text(
            "${score.awayScore}", color = EnktelOk, fontSize = 15.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            score.away, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
        )
        if (score.minute.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(score.minute, color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChannelLogo(url: String, fallback: String) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(EnktelSurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text(fallback.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

private fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
