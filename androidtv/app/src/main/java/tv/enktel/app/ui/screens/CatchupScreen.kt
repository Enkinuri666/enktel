package tv.enktel.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.catchup.CatchupUrls
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute

/**
 * Catch-Up: last week's television, replayed from the provider's archive.
 *
 * Three things were wrong with this screen, and the first is why users
 * reported the feature as missing rather than broken:
 *
 *  1. **The only way in was from inside the player.** You had to already be
 *     watching a channel, open the quick menu and find "Catch-up". Nothing in
 *     the app listed which channels had an archive, so there was no way to
 *     discover the feature and no way to answer "does my package include it".
 *     [CatchupBrowseScreen] is that list.
 *
 *  2. **It built one URL shape and offered no explanation when it failed.**
 *     See [CatchupUrls] — the app already knew about four catch-up schemes and
 *     used none of them here. Candidates are now probed before navigating, so
 *     a panel that does not serve the archive says so instead of dropping the
 *     user into a player that fails a few seconds later with a codec error.
 *
 *  3. **It never said what catch-up is.** An empty screen and the word
 *     "archive" is not an explanation, and the two ways of being empty — your
 *     line has no catch-up at all, versus the guide has no history loaded for
 *     this channel — need completely different actions from the user.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun CatchupScreen(graph: AppGraph, nav: NavHostController, channelKey: String) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val channel by produceState<Channel?>(initialValue = null, channelKey) { value = graph.content.channel(channelKey) }
    val ch = channel ?: return
    val scope = rememberCoroutineScope()

    val programs by produceState<List<EpgProgram>?>(initialValue = null, ch.key) {
        value = graph.epg.archive(p.id, ch.epgId, CatchupUrls.archiveDays(ch))
    }
    val supported = remember(ch.key) { CatchupUrls.isSupported(p, ch) }

    // Set while a programme is being resolved, so the row the user pressed
    // shows it is working rather than appearing to have ignored them.
    var resolving by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        SectionTitle("Catch-Up · ${ch.name}")
        CatchupExplainer(days = CatchupUrls.archiveDays(ch), supported = supported)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = EnktelLive, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        when {
            !supported -> CenterMessage(
                "This channel does not carry a catch-up archive.\n\n" +
                    "Catch-Up is a package feature — your provider decides which channels keep " +
                    "recent broadcasts. Channels that have it show a CATCH-UP badge in Live TV.",
            )
            programs == null -> CenterMessage("Loading archive…")
            programs!!.isEmpty() -> CenterMessage(
                "The archive is there, but the guide has no history loaded for this channel.\n\n" +
                    "Catch-Up plays a programme the guide knows about, so it needs the past few " +
                    "days of listings. Refresh the guide in Settings → EPG, then come back.",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(programs!!, key = { it.id }) { prog ->
                    val inWindow = CatchupUrls.isWithinWindow(ch, prog.startMs)
                    val playArchive = play@{
                        if (!inWindow || resolving != null) return@play
                        error = null
                        resolving = prog.id
                        scope.launch {
                            val url = CatchupUrls.resolve(graph.http, p, ch, prog.startMs, prog.endMs)
                            resolving = null
                            if (url == null) {
                                error = "The panel would not serve that programme. " +
                                    "Catch-Up is switched on for this channel, so this is usually a gap " +
                                    "in the provider's recording rather than a fault here — try a " +
                                    "different programme, or one closer to now."
                            } else {
                                nav.navigate(vodPlayerRoute(url, "${ch.name} · ${prog.title}"))
                            }
                        }
                        Unit
                    }
                    ArchiveRow(
                        prog = prog,
                        inWindow = inWindow,
                        busy = resolving == prog.id,
                        onPlay = playArchive,
                    )
                }
            }
        }
    }
}

/**
 * Every channel on the line that carries an archive — the answer to "what can
 * I actually catch up on", which the app previously had no screen for.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun CatchupBrowseScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val channels by produceState<List<Channel>?>(initialValue = null, p.id) {
        value = graph.content.channels(p.id).first()
            .filter { CatchupUrls.isSupported(p, it) }
            .sortedByDescending { it.archiveDays }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        SectionTitle("Catch-Up")
        val deepest = channels?.maxOfOrNull { CatchupUrls.archiveDays(it) } ?: 0
        CatchupExplainer(days = deepest, supported = !channels.isNullOrEmpty())
        Spacer(Modifier.height(16.dp))
        when {
            channels == null -> CenterMessage("Checking which channels keep an archive…")
            channels!!.isEmpty() -> CenterMessage(
                "No channel on this line carries a catch-up archive.\n\n" +
                    "Catch-Up is a package feature rather than an app setting — the provider decides " +
                    "which channels keep recent broadcasts, and for how long. Ask your reseller " +
                    "whether your plan includes it.",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(channels!!, key = { it.key }) { ch ->
                    val open = { nav.navigate("catchup/${ch.key}") }
                    Surface(
                        onClick = open,
                        modifier = Modifier.fillMaxWidth().tapClick(open),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = EnktelSurfaceHigh.copy(0.5f),
                            focusedContainerColor = EnktelBlue,
                            focusedContentColor = Color.White,
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (ch.num > 0) "${ch.num}" else "—",
                                Modifier.width(56.dp),
                                fontSize = 12.sp, color = EnktelTextDim,
                            )
                            Text(
                                ch.name, Modifier.weight(1f),
                                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${CatchupUrls.archiveDays(ch)} days back",
                                fontSize = 12.sp, color = EnktelOk, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * What Catch-Up is, in the two sentences a user needs before the list below
 * means anything. Shown on both screens because either can be the first one
 * somebody lands on.
 */
@Composable
private fun CatchupExplainer(days: Int, supported: Boolean) {
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EnktelSurface)
            .padding(14.dp),
    ) {
        Text(
            "WHAT CATCH-UP IS",
            color = EnktelBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Your provider keeps a rolling recording of some live channels. Catch-Up plays a " +
                "programme back from that recording, so you can watch something that has already " +
                "aired without having set a reminder.",
            color = Color.White.copy(0.9f), fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (supported && days > 0)
                "This archive reaches back $days day${if (days == 1) "" else "s"}. Anything older has " +
                    "already been overwritten. Programmes still on air can't be caught up — use the " +
                    "player's rewind for those."
            else
                "Which channels have it, and how far back it goes, is set by your provider — not " +
                    "in the app's settings.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
    }
}

@Composable
private fun ArchiveRow(
    prog: EpgProgram,
    inWindow: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
) {
    Surface(
        onClick = onPlay,
        enabled = inWindow && !busy,
        modifier = Modifier.fillMaxWidth().tapClick(onPlay),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Box(Modifier.width(110.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    when {
                        busy -> "Checking…"
                        // Rows outside the window are kept visible rather than
                        // hidden: "that one is too old" is information, and a
                        // list that silently stops is not.
                        !inWindow -> "Expired"
                        else -> "⏪ Play"
                    },
                    fontSize = 12.sp,
                    color = if (inWindow) EnktelTextDim else EnktelTextDim.copy(0.5f),
                )
            }
        }
    }
}

