package tv.enktel.app.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
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

@Composable
fun RecordingsScreen(graph: AppGraph, nav: NavHostController) {
    val recordings by graph.db.recordingDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        SectionTitle("DVR Recordings")
        Spacer(Modifier.height(16.dp))
        if (recordings.isEmpty()) {
            CenterMessage("No recordings yet. Use “Record now” in the Live player or schedule from the TV Guide.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingRow(
                        rec = rec,
                        onPlay = {
                            if (rec.filePath.isNotBlank() && File(rec.filePath).exists()) {
                                nav.navigate(vodPlayerRoute("file://${rec.filePath}", rec.title))
                            }
                        },
                        onStopOrCancel = { scope.launch { RecordScheduler.cancel(context, rec.id) } },
                        onDelete = {
                            scope.launch {
                                if (rec.filePath.isNotBlank()) runCatching { File(rec.filePath).delete() }
                                graph.db.recordingDao().delete(rec)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: Recording, onPlay: () -> Unit, onStopOrCancel: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().tapClick(onPlay),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue.copy(0.35f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        if (rec.sizeBytes > 0) " · ${rec.sizeBytes / (1024 * 1024)} MB" else "",
                    fontSize = 12.sp, color = EnktelTextDim,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (rec.status == "RECORDING" || rec.status == "SCHEDULED") {
                FocusButton(if (rec.status == "RECORDING") "■ Stop" else "Cancel", onClick = onStopOrCancel)
                Spacer(Modifier.width(8.dp))
            }
            if (rec.status == "DONE" && rec.filePath.isNotBlank()) {
                FocusButton("▶ Play", accent = true, onClick = onPlay)
                Spacer(Modifier.width(8.dp))
            }
            FocusButton("Delete", onClick = onDelete)
        }
    }
}
