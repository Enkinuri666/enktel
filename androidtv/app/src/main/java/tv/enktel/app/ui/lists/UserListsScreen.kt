package tv.enktel.app.ui.lists

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.UserList
import tv.enktel.app.data.db.UserListItem
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.components.tvGridFocus
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * User-created themed lists — "Kids Saturday", "Footy", "Rainy Sunday".
 *
 * The one collection in the app that spans content kinds: a list can hold live
 * channels, films and series together, which is what makes it a *theme* rather
 * than another favourites bucket. Favourites is one flat starred set per kind
 * and the watchlist is "things I mean to watch"; neither can say "these nine
 * channels and four films belong together".
 */
@Composable
fun UserListsScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val dao = graph.db.userListDao()

    val lists by dao.lists(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var openList by remember { mutableStateOf<UserList?>(null) }
    var newName by remember { mutableStateOf("") }

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val hPad = if (isMobile) 20.dp else 48.dp

    val open = openList
    if (open != null) {
        ListDetail(graph, nav, open, onBack = { openList = null }, hPad = hPad)
        return
    }

    Column(Modifier.fillMaxSize().padding(top = if (isMobile) 12.dp else 20.dp)) {
        Row(Modifier.padding(horizontal = hPad), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("My Lists")
            Spacer(Modifier.weight(1f))
            Text("${lists.size} lists", color = EnktelTextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(horizontal = hPad),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) {
                TvTextField(newName, { newName = it }, "New list name")
            }
            Spacer(Modifier.width(10.dp))
            FocusButton("Create", accent = true, onClick = {
                val name = newName.trim()
                if (name.isBlank()) return@FocusButton
                scope.launch {
                    dao.createList(
                        UserList(profileId = p.id, name = name, sortIdx = lists.size),
                    )
                    newName = ""
                }
            })
        }
        Spacer(Modifier.height(14.dp))

        if (lists.isEmpty()) {
            CenterMessage(
                "No lists yet. Name one above, then press and hold any channel " +
                    "or title to add it.",
            )
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isMobile) 150.dp else 220.dp),
            modifier = Modifier.fillMaxSize().tvGridFocus(),
            contentPadding = PaddingValues(horizontal = hPad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(lists, key = { it.id }) { l ->
                ListTile(
                    list = l,
                    count = dao.itemCount(l.id).collectAsStateWithLifecycle(initialValue = 0).value,
                    onOpen = { openList = l },
                    onDelete = {
                        scope.launch { dao.deleteItemsOf(l.id); dao.deleteList(l.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ListTile(list: UserList, count: Int, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (confirming) confirming = false else onOpen() },
        onLongClick = { confirming = !confirming },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInputTap(onTap = { if (confirming) confirming = false else onOpen() },
                onLongPress = { confirming = !confirming }),
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
        Column(Modifier.padding(14.dp)) {
            Text(list.icon, fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                list.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (count == 1) "1 item" else "$count items",
                color = EnktelTextDim, fontSize = 11.sp,
            )
            if (confirming) {
                Spacer(Modifier.height(8.dp))
                FocusButton("Delete this list", onClick = onDelete, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ListDetail(
    graph: AppGraph,
    nav: NavHostController,
    list: UserList,
    onBack: () -> Unit,
    hPad: androidx.compose.ui.unit.Dp,
) {
    val scope = rememberCoroutineScope()
    val dao = graph.db.userListDao()
    val items by dao.items(list.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(Modifier.padding(horizontal = hPad), verticalAlignment = Alignment.CenterVertically) {
            FocusButton("‹ Lists", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            SectionTitle("${list.icon}  ${list.name}")
            Spacer(Modifier.weight(1f))
            Text("${items.size} items", color = EnktelTextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        if (items.isEmpty()) {
            CenterMessage("Nothing here yet — press and hold a channel or title to add it.")
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            modifier = Modifier.fillMaxSize().tvGridFocus(),
            contentPadding = PaddingValues(horizontal = hPad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.key }) { item ->
                ItemTile(
                    item = item,
                    onOpen = {
                        when (item.kind) {
                            "live" -> nav.navigate("live?ch=${item.itemKey}")
                            "series" -> nav.navigate("seriesDetails/${item.itemKey}")
                            else -> nav.navigate("movie/${item.itemKey}")
                        }
                    },
                    onRemove = { scope.launch { dao.removeItem(item.key) } },
                )
            }
        }
    }
}

@Composable
private fun ItemTile(item: UserListItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (confirming) confirming = false else onOpen() },
        onLongClick = { confirming = !confirming },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInputTap(onTap = { if (confirming) confirming = false else onOpen() },
                onLongPress = { confirming = !confirming }),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface,
            focusedContainerColor = EnktelSurfaceHigh,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    // Channels are 16:9 logos, films are 2:3 posters. One shape
                    // for both would letterbox half the grid, so the tile takes
                    // its aspect from the kind it holds.
                    .aspectRatio(if (item.kind == "live") 16f / 9f else 2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (item.poster.isNotBlank()) {
                    AsyncImage(
                        model = item.poster, contentDescription = item.name,
                        contentScale = if (item.kind == "live") ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(if (item.kind == "live") 6.dp else 0.dp),
                    )
                } else {
                    Text(item.name.take(2).uppercase(), color = EnktelTextDim, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(item.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (confirming) {
                Spacer(Modifier.height(4.dp))
                FocusButton("Remove", onClick = onRemove, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Touch equivalent of tv-material's `onClick` / `onLongClick`, which only fire
 * on D-pad SELECT. Both flavours share these screens, so both gestures need a
 * pointer path as well — `tapClick` covers only the short press.
 */
private fun Modifier.pointerInputTap(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(Unit) {
    detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
}
