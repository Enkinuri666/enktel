package tv.enktel.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.net.NetworkClass
import tv.enktel.app.data.net.SpeedTestEngine
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * In-app network diagnostic tool.  Tests connectivity directly against
 * the active profile's IPTV server — ping (TCP connect time), jitter,
 * packet-loss estimate, real download throughput, DNS latency, resolved
 * IP, connection type, and a plain-language format recommendation +
 * buffer-health projection.  One-tap "Copy Diagnostic Report" for
 * pasting into a support ticket.
 */
@Composable
fun SpeedTestScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SpeedTestEngine.Result?>(null) }

    fun runTest() {
        if (running) return
        running = true
        result = null
        scope.launch {
            val streamUrl = try {
                val list: List<tv.enktel.app.data.db.Channel> = graph.content.channels(p.id).first()
                list.firstOrNull()?.let { c -> graph.content.liveUrl(p, c, "hls") }
            } catch (_: Throwable) { null }
            val r = SpeedTestEngine.run(
                http = graph.http,
                serverUrl = p.server,
                streamSampleUrl = streamUrl,
                onProgress = { status = it },
            )
            result = r
            running = false
            status = ""
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Network Speed Test")
                Spacer(Modifier.weight(1f))
                FocusButton(
                    if (running) "Testing…" else "▶ Run Test",
                    accent = true,
                    onClick = { runTest() },
                )
            }
        }
        item {
            Text(
                "Tests your connection directly against your IPTV server (${p.server}) — " +
                    "not a generic third-party benchmark.",
                color = EnktelTextDim, fontSize = 12.sp,
            )
        }
        if (running) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnktelSurface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(status.ifBlank { "Starting…" }, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        result?.let { r ->
            item { MetricRow("Resolved IP", r.resolvedIp ?: "unresolved") }
            item { MetricRow("DNS lookup", "${r.dnsLookupMs} ms") }
            item {
                MetricRow(
                    "Ping (TCP connect)",
                    if (r.pingMs >= 0) "${r.pingMs} ms" else "failed",
                    color = when {
                        r.pingMs < 0 -> EnktelLive
                        r.pingMs < 80 -> EnktelOk
                        r.pingMs < 200 -> Color(0xFFFBBF24)
                        else -> EnktelLive
                    },
                )
            }
            item { MetricRow("Jitter", "${r.jitterMs} ms") }
            item {
                MetricRow(
                    "Packet loss", "${r.packetLossPct}%",
                    color = if (r.packetLossPct >= 20) EnktelLive else if (r.packetLossPct > 0) Color(0xFFFBBF24) else EnktelOk,
                )
            }
            item {
                MetricRow(
                    "Download throughput", "%.2f Mbps".format(r.downloadMbps),
                    color = when {
                        r.downloadMbps >= 15 -> EnktelOk
                        r.downloadMbps >= 5 -> Color(0xFFFBBF24)
                        else -> EnktelLive
                    },
                )
            }
            item { MetricRow("Connection type", r.connectionType.name) }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnktelSurfaceHigh)
                        .padding(16.dp),
                ) {
                    Text("Recommendation", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(r.recommendation, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Buffer health", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(r.bufferProjection, color = Color.White, fontSize = 14.sp)
                }
            }
            item {
                FocusButton(
                    "📋 Copy Diagnostic Report",
                    onClick = { clipboard.setText(AnnotatedString(r.toReport())) },
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, color: Color = Color.White) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EnktelSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = EnktelTextDim, fontSize = 13.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
