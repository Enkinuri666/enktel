package tv.enktel.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = graph.playlists.activeProfile()
    }
    val p = profile ?: return
    val autoplay by graph.settings.autoplayLast.collectAsStateWithLifecycle(initialValue = false)

    val continueWatching by graph.content.continueWatching(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val favChannels by graph.content.favoriteChannels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val recentMovies by graph.content.recentMovies(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val favMovies by graph.content.favoriteMovies(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allChannels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())

    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm · EEE d MMM", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 28.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_full),
                    contentDescription = "EnkTel",
                    modifier = Modifier.width(190.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(clock, color = EnktelTextDim, fontSize = 14.sp)
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FocusButton("▶  Live TV", accent = true, onClick = { nav.navigate("live?ch=") })
                FocusButton("TV Guide", onClick = { nav.navigate("guide") })
                FocusButton("Movies", onClick = { nav.navigate("movies") })
                FocusButton("Series", onClick = { nav.navigate("series") })
                FocusButton("Search", onClick = { nav.navigate("search") })
                FocusButton("Recordings", onClick = { nav.navigate("recordings") })
                FocusButton("Settings", onClick = { nav.navigate("settings") })
            }
        }
        if (continueWatching.isNotEmpty()) {
            item {
                ContentRail("Continue Watching", continueWatching, key = { it.key }) { cw ->
                    val pct = if (cw.durationMs > 0) " · ${(cw.positionMs * 100 / cw.durationMs)}%" else ""
                    PosterCard(
                        title = cw.name,
                        imageUrl = cw.poster,
                        subtitle = "Resume$pct",
                        wide = true,
                        onClick = { nav.navigate(vodPlayerRoute(cw.url, cw.name, cw.key)) },
                    )
                }
            }
        }
        if (favChannels.isNotEmpty()) {
            item {
                ContentRail("Favorite Channels", favChannels, key = { it.key }) { ch ->
                    PosterCard(
                        title = ch.name,
                        imageUrl = ch.logo,
                        subtitle = if (ch.num > 0) "CH ${ch.num}" else "",
                        wide = true,
                        onClick = { nav.navigate("live?ch=${ch.key}") },
                    )
                }
            }
        }
        if (recentMovies.isNotEmpty()) {
            item {
                ContentRail("Latest Movies", recentMovies, key = { it.key }) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        if (favMovies.isNotEmpty()) {
            item {
                ContentRail("Favorite Movies", favMovies, key = { it.key }) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        item {
            ContentRail("All Channels", allChannels.take(30), key = { it.key }) { ch ->
                PosterCard(
                    title = ch.name,
                    imageUrl = ch.logo,
                    subtitle = ch.categoryName,
                    wide = true,
                    onClick = { nav.navigate("live?ch=${ch.key}") },
                )
            }
        }
        item {
            Text(
                "Profile: ${p.name} · Synced ${if (p.lastSync > 0) SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.lastSync)) else "never"}",
                color = EnktelTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}
