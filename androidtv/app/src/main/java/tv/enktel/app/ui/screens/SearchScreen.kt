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
import tv.enktel.app.ui.components.ChipRowLabel
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelTextDim

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SearchScreen(
    graph: AppGraph,
    nav: NavHostController,
    voiceBus: tv.enktel.app.voice.VoiceCommandBus? = null,
) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }

    // Receive spoken searches: "search for Interstellar" navigates here and
    // pushes "Interstellar" onto the voice bus. We collect it and drop it
    // straight into the query field.
    androidx.compose.runtime.LaunchedEffect(voiceBus) {
        voiceBus?.searchQueries?.collect { spoken -> query = spoken }
    }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var series by remember { mutableStateOf<List<Series>>(emptyList()) }
    var epg by remember { mutableStateOf<List<tv.enktel.app.data.db.EpgProgram>>(emptyList()) }
    val history by graph.db.searchDao().recent(20).collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(query) {
        if (query.length < 2) {
            channels = emptyList(); movies = emptyList(); series = emptyList(); epg = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        channels = graph.content.searchChannels(p.id, query)
        movies = graph.db.searchDao().searchMoviesDeep(p.id, query)
        series = graph.db.searchDao().searchSeriesDeep(p.id, query)
        epg = try { graph.db.epgDao().searchUpcoming(p.id, query, System.currentTimeMillis()) }
              catch (_: Throwable) { emptyList() }
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        ChipRowLabel("Recent searches")
                        Spacer(Modifier.weight(1f))
                        FocusButton("Clear", onClick = { scope.launch { graph.db.searchDao().clear() } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.take(8).forEach { h ->
                            GlassChip(h.query, selected = false, onClick = { query = h.query })
                        }
                    }
                }
            }
        }
        if (query.length >= 2 && channels.isEmpty() && movies.isEmpty() && series.isEmpty() && epg.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 48.dp, vertical = 24.dp)) {
                    Text("No matches for \"$query\"", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Try a shorter query, an actor's name, or a genre.", color = EnktelTextDim, fontSize = 12.sp)
                }
            }
        }
        if (query.length >= 2 && (channels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty() || epg.isNotEmpty())) {
            item {
                LaunchedEffect(query) {
                    graph.db.searchDao().push(SearchHistoryItem(query.trim()))
                }
            }
        }
        if (channels.isNotEmpty()) {
            item {
                ContentRail("Channels", channels, accent = EnktelBlue, key = { it.key }) { ch ->
                    PosterCard(ch.name, ch.logo, wide = true, subtitle = ch.categoryName,
                        onClick = { nav.navigate("live?ch=${ch.key}") })
                }
            }
        }
        // Master-search EPG rail: upcoming programs matching the query
        // across every channel, sorted by earliest start.  Tap to open the
        // guide anchored on that program's channel + time.
        if (epg.isNotEmpty()) {
            item {
                val epgWithChan = remember(epg, channels) {
                    epg.map { p -> p to channels.firstOrNull { it.epgId == p.epgId } }
                }
                val fmt = remember { java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault()) }
                ContentRail("In the Guide", epgWithChan, accent = EnktelPurple,
                    key = { "${it.first.id}" }) { (prog, ch) ->
                    val time = fmt.format(java.util.Date(prog.startMs))
                    PosterCard(
                        prog.title, ch?.logo.orEmpty(), wide = true,
                        subtitle = listOfNotNull(time, ch?.name).joinToString(" · "),
                        onClick = {
                            if (ch != null) nav.navigate("live?ch=${ch.key}")
                            else nav.navigate("guide")
                        },
                    )
                }
            }
        }
        if (movies.isNotEmpty()) {
            item {
                ContentRail("Movies", movies, accent = EnktelOk, key = { it.key }) { m ->
                    PosterCard(
                        m.name, m.poster,
                        subtitle = if (m.year > 0) "${m.year}" else m.genre.take(20),
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        if (series.isNotEmpty()) {
            item {
                ContentRail("Series", series, accent = EnktelPurple, key = { it.key }) { s ->
                    PosterCard(s.name, s.poster, subtitle = if (s.year > 0) "${s.year}" else "",
                        onClick = { nav.navigate("seriesDetails/${s.key}") })
                }
            }
        }
    }
}
