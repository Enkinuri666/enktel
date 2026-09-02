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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.debrid.DebridSearch
import tv.enktel.app.data.debrid.MagnetFlow
import tv.enktel.app.data.debrid.Magnets
import tv.enktel.app.data.debrid.RealDebrid
import tv.enktel.app.data.debrid.RealDebridClient
import tv.enktel.app.data.debrid.TorrentFiles
import tv.enktel.app.data.download.humanBytes
import tv.enktel.app.data.repo.EnktelFeed
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.components.rememberScreenShape
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute

/**
 * What is already in the viewer's Real-Debrid account, and boxes to add to it.
 *
 * Every half of this screen starts from something the viewer already has:
 * files in their own account, a hoster link they hold, a magnet they were
 * given. Nothing here searches anywhere for content — that is the line this
 * feature is built on, and the absence of a search box for the internet is the
 * shape of it. The search box that *is* here searches the account.
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
    var quota by remember { mutableStateOf<List<RealDebridClient.HostQuota>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<RealDebridClient.Item>>(emptyList()) }
    var torrents by remember { mutableStateOf<List<RealDebridClient.Item>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    // The magnet flow runs for minutes and keeps its own busy flag, so a
    // download in progress never blocks playing something else on the screen
    // behind it.
    var magnet by remember { mutableStateOf("") }
    var magnetStatus by remember { mutableStateOf("") }
    var magnetBusy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RealDebridClient.Torrent?>(null) }
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // A season pack in the account is one row with twenty links behind it.
    // Opening it lists them so the viewer can reach episode nine.
    var opened by remember { mutableStateOf<RealDebridClient.Item?>(null) }
    var episodes by remember { mutableStateOf<List<TorrentFiles.Playable>>(emptyList()) }

    /** Re-read both lists. Separate calls, so one failing must not blank the other. */
    suspend fun refreshLists(client: RealDebridClient) {
        client.downloads().onSuccess { downloads = it }.onFailure {
            if (status.isBlank()) status = it.message.orEmpty()
        }
        client.torrents().onSuccess { torrents = it.filter { t -> t.streamable } }
    }

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
        // Advisory only: an account with no capped hosters gets an empty list,
        // which is the common case and shows nothing.
        client.traffic().onSuccess { quota = it }
        refreshLists(client)
        busy = false
    }

    /** Send a direct URL to the player. */
    fun play(url: String, title: String, id: String) {
        // A stable progress key means Continue Watching works for these the
        // same as for anything else; the account id is stable across sessions
        // where the URL is not — an unrestricted link expires.
        nav.navigate(vodPlayerRoute(url = url, title = title, progressKey = "rd:$id"))
    }

    /** Resolve one restricted link and play what comes back. */
    fun resolveAndPlay(link: String, fallbackTitle: String) {
        if (busy) return
        busy = true
        status = "Resolving…"
        scope.launch {
            graph.realDebrid().unrestrict(link).fold(
                onSuccess = {
                    status = ""
                    busy = false
                    play(it.download, it.filename.ifBlank { fallbackTitle }, it.id)
                },
                onFailure = { status = it.message.orEmpty(); busy = false },
            )
        }
    }

    /**
     * Open a row in the account.
     *
     * One file plays straight away. Several means a pack, and playing the
     * first link — which is all the row can otherwise reach — hands back
     * episode one every time.
     */
    fun openTorrent(item: RealDebridClient.Item) {
        if (busy) return
        if (item.linkCount <= 1) {
            resolveAndPlay(item.download, item.filename)
            return
        }
        busy = true
        status = "Opening…"
        scope.launch {
            graph.realDebrid().torrentInfo(item.id).fold(
                onSuccess = {
                    busy = false
                    status = ""
                    opened = item
                    episodes = TorrentFiles.playable(it.files, it.links)
                },
                onFailure = { status = it.message.orEmpty(); busy = false },
            )
        }
    }

    /**
     * Watch a torrent until Real-Debrid has it, then play it.
     *
     * Bounded, and the bound is on the watching rather than on the download:
     * when it runs out the account carries on fetching and the item turns up
     * in the list below, which is what [MagnetFlow.stillGoingLine] says.
     */
    suspend fun awaitAndPlay(client: RealDebridClient, id: String, name: String) {
        var waited = 0L
        var attempt = 0
        while (waited < MagnetFlow.MAX_WAIT_MS) {
            val info = client.torrentInfo(id).getOrElse {
                magnetStatus = it.message.orEmpty()
                magnetBusy = false
                return
            }
            magnetStatus = MagnetFlow.progressLine(info.status, info.progress)
            if (MagnetFlow.isFailed(info.status)) {
                magnetBusy = false
                return
            }
            if (info.ready) {
                refreshLists(client)
                magnetBusy = false
                val first = info.links.firstOrNull()
                if (first.isNullOrBlank()) {
                    magnetStatus = "Real-Debrid has this but returned no link for it."
                    return
                }
                client.unrestrict(first).fold(
                    onSuccess = {
                        magnetStatus = ""
                        play(it.download, it.filename.ifBlank { info.filename }, it.id)
                    },
                    onFailure = { magnetStatus = it.message.orEmpty() },
                )
                return
            }
            val wait = MagnetFlow.pollDelayMs(attempt++)
            delay(wait)
            waited += wait
        }
        magnetStatus = MagnetFlow.stillGoingLine(name)
        magnetBusy = false
        refreshLists(client)
    }

    /** Hand a magnet the viewer pasted to their account. */
    fun startMagnet() {
        if (magnetBusy) return
        val parsed = Magnets.parse(magnet)
        if (parsed == null) {
            magnetStatus = "That is not a magnet link. It should start with magnet:?xt=urn:btih:"
            return
        }
        magnetBusy = true
        pending = null
        magnetStatus = "Checking whether Real-Debrid already has this…"
        scope.launch {
            val client = graph.realDebrid()
            // Asked before adding, because the answer changes what the viewer
            // should expect: cached is seconds, uncached is however long the
            // swarm takes. A failure to get an answer is not a failure to add,
            // so it is reported as "not cached" and the add goes ahead.
            val cached = client.isCached(parsed.infoHash).getOrDefault(false)
            magnetStatus = if (cached) {
                "Real-Debrid already has this — adding it."
            } else {
                "Real-Debrid does not have this yet — adding it, then fetching."
            }
            val id = client.addMagnet(parsed.uri).getOrElse {
                magnetStatus = it.message.orEmpty()
                magnetBusy = false
                return@launch
            }
            magnet = ""
            val name = parsed.displayName

            // The file list is not there the instant a magnet is added: the
            // service resolves the metadata first, and asking too early gets
            // an empty list that looks like a torrent with no files in it.
            var info = client.torrentInfo(id).getOrElse {
                magnetStatus = it.message.orEmpty()
                magnetBusy = false
                return@launch
            }
            var tries = 0
            while (info.files.isEmpty() && !MagnetFlow.isFailed(info.status) && tries < 10) {
                magnetStatus = MagnetFlow.progressLine(info.status, info.progress)
                delay(MagnetFlow.pollDelayMs(tries++))
                info = client.torrentInfo(id).getOrElse {
                    magnetStatus = it.message.orEmpty()
                    magnetBusy = false
                    return@launch
                }
            }
            if (MagnetFlow.isFailed(info.status)) {
                magnetStatus = MagnetFlow.progressLine(info.status, info.progress)
                magnetBusy = false
                return@launch
            }

            // One file is not a choice, so it is not offered as one.
            if (info.files.size > 1) {
                pending = info
                picked = TorrentFiles.suggested(info.files).toSet()
                magnetStatus = MagnetFlow.progressLine("waiting_files_selection", 0)
                magnetBusy = false
                return@launch
            }
            client.selectFiles(id, TorrentFiles.suggested(info.files)).onFailure {
                magnetStatus = it.message.orEmpty()
                magnetBusy = false
                return@launch
            }
            awaitAndPlay(client, id, name.ifBlank { info.filename })
        }
    }

    /** Commit the picker's selection and start waiting on it. */
    fun fetchPicked() {
        val t = pending ?: return
        if (magnetBusy || picked.isEmpty()) return
        magnetBusy = true
        pending = null
        magnetStatus = "Telling Real-Debrid what to fetch…"
        scope.launch {
            val client = graph.realDebrid()
            client.selectFiles(t.id, picked.toList().sorted()).onFailure {
                magnetStatus = it.message.orEmpty()
                magnetBusy = false
                return@launch
            }
            awaitAndPlay(client, t.id, t.filename)
        }
    }

    /** Abandon a torrent rather than leave it sitting unselected in the account. */
    fun cancelPending() {
        val t = pending ?: return
        pending = null
        magnetStatus = ""
        scope.launch { graph.realDebrid().deleteTorrent(t.id) }
    }

    fun remove(item: RealDebridClient.Item, fromDownloads: Boolean) {
        scope.launch {
            val client = graph.realDebrid()
            val result = if (fromDownloads) client.deleteDownload(item.id) else client.deleteTorrent(item.id)
            result.fold(
                // Removed locally too, rather than re-fetching both lists: the
                // account has one fewer item and a round trip to be told so
                // spends the rate limit to show what is already known.
                onSuccess = {
                    if (fromDownloads) downloads = downloads.filterNot { it.id == item.id }
                    else torrents = torrents.filterNot { it.id == item.id }
                },
                onFailure = { status = it.message.orEmpty() },
            )
        }
    }

    // Filtered once rather than inside the list builders, so the counts shown
    // beside the box and the rows below it cannot disagree.
    val shownDownloads = DebridSearch.filter(downloads, query) { it.filename }
    val shownTorrents = DebridSearch.filter(torrents, query) { it.filename }

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
            if (quota.isNotEmpty()) {
                Text(
                    quota.take(3).joinToString(" · ") {
                        RealDebrid.quotaLine(it.host, it.left, it.unit)
                    },
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, color = EnktelTextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }

        item {
            // Searches this account, not the internet. Real-Debrid publishes
            // no endpoint that looks for content — its API unrestricts links
            // and lists what you already have — so the only thing there is to
            // search is the account itself.
            Text("SEARCH MY ACCOUNT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            TvTextField(query, { query = it }, "Film or series name")
            if (query.isNotBlank()) {
                Text(
                    "${shownDownloads.size + shownTorrents.size} of " +
                        "${downloads.size + torrents.size} match",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
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

        item {
            Text("ADD A MAGNET", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "Paste a magnet you already have. Real-Debrid fetches it to your " +
                    "account and hands back a direct link — nothing is downloaded here.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
            TvTextField(magnet, { magnet = it }, "magnet:?xt=urn:btih:…")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton(
                    if (magnetBusy) "Working…" else "Add & play",
                    accent = true,
                    onClick = { startMagnet() },
                )
            }
            if (magnetStatus.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(magnetStatus, color = EnktelTextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
        }

        pending?.let { t ->
            item {
                Text("CHOOSE WHAT TO FETCH", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    // The suggestion is stated, because a viewer who does not
                    // know the samples were dropped for them cannot tell an
                    // opinionated default from a broken file list.
                    "${t.files.size} files. Videos are ticked and samples are not — change it if you like.",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            items(t.files, key = { "f${it.id}" }) { f ->
                val on = f.id in picked
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            f.name.ifBlank { f.path },
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (f.bytes > 0) f.bytes.humanBytes() else "size unknown",
                            color = EnktelTextDim,
                            fontSize = 11.sp,
                        )
                    }
                    GlassChip(
                        text = if (on) "✓ Fetch" else "Skip",
                        selected = on,
                        onClick = { picked = if (on) picked - f.id else picked + f.id },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton(
                        "Fetch ${picked.size} file${if (picked.size == 1) "" else "s"}",
                        accent = true,
                        onClick = { fetchPicked() },
                    )
                    FocusButton("Select all", onClick = { picked = t.files.map { f -> f.id }.toSet() })
                    FocusButton("Cancel", onClick = { cancelPending() })
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        opened?.let { pack ->
            item {
                Text("IN THIS PACK", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    pack.filename.ifBlank { "Untitled" },
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            items(episodes, key = { "e${it.link}" }) { e ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.name,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (e.bytes > 0) {
                            Text(e.bytes.humanBytes(), color = EnktelTextDim, fontSize = 11.sp)
                        }
                    }
                    FocusButton("▶ Play", onClick = { resolveAndPlay(e.link, e.name) })
                }
            }
            item {
                FocusButton("Close", onClick = { opened = null; episodes = emptyList() })
                Spacer(Modifier.height(14.dp))
            }
        }

        if (shownDownloads.isNotEmpty()) {
            item {
                Text("MY DOWNLOADS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            items(shownDownloads, key = { it.id }) { d ->
                // Already a direct URL, so this plays without a round trip.
                DebridRow(d, onPlay = { play(d.download, d.filename, d.id) }, onRemove = { remove(d, true) })
            }
        }

        if (shownTorrents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                Text("IN MY ACCOUNT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    "Resolved when you play, because an unrestricted link expires.",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
            items(shownTorrents, key = { it.id }) { t ->
                DebridRow(
                    t,
                    playLabel = if (t.linkCount > 1) "▶ ${t.linkCount} files" else "▶ Play",
                    onPlay = { openTorrent(t) },
                    onRemove = { remove(t, false) },
                )
            }
        }

        if (!busy && shownDownloads.isEmpty() && shownTorrents.isEmpty()) {
            item {
                CenterMessage(
                    // An account with nothing in it and a search that matched
                    // nothing look identical on screen and mean opposite
                    // things, so they say different words.
                    if (downloads.isEmpty() && torrents.isEmpty()) {
                        "Nothing in this account yet. Add files on real-debrid.com, " +
                            "or paste a link or magnet above."
                    } else {
                        "Nothing in this account matches \"$query\"."
                    },
                )
            }
        }
    }
}

@Composable
private fun DebridRow(
    item: RealDebridClient.Item,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    playLabel: String = "▶ Play",
) {
    // Removing is permanent at the account, and a remote's Select is easy to
    // press by accident, so the button asks once before it does it.
    var confirming by remember { mutableStateOf(false) }
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
        FocusButton(playLabel, onClick = onPlay)
        FocusButton(
            text = if (confirming) "Remove?" else "Remove",
            onClick = { if (confirming) onRemove() else confirming = true },
        )
    }
}
