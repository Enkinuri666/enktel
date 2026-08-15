package tv.enktel.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.net.ChannelStatus
import tv.enktel.app.data.repo.ChannelFilters
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.components.tvGridFocus
import tv.enktel.app.ui.components.tvRailFocus
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * The Live TV landing screen: a browsable grid of channels, grouped by
 * category.
 *
 * Selecting "Live TV" used to jump straight into the player on whatever was
 * last watched, which makes the whole channel list unreachable unless you
 * already know how to get out of a running stream. Browsing first is how
 * every set-top box works, and it is what the nav rail's "Live TV" label
 * implies.
 *
 * Filtering is shared with the guide via [ChannelFilters] so both screens
 * agree about what a category contains and what a search matches. Every chip
 * carries its own count, so a filter that would show nothing says so before
 * you select it rather than after.
 */
@Composable
fun ChannelBrowserScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()

    val allChannels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by graph.settings.hiddenChannels.collectAsStateWithLifecycle(initialValue = emptySet())
    val favouriteChannels by graph.content.favoriteChannels(p.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val favouriteKeys = remember(favouriteChannels) { favouriteChannels.map { it.key }.toSet() }

    var categoryId by remember { mutableStateOf(ChannelFilters.ALL) }
    var query by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var radioOnly by remember { mutableStateOf<Boolean?>(false) }
    var showHidden by remember { mutableStateOf(false) }

    // Internet radio, fetched the first time the Radio view is opened.
    //
    // Most lines carry a token handful of radio streams, so this view used to
    // be all but empty. The directory supplies real stations already tagged
    // with a genre and a country, which is what the category chips group by.
    // Kept out of the channels table on purpose: a playlist refresh clears
    // that for the profile, and these have nothing to do with the playlist.
    var directoryRadio by remember { mutableStateOf<List<tv.enktel.app.data.db.Channel>>(emptyList()) }
    var radioLoading by remember { mutableStateOf(false) }
    var radioError by remember { mutableStateOf("") }
    LaunchedEffect(radioOnly, p.id) {
        if (radioOnly != true || directoryRadio.isNotEmpty() || radioLoading) return@LaunchedEffect
        radioLoading = true
        radioError = ""
        tv.enktel.app.data.net.RadioDirectory.fetch(graph.http).fold(
            onSuccess = { stations ->
                directoryRadio = with(tv.enktel.app.data.net.RadioDirectory) {
                    stations.mapIndexed { i, s -> s.toChannel(p.id, i) }
                }
            },
            onFailure = { radioError = it.message ?: "Could not reach the radio directory" },
        )
        radioLoading = false
    }

    // The line's own radio streams come first — they are what the subscription
    // paid for — with the directory appended behind them.
    val sourceChannels = remember(allChannels, directoryRadio, radioOnly) {
        if (radioOnly == true) allChannels + directoryRadio else allChannels
    }

    val categories = remember(sourceChannels) { ChannelFilters.categoriesOf(sourceChannels) }
    val counts = remember(sourceChannels, hidden) { ChannelFilters.categoryCounts(sourceChannels, hidden) }
    val channels = remember(
        sourceChannels, categoryId, query, favouriteKeys, hidden, favouritesOnly, radioOnly, showHidden,
    ) {
        ChannelFilters.apply(
            channels = sourceChannels,
            categoryId = categoryId,
            query = query,
            favourites = favouriteKeys,
            // "Manage hidden" is the one view where hidden channels have to be
            // visible — otherwise there is no way to ever unhide one.
            hidden = if (showHidden) emptySet() else hidden,
            favouritesOnly = favouritesOnly,
            radioOnly = radioOnly,
        ).let { list -> if (showHidden) list.filter { it.key in hidden } else list }
    }

    // Now-playing titles for the visible page, one query rather than one per card.
    var nowTitles by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }
    LaunchedEffect(channels, p.id) {
        val ids = channels.take(60).map { it.epgId }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) { nowTitles = emptyMap(); return@LaunchedEffect }
        val now = System.currentTimeMillis()
        nowTitles = graph.epg.window(p.id, ids, now - 4 * 3_600_000L, now + 3_600_000L)
            .mapNotNull { (id, list) ->
                list.firstOrNull { now in it.startMs..it.endMs }?.let { id to it }
            }.toMap()
    }

    val userLists by graph.db.userListDao().lists(p.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val statuses by ChannelStatus.states.collectAsStateWithLifecycle()
    LaunchedEffect(channels, p.id) {
        ChannelStatus.watch(graph, p, channels.take(ChannelStatus.MAX_WATCHED))
    }

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    // Padding from the viewport rather than the flavour — a landscape phone
    // has a TV's width and a third of its height, and was getting a TV's
    // margins on both axes. See ScreenShape.
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val hPad = shape.padH

    Column(Modifier.fillMaxSize().padding(top = shape.padV)) {
        Row(
            Modifier.padding(horizontal = hPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Live TV")
            Spacer(Modifier.weight(1f))
            Text(
                "${channels.size} of ${counts[ChannelFilters.ALL] ?: 0} channels",
                color = EnktelTextDim, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(shape.headerGap))

        // Mode chips: favourites, radio, hidden.
        LazyRow(
            modifier = Modifier.tvRailFocus(),
            contentPadding = PaddingValues(horizontal = hPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GlassChip(
                    "★ Favourites (${favouriteKeys.size})",
                    selected = favouritesOnly,
                    onClick = { favouritesOnly = !favouritesOnly; showHidden = false },
                )
            }
            item {
                GlassChip(
                    if (radioOnly == true) "📻 Radio" else "📺 TV",
                    selected = radioOnly == true,
                    onClick = { radioOnly = if (radioOnly == true) false else true },
                )
            }
            if (hidden.isNotEmpty()) {
                item {
                    GlassChip(
                        "🚫 Hidden (${hidden.size})",
                        selected = showHidden,
                        onClick = { showHidden = !showHidden; favouritesOnly = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Category chips, each with its own count.
        LazyRow(
            modifier = Modifier.tvRailFocus(),
            contentPadding = PaddingValues(horizontal = hPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GlassChip(
                    "All (${counts[ChannelFilters.ALL] ?: 0})",
                    selected = categoryId == ChannelFilters.ALL,
                    onClick = { categoryId = ChannelFilters.ALL },
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

        Box(Modifier.padding(horizontal = hPad)) {
            TvTextField(query, { query = it }, "Search channels — name, number or category")
        }
        Spacer(Modifier.height(12.dp))

        if (allChannels.isEmpty()) {
            CenterMessage("No channels yet — add a playlist in Settings.")
            return
        }
        if (channels.isEmpty()) {
            CenterMessage(
                when {
                    // Radio has a network step the other views do not, so it
                    // gets to say which of the three states it is in rather
                    // than showing "nothing here" while a fetch is running.
                    radioOnly == true && radioLoading -> "Loading radio stations…"
                    radioOnly == true && radioError.isNotBlank() ->
                        "Your line's radio channels are shown here. The station directory " +
                            "could not be reached — $radioError"
                    showHidden -> "Nothing hidden."
                    query.isNotBlank() -> "No channel matches \"$query\" in this filter."
                    favouritesOnly -> "No favourites yet — press and hold a channel to star it."
                    else -> "Nothing in this category."
                },
            )
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isMobile) 116.dp else 168.dp),
            modifier = Modifier.fillMaxSize().tvGridFocus(),
            contentPadding = PaddingValues(horizontal = hPad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(channels, key = { it.key }) { ch ->
                ChannelCard(
                    channel = ch,
                    favourite = ch.key in favouriteKeys,
                    isHidden = ch.key in hidden,
                    status = statuses[ch.key] ?: ChannelStatus.State.UNKNOWN,
                    nowTitle = nowTitles[ch.epgId]?.title.orEmpty(),
                    onPlay = {
                        scope.launch { graph.settings.setLastChannel(ch.key) }
                        nav.navigate("live?ch=${ch.key}")
                    },
                    onToggleFavourite = {
                        scope.launch { graph.content.toggleFavorite(p.id, "live", ch.streamId) }
                    },
                    onToggleHidden = { scope.launch { graph.settings.toggleHiddenChannel(ch.key) } },
                    lists = userLists,
                    onAddToList = { listId ->
                        scope.launch {
                            graph.db.userListDao().addItem(
                                tv.enktel.app.data.db.UserListItem(
                                    key = "$listId:${ch.key}", listId = listId, kind = "live",
                                    itemKey = ch.key, name = ch.name, poster = ch.logo,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * One channel tile: logo, number, what is on now, and a reachability dot.
 *
 * The logo gets a plate of its own rather than being drawn on the card
 * background — channel logos are a mix of transparent PNGs and white-on-white
 * JPEGs, and without the plate half a provider's line renders as blank tiles.
 */
@Composable
private fun ChannelCard(
    channel: Channel,
    favourite: Boolean,
    isHidden: Boolean,
    status: ChannelStatus.State,
    nowTitle: String,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleHidden: () -> Unit,
    lists: List<tv.enktel.app.data.db.UserList>,
    onAddToList: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shareToaster = tv.enktel.app.ui.components.LocalToaster.current
    Surface(
        onClick = { if (menuOpen) menuOpen = false else onPlay() },
        onLongClick = { menuOpen = !menuOpen },
        // tv-material's own onClick/onLongClick only fire on D-pad SELECT, so
        // touch needs its own gesture detector — the same split the rest of
        // the app handles with `tapClick`, extended here to long-press.
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(channel.key) {
                detectTapGestures(
                    onTap = { if (menuOpen) menuOpen = false else onPlay() },
                    onLongPress = { menuOpen = !menuOpen },
                )
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface,
            focusedContainerColor = EnktelSurfaceHigh,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, EnktelBlue),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                } else {
                    Text(
                        channel.name.take(2).uppercase(),
                        color = EnktelTextDim, fontSize = 22.sp, fontWeight = FontWeight.Black,
                    )
                }
                // Status + favourite markers, top corners.
                Row(
                    Modifier.align(Alignment.TopEnd).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (favourite) Text("★", color = EnktelBlue, fontSize = 12.sp)
                    if (isHidden) Text("🚫", fontSize = 10.sp)
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            when (status) {
                                ChannelStatus.State.UP -> EnktelOk
                                ChannelStatus.State.DOWN -> EnktelLive
                                ChannelStatus.State.UNKNOWN -> EnktelTextDim.copy(alpha = 0.45f)
                            },
                        ),
                    )
                }
                if (channel.num > 0) {
                    Text(
                        "${channel.num}",
                        color = EnktelTextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                channel.name,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                nowTitle.ifBlank { channel.categoryName.ifBlank { " " } },
                color = if (nowTitle.isNotBlank()) EnktelBlue else EnktelTextDim,
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )

            // Per-card overflow: long-press (or hold SELECT) reveals the two
            // actions that would otherwise need a separate management screen.
            if (menuOpen) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OverflowAction(
                        if (favourite) "★ Unstar" else "☆ Star",
                        Modifier.weight(1f),
                    ) { onToggleFavourite(); menuOpen = false }
                    OverflowAction(
                        if (isHidden) "Unhide" else "Hide",
                        Modifier.weight(1f),
                    ) { onToggleHidden(); menuOpen = false }
                }
                Spacer(Modifier.height(3.dp))
                // Send this channel to somebody. The link opens the same
                // channel in their EnkTel, or tells them plainly that their
                // line does not carry it — see ContentRepository.resolveShared.
                OverflowAction("↗ Share channel", Modifier.fillMaxWidth()) {
                    menuOpen = false
                    val shared = tv.enktel.app.ui.components.shareTarget(
                        context,
                        tv.enktel.app.DeepLink.Target.Channel(channel.streamId, channel.name),
                    )
                    if (!shared) shareToaster.info("Link copied to the clipboard")
                }
                // Themed lists, when the user has any. No "create list" here on
                // purpose — a naming field inside a grid tile is a bad place to
                // type, and My Lists already owns that.
                if (lists.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    lists.take(4).forEach { l ->
                        OverflowAction("+ ${l.name}", Modifier.fillMaxWidth()) {
                            onAddToList(l.id); menuOpen = false
                        }
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(28.dp).tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = EnktelBlue,
            contentColor = EnktelTextDim,
            focusedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
