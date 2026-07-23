package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Recording
import tv.enktel.app.dvr.RecordScheduler
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.ConfirmDialog
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.ThumbBox
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * DVR manager: sections recordings by lifecycle state, shows a storage-usage summary for
 * the recordings volume, and surfaces per-item actions (play / stop / cancel / delete).
 */
@Composable
fun RecordingsScreen(graph: AppGraph, nav: NavHostController) {
    val recordings by graph.db.recordingDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }

    val recordingNow = recordings.filter { it.status == "RECORDING" }
    val scheduled = recordings.filter { it.status == "SCHEDULED" }.sortedBy { it.startMs }
    val completed = recordings.filter { it.status == "DONE" }.sortedByDescending { it.startMs }
    val inactive = recordings.filter { it.status == "FAILED" || it.status == "CANCELLED" }.sortedByDescending { it.startMs }

    val usedBytes = remember(completed) { completed.sumOf { it.sizeBytes } }
    val freeBytes = remember(recordings.size) { RecordScheduler.freeStorageBytes(context) }
    // Palette colours are @Composable-getter values; snap them here so lazy-scope
    // extension calls (recordingSection) can take Color arguments.
    val liveAccent = EnktelLive
    val blueAccent = EnktelBlue
    val okAccent = EnktelOk

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            SectionTitle("DVR Recordings")
            Spacer(Modifier.height(14.dp))
            StorageSummary(usedBytes = usedBytes, freeBytes = freeBytes)
        }

        if (recordings.isEmpty()) {
            item { CenterMessage("No recordings yet. Use “Record now” in the Live player or schedule from the TV Guide.") }
            return@LazyColumn
        }

        recordingSection(title = "Recording Now", accent = liveAccent, items = recordingNow) { rec ->
            RecordingRow(
                rec = rec,
                onPlay = null,
                onStopOrCancel = { scope.launch { RecordScheduler.cancel(context, rec.id) } },
                onDelete = { pendingDelete = rec },
            )
        }

        recordingSection(title = "Scheduled", accent = blueAccent, items = scheduled) { rec ->
            RecordingRow(
                rec = rec,
                onPlay = null,
                onStopOrCancel = { scope.launch { RecordScheduler.cancel(context, rec.id) } },
                onDelete = { pendingDelete = rec },
            )
        }

        recordingSection(title = "Completed", accent = okAccent, items = completed) { rec ->
            RecordingRow(
                rec = rec,
                onPlay = {
                    if (rec.filePath.isNotBlank() && File(rec.filePath).exists()) {
                        nav.navigate(vodPlayerRoute("file://${rec.filePath}", rec.title))
                    }
                },
                onStopOrCancel = null,
                onDelete = { pendingDelete = rec },
            )
        }

        recordingSection(title = "Failed & Cancelled", accent = liveAccent, items = inactive) { rec ->
            RecordingRow(rec = rec, onPlay = null, onStopOrCancel = null, onDelete = { pendingDelete = rec })
        }
    }

    pendingDelete?.let { rec ->
        ConfirmDialog(
            title = "Delete recording?",
            message = "\"${rec.title}\" will be permanently removed" +
                if (rec.sizeBytes > 0) " (${formatBytes(rec.sizeBytes)} freed)." else ".",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    if (rec.filePath.isNotBlank()) runCatching { File(rec.filePath).delete() }
                    graph.db.recordingDao().delete(rec)
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recordingSection(
    title: String,
    accent: Color,
    items: List<Recording>,
    row: @Composable (Recording) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(18.dp)
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.3.sp)
            Spacer(Modifier.width(10.dp))
            Badge(items.size.toString(), accent)
        }
    }
    items(items, key = { it.id }) { rec ->
        Column { row(rec); Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StorageSummary(usedBytes: Long, freeBytes: Long) {
    val total = max(usedBytes + freeBytes, 1L)
    val fraction = (usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = EnktelSurfaceHigh.copy(0.5f), contentColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Storage used by recordings", fontSize = 12.sp, color = EnktelTextDim)
                Text(
                    "${formatBytes(usedBytes)} used · ${formatBytes(freeBytes)} free",
                    fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBarThin(fraction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecordingRow(
    rec: Recording,
    onPlay: (() -> Unit)?,
    onStopOrCancel: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val primaryAction = onPlay ?: onStopOrCancel ?: onDelete
    Surface(
        onClick = primaryAction,
        modifier = Modifier.fillMaxWidth().tapClick(primaryAction),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue.copy(0.35f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ThumbBox(label = rec.channelName, imageUrl = rec.channelLogo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rec.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when (rec.status) {
                        "RECORDING" -> Badge("● REC", EnktelLive)
                        "SCHEDULED" -> Badge("SCHEDULED")
                        "DONE" -> Badge("DONE", EnktelOk)
                        "FAILED" -> Badge("FAILED", EnktelLive)
                        "CANCELLED" -> Badge("CANCELLED", EnktelTextDim)
                    }
                }
                Text(
                    "${rec.channelName} · ${SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(rec.startMs))}" +
                        if (rec.sizeBytes > 0) " · ${formatBytes(rec.sizeBytes)}" else "",
                    fontSize = 12.sp, color = EnktelTextDim,
                )
                if (rec.status == "RECORDING") {
                    Spacer(Modifier.height(6.dp))
                    val now = System.currentTimeMillis()
                    if (rec.endMs > rec.startMs) {
                        val pct = ((now - rec.startMs).toFloat() / (rec.endMs - rec.startMs)).coerceIn(0f, 1f)
                        ProgressBarThin(pct, Modifier.width(160.dp))
                    } else {
                        Text("Recording · no end time set", fontSize = 11.sp, color = EnktelTextDim)
                    }
                } else if (rec.status == "SCHEDULED") {
                    val mins = (rec.startMs - System.currentTimeMillis()) / 60_000
                    Text(
                        if (mins > 0) "Starts in ${mins} min" else "Starting…",
                        fontSize = 11.sp, color = EnktelTextDim,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (onStopOrCancel != null) {
                FocusButton(if (rec.status == "RECORDING") "■ Stop" else "Cancel", onClick = onStopOrCancel)
                Spacer(Modifier.width(8.dp))
            }
            if (onPlay != null && rec.filePath.isNotBlank()) {
                FocusButton("▶ Play", accent = true, onClick = onPlay)
                Spacer(Modifier.width(8.dp))
            }
            FocusButton("Delete", onClick = onDelete)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1L shl 20 -> "${bytes / (1024 * 1024)} MB"
    bytes > 0 -> "${bytes / 1024} KB"
    else -> "0 MB"
}
