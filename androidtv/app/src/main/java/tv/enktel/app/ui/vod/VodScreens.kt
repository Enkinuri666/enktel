package tv.enktel.app.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelTextDim

/** Shared PIN gate for locked VOD/series categories. */
class ParentalGateState {
    var isLockedImpl: (String) -> Boolean = { false }
    var pinHash: String = ""
    var prompting by androidx.compose.runtime.mutableStateOf(false)
    val isLocked: (String) -> Boolean get() = isLockedImpl
    val prompt: (String) -> Unit = { prompting = true }

    @Composable
    fun Dialog() {
        if (!prompting) return
        val toaster = tv.enktel.app.ui.components.LocalToaster.current
        tv.enktel.app.ui.components.PinDialog(
            title = "Locked category — enter PIN",
            onSubmit = { pin ->
                if (tv.enktel.app.util.Pin.matches(pin, pinHash)) {
                    tv.enktel.app.util.UnlockSession.unlocked = true
                    prompting = false
                    toaster.success("Unlocked")
                } else {
                    toaster.error("Wrong PIN")
                }
            },
            onDismiss = { prompting = false },
        )
    }
}

@Composable
fun rememberParentalGate(graph: AppGraph, kind: String): ParentalGateState {
    val pinHash by graph.settings.parentalPinHash.collectAsStateWithLifecycle(initialValue = "")
    val locked by graph.settings.lockedCategories.collectAsStateWithLifecycle(initialValue = emptySet())
    val state = remember { ParentalGateState() }
    state.pinHash = pinHash
    state.isLockedImpl = { catId ->
        pinHash.isNotBlank() && !tv.enktel.app.util.UnlockSession.unlocked && "$kind:$catId" in locked
    }
    return state
}

@Composable
private fun CategorySidebar(
    title: String,
    categories: List<Pair<String, String>>, // id to name
    selected: String?,
    isLocked: (String) -> Boolean = { false },
    onLocked: (String) -> Unit = {},
    onSelect: (String?) -> Unit,
) {
    Column(
        Modifier.width(230.dp).fillMaxHeight().padding(top = 20.dp, start = 24.dp),
    ) {
        SectionTitle(title)
        Spacer(Modifier.height(14.dp))
        LazyColumn {
            item {
                SidebarRow("All", selected == null) { onSelect(null) }
            }
            items(categories) { (id, name) ->
                val locked = isLocked(id)
                SidebarRow((if (locked) "🔒 " else "") + name, selected == id) {
                    if (locked) onLocked(id) else onSelect(id)
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) EnktelBlue.copy(0.22f) else Color.Transparent,
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = if (selected) Color.White else EnktelTextDim,
        ),
    ) {
        Text(
            text, fontSize = 13.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun MoviesScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    var cat by remember { mutableStateOf<String?>(null) }
    val categories by graph.content.categories(p.id, "vod").collectAsStateWithLifecycle(initialValue = emptyList())
    val raw by (if (cat == null) graph.content.movies(p.id) else graph.content.moviesIn(p.id, cat!!))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sort by graph.settings.vodSort.collectAsStateWithLifecycle(initialValue = "name")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var decadeFilter by remember { mutableStateOf<Int?>(null) }
    val genres = remember(raw) {
        raw.flatMap { tv.enktel.app.data.repo.ContentRepository.splitGenres(it.genre) }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(18).map { it.key }
    }
    val movies = remember(raw, sort, genreFilter, decadeFilter) {
        raw.asSequence()
            .filter { m -> genreFilter == null || m.genre.contains(genreFilter!!, ignoreCase = true) }
            .filter { m ->
                when (decadeFilter) {
                    null -> true
                    -1 -> m.year in 1900..1989
                    else -> m.year in decadeFilter!!..(decadeFilter!! + 9)
                }
            }
            .let { seq ->
                when (sort) {
                    "rating" -> seq.sortedByDescending { it.rating }
                    "added" -> seq.sortedByDescending { it.addedAt }
                    "year" -> seq.sortedByDescending { it.year }
                    else -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }
    val gate = rememberParentalGate(graph, "vod")

    Row(Modifier.fillMaxSize()) {
        CategorySidebar(
            "Movies",
            categories.map { it.categoryId to it.name },
            cat,
            isLocked = gate.isLocked,
            onLocked = gate.prompt,
        ) { cat = it }
        Column(Modifier.fillMaxSize()) {
            FilterBar(
                sort = sort,
                sortOptions = listOf("name" to "A–Z", "rating" to "Top rated", "added" to "Recently added", "year" to "Newest"),
                onSort = { scope.launch { graph.settings.setVodSort(it) } },
                genres = genres,
                genreFilter = genreFilter,
                onGenre = { genreFilter = it },
                decadeFilter = decadeFilter,
                onDecade = { decadeFilter = it },
            )
            if (movies.isEmpty()) {
                CenterMessage("No movies in this category.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(movies, key = { it.key }) { m ->
                        PosterCard(
                            title = m.name,
                            imageUrl = m.poster,
                            subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                            onClick = { nav.navigate("movie/${m.key}") },
                        )
                    }
                }
            }
        }
    }
    gate.Dialog()
}

@Composable
fun SeriesScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    var cat by remember { mutableStateOf<String?>(null) }
    val categories by graph.content.categories(p.id, "series").collectAsStateWithLifecycle(initialValue = emptyList())
    val raw by (if (cat == null) graph.content.series(p.id) else graph.content.seriesIn(p.id, cat!!))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val gate = rememberParentalGate(graph, "series")
    val sort by graph.settings.vodSort.collectAsStateWithLifecycle(initialValue = "name")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var decadeFilter by remember { mutableStateOf<Int?>(null) }
    val genres = remember(raw) {
        raw.flatMap { tv.enktel.app.data.repo.ContentRepository.splitGenres(it.genre) }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(18).map { it.key }
    }
    val series = remember(raw, sort, genreFilter, decadeFilter) {
        raw.asSequence()
            .filter { s -> genreFilter == null || s.genre.contains(genreFilter!!, ignoreCase = true) }
            .filter { s ->
                when (decadeFilter) {
                    null -> true
                    -1 -> s.year in 1900..1989
                    else -> s.year in decadeFilter!!..(decadeFilter!! + 9)
                }
            }
            .let { seq ->
                when (sort) {
                    "rating" -> seq.sortedByDescending { it.rating }
                    "year", "added" -> seq.sortedByDescending { it.year }
                    else -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }

    Row(Modifier.fillMaxSize()) {
        CategorySidebar(
            "Series",
            categories.map { it.categoryId to it.name },
            cat,
            isLocked = gate.isLocked,
            onLocked = gate.prompt,
        ) { cat = it }
        Column(Modifier.fillMaxSize()) {
            FilterBar(
                sort = sort,
                sortOptions = listOf("name" to "A–Z", "rating" to "Top rated", "year" to "Newest"),
                onSort = { scope.launch { graph.settings.setVodSort(it) } },
                genres = genres,
                genreFilter = genreFilter,
                onGenre = { genreFilter = it },
                decadeFilter = decadeFilter,
                onDecade = { decadeFilter = it },
            )
            if (series.isEmpty()) {
                CenterMessage("No series match these filters.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(series, key = { it.key }) { s ->
                        PosterCard(
                            title = s.name,
                            imageUrl = s.poster,
                            subtitle = buildString {
                                if (s.year > 0) append(s.year)
                                if (s.rating > 0) { if (isNotEmpty()) append(" · "); append("★ ${"%.1f".format(s.rating)}") }
                            },
                            onClick = { nav.navigate("seriesDetails/${s.key}") },
                        )
                    }
                }
            }
        }
    }
    gate.Dialog()
}

/** Shared sort + genre + decade filter chips for the VOD browsers. */
@Composable
private fun FilterBar(
    sort: String,
    sortOptions: List<Pair<String, String>>,
    onSort: (String) -> Unit,
    genres: List<String>,
    genreFilter: String?,
    onGenre: (String?) -> Unit,
    decadeFilter: Int?,
    onDecade: (Int?) -> Unit,
) {
    Column(Modifier.padding(start = 24.dp, top = 16.dp)) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sortOptions) { (id, label) ->
                tv.enktel.app.ui.components.FocusButton(label, accent = sort == id, onClick = { onSort(id) })
            }
        }
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    tv.enktel.app.ui.components.FocusButton("All genres", accent = genreFilter == null, onClick = { onGenre(null) })
                }
                items(genres) { g ->
                    tv.enktel.app.ui.components.FocusButton(g, accent = genreFilter == g, onClick = {
                        onGenre(if (genreFilter == g) null else g)
                    })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                tv.enktel.app.ui.components.FocusButton("Any year", accent = decadeFilter == null, onClick = { onDecade(null) })
            }
            items(listOf(2020, 2010, 2000, 1990)) { d ->
                tv.enktel.app.ui.components.FocusButton("${d}s", accent = decadeFilter == d, onClick = {
                    onDecade(if (decadeFilter == d) null else d)
                })
            }
            item {
                tv.enktel.app.ui.components.FocusButton("Older", accent = decadeFilter == -1, onClick = {
                    onDecade(if (decadeFilter == -1) null else -1)
                })
            }
        }
    }
}
