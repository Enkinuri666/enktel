package tv.enktel.app.ui.watchlist

import androidx.compose.material.icons.rounded.BookmarkBorder
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
    // Collect the whole list once so the filter chips can show per-kind
    // counts without three separate Flows — the chips used to say
    // "All (12) · Movies · Series" with only the first count filled in,
    // which made the other two read as section labels rather than counts.
    val all by remember(p.id) { graph.watchlist.all(p.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val movieCount = remember(all) { all.count { it.kind == "vod" } }
    val seriesCount = remember(all) { all.count { it.kind == "series" } }
    val items = remember(all, kind) {
        when (kind) {
            "vod" -> all.filter { it.kind == "vod" }
            "series" -> all.filter { it.kind == "series" }
            else -> all
        }
    }

    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    Column(Modifier.fillMaxSize().padding(top = shape.padV)) {
        Row(
            Modifier.padding(horizontal = shape.padH),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("My Watchlist")
            Spacer(Modifier.height(10.dp))
        }
        // No "FILTER" caption: three chips reading All, Movies, Series under
        // a title that says Watchlist do not need to be told what they are.
        Row(
            Modifier.padding(start = shape.padH, end = shape.padH, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip("All (${all.size})", selected = kind == "all", onClick = { kind = "all" })
            GlassChip("Movies ($movieCount)", selected = kind == "vod", onClick = { kind = "vod" })
            GlassChip("Series ($seriesCount)", selected = kind == "series", onClick = { kind = "series" })
        }
        if (items.isEmpty()) {
            // Tells the viewer *which* list is empty, so the two cases read
            // differently: nothing saved anywhere → the onboarding message,
            // but nothing of the current kind → an invitation to see the
            // other kind instead of a repeat of the first-run copy.
            val msg = when {
                all.isEmpty() -> "Nothing saved yet. Choose Watchlist on a movie or series to keep it here."
                kind == "vod" -> "No movies in your watchlist yet."
                kind == "series" -> "No series in your watchlist yet."
                else -> "Nothing saved yet."
            }
            CenterMessage(
                msg,
                icon = androidx.compose.material.icons.Icons.Rounded.BookmarkBorder,
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(horizontal = shape.padH, vertical = 20.dp),
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
