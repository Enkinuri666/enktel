package tv.enktel.app.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.repo.LiveScore
import tv.enktel.app.data.repo.SportsRepository
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Smart Channel Finder — "which of my channels has live sport on right now?"
 *
 * The problem this solves is specific: five minutes before a big game, a user
 * with a 15,000-channel playlist has no way to find the one channel carrying it
 * except by scrolling. The finder answers that in one screen by scoring every
 * channel's currently-airing programme (see
 * [SportsRepository.findLiveNow]) and putting the strongest matches at the top.
 *
 * Rows the user's followed teams appear in are pinned above everything else,
 * and each row tunes straight to the channel. Where the fixture can be matched
 * to a live scoreline, the score rides along on the row and the row opens the
 * Match Centre instead of guessing.
 *
 * Auto-refreshes every 60 s so the list stays honest as fixtures start and end.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun ChannelFinderScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    val p = profile ?: return
    val toaster = LocalToaster.current
    val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())
    val scoresEnabled by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)

    var loading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<SportsRepository.LiveSportsChannel>>(emptyList()) }
    var scores by remember { mutableStateOf<List<LiveScore>>(emptyList()) }
    var sportFilter by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(p.id, refreshTick, followed) {
        loading = rows.isEmpty()
        error = null
        try {
            rows = graph.sports.findLiveNow(p.id, followed.map { it.name })
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (t: Throwable) {
            error = t.message ?: "Could not scan the guide"
        }
        loading = false
    }
    LaunchedEffect(scoresEnabled, refreshTick) {
        scores = try { if (scoresEnabled) graph.scores.live() else emptyList() }
        catch (ce: kotlinx.coroutines.CancellationException) { throw ce }
        catch (_: Throwable) { emptyList() }
    }
    // Fixtures start and finish while the screen is open; a minute is frequent
    // enough to stay current without re-querying the EPG constantly.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refreshTick++
        }
    }

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val pad = if (isMobile) 16.dp else 48.dp

    val sports = remember(rows) {
        rows.groupingBy { it.sport }.eachCount().entries
            .sortedWith(compareBy({ it.key == "Other" }, { -it.value }, { it.key }))
            .map { it.key }
    }
    val visible = remember(rows, sportFilter) {
        if (sportFilter == null) rows else rows.filter { it.sport == sportFilter }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(EnktelBg),
        contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = pad)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Live Sport Finder", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Every channel carrying sport at this moment — no scrolling required.",
                            color = EnktelTextDim, fontSize = 12.sp,
                        )
                    }
                    FocusButton("↻", onClick = { refreshTick++ })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge("● ${visible.size} ON NOW", EnktelLive)
                    if (visible.any { it.followed }) {
                        Badge("★ ${visible.count { it.followed }} FOLLOWED", EnktelOk)
                    }
                }
            }
        }
        if (sports.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = pad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        tv.enktel.app.ui.components.GlassChip(
                            "All sports", selected = sportFilter == null, accent = EnktelBlue,
                            onClick = { sportFilter = null },
                        )
                    }
                    items(sports, key = { it }) { s ->
                        tv.enktel.app.ui.components.GlassChip(
                            s, selected = sportFilter == s, accent = EnktelBlue,
                            onClick = { sportFilter = if (sportFilter == s) null else s },
                        )
                    }
                }
            }
        }
        when {
            error != null -> item {
                FinderCard(pad) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Couldn't scan the guide", color = EnktelLive, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(error!!, color = EnktelTextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        FocusButton("Try again", accent = true, onClick = { refreshTick++ })
                    }
                }
            }
            loading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("Checking every channel…", color = EnktelTextDim, fontSize = 14.sp)
                }
            }
            visible.isEmpty() -> item {
                FinderCard(pad) {
                    Column(Modifier.padding(20.dp)) {
                        Text("No live sport on your channels right now.", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The finder reads your TV guide, so it needs EPG data. If you've just added " +
                                "this playlist, refresh the guide from the Sports Hub and try again.",
                            color = EnktelTextDim, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        items(visible, key = { it.channel.key + "@" + it.program.id }) { row ->
            val score = remember(row, scores) {
                if (scoresEnabled) graph.scores.matchByTitle(row.title, scores) else null
            }
            FinderRow(
                row = row,
                score = score,
                pad = pad,
                onTune = {
                    toaster.info("Tuning to ${row.channel.name}")
                    nav.navigate("live?ch=${row.channel.key}")
                },
                onMatchCenter = score?.eventId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id -> { nav.navigate(matchCenterRoute(id, row.title)) } },
            )
        }
    }
}

@Composable
private fun FinderCard(pad: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad)
            .clip(RoundedCornerShape(14.dp))
            .background(EnktelSurface.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun FinderRow(
    row: SportsRepository.LiveSportsChannel,
    score: LiveScore?,
    pad: androidx.compose.ui.unit.Dp,
    onTune: () -> Unit,
    onMatchCenter: (() -> Unit)?,
) {
    Surface(
        onClick = onTune,
        modifier = Modifier.fillMaxWidth().padding(horizontal = pad).tapClick(onTune),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (row.followed) EnktelOk.copy(0.16f) else EnktelSurfaceHigh.copy(0.45f),
            focusedContainerColor = EnktelLive.copy(0.4f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(EnktelSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.channel.logo.isNotBlank()) {
                        AsyncImage(model = row.channel.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(row.channel.name.take(2).uppercase(), color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge("● LIVE", EnktelLive)
                        Badge(row.sport, EnktelBlue)
                        if (row.followed) Badge("★ FOLLOWED", EnktelOk)
                        // Low-confidence rows are honest about it rather than
                        // being hidden — on a sports channel with a vague EPG
                        // title, "probably" still beats scrolling blind.
                        if (row.confidence < 60) Badge("POSSIBLE", EnktelTextDim)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${row.channel.name}${if (row.channel.num > 0) "  ·  ch ${row.channel.num}" else ""}",
                        color = EnktelTextDim, fontSize = 11.sp,
                    )
                }
                if (onMatchCenter != null) {
                    FocusButton("📊", onClick = onMatchCenter)
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
            ProgressBarThin(row.progressFrac, Modifier.fillMaxWidth())
            Text(
                "${(row.progressFrac * 100).toInt()}% through · ${(row.endMs - System.currentTimeMillis()) / 60_000}m left",
                color = EnktelTextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
