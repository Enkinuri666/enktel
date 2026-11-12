package tv.enktel.app.ui.screens

import androidx.compose.material.icons.automirrored.rounded.Dvr
import androidx.compose.material.icons.rounded.Alarm
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
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Recording
import tv.enktel.app.dvr.RecordScheduler
import tv.enktel.app.dvr.RecordingAlarms
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
import kotlin.math.max

/**
 * DVR manager: sections recordings by lifecycle state, shows a storage-usage summary for
 * the recordings volume, and surfaces per-item actions (play / stop / cancel / delete).
 */
@Composable
fun RecordingsScreen(graph: AppGraph, nav: NavHostController) {
    val recordings by remember() { graph.db.recordingDao().all() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    val toaster = tv.enktel.app.ui.components.LocalToaster.current

    // Whether a scheduled recording can start with the app closed — see
    // RecordingAlarms. Asked again every time the screen comes back to the
    // front, because the only way to change the answer is to leave for
    // Settings and return; and when the answer has become yes, the alarms
    // Android cancelled while it was no are put back.
    var exactAllowed by remember { mutableStateOf(RecordingAlarms.exactAllowed(context)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        val allowed = RecordingAlarms.exactAllowed(context)
        exactAllowed = allowed
        if (allowed) scope.launch {
            runCatching { RecordingAlarms.rearmAll(context.applicationContext, graph.db.recordingDao()) }
        }
    }

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

    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = shape.padH,
            vertical = shape.padV,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            SectionTitle("DVR Recordings")
            Spacer(Modifier.height(14.dp))
            StorageSummary(usedBytes = usedBytes, freeBytes = freeBytes)
        }

        // Only while it matters: something is scheduled and it will not start
        // on its own. Nobody needs to hear about a permission for a feature
        // they are not using.
        if (scheduled.isNotEmpty() && !exactAllowed) {
            item {
                AlarmPermissionNotice(
                    onAllow = {
                        val opened = RecordingAlarms.settingsIntent(context)?.let { intent ->
                            runCatching { context.startActivity(intent); true }.getOrDefault(false)
                        } ?: false
                        // Some television builds have the permission and no
                        // screen to grant it on. Say so, rather than leave a
                        // button that does nothing.
                        if (!opened) toaster.error(ALARM_SETTING_MISSING)
                    },
                )
            }
        }

        if (recordings.isEmpty()) {
            item {
                CenterMessage(
                    "No recordings yet. Use “Record now” in the Live player or schedule from the TV Guide.",
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Dvr,
                )
            }
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
                    if (rec.status == "SCHEDULED") RecordingAlarms.cancel(context, rec.id)
                    // Deleting one that is still recording has to stop it
                    // first. Removing the file and the row left the recorder
                    // running: still downloading, into a file nothing could
                    // see, and still holding one of the line's connections.
                    if (rec.status == "RECORDING") runCatching { RecordScheduler.cancel(context, rec.id) }
                    if (rec.filePath.isNotBlank()) runCatching { File(rec.filePath).delete() }
                    runCatching { graph.db.recordingDao().delete(rec) }
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.3.sp)
            Spacer(Modifier.width(10.dp))
            Badge(items.size.toString(), accent)
        }
    }
    items(items, key = { it.id }) { rec ->
        Column { row(rec); Spacer(Modifier.height(8.dp)) }
    }
}

private const val ALARM_SETTING_MISSING =
    "This device has no setting for it. Scheduled recordings will start while EnkTel is open."

/**
 * Says, once and in the place it applies, that scheduled recordings will not
 * start by themselves — and offers the one way to change that.
 *
 * From Android 14 the permission that lets a recording start in the background
 * is off until the viewer turns it on, and there is no dialog an app can show
 * for it: it lives in system settings. Without this card the only sign would
 * be a recording that did not happen.
 */
@Composable
private fun AlarmPermissionNotice(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurfaceHigh.copy(0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Scheduled recordings can't start on their own yet",
            style = tv.enktel.app.ui.theme.EnktelType.subtitle,
            color = Color.White,
        )
        Text(
            "Android only lets EnkTel start a recording in the background if it is allowed to set " +
                "alarms. Until then, a scheduled recording starts only if EnkTel is open at the time. " +
                "Allow \"Alarms & reminders\" for EnkTel and it will start on time with the app closed.",
            style = tv.enktel.app.ui.theme.EnktelType.body,
            color = EnktelTextDim,
        )
        FocusButton(
            "Allow alarms",
            icon = androidx.compose.material.icons.Icons.Rounded.Alarm,
            accent = true,
            onClick = onAllow,
        )
    }
}

@Composable
private fun StorageSummary(usedBytes: Long, freeBytes: Long) {
    val total = max(usedBytes + freeBytes, 1L)
    val fraction = (usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = EnktelSurfaceHigh.copy(0.5f), contentColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            // A flow: label at one end and figures at the other when both
            // fit, figures underneath when they do not. As a row, a phone ran
            // "…recordings" straight into "2.1 GB used" with no space between.
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Storage used by recordings", fontSize = 12.sp, color = EnktelTextDim,
                    modifier = Modifier.padding(end = 12.dp),
                )
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
    // By the room there is, not by the build. Side by side, the two buttons
    // leave a phone's row about a finger's width for its words: the title
    // was cut to "…" and the line under it wrapped one syllable at a time,
    // twenty lines tall. This screen had only ever been photographed empty,
    // which is the one state with no rows in it. On a narrow screen the
    // buttons go underneath.
    val narrow = tv.enktel.app.ui.components.rememberScreenShape().narrow
    Surface(
        onClick = primaryAction,
        modifier = Modifier.fillMaxWidth().tapClick(primaryAction),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue.copy(0.35f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        if (narrow) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThumbBox(label = rec.channelName, imageUrl = rec.channelLogo)
                    Spacer(Modifier.width(14.dp))
                    RecordingFacts(rec, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordingActions(rec, onPlay, onStopOrCancel, onDelete)
                }
            }
        } else {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ThumbBox(label = rec.channelName, imageUrl = rec.channelLogo)
                Spacer(Modifier.width(14.dp))
                RecordingFacts(rec, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordingActions(rec, onPlay, onStopOrCancel, onDelete)
                }
            }
        }
    }
}

/** What a recording is: its name, its state, where and when, and how far along. */
@Composable
private fun RecordingFacts(rec: Recording, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // weight(fill = false): the title gives way to the badge, not the
            // other way round. Unweighted, a long title pushed its own status
            // off the end of the row.
            Text(
                rec.title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (rec.status) {
                "RECORDING" -> Badge("● REC", EnktelLive)
                "SCHEDULED" -> Badge("SCHEDULED")
                "DONE" -> Badge("DONE", EnktelOk)
                "FAILED" -> Badge("FAILED", EnktelLive)
                "CANCELLED" -> Badge("CANCELLED", EnktelTextDim)
            }
        }
        Text(
            "${rec.channelName} · ${TimeFormat.format("EEE d MMM HH:mm", rec.startMs)}" +
                if (rec.sizeBytes > 0) " · ${formatBytes(rec.sizeBytes)}" else "",
            fontSize = 12.sp, color = EnktelTextDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
                tv.enktel.app.dvr.RecordingOutcome.startsIn(mins),
                fontSize = 11.sp, color = EnktelTextDim,
            )
        }
    }
}

/** What can be done to it. Emitted into the caller's row, so the caller decides where that row sits. */
@Composable
private fun RecordingActions(
    rec: Recording,
    onPlay: (() -> Unit)?,
    onStopOrCancel: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    if (onStopOrCancel != null) {
        FocusButton(if (rec.status == "RECORDING") "■ Stop" else "Cancel", onClick = onStopOrCancel)
    }
    if (onPlay != null && rec.filePath.isNotBlank()) {
        FocusButton("▶ Play", accent = true, onClick = onPlay)
    }
    FocusButton("Delete", onClick = onDelete)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1L shl 20 -> "${bytes / (1024 * 1024)} MB"
    bytes > 0 -> "${bytes / 1024} KB"
    else -> "0 MB"
}
