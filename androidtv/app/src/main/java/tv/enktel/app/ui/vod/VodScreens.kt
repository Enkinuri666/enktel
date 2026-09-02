package tv.enktel.app.ui.vod

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
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
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.components.tvRailFocus
import tv.enktel.app.ui.components.tvGridFocus

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
    // v1.25.0 — trimmed from 230→168 dp so the poster grid has ~60 dp
    // more width to render posters in (an extra row of posters at 118 dp
    // adaptive cells on 1080p / 1280p).
    Column(
        Modifier.width(168.dp).fillMaxHeight().padding(top = 20.dp, start = 16.dp),
    ) {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
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

/**
 * v1.26.0 phone-portrait fallback: on the mobile flavor at portrait
 * widths the sidebar was eating ~40 % of the screen and squeezing the
 * poster grid down to a single column. This chip row collapses the
 * category picker into a horizontal scroll at the top of the screen so
 * the whole width is available for posters.
 */
@Composable
private fun CategoryChipRow(
    title: String,
    categories: List<Pair<String, String>>,
    selected: String?,
    isLocked: (String) -> Boolean = { false },
    onLocked: (String) -> Unit = {},
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.tvRailFocus(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                tv.enktel.app.ui.components.GlassChip(
                    "All", selected = selected == null,
                    accent = EnktelBlue,
                    onClick = { onSelect(null) },
                )
            }
            items(categories) { (id, name) ->
                val locked = isLocked(id)
                tv.enktel.app.ui.components.GlassChip(
                    (if (locked) "🔒 " else "") + name,
                    selected = selected == id,
                    accent = EnktelBlue,
                    onClick = { if (locked) onLocked(id) else onSelect(id) },
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) EnktelBlue.copy(0.18f) else Color.Transparent,
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = if (selected) Color.White else EnktelTextDim,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left accent stripe mirrors the ContentRail / Recordings section headers.
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(if (selected) EnktelBlue else Color.Transparent, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text, fontSize = 13.sp, maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(PaddingValues(top = 8.dp, bottom = 8.dp, end = 8.dp)),
            )
        }
    }
}

/**
 * Shared backdrop shell for the Movies and Series browsers.
 *
 * Installs the [tv.enktel.app.ui.components.FocusedPosterState] every
 * [PosterCard] reports into, then layers, back to front:
 *   1. the Ambilight colour wash pulled from the focused poster,
 *   2. the hover auto-trailer for that poster once focus settles,
 *   3. the grid itself.
 *
 * Both backdrop layers sit below the content in Z order and take no input, so
 * D-pad focus and touch behave exactly as they did before.
 */
@Composable
private fun VodBrowseShell(graph: AppGraph, content: @Composable () -> Unit) {
    val focusedPoster = tv.enktel.app.ui.components.rememberFocusedPosterState()
    androidx.compose.runtime.CompositionLocalProvider(
        tv.enktel.app.ui.components.LocalFocusedPoster provides focusedPoster,
    ) {
        Box(Modifier.fillMaxSize()) {
            tv.enktel.app.ui.components.AmbilightGlow(
                imageUrl = focusedPoster.currentUrl,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            tv.enktel.app.ui.components.AutoTrailerLayer(graph)
            content()
        }
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
    val sort by graph.settings.vodSort.collectAsStateWithLifecycle(initialValue = "added")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var decadeFilter by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    val genres = remember(raw) {
        raw.flatMap { tv.enktel.app.data.repo.ContentRepository.splitGenres(it.genre) }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(18).map { it.key }
    }
    val movies = remember(raw, sort, genreFilter, decadeFilter, query) {
        val needle = query.trim().lowercase()
        raw.asSequence()
            .filter { m -> genreFilter == null || m.genre.contains(genreFilter!!, ignoreCase = true) }
            .filter { m -> tv.enktel.app.data.repo.VodFilters.matchesYear(m.year, decadeFilter) }
            .filter { m ->
                if (needle.isEmpty()) true
                else m.name.lowercase().contains(needle) ||
                    m.cast.lowercase().contains(needle) ||
                    m.director.lowercase().contains(needle)
            }
            .let { seq ->
                when (sort) {
                    // Sorting on the panel's own number ordered a shelf of
                    // zeroes on any lineup that does not publish ratings —
                    // which is most of them — while a real IMDb rating sat
                    // unused in the same row. RatingRank prefers that one and
                    // weights it by how many votes it rests on.
                    "rating" -> seq.sortedByDescending {
                        tv.enktel.app.data.metadata.RatingRank.score(it.imdbRating, it.imdbVotes, it.rating)
                    }
                    "added" -> seq.sortedByDescending { it.addedAt }
                    // Year alone left every title the panel gave no year for in
                    // one undifferentiated block in query order; ingest time
                    // breaks the tie.
                    "year" -> tv.enktel.app.data.repo.VodFilters
                        .newest(seq.toList(), { it.year }, { it.addedAt }).asSequence()
                    else -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }
    val gate = rememberParentalGate(graph, "vod")

    // v1.26.0 — phone portrait uses chip-row + full-width grid instead of a
    // vertical sidebar, so the poster grid isn't squeezed into a single
    // column on ~410 dp handsets. TV and tablet/landscape keep the sidebar.
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val narrow = tv.enktel.app.BuildConfig.FLAVOR == "mobile" && shape.narrow
    // Smaller tiles when the viewport is short. A landscape phone was handed
    // the full 118 dp cell, so the grid showed one and a bit rows of posters
    // between the header and the nav bar — wide, and almost nothing in it.
    val cellSize = when {
        shape.short -> 92.dp
        narrow -> 104.dp
        else -> 118.dp
    }

    VodBrowseShell(graph) {
        if (narrow) {
            Column(Modifier.fillMaxSize()) {
                CategoryChipRow(
                    "Movies",
                    categories.map { it.categoryId to it.name },
                    cat,
                    isLocked = gate.isLocked,
                    onLocked = gate.prompt,
                ) { cat = it }
                MoviesGrid(
                    movies = movies, categories = categories, cat = cat, nav = nav,
                    cellSize = cellSize, sort = sort, genres = genres,
                    genreFilter = genreFilter, onGenre = { genreFilter = it },
                    decadeFilter = decadeFilter, onDecade = { decadeFilter = it },
                    onSort = { scope.launch { graph.settings.setVodSort(it) } },
                    query = query, onQuery = { query = it },
                )
            }
        } else Row(Modifier.fillMaxSize()) {
            CategorySidebar(
                "Movies",
                categories.map { it.categoryId to it.name },
                cat,
                isLocked = gate.isLocked,
                onLocked = gate.prompt,
            ) { cat = it }
            Column(Modifier.fillMaxSize()) {
                MoviesGrid(
                    movies = movies, categories = categories, cat = cat, nav = nav,
                    cellSize = cellSize, sort = sort, genres = genres,
                    genreFilter = genreFilter, onGenre = { genreFilter = it },
                    decadeFilter = decadeFilter, onDecade = { decadeFilter = it },
                    onSort = { scope.launch { graph.settings.setVodSort(it) } },
                    query = query, onQuery = { query = it },
                )
            }
        }
    }
    gate.Dialog()
}

@Composable
private fun MoviesGrid(
    movies: List<tv.enktel.app.data.db.Movie>,
    categories: List<tv.enktel.app.data.db.Category>,
    cat: String?,
    nav: NavHostController,
    cellSize: androidx.compose.ui.unit.Dp,
    sort: String,
    genres: List<String>,
    genreFilter: String?,
    onGenre: (String?) -> Unit,
    decadeFilter: Int?,
    onDecade: (Int?) -> Unit,
    onSort: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FilterBar(
            sort = sort,
            sortOptions = listOf("name" to "A–Z", "rating" to "Top rated", "added" to "Recently added", "year" to "Newest"),
            onSort = onSort,
            genres = genres,
            genreFilter = genreFilter,
            onGenre = onGenre,
            decadeFilter = decadeFilter,
            onDecade = onDecade,
            query = query,
            onQuery = onQuery,
            resultCount = movies.size,
        )
        if (movies.isEmpty()) {
            CenterMessage("No movies in this category.")
        } else {
            LazyVerticalGrid(
                // v1.25.0/v1.26.0 — denser adaptive grid: 104-118 dp cells.
                columns = GridCells.Adaptive(cellSize),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().tvGridFocus(),
            ) {
                    val hero = movies.firstOrNull { it.poster.isNotBlank() && it.rating >= 6.0 }
                        ?: movies.firstOrNull { it.poster.isNotBlank() }
                    if (hero != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CategoryHero(
                                title = hero.name,
                                poster = hero.poster,
                                rating = hero.rating,
                                year = hero.year,
                                genre = hero.genre,
                                subtitle = if (cat != null) categories.firstOrNull { it.categoryId == cat }?.name ?: "Featured" else "Top pick",
                                onOpen = { nav.navigate("movie/${hero.key}") },
                            )
                        }
                    }
                items(movies, key = { it.key }) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else if (m.year > 0) "${m.year}" else "",
                        onClick = { nav.navigate("movie/${m.key}") },
                        // Enriched rows carry a TMDB id, which is all the hover
                        // auto-trailer needs to find the clip.
                        tmdbId = m.tmdbId,
                        isSeries = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesGrid(
    series: List<tv.enktel.app.data.db.Series>,
    categories: List<tv.enktel.app.data.db.Category>,
    cat: String?,
    nav: NavHostController,
    cellSize: androidx.compose.ui.unit.Dp,
    sort: String,
    genres: List<String>,
    genreFilter: String?,
    onGenre: (String?) -> Unit,
    decadeFilter: Int?,
    onDecade: (Int?) -> Unit,
    onSort: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FilterBar(
            sort = sort,
            sortOptions = listOf("name" to "A–Z", "rating" to "Top rated", "year" to "Newest"),
            onSort = onSort,
            genres = genres,
            genreFilter = genreFilter,
            onGenre = onGenre,
            decadeFilter = decadeFilter,
            onDecade = onDecade,
            query = query,
            onQuery = onQuery,
            resultCount = series.size,
        )
        if (series.isEmpty()) {
            CenterMessage("No series match these filters.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(cellSize),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().tvGridFocus(),
            ) {
                val hero = series.firstOrNull { it.poster.isNotBlank() && it.rating >= 6.0 }
                    ?: series.firstOrNull { it.poster.isNotBlank() }
                if (hero != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryHero(
                            title = hero.name,
                            poster = hero.poster,
                            rating = hero.rating,
                            year = hero.year,
                            genre = hero.genre,
                            subtitle = if (cat != null) categories.firstOrNull { it.categoryId == cat }?.name ?: "Featured" else "Top pick",
                            onOpen = { nav.navigate("seriesDetails/${hero.key}") },
                        )
                    }
                }
                items(series, key = { it.key }) { s ->
                    PosterCard(
                        title = s.name,
                        imageUrl = s.poster,
                        subtitle = buildString {
                            if (s.year > 0) append(s.year)
                            if (s.rating > 0) { if (isNotEmpty()) append(" · "); append("★ ${"%.1f".format(s.rating)}") }
                        },
                        onClick = { nav.navigate("seriesDetails/${s.key}") },
                        tmdbId = s.tmdbId,
                        isSeries = true,
                    )
                }
            }
        }
    }
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
    val sort by graph.settings.vodSort.collectAsStateWithLifecycle(initialValue = "added")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var decadeFilter by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    val genres = remember(raw) {
        raw.flatMap { tv.enktel.app.data.repo.ContentRepository.splitGenres(it.genre) }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(18).map { it.key }
    }
    val series = remember(raw, sort, genreFilter, decadeFilter, query) {
        val needle = query.trim().lowercase()
        raw.asSequence()
            .filter { s -> genreFilter == null || s.genre.contains(genreFilter!!, ignoreCase = true) }
            .filter { s -> tv.enktel.app.data.repo.VodFilters.matchesYear(s.year, decadeFilter) }
            .filter { s -> needle.isEmpty() || s.name.lowercase().contains(needle) }
            .let { seq ->
                when (sort) {
                    // Same reasoning as the film list above.
                    "rating" -> seq.sortedByDescending {
                        tv.enktel.app.data.metadata.RatingRank.score(it.imdbRating, it.imdbVotes, it.rating)
                    }
                    "year", "added" -> seq.sortedByDescending { it.year }
                    else -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }

    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val narrow = tv.enktel.app.BuildConfig.FLAVOR == "mobile" && shape.narrow
    // Smaller tiles when the viewport is short. A landscape phone was handed
    // the full 118 dp cell, so the grid showed one and a bit rows of posters
    // between the header and the nav bar — wide, and almost nothing in it.
    val cellSize = when {
        shape.short -> 92.dp
        narrow -> 104.dp
        else -> 118.dp
    }

    VodBrowseShell(graph) {
        if (narrow) {
            Column(Modifier.fillMaxSize()) {
                CategoryChipRow(
                    "Series",
                    categories.map { it.categoryId to it.name },
                    cat,
                    isLocked = gate.isLocked,
                    onLocked = gate.prompt,
                ) { cat = it }
                SeriesGrid(
                    series = series, categories = categories, cat = cat, nav = nav,
                    cellSize = cellSize, sort = sort, genres = genres,
                    genreFilter = genreFilter, onGenre = { genreFilter = it },
                    decadeFilter = decadeFilter, onDecade = { decadeFilter = it },
                    onSort = { scope.launch { graph.settings.setVodSort(it) } },
                    query = query, onQuery = { query = it },
                )
            }
        } else Row(Modifier.fillMaxSize()) {
            CategorySidebar(
                "Series",
                categories.map { it.categoryId to it.name },
                cat,
                isLocked = gate.isLocked,
                onLocked = gate.prompt,
            ) { cat = it }
            Column(Modifier.fillMaxSize()) {
                SeriesGrid(
                    series = series, categories = categories, cat = cat, nav = nav,
                    cellSize = cellSize, sort = sort, genres = genres,
                    genreFilter = genreFilter, onGenre = { genreFilter = it },
                    decadeFilter = decadeFilter, onDecade = { decadeFilter = it },
                    onSort = { scope.launch { graph.settings.setVodSort(it) } },
                    query = query, onQuery = { query = it },
                )
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
    query: String,
    onQuery: (String) -> Unit,
    resultCount: Int,
) {
    Column(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp)) {
        // v1.25.0 — inline title search. Filters the visible grid live
        // without navigating away to the global search screen; useful for
        // "I know it's in Movies, I just don't want to scroll to it".
        // TV project uses androidx.tv.material3, not compose-material3, so
        // we drive the field with the app's existing TvTextField helper
        // (BasicTextField wrapped with DPAD-friendly focus handling).
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
            Box(Modifier.weight(1f)) {
                tv.enktel.app.ui.components.TvTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = "🔍 Search titles, cast, or director",
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                FocusButton("Clear", onClick = { onQuery("") })
            }
        }
        // Sort is a *mode*, not a filter — exactly one applies and it reorders
        // the same set — so it gets a segmented control rather than another row
        // of chips indistinguishable from the genre and decade rows below it.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            tv.enktel.app.ui.components.SegmentedControl(
                segments = sortOptions.map { (id, label) ->
                    tv.enktel.app.ui.components.Segment(id, label)
                },
                selectedId = sort,
                onSelect = onSort,
            )
            Spacer(Modifier.weight(1f))
            // The count next to the grid is what makes a filter that returns
            // nothing legible: "0 titles" is a fact, an empty grid is a
            // mystery. Same reasoning as the category chips in Live TV.
            Text(
                if (resultCount == 1) "1 title" else "$resultCount titles",
                color = tv.enktel.app.ui.theme.EnktelTextDim,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            tv.enktel.app.ui.components.ChipRowLabel(
                "Genre",
                modifier = Modifier.padding(bottom = 6.dp),
            )
            androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.tvRailFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    tv.enktel.app.ui.components.GlassChip(
                        "All", selected = genreFilter == null,
                        accent = tv.enktel.app.ui.theme.EnktelPurple,
                        onClick = { onGenre(null) },
                    )
                }
                items(genres) { g ->
                    tv.enktel.app.ui.components.GlassChip(
                        g, selected = genreFilter == g,
                        accent = tv.enktel.app.ui.theme.EnktelPurple,
                        onClick = { onGenre(if (genreFilter == g) null else g) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        tv.enktel.app.ui.components.ChipRowLabel(
            "Year",
            modifier = Modifier.padding(bottom = 6.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.tvRailFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                tv.enktel.app.ui.components.GlassChip(
                    "Any", selected = decadeFilter == null,
                    accent = EnktelOk,
                    onClick = { onDecade(null) },
                )
            }
            // Chips and their captions both come from VodFilters, which is also
            // what evaluates them — the old code drew "2026+" here and matched a
            // ten-year window there, and nothing connected the two.
            items(tv.enktel.app.data.repo.VodFilters.YEAR_CHIPS) { d ->
                tv.enktel.app.ui.components.GlassChip(
                    tv.enktel.app.data.repo.VodFilters.label(d),
                    selected = decadeFilter == d,
                    accent = EnktelOk,
                    onClick = { onDecade(if (decadeFilter == d) null else d) },
                )
            }
        }
    }
}

/** Full-width Netflix-style spotlight card shown at the top of the Movies/Series grid. */
@Composable
private fun CategoryHero(
    title: String,
    poster: String,
    rating: Double,
    year: Int,
    genre: String,
    subtitle: String,
    onOpen: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EnktelSurfaceHigh)
            .tapClick(onOpen),
    ) {
        if (poster.isNotBlank()) {
            AsyncImage(
                model = poster, contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(24.dp).fillMaxWidth(0.62f).widthIn(max = 500.dp),
        ) {
            Text(
                subtitle.uppercase(),
                color = EnktelBlue, fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                title, color = Color.White,
                fontSize = 26.sp, fontWeight = FontWeight.Black,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (rating > 0) Text("★ ${"%.1f".format(rating)}", color = EnktelOk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (year > 0) Text("$year", color = Color.White.copy(0.8f), fontSize = 13.sp)
                if (genre.isNotBlank()) Text("· ${genre.take(30)}", color = Color.White.copy(0.65f), fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            FocusButton("▶  View", accent = true, onClick = onOpen)
        }
    }
}
