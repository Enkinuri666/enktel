package tv.enktel.app.ui.multi

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Profile
import tv.enktel.app.player.PlayerEngine
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Multi-view: two panes side by side, or a 2x2 grid of four (v1.89).
 *
 * One pane owns the audio. On the two-pane layout LEFT/RIGHT pick the audio
 * side and UP/DOWN step its channel, as before. On the grid the D-pad moves the
 * audio focus between tiles and CH+/CH− step that tile's channel. OK takes the
 * audio tile full screen.
 *
 * Four live streams is four connections to the panel: a line limited to fewer
 * will refuse the extra tiles, which then show their channel plate and the
 * panel's error rather than a picture.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@UnstableApi
@Composable
fun MultiViewScreen(
    graph: AppGraph,
    nav: NavHostController,
    leftKey: String,
    rightKey: String,
    /** Extra channel keys for the 2x2 grid (third and fourth tiles). */
    moreKeys: List<String> = emptyList(),
    /** Ask for a 2x2 grid even if fewer than four keys were given. */
    quad: Boolean = moreKeys.isNotEmpty(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")

    val paneCount = if (quad) 4 else 2
    var chans by remember { mutableStateOf<List<Channel>?>(null) }
    var primary by remember { mutableIntStateOf(0) }

    LaunchedEffect(leftKey, rightKey, moreKeys, paneCount) {
        val requested = (listOf(leftKey, rightKey) + moreKeys)
            .filter { it.isNotBlank() }
            .mapNotNull { graph.content.channel(it) }
            .distinctBy { it.key }
        // Fill any empty tiles with the channels that follow the first one.
        val filled = requested.toMutableList()
        if (filled.size < paneCount) {
            val list = graph.content.channels(p.id).first()
            val start = filled.firstOrNull()?.let { f -> list.indexOfFirst { it.key == f.key } } ?: -1
            var i = start + 1
            while (filled.size < paneCount && list.isNotEmpty() && i < start + 1 + list.size) {
                val c = list[((i % list.size) + list.size) % list.size]
                if (filled.none { it.key == c.key }) filled += c
                i++
            }
        }
        chans = filled.take(paneCount)
    }

    // Captured once per composition so callbacks never see a half-updated list.
    val channels = chans
    if (channels == null || channels.size < paneCount) {
        CenterMessage("Loading multi-view…")
        return
    }

    val engines = remember(p.id, paneCount) {
        List(paneCount) { PlayerEngine(context, graph.streamHttp, if (paneCount == 4) "low" else bufferProfile) }
    }
    DisposableEffect(engines) { onDispose { engines.forEach { it.release() } } }
    channels.forEachIndexed { i, ch ->
        LaunchedEffect(i, ch.key) { engines[i].play(graph.content.liveUrl(p, ch, streamFormat), live = true) }
    }
    LaunchedEffect(primary, engines) {
        engines.forEachIndexed { i, e -> e.player.volume = if (i == primary) 1f else 0f }
    }

    fun step(delta: Int) {
        scope.launch {
            val list = graph.content.channels(p.id).first()
            val cur = channels[primary]
            val idx = list.indexOfFirst { it.key == cur.key }
            if (idx < 0 || list.isEmpty()) return@launch
            val next = list[((idx + delta) % list.size + list.size) % list.size]
            chans = channels.toMutableList().also { it[primary] = next }
        }
    }
    fun focusTo(i: Int) {
        if (i !in channels.indices || i == primary) return
        primary = i
        toaster.info("Audio: ${channels[i].name}")
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    BackHandler { nav.popBackStack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val col = primary % 2
                val row = primary / 2
                when (ev.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { focusTo(row * 2); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { focusTo(row * 2 + 1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP ->
                        { if (paneCount == 4) focusTo(col) else step(+1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN ->
                        { if (paneCount == 4) focusTo(2 + col) else step(-1); true }
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); true }
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                        nav.navigate("live?ch=${channels[primary].key}") { popUpTo("home") }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            channels.chunked(2).forEachIndexed { r, rowChans ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    rowChans.forEachIndexed { c, ch ->
                        val i = r * 2 + c
                        Pane(
                            engine = engines[i], isPrimary = primary == i, channel = ch,
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(if (paneCount == 4) 3.dp else 0.dp),
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(0.7f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                if (paneCount == 4) "D-pad: audio tile · CH+/− change channel · OK fullscreen · BACK exit"
                else "◀▶ audio · ▲▼ swap channel · OK fullscreen · BACK exit",
                color = EnktelTextDim, fontSize = 11.sp,
            )
        }
    }
}

@UnstableApi
@Composable
private fun Pane(engine: PlayerEngine, isPrimary: Boolean, channel: Channel, modifier: Modifier) {
    Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(EnktelSurface)
            .then(
                if (isPrimary) Modifier.border(3.dp, EnktelBlue, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                else Modifier,
            ),
    ) {
        // TEXTURE_VIEW: up to four of these on screen; see the v1.88 note —
        // hardware overlay planes are scarce on Fire TV, TextureViews are not.
        ContentFrame(
            player = engine.player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            keepContentOnReset = true,
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(if (isPrimary) "🔊 ${channel.name}" else "🔇 ${channel.name}", if (isPrimary) EnktelBlue else EnktelLive.copy(0.6f))
        }
    }
}
