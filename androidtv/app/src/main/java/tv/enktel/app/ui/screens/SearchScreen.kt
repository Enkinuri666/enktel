package tv.enktel.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.SearchHistoryItem
import tv.enktel.app.data.db.Series
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelTextDim

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SearchScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var series by remember { mutableStateOf<List<Series>>(emptyList()) }
    val history by graph.db.searchDao().recent(20).collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(query) {
        if (query.length < 2) {
            channels = emptyList(); movies = emptyList(); series = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        // Fuzzy = case-insensitive substring across name/cast/director/genre for VOD (title-only for channels).
        channels = graph.content.searchChannels(p.id, query)
        movies = graph.db.searchDao().searchMoviesDeep(p.id, query)
        series = graph.db.searchDao().searchSeriesDeep(p.id, query)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 48.dp)) {
                SectionTitle("Search")
                Spacer(Modifier.height(12.dp))
                TvTextField(
                    query, { query = it },
                    "Title, cast, director, genre…",
                    Modifier.width(520.dp),
                )
            }
        }
        if (query.isBlank() && history.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 48.dp)) {
                    Row {
                        Text("Recent searches", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        FocusButton("Clear", onClick = { scope.launch { graph.db.searchDao().clear() } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.take(8).forEach { h ->
                            FocusButton(h.query, onClick = { query = h.query })
                        }
                    }
                }
            }
        }
        if (query.length >= 2 && (channels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty())) {
            item {
                LaunchedEffect(query) {
                    graph.db.searchDao().push(SearchHistoryItem(query.trim()))
                }
            }
        }
        item {
            ContentRail("Channels (${channels.size})", channels, key = { it.key }) { ch ->
                PosterCard(ch.name, ch.logo, wide = true, subtitle = ch.categoryName,
                    onClick = { nav.navigate("live?ch=${ch.key}") })
            }
        }
        item {
            ContentRail("Movies (${movies.size})", movies, key = { it.key }) { m ->
                PosterCard(
                    m.name, m.poster,
                    subtitle = if (m.year > 0) "${m.year}" else m.genre.take(20),
                    onClick = { nav.navigate("movie/${m.key}") },
                )
            }
        }
        item {
            ContentRail("Series (${series.size})", series, key = { it.key }) { s ->
                PosterCard(s.name, s.poster, subtitle = if (s.year > 0) "${s.year}" else "",
                    onClick = { nav.navigate("seriesDetails/${s.key}") })
            }
        }
    }
}
