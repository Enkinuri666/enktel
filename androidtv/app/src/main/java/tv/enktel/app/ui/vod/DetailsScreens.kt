package tv.enktel.app.ui.vod

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.Series
import tv.enktel.app.data.repo.MovieDetails
import tv.enktel.app.data.repo.SeriesDetails
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.KeyValue
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute

// Lint false-positive: produceState's vararg-keys overload isn't recognized by the
// ProduceStateDoesNotAssignValue detector even though every producer below assigns `value`.
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun MovieDetailsScreen(graph: AppGraph, nav: NavHostController, key: String) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val movie by produceState<Movie?>(initialValue = null, key) { value = graph.content.movie(key) }
    val m = movie ?: return
    val details by produceState<MovieDetails?>(initialValue = null, m.key) {
        if (p.kind == "xtream") value = runCatching { graph.content.movieDetails(p, m.streamId) }.getOrNull()
    }
    val scope = rememberCoroutineScope()
    var resumeMs by remember { mutableStateOf(0L) }
    val progressKey = "${p.id}:vod:${m.streamId}"
    androidx.compose.runtime.LaunchedEffect(progressKey) {
        resumeMs = graph.content.progress(progressKey)?.positionMs ?: 0L
    }
    val url = graph.content.vodUrl(p, m)

    Box(Modifier.fillMaxSize()) {
        if (!details?.backdrop.isNullOrBlank()) {
            AsyncImage(
                model = details!!.backdrop, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.25f,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, EnktelBg))))
        }
        Row(Modifier.fillMaxSize().padding(48.dp)) {
            Box(Modifier.width(220.dp).height(320.dp).clip(RoundedCornerShape(12.dp)).background(EnktelSurfaceHigh)) {
                if (m.poster.isNotBlank()) {
                    AsyncImage(model = m.poster, contentDescription = m.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(m.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.rating > 0) Badge("★ ${"%.1f".format(m.rating)}")
                    if (details?.genre?.isNotBlank() == true) Badge(details!!.genre.take(30))
                    val mins = (details?.durationSecs ?: 0) / 60
                    if (mins > 0) Badge("$mins min")
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusButton("▶ Play", accent = true, onClick = {
                        nav.navigate(vodPlayerRoute(url, m.name, progressKey))
                    })
                    if (resumeMs > 60_000) {
                        FocusButton("Resume ${resumeMs / 60000}m", onClick = {
                            nav.navigate(vodPlayerRoute(url, m.name, progressKey))
                        })
                    }
                    FavButton(graph, p.id, "vod", m.streamId)
                }
                Spacer(Modifier.height(18.dp))
                if (details?.plot?.isNotBlank() == true) {
                    Text(details!!.plot, color = Color.White.copy(0.9f), fontSize = 14.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(16.dp))
                }
                if (details != null) {
                    if (details!!.cast.isNotBlank()) KeyValue("Cast", details!!.cast.take(160))
                    if (details!!.director.isNotBlank()) KeyValue("Director", details!!.director)
                    if (details!!.releaseDate.isNotBlank()) KeyValue("Released", details!!.releaseDate)
                }
            }
        }
    }
}

@Composable
fun FavButton(graph: AppGraph, profileId: Long, kind: String, refId: Long) {
    val fav by graph.content.isFavoriteFlow(profileId, kind, refId)
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    FocusButton(if (fav) "★ Favorited" else "☆ Favorite", onClick = {
        scope.launch { graph.content.toggleFavorite(profileId, kind, refId) }
    })
}

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SeriesDetailsScreen(graph: AppGraph, nav: NavHostController, key: String) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val series by produceState<Series?>(initialValue = null, key) { value = graph.content.oneSeries(key) }
    val s = series ?: return
    val details by produceState<SeriesDetails?>(initialValue = null, s.key) {
        value = runCatching { graph.content.seriesDetails(p, s.seriesId) }.getOrNull()
    }
    var season by remember { mutableIntStateOf(-1) }
    val seasons = details?.seasons ?: emptyMap()
    if (season == -1 && seasons.isNotEmpty()) season = seasons.keys.first()

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(10.dp)).background(EnktelSurfaceHigh)) {
                if (s.poster.isNotBlank()) {
                    AsyncImage(model = s.poster, contentDescription = s.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.rating > 0) Badge("★ ${"%.1f".format(s.rating)}")
                    if (s.genre.isNotBlank()) Badge(s.genre.take(30))
                    Badge("${seasons.size} season${if (seasons.size == 1) "" else "s"}")
                }
                Spacer(Modifier.height(8.dp))
                val plot = details?.plot?.ifBlank { s.plot } ?: s.plot
                if (plot.isNotBlank()) {
                    Text(plot, color = Color.White.copy(0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                FavButton(graph, p.id, "series", s.seriesId)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (details == null) {
            CenterMessage("Loading episodes…")
            return
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons.keys.toList()) { sn ->
                FocusButton("Season $sn", accent = sn == season, onClick = { season = sn })
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons[season].orEmpty(), key = { it.id }) { ep ->
                val playEpisode = {
                    val url = XtreamClient.episodeUrl(p, ep.id, ep.ext)
                    val pk = "${p.id}:episode:${ep.id}"
                    nav.navigate(vodPlayerRoute(url, "${s.name} S${ep.season}E${ep.episode} · ${ep.title}", pk))
                }
                androidx.tv.material3.Surface(
                    onClick = playEpisode,
                    modifier = Modifier.fillMaxWidth().tapClick(playEpisode),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = EnktelSurfaceHigh.copy(0.5f),
                        focusedContainerColor = tv.enktel.app.ui.theme.EnktelBlue,
                        focusedContentColor = Color.White,
                        contentColor = Color.White,
                    ),
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "E${ep.episode}",
                            color = tv.enktel.app.ui.theme.EnktelBlue, fontWeight = FontWeight.Black, fontSize = 14.sp,
                            modifier = Modifier.width(48.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ep.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ep.plot.isNotBlank()) {
                                Text(ep.plot, fontSize = 11.sp, color = EnktelTextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (ep.durationSecs > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text("${ep.durationSecs / 60} min", fontSize = 12.sp, color = EnktelTextDim)
                        }
                    }
                }
            }
        }
    }
}
