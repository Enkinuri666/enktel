package tv.enktel.app.ui.sports

import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.mutableIntStateOf
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
import tv.enktel.app.data.TimeFormat
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
    var refreshTick by remember { mutableIntStateOf(0) }
    var teamFilterOn by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scoresEnabled by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)
    val matchCenterEnabled by graph.settings.matchCenterEnabled.collectAsStateWithLifecycle(initialValue = true)
    var liveScores by remember { mutableStateOf<List<tv.enktel.app.data.repo.LiveScore>>(emptyList()) }
    val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())
    // Official broadcast schedule + published highlight packages. Both come
    // from TheSportsDB and are independent of the user's EPG, so they still
    // populate on a playlist with no guide data at all.
    var todaysFixtures by remember { mutableStateOf<List<tv.enktel.app.data.repo.LiveScore>>(emptyList()) }
    var highlightClips by remember { mutableStateOf<List<tv.enktel.app.data.repo.HighlightClip>>(emptyList()) }

    var scanCoverage by remember {
        mutableStateOf(tv.enktel.app.data.repo.SportsRepository.ScanCoverage())
    }
    LaunchedEffect(refreshTick, sportFilter) {
        loading = true; loadError = null
        try {
            events = graph.sports.load(p.id, sportFilter.orEmpty())
            scanCoverage = graph.sports.lastScan
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (t: Throwable) {
            loadError = t.message ?: "Could not load sports events"
            events = emptyMap()
        }
        loading = false
    }
    var scoresStatus by remember { mutableStateOf("") }
    LaunchedEffect(scoresEnabled, refreshTick) {
        liveScores = try { if (scoresEnabled) graph.scores.live() else emptyList() }
        catch (ce: kotlinx.coroutines.CancellationException) { throw ce }
        catch (_: Throwable) { emptyList() }
        // An empty scoreboard and an unreachable one looked identical, so
        // "live scores are on and I see no difference" had no explanation
        // anywhere in the UI. Say which it is.
        scoresStatus = if (scoresEnabled && liveScores.isEmpty()) graph.scores.lastStatus else ""
    }
    LaunchedEffect(matchCenterEnabled, sportFilter, refreshTick) {
        if (!matchCenterEnabled) {
            todaysFixtures = emptyList(); highlightClips = emptyList()
            return@LaunchedEffect
        }
        val sportQuery = sportFilter?.let { sportsDbName(it) }.orEmpty()
        todaysFixtures = try {
            graph.scores.scheduleForDay(System.currentTimeMillis(), sportQuery)
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (_: Throwable) { emptyList() }
        highlightClips = try {
            graph.scores.highlights(days = 2, sport = sportQuery)
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (_: Throwable) { emptyList() }
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
                    // Smart Channel Finder: the "it kicks off in two minutes and
                    // I can't find the channel" escape hatch.
                    FocusButton("🔎 On now", accent = true, onClick = { nav.navigate("sportsFinder") })
                    Spacer(Modifier.width(6.dp))
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
        // Say so when the scan didn't cover the whole playlist, rather than
        // letting a truncated list read as "your match isn't on". Picking a
        // sport narrows the scan instead of truncating it, so that's the
        // actionable advice.
        if (scanCoverage.truncated && loadError == null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = padHoriz, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            append("Showing a partial scan")
                            if (scanCoverage.channelsMatched > scanCoverage.channelsScanned) {
                                append(" — ${scanCoverage.channelsScanned} of ")
                                append("${scanCoverage.channelsMatched} sports channels")
                            }
                            append(". Pick a sport to search the rest.")
                        },
                        color = EnktelTextDim, fontSize = 11.sp,
                    )
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
        if (scoresEnabled && liveScores.isEmpty() && scoresStatus.isNotBlank()) {
            item {
                Text(
                    scoresStatus,
                    color = EnktelTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = padHoriz, vertical = 6.dp),
                )
            }
        }
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
                            // A scoreline we can identify opens the Match Centre;
                            // one we can't still tunes to a matching channel.
                            onStats = s.eventId.takeIf { it.isNotBlank() && matchCenterEnabled }?.let { id ->
                                { nav.navigate(matchCenterRoute(id, "${s.home} v ${s.away}")) }
                            },
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
        // Official broadcast guide — the published schedule for today, straight
        // from TheSportsDB rather than the user's EPG. It answers "what's on and
        // where do I tune in" even for fixtures the playlist doesn't carry, and
        // each row opens the Match Centre where the broadcaster list lives.
        if (matchCenterEnabled && todaysFixtures.isNotEmpty()) {
            item { SectionHeader("📡 OFFICIAL SCHEDULE — TODAY", EnktelBlue, padHoriz) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        todaysFixtures.take(40),
                        key = { "${it.eventId}-${it.home}-${it.away}" },
                    ) { f ->
                        FixtureChip(
                            fixture = f,
                            onTap = {
                                if (f.eventId.isNotBlank()) {
                                    nav.navigate(matchCenterRoute(f.eventId, "${f.home} v ${f.away}"))
                                } else {
                                    toaster.info("No match data published for that fixture yet")
                                }
                            },
                        )
                    }
                }
            }
        }
        // Published highlight packages for fixtures that have already finished.
        if (matchCenterEnabled && highlightClips.isNotEmpty()) {
            item { SectionHeader("🎞 HIGHLIGHTS — LATEST PACKAGES", EnktelOk, padHoriz) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(highlightClips.take(30), key = { it.videoUrl }) { clip ->
                        HighlightCard(
                            clip = clip,
                            onPlay = { openHighlight(context, nav, toaster, clip.videoUrl, clip.title) },
                        )
                    }
                }
            }
        }
        if (live.isNotEmpty()) {
            item { SectionHeader("🔴 LIVE NOW", EnktelLive, padHoriz) }
            items(live, key = { "L-${it.channel.key}-${it.program.id}" }) { ev ->
                val matchedScore = try { if (scoresEnabled) graph.scores.matchByTitle(ev.title, liveScores) else null } catch (_: Throwable) { null }
                LiveEventCard(
                    ev, score = matchedScore, padHoriz = padHoriz,
                    onTap = {
                        toaster.info("Tuning to ${ev.channel.name}")
                        nav.navigate("live?ch=${ev.channel.key}")
                    },
                    // Only offer the Match Centre when we actually resolved this
                    // programme to a real fixture — an empty stats screen is
                    // worse than no button.
                    onStats = matchedScore?.eventId
                        ?.takeIf { it.isNotBlank() && matchCenterEnabled }
                        ?.let { id -> { nav.navigate(matchCenterRoute(id, ev.title)) } },
                )
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
            item { SectionHeader("⏪ CATCH-UP — FINISHED IN THE LAST 6 HOURS", EnktelOk, padHoriz) }
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
    onStats: (() -> Unit)? = null,
) {
    val now = System.currentTimeMillis()
    val frac = ((now - ev.startMs).toFloat() / (ev.endMs - ev.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    Surface(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz).tapClick(onTap),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        // Was EnktelLive.copy(0.14f) — a 14 % red wash over the near-black
        // background, which rendered as muddy maroon on a phone and made a row
        // of live fixtures look like a row of error states. Live-ness is now
        // carried by the accent stripe and the ● LIVE badge, against the same
        // neutral elevated surface every other card in the app uses, so the
        // red reads as signal instead of tinting the whole panel.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.45f),
            focusedContainerColor = EnktelLive.copy(0.32f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left accent stripe — the live marker, matching the section
                // headers' stripe so the visual language stays consistent.
                Box(
                    Modifier
                        .size(width = 3.dp, height = 44.dp)
                        .background(EnktelLive, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(10.dp))
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
                if (onStats != null) {
                    FocusButton("📊", onClick = onStats)
                    Spacer(Modifier.width(8.dp))
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
                    "${ev.channel.name}  ·  ${TimeFormat.format("EEE d MMM · HH:mm", ev.startMs)}",
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
                    "${ev.channel.name} · ${TimeFormat.format("EEE d MMM HH:mm", ev.startMs)}",
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

/** Small team crest. Silently absent when the API has no badge for a side. */
@Composable
private fun TeamCrest(url: String) {
    if (url.isBlank()) return
    coil.compose.AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(18.dp).padding(end = 5.dp),
    )
}

/**
 * Compact live-score chip for the Sports ticker.
 *
 * Renders three distinct states — in play, not yet kicked off, finished — so
 * the live ones are findable at a glance rather than uniform with the rest.
 */
@Composable
private fun LiveScoreChip(
    score: tv.enktel.app.data.repo.LiveScore,
    onTap: () -> Unit,
    onStats: (() -> Unit)? = null,
) {
    // The strip carries three different things — matches in play, kick-offs
    // still to come, and finished results. Painting them identically made the
    // live ones impossible to pick out, which defeats the point of a live
    // ticker. Colour and the pulse are reserved for genuinely in-play games.
    val accent = when {
        score.inPlay -> EnktelOk
        score.notStarted -> EnktelBlue
        else -> EnktelTextDim
    }

    // Slow breathing pulse on the live dot. Only for in-play: a blinking dot
    // on a finished match is a lie.
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "livePulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable<Float>(
                androidx.compose.animation.core.tween(900),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "livePulseAlpha",
        )

    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(EnktelSurface.copy(0.7f))
            .border(1.dp, accent.copy(0.5f), RoundedCornerShape(20.dp))
            .tapClick(onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.copy(alpha = if (score.inPlay) pulse else 1f)),
        )
        Spacer(Modifier.width(8.dp))
        TeamCrest(score.homeBadge)
        Text(
            score.home, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (score.notStarted) {
            // No score exists yet; "– – –" reads as a 0-0 draw, which is wrong.
            Text("vs", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(score.homeScore, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(" – ", color = EnktelTextDim, fontSize = 12.sp)
            Text(score.awayScore, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(8.dp))
        TeamCrest(score.awayBadge)
        Text(
            score.away, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
        )
        if (score.minute.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                if (score.notStarted) "⏱ ${score.minute}" else score.minute,
                color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
        } else if (score.finished) {
            Spacer(Modifier.width(10.dp))
            Text("FT", color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (onStats != null) {
            Spacer(Modifier.width(10.dp))
            FocusButton("📊", onClick = onStats)
        }
    }
}

/**
 * One fixture from the official schedule. Deliberately compact — this row is a
 * "what's on today" strip, and the detail lives one tap away in the Match
 * Centre alongside the broadcaster list.
 */
@Composable
private fun FixtureChip(fixture: tv.enktel.app.data.repo.LiveScore, onTap: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 150.dp, max = 210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurfaceHigh.copy(0.5f))
            .border(1.dp, EnktelBlue.copy(0.3f), RoundedCornerShape(12.dp))
            .tapClick(onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fixture.minute.isNotBlank()) Badge(fixture.minute, EnktelBlue)
            if (fixture.sport.isNotBlank()) Badge(fixture.sport, EnktelTextDim)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (fixture.away.isBlank()) fixture.home else "${fixture.home} v ${fixture.away}",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (fixture.league.isNotBlank()) {
            Text(
                fixture.league, color = EnktelTextDim, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A published highlights package: thumbnail, fixture, and a play affordance. */
@Composable
private fun HighlightCard(clip: tv.enktel.app.data.repo.HighlightClip, onPlay: () -> Unit) {
    Column(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurface.copy(0.6f))
            .tapClick(onPlay),
    ) {
        Box(
            Modifier.fillMaxWidth().height(124.dp).background(EnktelSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (clip.thumb.isNotBlank()) {
                AsyncImage(
                    model = clip.thumb, contentDescription = clip.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text("▶", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                clip.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(clip.league, clip.sport).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = EnktelTextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Maps our internal sport tags onto the names TheSportsDB uses in its `s=`
 * query parameter. Anything we don't have a mapping for returns blank, which
 * the API reads as "all sports" — a wider result set is a better failure than
 * an empty one.
 */
private fun sportsDbName(sport: String): String = when (sport) {
    "Football" -> "Soccer"
    "American Football" -> "American Football"
    "Basketball" -> "Basketball"
    "Baseball" -> "Baseball"
    "Hockey" -> "Ice Hockey"
    "MMA/Boxing", "Combat" -> "Fighting"
    "Tennis" -> "Tennis"
    "Cricket" -> "Cricket"
    "Motor Racing" -> "Motorsport"
    "Cycling" -> "Cycling"
    "Golf" -> "Golf"
    "Rugby" -> "Rugby"
    "Volleyball" -> "Volleyball"
    "Handball" -> "Handball"
    "Esports" -> "ESports"
    else -> ""
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

private fun hhmm(ms: Long): String = TimeFormat.format("HH:mm", ms)
