package tv.enktel.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.PinDialog
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.tvGridFocus
import tv.enktel.app.ui.theme.EnktelBg

/**
 * Kids Mode home screen.
 *
 * A simplified, high-contrast, big-tap-target replacement for the full
 * Home dashboard, shown instead of it whenever Settings > Kids Mode is
 * enabled.  Content is auto-filtered to family/animation/kids genre
 * keywords and kid-safe live channel categories — no manual per-title
 * curation required, so it works immediately on any library.
 *
 * Exiting Kids Mode requires the same parental PIN used to lock VOD/
 * series categories elsewhere in the app, so a child can turn it on
 * freely but can't turn it back off alone.
 */
@Composable
fun KidsModeScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val pinHash by graph.settings.parentalPinHash.collectAsStateWithLifecycle(initialValue = "")

    var exitPrompt by remember { mutableStateOf(false) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(p.id) {
        val keywords = listOf("family", "animation", "kids", "children", "cartoon", "disney", "nick", "junior")
        val allMovies = try { graph.content.movies(p.id).first() } catch (_: Throwable) { emptyList() }
        movies = allMovies.filter { m -> keywords.any { it in m.genre.lowercase() } }.take(60)
        val allChannels = try { graph.content.channels(p.id).first() } catch (_: Throwable) { emptyList() }
        channels = allChannels.filter { c ->
            keywords.any { it in c.name.lowercase() || it in c.categoryName.lowercase() }
        }
    }

    Box(Modifier.fillMaxSize().background(EnktelBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧸 Kids Zone", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
            }

            if (movies.isEmpty() && channels.isEmpty()) {
                CenterMessage("No kid-safe content found yet. Ask a grown-up to refresh the playlist.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize().tvGridFocus(),
                ) {
                    items(channels, key = { "ch:${it.key}" }) { ch ->
                        PosterCard(
                            title = ch.name, imageUrl = ch.logo, wide = true,
                            onClick = { nav.navigate("live?ch=${ch.key}") },
                        )
                    }
                    items(movies, key = { "m:${it.key}" }) { m ->
                        PosterCard(
                            title = m.name, imageUrl = m.poster,
                            onClick = { nav.navigate("movie/${m.key}") },
                        )
                    }
                }
            }
        }

        // Big, obvious exit tap-target — top-right corner, full PIN gate.
        // Deliberately oversized (well over the 48dp a11y floor) so small
        // hands land it reliably, and requires the parental PIN so a child
        // can't back out of Kids Mode alone.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(pinHash) {
                    detectTapGestures {
                        if (pinHash.isBlank()) nav.navigate("settings") { popUpTo("home") }
                        else exitPrompt = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Exit Kids Mode",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(30.dp),
            )
        }
    }

    if (exitPrompt) {
        val toaster = tv.enktel.app.ui.components.LocalToaster.current
        PinDialog(
            title = "Enter PIN to exit Kids Mode",
            onSubmit = { pin ->
                if (tv.enktel.app.util.Pin.matches(pin, pinHash)) {
                    exitPrompt = false
                    nav.navigate("settings") { popUpTo("home") }
                } else {
                    toaster.error("Wrong PIN")
                }
            },
            onDismiss = { exitPrompt = false },
        )
    }
}
