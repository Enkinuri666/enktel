package tv.enktel.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Phone-optimised TV Guide.
 *
 * Trades the 24-column horizontal grid for a two-tier vertical layout:
 *  - top: horizontally-scrollable channel picker (48 dp taps, logo + name)
 *  - middle: horizontally-scrollable day picker (Today / +1 / +2 …)
 *  - main: vertical list of the selected channel's programs for the
 *    selected day, oldest to newest.  A red pill marks the program that
 *    is on air right now, with time-remaining underneath.
 *
 * Scrolling stays 1D on both axes, tap targets are well over 48 dp, and
 * the layout adapts fluidly from 360 dp (small phones) up to tablets in
 * portrait before the wide guide kicks in.
 */
@Composable
internal fun MobileGuideScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val channels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val prefsChannelKey by graph.settings.lastChannel.collectAsStateWithLifecycle(initialValue = "")

    var selectedIdx by remember(channels, prefsChannelKey) {
        val i = channels.indexOfFirst { it.key == prefsChannelKey }
        mutableIntStateOf(if (i >= 0) i else 0)
    }
    val selectedChannel = channels.getOrNull(selectedIdx)
    var dayOffset by remember { mutableIntStateOf(0) }
    val dayStart = remember(dayOffset) {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L

    var programs by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    LaunchedEffect(selectedChannel?.epgId, dayOffset) {
        val ch = selectedChannel ?: return@LaunchedEffect
        if (ch.epgId.isBlank()) { programs = emptyList(); return@LaunchedEffect }
        programs = graph.epg.window(p.id, listOf(ch.epgId), dayStart, dayEnd)[ch.epgId].orEmpty()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val mult = tv.enktel.app.data.net.ThermalGuard.level.value.pollIntervalMultiplier
            kotlinx.coroutines.delay((60_000L * mult).toLong().coerceAtMost(600_000L))
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(programs, dayOffset) {
        if (dayOffset != 0 || programs.isEmpty()) return@LaunchedEffect
        val idx = programs.indexOfFirst { it.endMs > now }
        if (idx > 0) listState.scrollToItem((idx - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("TV Guide")
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // Day picker chip strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GlassChip("Today", selected = dayOffset == 0, onClick = { dayOffset = 0 })
            }
            items((1..6).toList()) { d ->
                val label = SimpleDateFormat("EEE d", Locale.getDefault())
                    .format(Date(System.currentTimeMillis() + d * 86_400_000L))
                GlassChip(label, selected = dayOffset == d, onClick = { dayOffset = d })
            }
        }
        Spacer(Modifier.height(10.dp))

        // Channel picker rail — 48 dp min tap target, tight horizontal scroll
        if (channels.isEmpty()) {
            CenterMessage("No channels yet — add a playlist in Settings.")
            return
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(channels.take(80), key = { it.key }) { ch ->
                val idx = channels.indexOf(ch)
                val isSel = idx == selectedIdx
                Row(
                    Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (isSel) EnktelBlue else EnktelSurface)
                        .pointerInput(ch.key) { detectTapGestures { selectedIdx = idx } }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ch.logo.isNotBlank()) {
                        AsyncImage(
                            model = ch.logo,
                            contentDescription = ch.name,
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black),
                        )
                    }
                    Text(
                        ch.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Vertical program timeline for the selected channel + day
        val ch = selectedChannel
        if (ch == null) {
            CenterMessage("Pick a channel.")
        } else if (programs.isEmpty()) {
            CenterMessage("No guide data for ${ch.name} on this day yet.")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(programs, key = { it.id }) { prog ->
                    val isNow = now in prog.startMs..prog.endMs
                    val isPast = prog.endMs < now
                    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isNow) EnktelSurfaceHigh else EnktelSurface)
                            .pointerInput(prog.id) {
                                detectTapGestures {
                                    scope.launch { graph.settings.setLastChannel(ch.key) }
                                    nav.navigate("live?ch=${ch.key}")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(64.dp)) {
                            Text(
                                fmt.format(Date(prog.startMs)),
                                color = if (isPast) EnktelTextDim else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Black,
                            )
                            Text(
                                "→ ${fmt.format(Date(prog.endMs))}",
                                color = EnktelTextDim, fontSize = 10.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isNow) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EnktelLive)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    prog.title,
                                    color = if (isPast) EnktelTextDim else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isNow) {
                                val remainMin = ((prog.endMs - now) / 60_000L).coerceAtLeast(0)
                                Text(
                                    "$remainMin min left",
                                    color = EnktelLive, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else if (prog.desc.isNotBlank()) {
                                Text(
                                    prog.desc.take(90),
                                    color = EnktelTextDim, fontSize = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
