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
    val movies = remember(raw, sort) {
        when (sort) {
            "rating" -> raw.sortedByDescending { it.rating }
            "added" -> raw.sortedByDescending { it.addedAt }
            else -> raw.sortedBy { it.name.lowercase() }
        }
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
            Row(
                Modifier.padding(start = 24.dp, top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("name" to "A–Z", "rating" to "Top rated", "added" to "Recently added").forEach { (id, label) ->
                    tv.enktel.app.ui.components.FocusButton(label, accent = sort == id, onClick = {
                        scope.launch { graph.settings.setVodSort(id) }
                    })
                }
            }
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
    val series by (if (cat == null) graph.content.series(p.id) else graph.content.seriesIn(p.id, cat!!))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val gate = rememberParentalGate(graph, "series")

    Row(Modifier.fillMaxSize()) {
        CategorySidebar(
            "Series",
            categories.map { it.categoryId to it.name },
            cat,
            isLocked = gate.isLocked,
            onLocked = gate.prompt,
        ) { cat = it }
        if (series.isEmpty()) {
            CenterMessage("No series in this category.")
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
                        subtitle = if (s.rating > 0) "★ ${"%.1f".format(s.rating)}" else "",
                        onClick = { nav.navigate("seriesDetails/${s.key}") },
                    )
                }
            }
        }
    }
    gate.Dialog()
}
