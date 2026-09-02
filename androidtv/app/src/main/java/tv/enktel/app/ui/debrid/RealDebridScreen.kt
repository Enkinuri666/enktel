package tv.enktel.app.ui.debrid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.debrid.RealDebrid
import tv.enktel.app.data.debrid.RealDebridClient
import tv.enktel.app.data.download.humanBytes
import tv.enktel.app.data.repo.EnktelFeed
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.components.rememberScreenShape
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute

/**
 * What is already in the viewer's Real-Debrid account, and a box to paste a
 * link into.
 *
 * Both halves start from something the viewer already has: files they added to
 * their own account, and links they hold. Nothing here searches anywhere for
 * content — that is the line this feature is built on, and the absence of a
 * search box is the shape of it.
 *
 * Reached from Settings rather than the nav rail: it is an account view, and
 * it is empty and meaningless for the viewers who have no Real-Debrid
 * subscription, which is most of them.
 */
@Composable
fun RealDebridScreen(graph: AppGraph, nav: NavHostController) {
    val token by graph.settings.realDebridToken.collectAsStateWithLifecycle(initialValue = "")
    val shape = rememberScreenShape()
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf("") }
    var downloads by remember { mutableStateOf<List<RealDebridClient.Item>>(emptyList()) }
    var torrents by remember { mutableStateOf<List<RealDebridClient.Item>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }

    // Keyed on the token so disconnecting and reconnecting reloads rather than
    // showing the previous account's files.
    LaunchedEffect(token) {
        if (token.isBlank()) return@LaunchedEffect
        busy = true
        status = ""
        val client = graph.realDebrid()
        client.account().fold(
            onSuccess = {
                account = RealDebrid.accountLine(
                    it.username, it.type, it.expiration, EnktelFeed.todayEpochDay(),
                )
            },
            onFailure = { status = it.message.orEmpty() },
        )
        // Both lists are fetched, but a failure in one must not blank the
        // other: they are separate endpoints and one can be empty or refused
        // while the other is fine.
        client.downloads().onSuccess { downloads = it }.onFailure {
            if (status.isBlank()) status = it.message.orEmpty()
        }
        client.torrents().onSuccess { torrents = it.filter { t -> t.streamable } }
        busy = false
    }

    /** Send a direct URL to the player. */
    fun play(url: String, title: String, id: String) {
        // A stable progress key means Continue Watching works for these the
        // same as for anything else; the account id is stable across sessions
        // where the URL is not — an unrestricted link expires.
        nav.navigate(vodPlayerRoute(url = url, title = title, progressKey = "rd:$id"))
    }

    /** Resolve something that needs unrestricting first, then play it. */
    fun resolveAndPlay(item: RealDebridClient.Item) {
        if (busy) return
        busy = true
        status = "Resolving…"
        scope.launch {
            graph.realDebrid().unrestrict(item.download).fold(
                onSuccess = {
                    status = ""
                    busy = false
                    play(it.download, it.filename.ifBlank { item.filename }, it.id)
                },
                onFailure = { status = it.message.orEmpty(); busy = false },
            )
        }
    }

    if (token.isBlank()) {
        Column(Modifier.fillMaxSize().background(EnktelBg).padding(shape.padH, shape.padV)) {
            SectionTitle("Real-Debrid")
            CenterMessage(
                "No Real-Debrid account connected. Add your API token in " +
                    "Settings → Playlists to play links and files from your account.",
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(EnktelBg),
        contentPadding = PaddingValues(horizontal = shape.padH, vertical = shape.padV),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("Real-Debrid")
            if (account.isNotBlank()) {
                Text(account, color = EnktelTextDim, fontSize = 12.sp)
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, color = EnktelTextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }

        item {
            Text("PLAY A LINK", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "Paste a hoster link you already have. Real-Debrid turns it into a direct one.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
            TvTextField(link, { link = it }, "Hoster link")
            FocusButton(if (busy) "Working…" else "Unrestrict & play", accent = true, onClick = onClick@{
                if (busy || link.isBlank()) return@onClick
                busy = true
                status = "Resolving…"
                scope.launch {
                    graph.realDebrid().unrestrict(link).fold(
                        onSuccess = {
                            status = ""
                            busy = false
                            link = ""
                            play(it.download, it.filename.ifBlank { "Real-Debrid" }, it.id)
                        },
                        onFailure = { status = it.message.orEmpty(); busy = false },
                    )
                }
            })
            Spacer(Modifier.height(14.dp))
        }

        if (downloads.isNotEmpty()) {
            item {
                Text("MY DOWNLOADS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            items(downloads, key = { it.id }) { d ->
                // Already a direct URL, so this plays without a round trip.
                DebridRow(d) { play(d.download, d.filename, d.id) }
            }
        }

        if (torrents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                Text("IN MY ACCOUNT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    "Resolved when you play, because an unrestricted link expires.",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            items(torrents, key = { it.id }) { t ->
                DebridRow(t) { resolveAndPlay(t) }
            }
        }

        if (!busy && downloads.isEmpty() && torrents.isEmpty()) {
            item {
                CenterMessage(
                    "Nothing in this account yet. Add files on real-debrid.com, " +
                        "or paste a link above.",
                )
            }
        }
    }
}

@Composable
private fun DebridRow(item: RealDebridClient.Item, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.filename.ifBlank { "Untitled" },
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.bytes > 0) item.bytes.humanBytes() else "size unknown",
                color = EnktelTextDim,
                fontSize = 11.sp,
            )
        }
        FocusButton("▶ Play", onClick = onPlay)
    }
}
