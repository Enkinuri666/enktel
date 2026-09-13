package tv.enktel.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import tv.enktel.app.data.repo.ChannelFilters
import tv.enktel.app.data.repo.SportsRepository
import tv.enktel.app.i18n.LocalStrings
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.rememberScreenShape
import tv.enktel.app.ui.components.Segment
import tv.enktel.app.ui.components.SegmentedControl
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelTextDim

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SearchScreen(
    graph: AppGraph,
    nav: NavHostController,
    voiceBus: tv.enktel.app.voice.VoiceCommandBus? = null,
    /**
     * Pre-filled search terms — from an Alexa phrase or a catalog deep link
     * ("show me action movies"). Empty for a normal visit to the screen.
     */
    initialQuery: String = "",
) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val t = LocalStrings.current

    var query by remember { mutableStateOf(initialQuery) }

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

    // Live channels come from the synced catalogue and are matched in-process
    // rather than by the DAO's `name LIKE '%q%'`. That query cannot find
    // "AU| SEVEN MATE HD" from "seven mate" (the space breaks the infix), nor
    // from "73" (its number), nor from "mate seven". See ChannelFilters.
    val allChannels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by graph.settings.hiddenChannels.collectAsStateWithLifecycle(initialValue = emptySet())

    LaunchedEffect(query) {
        if (query.length < 2) {
            channels = emptyList(); movies = emptyList(); series = emptyList(); epg = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        // Through the repository, which uses the FTS index when it exists and
        // falls back to LIKE when it doesn't (an upgrade lands with an empty
        // index until the next catalogue sync).
        val (m, sr) = graph.content.searchDeep(p.id, query)
        movies = m
        series = sr
        epg = try { graph.db.epgDao().searchUpcoming(p.id, query, System.currentTimeMillis()) }
              catch (_: Throwable) { emptyList() }
    }
    LaunchedEffect(query, allChannels, hidden) {
        channels = if (query.length < 2) emptyList()
        else ChannelFilters.apply(allChannels, query = query, hidden = hidden).take(60)
    }

    // Sport, cut from the guide hits rather than searched separately.
    //
    // A fixture *is* an EPG programme on a sports channel, so the rows are
    // already here — asking SportsRepository.load() would mean a multi-day
    // window over up to 400 channels on every keystroke to learn something
    // this list already knows. It also means the two rails cannot disagree:
    // whatever is a fixture leaves the Guide rail, so nothing appears twice.
    val sportHits = remember(epg, allChannels) {
        SportsRepository.searchHits(epg, allChannels)
    }
    val guideOnly = remember(epg, sportHits) {
        // Every folded feed, not just the row that survived the fold — the
        // duplicate that lost is still the same fixture, and leaving it here
        // showed the match a second time under "In the Guide".
        val claimed = sportHits.flatMapTo(HashSet()) { hit -> hit.feeds.map { it.program.id } }
        epg.filterNot { it.id in claimed }
    }

    // Scope switch. "All" stays the default — a search that silently excluded
    // four of the five content types would be worse than no scoping at all.
    var scope0 by remember { mutableStateOf("all") }
    val segments = remember(channels, movies, series, guideOnly, sportHits, t) {
        listOf(
            Segment("all", t.scopeAll, channels.size + sportHits.size + movies.size + series.size + guideOnly.size),
            Segment("live", t.navLive, channels.size),
            Segment("sport", t.railSport, sportHits.size),
            Segment("movies", t.navMovies, movies.size),
            Segment("series", t.navSeries, series.size),
            Segment("guide", t.scopeGuide, guideOnly.size),
        )
    }
    fun show(id: String) = scope0 == "all" || scope0 == id

    // The search box was 520 dp wide inside a 48 dp gutter — 616 dp of
    // demand on a 411 dp handset, so the field that the whole screen exists
    // for ran off the right edge. ScreenShape knows what the viewport is;
    // 48 dp is a television's overscan margin, not a phone's.
    val shape = rememberScreenShape()

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = shape.padV),
    ) {
        item {
            Column(Modifier.padding(horizontal = shape.padH)) {
                SectionTitle(t.searchTitle)
                Spacer(Modifier.height(12.dp))
                TvTextField(
                    query, { query = it },
                    // Shorter on a phone: the long prompt wrapped to three
                    // lines and pushed the field itself below the fold.
                    if (shape.narrow) t.searchHintShort else t.searchHint,
                    // widthIn first: it narrows the incoming constraint, and
                    // fillMaxWidth then takes whatever is left. The other way
                    // round, fillMaxWidth wins and the box runs the full width
                    // of a television.
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                )
                if (query.length >= 2) {
                    Spacer(Modifier.height(12.dp))
                    SegmentedControl(segments, scope0, onSelect = { scope0 = it })
                }
            }
        }
        if (query.isBlank() && history.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = shape.padH)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        ChipRowLabel(t.recentSearches)
                        Spacer(Modifier.weight(1f))
                        FocusButton(t.clear, onClick = { scope.launch { graph.db.searchDao().clear() } })
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
                Column(Modifier.padding(horizontal = shape.padH, vertical = 24.dp)) {
                    Text(t.noMatchesFor.format(query), color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(t.noMatchesHelp, color = EnktelTextDim, fontSize = 12.sp)
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
        if (channels.isNotEmpty() && show("live")) {
            item {
                ContentRail(t.railChannels, channels, accent = EnktelBlue, key = { it.key }) { ch ->
                    PosterCard(ch.name, ch.logo, wide = true, subtitle = ch.categoryName,
                        onClick = { nav.navigate("live?ch=${ch.key}") })
                }
            }
        }
        // Sport. Above the guide because someone typing a team name wants the
        // match, and above Movies because a fixture is the thing with a
        // kick-off time — it is the only result on the screen that stops being
        // available if they read past it.
        if (sportHits.isNotEmpty() && show("sport")) {
            item {
                val fmt = remember(t) { java.text.SimpleDateFormat(t.dayTimeFormat, t.locale) }
                ContentRail(t.railSport, sportHits, accent = EnktelLive,
                    key = { "${it.event.program.id}" }) { hit ->
                    val ev = hit.event
                    // What a fixture row has to answer, in order: is it on
                    // now, what sport, which channel, and is it somewhere
                    // else too.
                    val when0 = when (ev.phase) {
                        "LIVE" -> "● ${t.live}"
                        "FINISHED" -> t.ended
                        else -> fmt.format(java.util.Date(ev.startMs))
                    }
                    val also = if (hit.alsoOn > 0) t.alsoOnMore.format(hit.alsoOn) else null
                    PosterCard(
                        ev.title, ev.channel.logo, wide = true,
                        subtitle = listOfNotNull(when0, t.sport(ev.sport), ev.channel.name, also)
                            .joinToString(" · "),
                        onClick = { nav.navigate("live?ch=${ev.channel.key}") },
                    )
                }
            }
        }
        // Master-search EPG rail: everything else the guide matched, sorted by
        // earliest start. Tap to open the channel it is on. Fixtures have
        // already been taken out above, so a match never shows up twice.
        if (guideOnly.isNotEmpty() && show("guide")) {
            item {
                val epgWithChan = remember(guideOnly, channels) {
                    guideOnly.map { p -> p to channels.firstOrNull { it.epgId == p.epgId } }
                }
                val fmt = remember(t) { java.text.SimpleDateFormat(t.dayTimeFormat, t.locale) }
                ContentRail(t.railInGuide, epgWithChan, accent = EnktelPurple,
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
        if (movies.isNotEmpty() && show("movies")) {
            item {
                ContentRail(t.navMovies, movies, accent = EnktelOk, key = { it.key }) { m ->
                    PosterCard(
                        m.name, m.poster,
                        subtitle = if (m.year > 0) "${m.year}" else m.genre.take(20),
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        if (series.isNotEmpty() && show("series")) {
            item {
                ContentRail(t.navSeries, series, accent = EnktelPurple, key = { it.key }) { s ->
                    PosterCard(s.name, s.poster, subtitle = if (s.year > 0) "${s.year}" else "",
                        onClick = { nav.navigate("seriesDetails/${s.key}") })
                }
            }
        }
    }
}
