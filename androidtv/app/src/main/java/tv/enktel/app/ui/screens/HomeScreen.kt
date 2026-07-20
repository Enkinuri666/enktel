package tv.enktel.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Lint false-positive: produceState's vararg-keys overload isn't recognized by the
// ProduceStateDoesNotAssignValue detector even though every producer below assigns `value`.
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = graph.playlists.activeProfile()
    }
    val p = profile ?: return

    val continueWatching by graph.content.continueWatching(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val favChannels by graph.content.favoriteChannels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val recentMovies by graph.content.recentMovies(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val favMovies by graph.content.favoriteMovies(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allChannels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val recordings by graph.db.recordingDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentRecordings = remember(recordings) {
        recordings.filter { it.status == "DONE" && it.filePath.isNotBlank() }.sortedByDescending { it.startMs }.take(10)
    }

    val watchlist by graph.watchlist.all(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var becauseYouWatched by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var trending by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var newThisWeek by remember { mutableStateOf<List<Movie>>(emptyList()) }
    LaunchedEffect(p.id) {
        becauseYouWatched = graph.recommendations.becauseYouWatched(p.id)
        trending = graph.recommendations.trending(p.id)
        newThisWeek = graph.recommendations.newThisWeek(p.id)
    }

    // If the profile was created but has never finished its first sync (e.g. onboarding was
    // interrupted, the panel timed out, or the process died), kick off content + EPG sync in
    // the background so the user sees channels on Home without diving into Settings.
    var syncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("") }
    LaunchedEffect(p.id, p.lastSync, allChannels.size) {
        if (p.lastSync != 0L || allChannels.isNotEmpty() || syncing) return@LaunchedEffect
        syncing = true
        syncStatus = "Downloading your playlist…"
        runCatching { graph.content.refreshAll(p) }
            .onSuccess { summary -> syncStatus = "Downloading TV guide… ($summary)" }
            .onFailure { syncStatus = "Sync failed: ${it.message ?: "unknown error"}" }
        runCatching { graph.epg.refresh(p) }
            .onSuccess { syncStatus = "Ready" }
            .onFailure { syncStatus = "EPG failed: ${it.message ?: "unknown"}" }
        graph.playlists.markSynced(p)
        kotlinx.coroutines.delay(1200)
        syncing = false
    }

    val heroItems = remember(favMovies, recentMovies) {
        (favMovies + recentMovies).distinctBy { it.key }.filter { it.poster.isNotBlank() }.take(5)
    }

    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm · EEE d MMM", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        if (heroItems.isNotEmpty()) {
            item { HeroBanner(items = heroItems, clock = clock, nav = nav, graph = graph, profile = p) }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painter = painterResource(R.drawable.logo_full), contentDescription = "EnkTel", modifier = Modifier.width(190.dp))
                    Spacer(Modifier.weight(1f))
                    Text(clock, color = EnktelTextDim, fontSize = 14.sp)
                }
            }
        }
        if (syncing) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .background(
                            tv.enktel.app.ui.theme.EnktelBlue.copy(alpha = 0.15f),
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⟳", color = tv.enktel.app.ui.theme.EnktelBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(syncStatus, color = Color.White, fontSize = 13.sp)
                }
            }
        }
        item {
            // Horizontal scroll so the row survives narrow portrait screens on phones.
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { FocusButton("▶  Live TV", accent = true, onClick = { nav.navigate("live?ch=") }) }
                item { FocusButton("TV Guide", onClick = { nav.navigate("guide") }) }
                item { FocusButton("Movies", onClick = { nav.navigate("movies") }) }
                item { FocusButton("Series", onClick = { nav.navigate("series") }) }
                item { FocusButton("⚽ Sports", onClick = { nav.navigate("sports") }) }
                item { FocusButton("☆ Watchlist", onClick = { nav.navigate("watchlist") }) }
                item { FocusButton("Search", onClick = { nav.navigate("search") }) }
                item { FocusButton("Recordings", onClick = { nav.navigate("recordings") }) }
                item { FocusButton("Settings", onClick = { nav.navigate("settings") }) }
            }
        }
        if (continueWatching.isNotEmpty()) {
            item {
                ContentRail("Continue Watching", continueWatching, key = { it.key }) { cw ->
                    val pct = if (cw.durationMs > 0) " · ${(cw.positionMs * 100 / cw.durationMs)}%" else ""
                    PosterCard(
                        title = cw.name,
                        imageUrl = cw.poster,
                        subtitle = "Resume$pct",
                        wide = true,
                        onClick = { nav.navigate(vodPlayerRoute(cw.url, cw.name, cw.key)) },
                    )
                }
            }
        }
        if (watchlist.isNotEmpty()) {
            item {
                ContentRail("My Watchlist", watchlist.take(15), key = { it.key }) { w ->
                    PosterCard(
                        title = w.name, imageUrl = w.poster,
                        subtitle = if (w.kind == "series") "Series" else "Movie",
                        onClick = {
                            if (w.kind == "vod") nav.navigate("movie/${w.profileId}:${w.refId}")
                            else nav.navigate("seriesDetails/${w.profileId}:${w.refId}")
                        },
                    )
                }
            }
        }
        if (becauseYouWatched.isNotEmpty()) {
            item {
                ContentRail("Because You Watched", becauseYouWatched, key = { it.key }) { m ->
                    PosterCard(m.name, m.poster, subtitle = m.genre.take(20),
                        onClick = { nav.navigate("movie/${m.key}") })
                }
            }
        }
        if (trending.isNotEmpty()) {
            item {
                ContentRail("Trending on EnkTel", trending, key = { it.key }) { m ->
                    PosterCard(m.name, m.poster, subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") })
                }
            }
        }
        if (newThisWeek.isNotEmpty()) {
            item {
                ContentRail("New This Week", newThisWeek, key = { it.key }) { m ->
                    PosterCard(m.name, m.poster, subtitle = "New",
                        onClick = { nav.navigate("movie/${m.key}") })
                }
            }
        }
        if (recentRecordings.isNotEmpty()) {
            item {
                ContentRail("Recent Recordings", recentRecordings, key = { it.id }) { rec ->
                    PosterCard(
                        title = rec.title,
                        imageUrl = rec.channelLogo,
                        subtitle = rec.channelName,
                        wide = true,
                        onClick = { nav.navigate(vodPlayerRoute("file://${rec.filePath}", rec.title)) },
                    )
                }
            }
        }
        if (favChannels.isNotEmpty()) {
            item {
                ContentRail("Favorite Channels", favChannels, key = { it.key }) { ch ->
                    PosterCard(
                        title = ch.name,
                        imageUrl = ch.logo,
                        subtitle = if (ch.num > 0) "CH ${ch.num}" else "",
                        wide = true,
                        onClick = { nav.navigate("live?ch=${ch.key}") },
                    )
                }
            }
        }
        if (recentMovies.isNotEmpty()) {
            item {
                ContentRail("Latest Movies", recentMovies, key = { it.key }) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        if (favMovies.isNotEmpty()) {
            item {
                ContentRail("Favorite Movies", favMovies, key = { it.key }) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        item {
            ContentRail("All Channels", allChannels.take(30), key = { it.key }) { ch ->
                PosterCard(
                    title = ch.name,
                    imageUrl = ch.logo,
                    subtitle = ch.categoryName,
                    wide = true,
                    onClick = { nav.navigate("live?ch=${ch.key}") },
                )
            }
        }
        item {
            Text(
                "Profile: ${p.name} · Synced ${if (p.lastSync > 0) SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.lastSync)) else "never"}",
                color = EnktelTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}

/**
 * Full-width rotating hero banner (Netflix-style): large backdrop art with a readability
 * scrim, title/logo overlay, and Play / More Info actions. Auto-advances through up to
 * 5 featured titles (favorites first, then latest additions).
 */
@Composable
private fun HeroBanner(items: List<Movie>, clock: String, nav: NavHostController, graph: AppGraph, profile: Profile) {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(items) {
        index = 0
        while (items.size > 1) {
            delay(7_000)
            index = (index + 1) % items.size
        }
    }
    val current = items.getOrNull(index.coerceIn(0, items.lastIndex)) ?: return

    Box(Modifier.fillMaxWidth().height(420.dp)) {
        Crossfade(targetState = current, animationSpec = tween(600), label = "hero") { movie ->
            AsyncImage(
                model = movie.poster,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Left-to-right + bottom scrims so text stays legible over any artwork.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.92f),
                    0.45f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                )
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.95f),
                )
            ),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painter = painterResource(R.drawable.logo_full), contentDescription = "EnkTel", modifier = Modifier.width(150.dp))
            Spacer(Modifier.weight(1f))
            Text(clock, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }

        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 32.dp).width(560.dp),
        ) {
            Text(
                "FEATURED",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tv.enktel.app.ui.theme.EnktelBlue,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                current.name,
                fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (current.categoryId.isNotBlank() || current.rating > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    listOf(current.categoryId, if (current.rating > 0) "★ ${current.rating}" else "")
                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton(
                    "▶  Play", accent = true,
                    onClick = { nav.navigate(vodPlayerRoute(graph.content.vodUrl(profile, current), current.name, current.key)) },
                )
                FocusButton("More Info", onClick = { nav.navigate("movie/${current.key}") })
            }
            if (items.size > 1) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == index) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}
