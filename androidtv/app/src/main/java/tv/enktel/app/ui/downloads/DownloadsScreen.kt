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

    Column(Modifier.fillMaxSize().background(EnktelBg).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Downloads")
            Spacer(Modifier.width(12.dp))
            Text(
                "· ${totalBytes.humanBytes()} on device",
                color = EnktelTextDim, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Saved movies and episodes play offline. Files live in the app's private storage — uninstalling clears them.",
            color = EnktelTextDim, fontSize = 12.sp,
        )
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
            FocusButton(text = "✕", onClick = onDelete)
        }
    }
}
