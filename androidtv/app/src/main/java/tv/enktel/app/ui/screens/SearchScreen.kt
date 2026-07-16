package tv.enktel.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.Series
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField

@Composable
fun SearchScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    var query by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var series by remember { mutableStateOf<List<Series>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.length < 2) {
            channels = emptyList(); movies = emptyList(); series = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        channels = graph.content.searchChannels(p.id, query)
        movies = graph.content.searchMovies(p.id, query)
        series = graph.content.searchSeries(p.id, query)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 48.dp)) {
                SectionTitle("Search")
                Spacer(Modifier.height(12.dp))
                TvTextField(query, { query = it }, "Channel, movie or series name…", Modifier.width(520.dp))
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
                PosterCard(m.name, m.poster, onClick = { nav.navigate("movie/${m.key}") })
            }
        }
        item {
            ContentRail("Series (${series.size})", series, key = { it.key }) { s ->
                PosterCard(s.name, s.poster, onClick = { nav.navigate("seriesDetails/${s.key}") })
            }
        }
    }
}
