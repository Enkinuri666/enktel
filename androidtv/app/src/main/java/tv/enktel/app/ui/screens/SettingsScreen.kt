package tv.enktel.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
        Text("DVR RECORDING PADDING", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val recPrefix by graph.settings.recPrefixMin.collectAsStateWithLifecycle(initialValue = 2)
        val recSuffix by graph.settings.recSuffixMin.collectAsStateWithLifecycle(initialValue = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton("Start ${recPrefix}m early", onClick = {
                scope.launch { graph.settings.setRecPrefixMin(when (recPrefix) { 0 -> 2; 2 -> 5; 5 -> 10; else -> 0 }) }
            })
            FocusButton("End ${recSuffix}m late", onClick = {
                scope.launch { graph.settings.setRecSuffixMin(when (recSuffix) { 0 -> 5; 5 -> 10; 10 -> 15; else -> 0 }) }
            })
        }

        Spacer(Modifier.height(10.dp))
        Text("PARENTAL CONTROLS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val pinHash by graph.settings.parentalPinHash.collectAsStateWithLifecycle(initialValue = "")
        val lockedCats by graph.settings.lockedCategories.collectAsStateWithLifecycle(initialValue = emptySet())
        var newPin by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.width(180.dp)) {
                tv.enktel.app.ui.components.TvTextField(
                    newPin, { newPin = it.filter(Char::isDigit).take(8) },
                    if (pinHash.isBlank()) "Set PIN (digits)" else "Change PIN", password = true,
                )
            }
            FocusButton("Save PIN", onClick = {
                if (newPin.length >= 4) scope.launch {
                    graph.settings.setParentalPin(tv.enktel.app.util.Pin.hash(newPin))
                    newPin = ""
                    status = "Parental PIN saved"
                } else status = "PIN must be at least 4 digits"
            })
            if (pinHash.isNotBlank()) {
                FocusButton("Remove PIN", onClick = {
                    scope.launch {
                        graph.settings.setParentalPin("")
                        graph.settings.setLockedCategories(emptySet())
                        status = "Parental controls disabled"
                    }
                })
            }
        }
        if (pinHash.isNotBlank()) {
            Text("Tap a category to lock/unlock it (🔒 = PIN required):", color = EnktelTextDim, fontSize = 11.sp)
            val liveCats by graph.content.categories(activeId, "live").collectAsStateWithLifecycle(initialValue = emptyList())
            val vodCats by graph.content.categories(activeId, "vod").collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesCats by graph.content.categories(activeId, "series").collectAsStateWithLifecycle(initialValue = emptyList())
            (liveCats + vodCats + seriesCats).chunked(3).forEach { rowCats ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCats.forEach { cat ->
                        val lockKey = "${cat.kind}:${cat.categoryId}"
                        val locked = lockKey in lockedCats
                        FocusButton(
                            (if (locked) "🔒 " else "") + "[${cat.kind}] ${cat.name}",
                            accent = locked,
                            onClick = {
                                scope.launch {
                                    graph.settings.setLockedCategories(
                                        if (locked) lockedCats - lockKey else lockedCats + lockKey
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("SUBTITLES", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val subColor by graph.settings.subColor.collectAsStateWithLifecycle(initialValue = "white")
        val subEdge by graph.settings.subEdge.collectAsStateWithLifecycle(initialValue = "outline")
        val subBg by graph.settings.subBgAlpha.collectAsStateWithLifecycle(initialValue = 0)
        val extSub by graph.settings.extSubUrl.collectAsStateWithLifecycle(initialValue = "")
        var newSub by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("white", "yellow", "cyan", "green").forEach { c ->
                FocusButton("Color: $c", accent = subColor == c, onClick = {
                    scope.launch { graph.settings.setSubColor(c) }
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("none", "outline", "shadow", "raised", "depressed").forEach { e ->
                FocusButton("Edge: $e", accent = subEdge == e, onClick = { scope.launch { graph.settings.setSubEdge(e) } })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("Background: ${if (subBg == 0) "off" else "${subBg * 100 / 255}%"}", onClick = {
                scope.launch { graph.settings.setSubBgAlpha(when (subBg) { 0 -> 128; 128 -> 200; 200 -> 255; else -> 0 }) }
            })
        }
        if (extSub.isNotBlank()) Text("External subtitle: $extSub", color = EnktelTextDim, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.width(320.dp)) {
                tv.enktel.app.ui.components.TvTextField(newSub, { newSub = it }, "Load .srt/.vtt/.ass URL")
            }
            FocusButton("Apply", onClick = { scope.launch { graph.settings.setExtSubUrl(newSub.trim()) } })
            FocusButton("Clear", onClick = { scope.launch { graph.settings.setExtSubUrl("") } })
        }

        Spacer(Modifier.height(10.dp))
        Text("AUDIO", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val loud by graph.settings.loudnessOn.collectAsStateWithLifecycle(initialValue = false)
        FocusButton("Loudness normalization: ${if (loud) "ON" else "off"}", accent = loud, onClick = {
            scope.launch { graph.settings.setLoudnessOn(!loud) }
        })

        Spacer(Modifier.height(10.dp))
        Text("PLAYBACK", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val autoplay by graph.settings.autoplayNextEp.collectAsStateWithLifecycle(initialValue = true)
        val skipIntro by graph.settings.skipIntroSec.collectAsStateWithLifecycle(initialValue = 0)
        val pip by graph.settings.pipEnabled.collectAsStateWithLifecycle(initialValue = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("Auto-play next episode: ${if (autoplay) "on" else "off"}", accent = autoplay, onClick = {
                scope.launch { graph.settings.setAutoplayNextEp(!autoplay) }
            })
            FocusButton("Skip intro: ${skipIntro}s", onClick = {
                scope.launch { graph.settings.setSkipIntroSec(when (skipIntro) { 0 -> 30; 30 -> 60; 60 -> 90; 90 -> 120; else -> 0 }) }
            })
            FocusButton("Picture-in-Picture: ${if (pip) "on" else "off"}", accent = pip, onClick = {
                scope.launch { graph.settings.setPipEnabled(!pip) }
            })
        }

        Spacer(Modifier.height(10.dp))
        Text("SPORTS", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val scoresOn by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)
        FocusButton("Live scores (TheSportsDB): ${if (scoresOn) "ON" else "off"}", accent = scoresOn, onClick = {
            scope.launch { graph.settings.setScoresEnabled(!scoresOn) }
        })
        val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())
        var newTeam by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.width(260.dp)) {
                tv.enktel.app.ui.components.TvTextField(newTeam, { newTeam = it }, "Follow team or league")
            }
            FocusButton("Add", onClick = {
                if (newTeam.isNotBlank()) scope.launch {
                    graph.db.sportsDao().follow(
                        tv.enktel.app.data.db.FollowedTeam(name = newTeam.lowercase(), displayName = newTeam.trim())
                    )
                    newTeam = ""
                }
            })
        }
        followed.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    FocusButton("★ ${t.displayName}  ✕", onClick = {
                        scope.launch { graph.db.sportsDao().unfollow(t.name) }
                    })
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("SCREENSAVER", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        val ss by graph.settings.screensaverMin.collectAsStateWithLifecycle(initialValue = 5)
        FocusButton(if (ss == 0) "Screensaver: off" else "Screensaver: ${ss} min", onClick = {
            scope.launch { graph.settings.setScreensaverMin(when (ss) { 0 -> 3; 3 -> 5; 5 -> 10; 10 -> 20; else -> 0 }) }
        })

        Spacer(Modifier.height(10.dp))
        Text("ABOUT", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("EnkTel IPTV · Stream Beyond Limits", color = Color.White, fontSize = 13.sp)
        Text("Android TV & Fire TV · Xtream Codes + M3U · EPG · Catch-up · DVR", color = EnktelTextDim, fontSize = 12.sp)
    }
}
