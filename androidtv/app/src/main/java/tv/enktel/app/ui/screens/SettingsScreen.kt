package tv.enktel.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(graph: AppGraph, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val profiles by graph.playlists.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = 0L)
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")
    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    var status by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Settings")
        if (status.isNotBlank()) Text(status, color = EnktelOk, fontSize = 13.sp)

        Text("PLAYLISTS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        profiles.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton(
                    (if (p.id == activeId) "✓ " else "") + "${p.name} (${p.kind})",
                    accent = p.id == activeId,
                    onClick = { scope.launch { graph.playlists.switchTo(p.id) } },
                )
                FocusButton("Sync now", onClick = {
                    scope.launch {
                        status = "Syncing ${p.name}…"
                        status = runCatching { graph.content.refreshAll(p) }
                            .fold({ "Synced: $it" }, { "Sync failed: ${it.message}" })
                        graph.playlists.markSynced(p)
                    }
                })
                FocusButton("Refresh EPG", onClick = {
                    scope.launch {
                        status = "Downloading EPG…"
                        status = runCatching { graph.epg.refresh(p) }
                            .fold({ "EPG updated: $it programmes" }, { "EPG failed: ${it.message}" })
                    }
                })
                FocusButton("Remove", onClick = { scope.launch { graph.playlists.delete(p.id) } })
            }
            if (p.expiresAt > 0) {
                Text(
                    "Expires ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(p.expiresAt))} · max ${p.maxConnections} connection(s)",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
        }
        FocusButton("+ Add playlist", onClick = { nav.navigate("onboarding") })

        Spacer(Modifier.height(10.dp))
        Text("LIVE STREAM FORMAT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("HLS (m3u8)", accent = streamFormat == "hls", onClick = { scope.launch { graph.settings.setStreamFormat("hls") } })
            FocusButton("MPEG-TS", accent = streamFormat == "ts", onClick = { scope.launch { graph.settings.setStreamFormat("ts") } })
        }
        Text("MPEG-TS starts faster on some panels; HLS adapts quality automatically.", color = EnktelTextDim, fontSize = 11.sp)

        Text("PLAYER BUFFER", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("Fast zap", accent = bufferProfile == "low", onClick = { scope.launch { graph.settings.setBufferProfile("low") } })
            FocusButton("Balanced", accent = bufferProfile == "balanced", onClick = { scope.launch { graph.settings.setBufferProfile("balanced") } })
            FocusButton("Max stability", accent = bufferProfile == "large", onClick = { scope.launch { graph.settings.setBufferProfile("large") } })
        }
        Text("Buffer changes apply the next time a player opens.", color = EnktelTextDim, fontSize = 11.sp)

        Spacer(Modifier.height(10.dp))
        Text("ABOUT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("EnkTel IPTV · Stream Beyond Limits", color = Color.White, fontSize = 13.sp)
        Text("Android TV & Fire TV · Xtream Codes + M3U · EPG · Catch-up · DVR", color = EnktelTextDim, fontSize = 12.sp)
    }
}
