package tv.enktel.app.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.epg.NowPlaying
import tv.enktel.app.data.hub.MediaHubRepository
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FrostCard
import tv.enktel.app.ui.components.FrostProgress
import tv.enktel.app.ui.components.FrostSectionTitle
import tv.enktel.app.ui.components.InfoChip
import tv.enktel.app.ui.components.LiveDotLabel
import tv.enktel.app.ui.components.Monogram
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.rememberScreenShape
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Media Hub — Live TV and on-demand in one place.
 *
 * Every card is a programme airing right now. When the line also carries the
 * title on demand, the card is drawn from the matched VOD row's artwork and
 * opening it goes to that title's detail page (play, episodes, "More Like
 * This"); otherwise the card tunes the channel live. An "On Demand" chip marks
 * the ones that bridge, which is the whole point — it shows the viewer where
 * linear and library meet.
 */
@Composable
fun MediaHubScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return

    val favChannels by remember(p.id) { graph.content.favoriteChannels(p.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val headChannels by remember(p.id) { graph.content.channelsHead(p.id, 40) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val lineup = remember(favChannels, headChannels) {
        (favChannels + headChannels).distinctBy { it.key }
    }

    val items by produceState(initialValue = emptyList<MediaHubRepository.HubItem>(), lineup, p.id) {
        value = if (lineup.isEmpty()) emptyList()
        else graph.mediaHub.onNow(p.id, lineup, limit = 30)
    }

    val shape = rememberScreenShape()
    val gutter = shape.padH

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = gutter, end = gutter, top = shape.padV, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Media Hub")
            Spacer(Modifier.width(12.dp))
            Text(
                "Live TV, matched to on demand",
                color = EnktelTextDim, fontSize = 15.sp,
            )
        }

        if (lineup.isEmpty()) {
            CenterMessage("No channels yet — add a playlist in Settings.")
            return
        }
        if (items.isEmpty()) {
            CenterMessage("Reading what's on now…")
            return
        }

        val onDemand = remember(items) { items.filter { it.hasVod } }
        val liveOnly = remember(items) { items.filter { !it.hasVod && it.now != null } }

        LazyColumn(
            contentPadding = PaddingValues(bottom = shape.padV + 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (onDemand.isNotEmpty()) {
                item {
                    HubRail(
                        title = "On Now — Also On Demand",
                        items = onDemand,
                        gutter = gutter,
                        onOpen = { nav.navigate(it) },
                    )
                }
            }
            if (liveOnly.isNotEmpty()) {
                item {
                    HubRail(
                        title = "On Now — Live",
                        items = liveOnly,
                        gutter = gutter,
                        onOpen = { nav.navigate(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HubRail(
    title: String,
    items: List<MediaHubRepository.HubItem>,
    gutter: androidx.compose.ui.unit.Dp,
    onOpen: (route: String) -> Unit,
) {
    Column {
        FrostSectionTitle(title, Modifier.padding(start = gutter, bottom = 10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items.size, key = { items[it].channel.key }) { i ->
                HubCard(items[i], onOpen)
            }
        }
    }
}

@Composable
private fun HubCard(item: MediaHubRepository.HubItem, onOpen: (route: String) -> Unit) {
    val now = item.now
    val route = item.match?.key ?: "live?ch=${item.channel.key}"
    FrostCard(onClick = { onOpen(route) }, width = 300.dp, height = 168.dp) { _ ->
        Box(Modifier.fillMaxSize()) {
            val art = item.match?.backdrop?.ifBlank { item.match.poster } ?: ""
            if (art.isNotBlank()) {
                AsyncImage(
                    model = art, contentDescription = now?.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF1F3038), Color(0xFF0E181D))),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = item.channel.logo, contentDescription = item.channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(0.55f).padding(top = 10.dp, bottom = 46.dp).fillMaxSize(),
                        )
                    } else {
                        Monogram(item.channel.name)
                    }
                }
            }
            // Readability scrim at the foot.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
                ),
            )
            // Top-right chips: LIVE, and On Demand when it bridges.
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.hasVod) InfoChip("On Demand", accent = EnktelOk)
            }
            Row(Modifier.align(Alignment.TopStart).padding(8.dp)) { LiveDotLabel() }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
                Text(
                    now?.title ?: item.channel.name,
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(EnktelBlue))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.channel.name + (if (item.channel.num > 0) "  ·  CH ${item.channel.num}" else ""),
                        color = EnktelTextDim, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (now != null) {
                    Spacer(Modifier.height(6.dp))
                    FrostProgress(
                        NowPlaying.progress(now.startMs, now.endMs, System.currentTimeMillis()),
                        color = if (item.hasVod) EnktelOk else EnktelBlue,
                    )
                }
            }
        }
    }
}
