package tv.enktel.app.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.ChipRowLabel
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.tvGridFocus
import tv.enktel.app.ui.theme.EnktelPurple

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun WatchlistScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    var kind by remember { mutableStateOf("all") }
    val items by when (kind) {
        "vod" -> graph.watchlist.ofKind(p.id, "vod")
        "series" -> graph.watchlist.ofKind(p.id, "series")
        else -> graph.watchlist.all(p.id)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    Column(Modifier.fillMaxSize().padding(top = shape.padV)) {
        Row(
            Modifier.padding(horizontal = shape.padH),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("My Watchlist")
            Spacer(Modifier.height(10.dp))
        }
        ChipRowLabel(
            "Filter",
            modifier = Modifier.padding(start = shape.padH, top = 6.dp, bottom = 6.dp),
        )
        Row(
            Modifier.padding(horizontal = shape.padH, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip("All (${items.size})", selected = kind == "all", accent = EnktelPurple, onClick = { kind = "all" })
            GlassChip("Movies", selected = kind == "vod", accent = EnktelPurple, onClick = { kind = "vod" })
            GlassChip("Series", selected = kind == "series", accent = EnktelPurple, onClick = { kind = "series" })
        }
        if (items.isEmpty()) {
            CenterMessage("Nothing saved yet. Press ☆ on a movie or series to add it here.")
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().tvGridFocus(),
        ) {
            items(items, key = { it.key }) { w ->
                PosterCard(
                    title = w.name,
                    imageUrl = w.poster,
                    subtitle = if (w.kind == "series") "Series" else "Movie",
                    onClick = {
                        if (w.kind == "vod") nav.navigate("movie/${w.profileId}:${w.refId}")
                        else nav.navigate("seriesDetails/${w.profileId}:${w.refId}")
                    },
                )
            }
        }
    }
}
