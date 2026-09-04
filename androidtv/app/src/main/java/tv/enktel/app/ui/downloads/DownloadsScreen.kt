package tv.enktel.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import tv.enktel.app.data.db.DownloadEntry
import tv.enktel.app.data.download.humanBytes
import tv.enktel.app.ui.components.CenterMessage
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import tv.enktel.app.data.download.DownloadLocation
import tv.enktel.app.data.share.LanShare
import tv.enktel.app.data.share.LanShareFiles
import tv.enktel.app.data.share.LanShareController
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import java.io.File

/**
 * File-manager style screen: everything the user has queued or saved offline,
 * grouped movies-first / episodes-under-series, with per-item play (offline
 * from the local file when done, else nothing), delete, and running progress.
 * A header shows total on-device usage.
 */
@Composable
fun DownloadsScreen(graph: AppGraph, nav: NavHostController) {
    val entries by graph.downloads.observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val totalBytes by graph.downloads.totalBytes.collectAsStateWithLifecycle()
    val speeds by graph.downloads.speeds.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf<DownloadEntry?>(null) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Set when the folder button lands on a file the system will not let
    // anything open — which is every download made before a folder was picked.
    var sealedNotice by remember { mutableStateOf<DownloadLocation.Reveal.Sealed?>(null) }

    // The same picker Settings uses. Offered here as well because this is
    // where the viewer actually discovers the problem: they wanted the file
    // and could not have it.
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
            scope.launch { graph.settings.setDownloadFolderUri(uri.toString()) }
            sealedNotice = null
        }
    }

    /** Open the folder holding a finished download, or say why that is impossible. */
    fun reveal(entry: DownloadEntry) {
        when (val r = DownloadLocation.reveal(entry.filePath, android.os.Build.VERSION.SDK_INT)) {
            is DownloadLocation.Reveal.Sealed -> sealedNotice = r
            is DownloadLocation.Reveal.Folder -> {
                // ACTION_VIEW on a tree URI is honoured by some file managers
                // and ignored by others, so the picker — which every device
                // has, and which opens *at* the folder — is the fallback
                // rather than a dead button.
                val view = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(r.treeUri.toUri(), android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ctx.packageManager.resolveActivity(view, 0) != null) {
                    runCatching { ctx.startActivity(view) }
                } else {
                    runCatching { folderPicker.launch(r.treeUri.toUri()) }
                }
            }
            is DownloadLocation.Reveal.Path -> {
                sealedNotice = DownloadLocation.Reveal.Sealed(
                    r.path,
                    "This file is at the path below. Open it with your file manager.",
                )
            }
            DownloadLocation.Reveal.NotYet -> Unit
        }
    }

    // Page padding is the overscan safe zone on TV, not taste: this screen
    // was insetting 20 dp against a band a cropping panel eats at 58 dp,
    // which put the first and last rows off the visible picture.
    val shape = tv.enktel.app.ui.components.rememberScreenShape()

    Column(Modifier.fillMaxSize().background(EnktelBg).padding(horizontal = shape.padH, vertical = shape.padV)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Downloads")
            Spacer(Modifier.width(12.dp))
            Text(
                "· ${totalBytes.humanBytes()} on device",
                color = EnktelTextDim, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        // Two different truths, and the difference matters: one of these
        // folders survives uninstall and can be read from a PC, and the other
        // cannot be opened by anything at all.
        val dlFolder by graph.settings.downloadFolderUri.collectAsStateWithLifecycle(initialValue = "")
        Text(
            if (dlFolder.isBlank()) {
                "Saved movies and episodes play offline. Files live in the app's private storage — " +
                    "uninstalling clears them, and Android does not let file managers open that folder. " +
                    "Pick a download folder to keep them somewhere you can reach."
            } else {
                "Saved movies and episodes play offline. Files go to the folder you picked, so they " +
                    "survive uninstalling and can be opened here or copied from a PC."
            },
            color = EnktelTextDim, fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        // Sending to a PC.
        //
        // The whole feature is: the phone runs a small web server, the viewer
        // types its address into a browser on their computer, and the file
        // comes across the house network. Nothing to install on the PC, no
        // account, and the file never leaves the building.
        val netKind by tv.enktel.app.data.net.NetworkClass.kind.collectAsStateWithLifecycle()
        // Owned by a foreground service, not by this screen. A film is several
        // gigabytes over house Wi-Fi — the viewer starts the transfer and then
        // goes to look at something else, and it has to keep running. Anything
        // that only works while you stare at it is not a feature. The ongoing
        // notification is what keeps "something is listening" visible for as
        // long as it is true.
        val sharing by LanShareController.current.collectAsStateWithLifecycle()
        var shareError by remember { mutableStateOf("") }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusButton(
                text = if (sharing != null) "■ Stop sending" else "📤 Send to PC",
                accent = sharing == null,
                onClick = {
                    if (sharing != null) {
                        LanShareController.stop(ctx)
                        shareError = ""
                        return@FocusButton
                    }
                    val blocked = LanShare.blockedReason(netKind)
                    if (blocked != null) { shareError = blocked; return@FocusButton }
                    val ip = LanShareFiles.localAddress()
                    if (ip == null) {
                        shareError = "Could not find this device's address on the network."
                        return@FocusButton
                    }
                    shareError = LanShareController
                        .start(ctx, ip, LanShareFiles.shareable(ctx, entries), graph.downloads)
                        .orEmpty()
                },
            )
        }
        sharing?.let { live ->
            Spacer(Modifier.height(8.dp))
            Text(
                "On your PC, open a browser and go to:",
                color = EnktelTextDim, fontSize = 12.sp,
            )
            Text(live.url, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("PIN ${live.pin}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Or open the EnkTel app on your PC — it finds this device on the network by " +
                    "itself, saves straight to a folder you pick, and can pause or cancel " +
                    "downloads from there.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
            Text(
                "The PIN is new each time you start sending. Both devices have to be on the same " +
                    "Wi-Fi. This keeps running while you use the rest of the app — stop it " +
                    "here, or from the notification, when you are done.",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }
        if (shareError.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(shareError, color = EnktelTextDim, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))

        if (entries.isEmpty()) {
            CenterMessage("Nothing downloaded yet. Open a movie or episode and tap ⬇ Download.")
            return
        }

        val (running, done) = entries.partition { it.status != "DONE" }
        // Movies get flat rows; episodes group under a per-series folder header
        // and sub-group by season number for readability.
        val doneMovies = done.filter { it.kind != "episode" }
        val doneEpisodes = done.filter { it.kind == "episode" }
        val doneSeries = doneEpisodes.groupBy { it.seriesName.ifBlank { "Series" } }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (running.isNotEmpty()) {
                item { GroupHeader("In progress · ${running.size}") }
                items(running, key = { it.id }) { entry ->
                    DownloadRow(
                        entry,
                        onPlay = null,
                        onDelete = { confirmDelete = entry },
                        onPause = { graph.downloads.pause(entry.id) },
                        onResume = { graph.downloads.resume(entry.id) },
                        speedBps = speeds[entry.id] ?: 0L,
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
            if (doneMovies.isNotEmpty()) {
                item { GroupHeader("Movies · ${doneMovies.size}") }
                items(doneMovies, key = { it.id }) { entry ->
                    DownloadRow(
                        entry,
                        onPlay = playAction(entry, nav),
                        onDelete = { confirmDelete = entry },
                        onReveal = { reveal(entry) },
                    )
                }
            }
            doneSeries.forEach { (seriesName, eps) ->
                item {
                    GroupHeader("Series · $seriesName · ${eps.size} episode${if (eps.size == 1) "" else "s"}")
                }
                val bySeason = eps.groupBy { it.season }.toSortedMap()
                bySeason.forEach { (season, list) ->
                    item {
                        Text(
                            "Season $season", color = EnktelTextDim, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp, start = 6.dp),
                        )
                    }
                    items(list.sortedBy { it.episode }, key = { it.id }) { entry ->
                        DownloadRow(
                            entry,
                            onPlay = playAction(entry, nav),
                            onDelete = { confirmDelete = entry },
                            onReveal = { reveal(entry) },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { target ->
        tv.enktel.app.ui.components.ConfirmDialog(
            title = "Remove download?",
            message = "\"${target.title}\" will be deleted from device storage.",
            confirmLabel = "Remove",
            onConfirm = {
                graph.downloads.delete(target.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    // Reuses the confirm dialog because the shape is the same — a sentence and
    // a decision. The decision here is whether to fix it for every future
    // download, which is the only thing that actually can be fixed: the file
    // already on disk stays where Android put it.
    sealedNotice?.let { notice ->
        tv.enktel.app.ui.components.ConfirmDialog(
            title = "Where this file lives",
            message = "${notice.because}\n\n${notice.path}",
            confirmLabel = "Pick a folder",
            onConfirm = { runCatching { folderPicker.launch(null) } },
            onDismiss = { sealedNotice = null },
        )
    }
}

/** "45s" / "12m" / "1h 20m" — coarse on purpose, since the estimate is only
 *  as good as the current rate and false precision reads as a promise. */
private fun formatEta(secs: Long): String = when {
    secs < 60 -> "${secs}s"
    secs < 3600 -> "${secs / 60}m"
    else -> "${secs / 3600}h ${(secs % 3600) / 60}m"
}

@Composable
private fun GroupHeader(text: String) {
    Text(text, color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

/** Shared "open the saved file offline" click handler. Falls back to the
 *  original stream URL if the local file has been evicted (e.g. user cleared
 *  storage from OS settings). */
private fun playAction(entry: DownloadEntry, nav: NavHostController): () -> Unit = {
    val local = if (entry.filePath.isNotBlank() && File(entry.filePath).exists()) {
        "file://${entry.filePath}"
    } else entry.sourceUrl
    val pk = if (entry.kind == "episode") {
        "${entry.profileId}:episode:${entry.refId}"
    } else "${entry.profileId}:vod:${entry.refId}"
    nav.navigate(vodPlayerRoute(local, entry.title, pk))
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onPlay: (() -> Unit)?,
    onDelete: () -> Unit,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    /** Where this file ended up. Null while it is still being written. */
    onReveal: (() -> Unit)? = null,
    /** Live transfer rate, bytes/sec. 0 when not moving. */
    speedBps: Long = 0L,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 68.dp, height = 92.dp).clip(RoundedCornerShape(8.dp)).background(EnktelSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.poster.isNotBlank()) {
                AsyncImage(
                    model = entry.poster, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                androidx.compose.foundation.Image(
                    imageVector = Icons.Filled.Downloading,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(EnktelTextDim),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (entry.seriesName.isNotBlank()) {
                Text(
                    entry.seriesName, color = EnktelTextDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                entry.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val status = entry.status
            val statusColor = when (status) {
                "DONE" -> EnktelOk
                "FAILED" -> EnktelLive
                "PAUSED" -> EnktelTextDim
                else -> EnktelBlue
            }
            val sizeText = if (entry.sizeBytes > 0) entry.sizeBytes.humanBytes() else "size unknown"
            val doneText = entry.downloadedBytes.humanBytes()
            // Speed and time-remaining while it's actually moving. A progress
            // bar alone doesn't answer the question people actually have, which
            // is "is this worth waiting for or should I come back later".
            val rateText = if (speedBps > 0) " · ${speedBps.humanBytes()}/s" else ""
            val etaText = if (speedBps > 0 && entry.sizeBytes > entry.downloadedBytes) {
                val secsLeft = (entry.sizeBytes - entry.downloadedBytes) / speedBps
                " · ${formatEta(secsLeft)} left"
            } else ""
            val statusLine = when (status) {
                "DONE" -> "Saved · $sizeText"
                "FAILED" -> "Failed · ${entry.errorMessage.ifBlank { "tap ↻ to resume" }}"
                "RUNNING" -> "$doneText / $sizeText$rateText$etaText"
                "PAUSED" -> if (entry.errorMessage.startsWith("Waiting for Wi-Fi")) {
                    "${entry.errorMessage} · $doneText / $sizeText"
                } else "Paused · $doneText / $sizeText · ▶ resumes here"
                else -> "Queued · $sizeText"
            }
            Text(statusLine, color = statusColor, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (status == "RUNNING" || status == "PAUSED" || status == "QUEUED") {
                Spacer(Modifier.height(6.dp))
                ProgressBarThin(entry.progressPct / 100f, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (onPlay != null) {
                FocusButton(
                    text = "▶",
                    accent = true,
                    onClick = onPlay,
                )
                Spacer(Modifier.height(6.dp))
            }
            // In-flight rows get a transport control instead: pause holds the
            // bytes already on disk, resume picks up from that exact offset.
            when {
                entry.status == "RUNNING" || entry.status == "QUEUED" ->
                    onPause?.let {
                        FocusButton(text = "⏸", onClick = it)
                        Spacer(Modifier.height(6.dp))
                    }
                entry.status == "PAUSED" ->
                    onResume?.let {
                        FocusButton(text = "▶", accent = true, onClick = it)
                        Spacer(Modifier.height(6.dp))
                    }
                entry.status == "FAILED" ->
                    onResume?.let {
                        FocusButton(text = "↻", accent = true, onClick = it)
                        Spacer(Modifier.height(6.dp))
                    }
            }
            // Only on a finished row: there is no folder to show until the
            // file exists, and offering one mid-download would point at a
            // partial file.
            if (entry.status == "DONE" && onReveal != null) {
                FocusButton(
                    text = DownloadLocation.buttonLabel(
                        DownloadLocation.reveal(entry.filePath, android.os.Build.VERSION.SDK_INT),
                    ),
                    onClick = onReveal,
                )
                Spacer(Modifier.height(6.dp))
            }
            FocusButton(text = "✕", onClick = onDelete)
        }
    }
}
