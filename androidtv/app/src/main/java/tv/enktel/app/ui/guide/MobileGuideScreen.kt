package tv.enktel.app.ui.guide

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.net.ChannelStatus
import tv.enktel.app.data.repo.ChannelFilters
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import tv.enktel.app.ui.components.tvRailFocus

/**
 * Phone-optimised TV Guide.
 *
 * Trades the 24-column horizontal grid for a stacked layout:
 *  - day picker (Today / +1 / +2 …)
 *  - category picker, with a live count beside every name
 *  - search + favourites filter
 *  - horizontally-scrollable channel rail (48 dp taps, logo + name + status)
 *  - vertical list of the selected channel's programs for the selected day
 *
 * Scrolling stays 1D on both axes and tap targets stay well over 48 dp.
 *
 * Two faults used to make this screen look permanently stuck on one channel:
 *
 *  1. The rail was capped at `channels.take(80)` with **no category filter at
 *     all**, so on a line whose first category is large you could only ever
 *     see channels from that one category — the "locked into the first
 *     category" report.
 *  2. The programme lookup was keyed on `epgId`, not on the channel. Regional
 *     variants share one guide id (SEVEN MATE, SEVEN MATE SYDNEY and SEVEN
 *     FLIX are all `seven.au`), so tapping between them never re-ran the
 *     lookup and the listing below stayed frozen on whatever loaded first —
 *     exactly what the screenshots showed, a changed selection above an
 *     unchanged listing.
 */
@Composable
internal fun MobileGuideScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val allChannels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val prefsChannelKey by graph.settings.lastChannel.collectAsStateWithLifecycle(initialValue = "")
    val hidden by graph.settings.hiddenChannels.collectAsStateWithLifecycle(initialValue = emptySet())
    val favouriteChannels by graph.content.favoriteChannels(p.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val favouriteKeys = remember(favouriteChannels) { favouriteChannels.map { it.key }.toSet() }

    var categoryId by remember { mutableStateOf(ChannelFilters.ALL) }
    var query by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }

    val categories = remember(allChannels) { ChannelFilters.categoriesOf(allChannels) }
    val counts = remember(allChannels, hidden) { ChannelFilters.categoryCounts(allChannels, hidden) }
    val channels = remember(allChannels, categoryId, query, favouriteKeys, hidden, favouritesOnly) {
        ChannelFilters.apply(
            channels = allChannels,
            categoryId = categoryId,
            query = query,
            favourites = favouriteKeys,
            hidden = hidden,
            favouritesOnly = favouritesOnly,
        )
    }

    // Selection is held by key, not by index. An index into a list that
    // re-filters underneath you silently points at a different channel.
    var selectedKey by remember { mutableStateOf("") }
    LaunchedEffect(channels, prefsChannelKey) {
        if (channels.none { it.key == selectedKey }) {
            selectedKey = channels.firstOrNull { it.key == prefsChannelKey }?.key
                ?: channels.firstOrNull()?.key.orEmpty()
        }
    }
    val selectedChannel = channels.firstOrNull { it.key == selectedKey }

    var dayOffset by remember { mutableIntStateOf(0) }
    // Observable read, so the day chips re-label if the device language changes.
    val dayLocale = TimeFormat.currentLocale()
    val dayStart = remember(dayOffset) {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L

    var programs by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    // Keyed on the channel's own key. See the class note: keying on epgId is
    // what froze this list.
    LaunchedEffect(selectedChannel?.key, dayOffset) {
        val ch = selectedChannel
        if (ch == null || ch.epgId.isBlank()) { programs = emptyList(); return@LaunchedEffect }
        programs = graph.epg.window(p.id, listOf(ch.epgId), dayStart, dayEnd)[ch.epgId].orEmpty()
    }

    // Reachability for whatever is on screen, so a dead channel is visible
    // before you tune to it rather than after.
    val statuses by ChannelStatus.states.collectAsStateWithLifecycle()
    LaunchedEffect(channels, p.id) {
        ChannelStatus.watch(graph, p, channels.take(ChannelStatus.MAX_WATCHED))
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val mult = tv.enktel.app.data.net.ThermalGuard.level.value.pollIntervalMultiplier
            kotlinx.coroutines.delay((60_000L * mult).toLong().coerceAtMost(600_000L))
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(programs, dayOffset) {
        if (dayOffset != 0 || programs.isEmpty()) return@LaunchedEffect
        val idx = programs.indexOfFirst { it.endMs > now }
        if (idx > 0) listState.scrollToItem((idx - 1).coerceAtLeast(0))
    }

    val railState = rememberLazyListState()
    // Keep the selected channel on screen when the rail re-filters.
    LaunchedEffect(selectedKey, channels) {
        val i = channels.indexOfFirst { it.key == selectedKey }
        if (i >= 0) railState.scrollToItem(i)
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("TV Guide")
            Spacer(Modifier.weight(1f))
            Text(
                "${channels.size} of ${counts[ChannelFilters.ALL] ?: 0}",
                color = EnktelTextDim, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))

        // Day picker chip strip
        LazyRow(
            modifier = Modifier.tvRailFocus(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GlassChip("Today", selected = dayOffset == 0, onClick = { dayOffset = 0 })
            }
            items((1..6).toList()) { d ->
                val label = TimeFormat.format(
                    "EEE d", System.currentTimeMillis() + d * 86_400_000L, dayLocale,
                )
                GlassChip(label, selected = dayOffset == d, onClick = { dayOffset = d })
            }
        }
        Spacer(Modifier.height(8.dp))

        // Category picker. Counts sit in the label so an empty filter explains
        // itself instead of just showing nothing.
        LazyRow(
            modifier = Modifier.tvRailFocus(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GlassChip(
                    "All (${counts[ChannelFilters.ALL] ?: 0})",
                    selected = categoryId == ChannelFilters.ALL,
                    onClick = { categoryId = ChannelFilters.ALL },
                )
            }
            item {
                GlassChip(
                    "★ Favourites (${favouriteKeys.size})",
                    selected = favouritesOnly,
                    onClick = { favouritesOnly = !favouritesOnly },
                )
            }
            items(categories, key = { it.first }) { (id, name) ->
                GlassChip(
                    "$name (${counts[id] ?: 0})",
                    selected = categoryId == id,
                    onClick = { categoryId = if (categoryId == id) ChannelFilters.ALL else id },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.padding(horizontal = 18.dp)) {
            TvTextField(query, { query = it }, "Search channels")
        }
        Spacer(Modifier.height(10.dp))

        if (allChannels.isEmpty()) {
            CenterMessage("No channels yet — add a playlist in Settings.")
            return
        }
        if (channels.isEmpty()) {
            CenterMessage(
                if (query.isNotBlank()) "No channel matches \"$query\" in this filter."
                else "Nothing here — try another category, or clear Favourites.",
            )
            return
        }

        // Channel rail — 48 dp min tap target, the whole filtered list.
        LazyRow(
            modifier = Modifier.tvRailFocus(),
            state = railState,
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.key }) { _, ch ->
                ChannelChip(
                    channel = ch,
                    selected = ch.key == selectedKey,
                    favourite = ch.key in favouriteKeys,
                    status = statuses[ch.key] ?: ChannelStatus.State.UNKNOWN,
                    onClick = { selectedKey = ch.key },
                    onLongClick = {
                        scope.launch { graph.settings.toggleHiddenChannel(ch.key) }
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Vertical program timeline for the selected channel + day
        val ch = selectedChannel
        if (ch == null) {
            CenterMessage("Pick a channel.")
        } else if (programs.isEmpty()) {
            CenterMessage("No guide data for ${ch.name} on this day yet.")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(programs, key = { it.id }) { prog ->
                    val isNow = now in prog.startMs..prog.endMs
                    val isPast = prog.endMs < now
                    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isNow) EnktelSurfaceHigh else EnktelSurface)
                            .pointerInput(prog.id) {
                                detectTapGestures {
                                    scope.launch { graph.settings.setLastChannel(ch.key) }
                                    nav.navigate("live?ch=${ch.key}")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(64.dp)) {
                            Text(
                                fmt.format(Date(prog.startMs)),
                                color = if (isPast) EnktelTextDim else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Black,
                            )
                            Text(
                                "→ ${fmt.format(Date(prog.endMs))}",
                                color = EnktelTextDim, fontSize = 10.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isNow) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EnktelLive)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    prog.title,
                                    color = if (isPast) EnktelTextDim else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isNow) {
                                val remainMin = ((prog.endMs - now) / 60_000L).coerceAtLeast(0)
                                Text(
                                    "$remainMin min left",
                                    color = EnktelLive, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else if (prog.desc.isNotBlank()) {
                                Text(
                                    prog.desc.take(90),
                                    color = EnktelTextDim, fontSize = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One channel in the picker rail, with its live reachability dot. */
@Composable
private fun ChannelChip(
    channel: Channel,
    selected: Boolean,
    favourite: Boolean,
    status: ChannelStatus.State,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) EnktelBlue else EnktelSurface)
            .pointerInput(channel.key) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (channel.logo.isNotBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black),
            )
        }
        Text(
            channel.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (favourite) Text("★", color = Color.White, fontSize = 12.sp)
        val dot = when (status) {
            ChannelStatus.State.UP -> EnktelOk
            ChannelStatus.State.DOWN -> EnktelLive
            ChannelStatus.State.UNKNOWN -> EnktelTextDim.copy(alpha = 0.5f)
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
    }
}
