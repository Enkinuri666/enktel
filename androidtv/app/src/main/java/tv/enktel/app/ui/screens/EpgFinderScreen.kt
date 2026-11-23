package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.repo.EpgRepository
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.components.rememberScreenShape
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextFaint

/**
 * EPG Finder — a diagnostic + repair surface for the TV guide.
 *
 * Two questions this screen answers that users have been asking in other
 * ways for a while:
 *
 *   1. "How many of my channels actually carry a programme guide?" The
 *      Guide screen only shows one row at a time, so a line that covers
 *      30 of 900 channels looks indistinguishable from one that covers
 *      all of them until the viewer scrolls past the first page.
 *   2. "The Panel's XMLTV is incomplete — can I add my own?" An M3U
 *      profile already has an `epgUrl` field, but there is nowhere in
 *      the app to set it after onboarding.
 *
 * Reads [EpgRepository.coverage] once per visit and once per refresh,
 * which under the hood is one Channels query + one chunked EPG window
 * query — the same calls the Guide screen already does, so there is no
 * new load against the panel or the database.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun EpgFinderScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val shape = rememberScreenShape()

    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val coverage by produceState<EpgRepository.Coverage?>(initialValue = null, tick, p.id) {
        value = try { graph.epg.coverage(p.id) } catch (_: Throwable) { null }
    }
    // Local echo of profile.epgUrl so the field survives a recomposition
    // mid-typing. Primed from the stored value once per profile.
    var supplementalUrl by remember(p.id) { mutableStateOf(p.epgUrl) }

    Column(
        Modifier.fillMaxSize()
            .background(tv.enktel.app.ui.theme.EnktelBg)
            .padding(horizontal = shape.padH, vertical = shape.padV),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("EPG Finder")
        Text(
            "Which channels have a programme guide, and how to fill in the gaps.",
            color = EnktelTextDim, fontSize = 13.sp,
        )

        val c = coverage
        Column(
            Modifier.fillMaxWidth()
                .background(EnktelSurface.copy(0.6f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (c == null) {
                Text("Loading coverage…", color = Color.White)
            } else {
                val pct = if (c.totalChannels > 0) {
                    (c.channelsWithEpg * 100 / c.totalChannels)
                } else 0
                val pctColor = when {
                    c.totalChannels == 0 -> EnktelTextDim
                    pct >= 80 -> EnktelOk
                    pct >= 40 -> EnktelBlue
                    else -> EnktelLive
                }
                Text(
                    "${c.channelsWithEpg} / ${c.totalChannels} channels have EPG right now",
                    color = pctColor, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                val horizonDays = if (c.horizonMs > 0) {
                    ((c.horizonMs - System.currentTimeMillis()) / (24L * 3_600_000L))
                        .coerceAtLeast(0)
                } else 0
                Text(
                    "Guide reaches ${horizonDays} day${if (horizonDays == 1L) "" else "s"} ahead.",
                    color = EnktelTextDim, fontSize = 12.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusButton(
                    if (busy) "Refreshing…" else "Refresh EPG now",
                    icon = Icons.Rounded.Refresh, accent = true,
                    onClick = {
                        if (busy) return@FocusButton
                        busy = true
                        scope.launch {
                            runCatching { graph.epg.refresh(p) }
                                .onSuccess { tick++; toaster.success("EPG refreshed") }
                                .onFailure { toaster.error("EPG refresh failed: ${it.message ?: "unknown"}") }
                            busy = false
                        }
                    },
                )
                FocusButton(
                    "Open Guide", icon = Icons.Rounded.Search,
                    onClick = { nav.navigate("guide") },
                )
            }
        }

        // Per-profile supplemental XMLTV URL. An Xtream line already carries
        // one via XtreamClient; an M3U profile stores its own. This is where
        // the viewer adds a community XMLTV source (e.g. iptv-org) to fill
        // in a panel whose EPG is thin.
        Column(
            Modifier.fillMaxWidth()
                .background(EnktelSurface.copy(0.6f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Supplemental XMLTV URL",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "Used by this profile when its panel does not carry EPG, or to top it up.",
                color = EnktelTextDim, fontSize = 12.sp,
            )
            TvTextField(
                supplementalUrl, { supplementalUrl = it }, "https://.../guide.xml.gz",
                Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusButton(
                    "Save", accent = true,
                    onClick = {
                        scope.launch {
                            runCatching {
                                graph.db.profileDao().update(p.copy(epgUrl = supplementalUrl.trim()))
                                toaster.success("Saved")
                            }.onFailure { toaster.error("Save failed: ${it.message}") }
                        }
                    },
                )
                FocusButton(
                    "Clear",
                    onClick = {
                        supplementalUrl = ""
                        scope.launch {
                            runCatching { graph.db.profileDao().update(p.copy(epgUrl = "")) }
                                .onSuccess { toaster.success("Cleared") }
                                .onFailure { toaster.error("Clear failed: ${it.message}") }
                        }
                    },
                )
            }
            Text(
                "After saving, press Refresh EPG now to fetch programmes from the new URL.",
                color = EnktelTextFaint, fontSize = 11.sp,
            )
        }

        // Channels without a programme guide right now. Rendered as a plain
        // list so a D-pad user reads it the same as everything else; a
        // repair for a specific channel means editing its epgId on the
        // panel's side, which this screen can't do — the list is the
        // diagnostic, not the fix.
        if (c != null && c.missing.isNotEmpty()) {
            Text(
                "Channels missing EPG (${c.missing.size} shown)",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(c.missing, key = { it.key }) { m ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(EnktelSurface.copy(0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(m.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val reason = if (m.epgId.isBlank()) "no epg-id on channel" else "epg-id '${m.epgId}' not in guide"
                        Text(
                            "${m.categoryName.ifBlank { "Uncategorised" }}  ·  $reason",
                            color = EnktelTextDim, fontSize = 11.sp,
                        )
                    }
                }
            }
        } else if (c != null) {
            Text(
                "Every channel this line carries has EPG for the next 24 hours.",
                color = EnktelOk, fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}
