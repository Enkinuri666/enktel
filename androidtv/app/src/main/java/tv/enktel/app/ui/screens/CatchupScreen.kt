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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute

// Lint false-positive: produceState's vararg-keys overload isn't recognized by the
// ProduceStateDoesNotAssignValue detector even though every producer below assigns `value`.
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun CatchupScreen(graph: AppGraph, nav: NavHostController, channelKey: String) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val channel by produceState<Channel?>(initialValue = null, channelKey) { value = graph.content.channel(channelKey) }
    val ch = channel ?: return
    val programs by produceState<List<EpgProgram>?>(initialValue = null, ch.key) {
        value = graph.epg.archive(p.id, ch.epgId, ch.archiveDays.coerceAtLeast(1))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        SectionTitle("Catch-up · ${ch.name}")
        Text(
            "Archive window: ${ch.archiveDays.coerceAtLeast(1)} day(s)",
            color = EnktelTextDim, fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
        when {
            programs == null -> CenterMessage("Loading archive…")
            programs!!.isEmpty() -> CenterMessage("No EPG archive data for this channel. Refresh the EPG in Settings.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(programs!!, key = { it.id }) { prog ->
                    val playArchive = {
                        val mins = (prog.endMs - prog.startMs) / 60000
                        val url = XtreamClient.timeshiftUrl(p, ch.streamId, prog.startMs, mins)
                        nav.navigate(vodPlayerRoute(url, "${ch.name} · ${prog.title}"))
                    }
                    Surface(
                        onClick = playArchive,
                        modifier = Modifier.fillMaxWidth().tapClick(playArchive),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = EnktelSurfaceHigh.copy(0.5f),
                            focusedContainerColor = EnktelBlue,
                            focusedContentColor = Color.White,
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(130.dp)) {
                                Text(
                                    TimeFormat.format("EEE d MMM", prog.startMs),
                                    fontSize = 12.sp, color = EnktelBlue, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${TimeFormat.format("HH:mm", prog.startMs)}–${TimeFormat.format("HH:mm", prog.endMs)}",
                                    fontSize = 11.sp, color = EnktelTextDim,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(prog.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (prog.desc.isNotBlank()) {
                                    Text(prog.desc, fontSize = 11.sp, color = EnktelTextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("⏪ Play", fontSize = 12.sp, color = EnktelTextDim)
                        }
                    }
                }
            }
        }
    }
}
