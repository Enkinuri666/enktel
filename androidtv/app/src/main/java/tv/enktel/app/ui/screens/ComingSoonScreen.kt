package tv.enktel.app.ui.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.repo.EnktelFeed
import tv.enktel.app.data.repo.ReleaseCountdown
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.tvRailFocus
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Coming Soon — films that are not out yet.
 *
 * ### Why this reads a published feed and Home does not
 *
 * Home's rails are built from the user's own catalogue, deliberately: a rail
 * that advertises films the line does not carry is a rail of dead cards, and
 * that is exactly the bug the old feed-driven "Coming Soon" rail caused.
 *
 * A screen *about unreleased films* is the one place that objection does not
 * apply. Nothing here is playable, and nobody expects it to be — the thing
 * being offered is the trailer, the date and the wait. So the actions are
 * honest about that: watch the trailer, and see whether the line already
 * carries it (some panels list a title before its release, and then it is
 * worth knowing).
 *
 * ### Staying current
 *
 * The feed caches for six hours on its own, and a catalogue re-sync now clears
 * that cache — a re-sync is the user asking, in as many words, for everything
 * to be brought up to date, and answering with a six-hour-old copy is the kind
 * of small dishonesty that makes an app feel stale.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun ComingSoonScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var titles by remember { mutableStateOf<List<EnktelFeed.Upcoming>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        failed = false
        val today = EnktelFeed.todayEpochDay()
        val got = try { graph.feed.upcoming(today, limit = 40) } catch (_: Throwable) { null }
        titles = got.orEmpty()
        failed = got == null
        loading = false
    }

    // One clock for the whole screen rather than one per card. Forty cards each
    // running their own second-timer is forty recompositions a second on a
    // device that has better things to do; a single tick that every card reads
    // is one.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(titles) {
        while (true) {
            nowMs = System.currentTimeMillis()
            // A second while anything on screen is inside its final day,
            // a minute otherwise.
            val soon = titles.any { it.releaseEpochDay - EnktelFeed.todayEpochDay() <= 1 }
            delay(if (soon) 1_000L else 30_000L)
        }
    }
    val zoneOffsetMs = remember {
        java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    }

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val pad = if (isMobile) 16.dp else 48.dp

    Box(Modifier.fillMaxSize().background(EnktelBg)) {
        when {
            loading && titles.isEmpty() -> CenterMessage("Loading upcoming releases…")
            failed && titles.isEmpty() -> CenterMessage(
                "Couldn't reach the release feed. Check the connection and try again.",
            )
            titles.isEmpty() -> CenterMessage("Nothing announced at the moment — check back soon.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = pad)) {
                        Text(
                            "Coming Soon", color = Color.White,
                            fontSize = if (isMobile) 24.sp else 30.sp, fontWeight = FontWeight.Black,
                        )
                        Text(
                            "Films with a release date, counting down. Trailers play in EnkTel.",
                            color = EnktelTextDim, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FocusButton("↻  Refresh", onClick = {
                                // Straight past the six-hour cache, because a
                                // person pressing Refresh has said what they want.
                                graph.appScope.launch { runCatching { graph.feed.invalidate() } }
                                refreshTick++
                            })
                        }
                    }
                }
                items(titles, key = { it.id }) { t ->
                    UpcomingCard(
                        graph = graph,
                        nav = nav,
                        item = t,
                        nowMs = nowMs,
                        zoneOffsetMs = zoneOffsetMs,
                        profileId = profile?.id ?: -1L,
                        pad = pad,
                        isMobile = isMobile,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(
    graph: AppGraph,
    nav: NavHostController,
    item: EnktelFeed.Upcoming,
    nowMs: Long,
    zoneOffsetMs: Long,
    profileId: Long,
    pad: androidx.compose.ui.unit.Dp,
    isMobile: Boolean,
) {
    val remaining = remember(nowMs, item.releaseEpochDay) {
        ReleaseCountdown.remaining(nowMs, item.releaseEpochDay, zoneOffsetMs)
    }

    // The trailer key, looked up once per card and only when the card exists —
    // a LazyColumn only composes what is on screen, so forty titles do not mean
    // forty TMDB requests unless somebody scrolls through all forty.
    val trailerKey by produceState<String?>(initialValue = null, item.id) {
        value = try {
            graph.trailers.trailerKey(item.id, item.title, isSeries = false)
        } catch (_: Throwable) { null }
    }

    // Whether this line already carries it. Some panels list a title before
    // release, and "you already have this" is worth more than a countdown.
    val catalogue by graph.content.movies(profileId).collectAsStateWithLifecycle(initialValue = emptyList())
    val already = remember(catalogue, item.title) {
        catalogue.firstOrNull { it.name.trim().equals(item.title.trim(), ignoreCase = true) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad)
            .clip(RoundedCornerShape(14.dp))
            .background(EnktelSurface.copy(0.6f))
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .width(if (isMobile) 84.dp else 110.dp)
                .height(if (isMobile) 124.dp else 162.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(EnktelSurfaceHigh),
        ) {
            if (item.poster.isNotBlank()) {
                AsyncImage(
                    model = item.poster, contentDescription = item.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                item.title, color = Color.White,
                fontSize = if (isMobile) 16.sp else 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // The clock, in the accent colour, because it is the thing this
                // screen exists to show.
                Badge(
                    ReleaseCountdown.format(remaining),
                    if (remaining.out) EnktelOk else EnktelBlue,
                )
                if (item.releaseDate.isNotBlank()) Badge(item.releaseDate, EnktelTextDim)
                if (item.rating > 0) Badge("★ ${"%.1f".format(item.rating)}", EnktelTextDim)
            }
            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.genres.take(3).joinToString(" · "),
                    color = EnktelTextDim, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    item.overview, color = Color.White.copy(0.86f), fontSize = 13.sp,
                    lineHeight = 19.sp, maxLines = if (isMobile) 4 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.tvRailFocus(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Only offered once there is something to play. A trailer
                // button that reports "no trailer" after a spinner is worse
                // than a button that was never there.
                trailerKey?.let { key ->
                    FocusButton("▶  Trailer", accent = true, onClick = {
                        nav.navigate(
                            "trailer?key=$key&title=${tv.enktel.app.encode(item.title)}&alts=",
                        )
                    })
                }
                already?.let { m ->
                    FocusButton("Open in your catalogue", onClick = {
                        nav.navigate("movie/${m.key}")
                    })
                }
                tv.enktel.app.ui.components.ShareButton(
                    tv.enktel.app.DeepLink.Target.Search(item.title),
                    label = "↗  Share",
                )
            }
        }
    }
}
