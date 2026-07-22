package tv.enktel.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelTextDim

@Composable
fun OnboardingScreen(graph: AppGraph, onDone: () -> Unit) {
    var mode by remember { mutableStateOf("xtream") }
    var name by remember { mutableStateOf("My Playlist") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_full),
                contentDescription = "EnkTel IPTV",
                modifier = Modifier.width(360.dp),
            )
            Spacer(Modifier.height(24.dp))
            tv.enktel.app.ui.components.ChipRowLabel("Playlist type")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.GlassChip("Xtream Codes", selected = mode == "xtream", onClick = { mode = "xtream" })
                tv.enktel.app.ui.components.GlassChip("M3U Playlist", selected = mode == "m3u", onClick = { mode = "m3u" })
            }
            Spacer(Modifier.height(20.dp))
            TvTextField(name, { name = it }, "Playlist name")
            Spacer(Modifier.height(12.dp))
            if (mode == "xtream") {
                TvTextField(server, { server = it }, "Server URL (http://host:port)")
                Spacer(Modifier.height(12.dp))
                TvTextField(username, { username = it }, "Username")
                Spacer(Modifier.height(12.dp))
                TvTextField(password, { password = it }, "Password", password = true)
            } else {
                TvTextField(m3uUrl, { m3uUrl = it }, "M3U URL")
                Spacer(Modifier.height(12.dp))
                TvTextField(epgUrl, { epgUrl = it }, "EPG / XMLTV URL (optional)")
            }
            Spacer(Modifier.height(8.dp))
            if (error.isNotBlank()) {
                Text(error, color = EnktelLive, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            FocusButton(
                if (busy) "Connecting…" else "Connect & Import",
                accent = true,
                onClick = onClick@{
                    if (busy) return@onClick
                    busy = true; error = ""
                    scope.launch {
                        val result = if (mode == "xtream") {
                            graph.playlists.addXtream(name, server, username, password)
                        } else {
                            graph.playlists.addM3u(name, m3uUrl, epgUrl)
                        }
                        result.fold(
                            onSuccess = { profile ->
                                runCatching { graph.content.refreshAll(profile) }
                                    .onFailure { error = "Imported profile but sync failed: ${it.message}" }
                                runCatching { graph.epg.refresh(profile) }
                                graph.playlists.markSynced(profile)
                                busy = false
                                onDone()
                            },
                            onFailure = {
                                busy = false
                                error = it.message ?: "Could not connect"
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your credentials stay on this device.",
                color = EnktelTextDim,
                fontSize = 12.sp,
            )
        }
    }
}
