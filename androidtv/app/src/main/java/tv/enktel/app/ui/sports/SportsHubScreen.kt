package tv.enktel.app.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.components.ProgressBarThin
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
import java.util.Date
import java.util.Locale

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SportsHubScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current

    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<Map<String, List<SportsEvent>>>(emptyMap()) }
    var sportFilter by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var teamFilterOn by remember { mutableStateOf(false) }
    val scoresEnabled by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)
    var liveScores by remember { mutableStateOf<List<tv.enktel.app.data.repo.LiveScore>>(emptyList()) }
    val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())

    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refreshTick, sportFilter) {
        loading = true; loadError = null
        try {
            events = graph.sports.load(p.id, sportFilter.orEmpty())
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load sports events"
            events = emptyMap()
        }
        loading = false
    }
    LaunchedEffect(scoresEnabled, refreshTick) {
        liveScores = try { if (scoresEnabled) graph.scores.live() else emptyList() } catch (_: Exception) { emptyList() }
    }
    // Live view refreshes itself so LIVE/UPCOMING/FINISHED boundaries stay correct.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            refreshTick++
        }
    }

    val allSports = remember(events) { graph.sports.sportsInSet(events) }
    fun matchesTeam(ev: SportsEvent): Boolean =
        followed.any { it.name in ev.title.lowercase() }
    val live = events["LIVE"].orEmpty().let { if (teamFilterOn) it.filter(::matchesTeam) else it }
    val upcoming = events["UPCOMING"].orEmpty().let { if (teamFilterOn) it.filter(::matchesTeam) else it }
    val finished = events["FINISHED"].orEmpty().let { if (teamFilterOn) it.filter(::matchesTeam) else it }

    val padHoriz = if (tv.enktel.app.BuildConfig.FLAVOR == "mobile") 16.dp else 48.dp

    // Single LazyColumn as the root — nesting a LazyColumn inside a plain Column with
    // fillMaxSize causes intermittent crashes on some devices when items produce zero
    // height during measurement.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = padHoriz),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Sports Hub")
                Spacer(Modifier.width(14.dp))
                Badge("${live.size} LIVE", EnktelLive)
                Spacer(Modifier.width(6.dp))
                Badge("${upcoming.size} UP", EnktelBlue)
                Spacer(Modifier.width(6.dp))
                Badge("${finished.size} REPLAY", EnktelOk)
                Spacer(Modifier.weight(1f))
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
        if (allSports.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FocusButton("All", accent = sportFilter == null, onClick = { sportFilter = null }) }
                    items(allSports, key = { it }) { sport ->
                        FocusButton(sport, accent = sportFilter == sport, onClick = {
                            sportFilter = if (sportFilter == sport) null else sport
                        })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }

        when {
            loadError != null -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = padHoriz)) {
                    Text("Couldn't load sports: $loadError", color = EnktelLive, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    FocusButton("Try again", accent = true, onClick = { refreshTick++ })
                }
            }
            loading && events.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("Scanning EPG for sports events…", color = EnktelTextDim, fontSize = 14.sp)
                }
            }
            live.isEmpty() && upcoming.isEmpty() && finished.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = padHoriz, vertical = 20.dp)) {
                    Text("No sports events found in your EPG yet.", color = EnktelTextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("If you just added the playlist, wait for the EPG download to finish, or refresh manually below.", color = EnktelTextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusButton("Refresh EPG", accent = true, onClick = {
                            scope.launch {
                                try { graph.epg.refresh(p) } catch (_: Exception) {}
                                refreshTick++
                            }
                        })
                        FocusButton("Rescan", onClick = { refreshTick++ })
                    }
                }
            }
        }
            if (live.isNotEmpty()) {
                item { SectionHeader("🔴 LIVE NOW", EnktelLive) }
                items(live, key = { "L-${it.channel.key}-${it.program.id}" }) { ev ->
                    val matchedScore = if (scoresEnabled) graph.scores.matchByTitle(ev.title, liveScores) else null
                    LiveEventRow(ev, score = matchedScore, onTap = {
                        toaster.info("Tuning to ${ev.channel.name}")
                        nav.navigate("live?ch=${ev.channel.key}")
                    }, onFollow = { teamName ->
                        scope.launch {
                            graph.db.sportsDao().follow(
                                tv.enktel.app.data.db.FollowedTeam(
                                    name = teamName.lowercase(),
                                    displayName = teamName,
                                )
                            )
                            toaster.success("Following $teamName")
                        }
                    })
                }
            }
            if (upcoming.isNotEmpty()) {
                item { SectionHeader("📅 UPCOMING", EnktelBlue) }
                items(upcoming, key = { "U-${it.channel.key}-${it.program.id}" }) { ev ->
                    UpcomingEventRow(
                        ev,
                        onSchedule = {
                            scope.launch {
                                val url = if (p.kind == "m3u") ev.channel.url
                                    else XtreamClient.liveUrl(p, ev.channel.streamId, hls = false)
                                RecordScheduler.schedule(context, p.id, ev.title, ev.channel.name, url, ev.startMs, ev.endMs)
                                toaster.success("Recording scheduled: ${ev.title}")
                            }
                        },
                        onRemind = {
                            scope.launch {
                                tv.enktel.app.dvr.MatchReminderScheduler.schedule(
                                    context, ev.channel.key, ev.channel.name, ev.title, ev.startMs, ev.endMs,
                                )
                                toaster.success("Reminder set for ${ev.title}")
                            }
                        },
                        onOpen = { nav.navigate("live?ch=${ev.channel.key}") },
                    )
                }
            }
            if (finished.isNotEmpty()) {
                item { SectionHeader("✓ RESULTS & REPLAYS", EnktelOk) }
                items(finished, key = { "F-${it.channel.key}-${it.program.id}" }) { ev ->
                    FinishedEventRow(
                        ev,
                        onReplay = {
                            if (ev.channel.hasArchive && p.kind == "xtream") {
                                val mins = (ev.endMs - ev.startMs) / 60_000
                                val url = XtreamClient.timeshiftUrl(p, ev.channel.streamId, ev.startMs, mins)
                                nav.navigate(vodPlayerRoute(url, "${ev.channel.name} · ${ev.title}"))
                            } else toaster.error("This channel has no catch-up archive")
                        },
                    )
                }
            }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 20.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiveEventRow(
    ev: SportsEvent,
    score: tv.enktel.app.data.repo.LiveScore? = null,
    onFollow: (String) -> Unit = {},
    onTap: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val frac = ((now - ev.startMs).toFloat() / (ev.endMs - ev.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    Surface(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp).tapClick(onTap),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelLive.copy(0.12f),
            focusedContainerColor = EnktelLive,
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge("● LIVE", EnktelLive)
                    Badge(ev.sport, EnktelBlue)
                    Text(ev.channel.name, color = EnktelTextDim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(ev.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        "${hhmm(ev.startMs)}–${hhmm(ev.endMs)}",
                        color = EnktelTextDim, fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("· started ${(now - ev.startMs) / 60_000}m ago", color = EnktelTextDim, fontSize = 11.sp)
                }
                if (score != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${score.home} ${score.homeScore} – ${score.awayScore} ${score.away}   ${score.minute}",
                        color = EnktelOk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                ProgressBarThin(frac, Modifier.fillMaxWidth(0.55f))
            }
            Spacer(Modifier.width(12.dp))
            Text("▶  Watch", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UpcomingEventRow(ev: SportsEvent, onSchedule: () -> Unit, onRemind: () -> Unit = {}, onOpen: () -> Unit) {
    val inMs = ev.startMs - System.currentTimeMillis()
    val eta = if (inMs < 60 * 60_000) "in ${(inMs / 60_000).coerceAtLeast(0)}m"
        else if (inMs < 24 * 3600_000) "in ${inMs / 3600_000}h ${inMs / 60_000 % 60}m"
        else "in ${inMs / 86_400_000}d ${inMs / 3600_000 % 24}h"
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp).tapClick(onOpen),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(ev.sport, EnktelBlue)
                    Text(ev.channel.name, color = EnktelTextDim, fontSize = 12.sp)
                    Text("· $eta", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                Text(ev.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(ev.startMs))}–${hhmm(ev.endMs)}",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            FocusButton("🔔 Remind", onClick = onRemind)
            Spacer(Modifier.width(6.dp))
            FocusButton("● Record", onClick = onSchedule)
        }
    }
}

@Composable
private fun FinishedEventRow(ev: SportsEvent, onReplay: () -> Unit) {
    Surface(
        onClick = onReplay,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp).tapClick(onReplay),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface.copy(0.5f),
            focusedContainerColor = EnktelOk.copy(0.5f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge("FT", EnktelOk)
                    Badge(ev.sport, EnktelTextDim)
                    if (ev.channel.hasArchive) Badge("CATCH-UP", EnktelOk)
                }
                Spacer(Modifier.height(3.dp))
                Text(ev.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ev.channel.name} · ${SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(ev.startMs))}",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (ev.channel.hasArchive) "⏪ Replay" else "—",
                color = if (ev.channel.hasArchive) EnktelOk else EnktelTextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ChannelLogo(url: String, fallback: String) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(EnktelSurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text(fallback.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

private fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
