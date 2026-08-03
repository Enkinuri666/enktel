package tv.enktel.app.ui.multi

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
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
 * Two-pane live view — the "multi-view" feature typical of premium sports apps. The primary
 * pane owns audio; DPAD-swap flips which side is primary. Perfect for watching two matches
 * side-by-side without leaving one to check the other.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@UnstableApi
@Composable
fun MultiViewScreen(graph: AppGraph, nav: NavHostController, leftKey: String, rightKey: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")

    var leftCh by remember { mutableStateOf<Channel?>(null) }
    var rightCh by remember { mutableStateOf<Channel?>(null) }
    var primary by remember { mutableIntStateOf(0) } // 0 = left, 1 = right

    LaunchedEffect(leftKey, rightKey) {
        leftCh = leftKey.takeIf { it.isNotBlank() }?.let { graph.content.channel(it) }
        rightCh = rightKey.takeIf { it.isNotBlank() }?.let { graph.content.channel(it) }
        // Sensible fallbacks: if only one side is set, pick the next channel of the same category.
        if (leftCh != null && rightCh == null) {
            val list = graph.content.channels(p.id).first()
            val idx = list.indexOfFirst { it.key == leftCh?.key }
            if (idx >= 0) rightCh = list.getOrNull((idx + 1) % list.size)
        }
    }

    if (leftCh == null || rightCh == null) {
        CenterMessage("Loading multi-view…")
        return
    }

    val left = remember(p.id, "L") { PlayerEngine(context, graph.http, bufferProfile) }
    val right = remember(p.id, "R") { PlayerEngine(context, graph.http, bufferProfile) }
    DisposableEffect(left, right) {
        onDispose { left.release(); right.release() }
    }
    LaunchedEffect(leftCh?.key) {
        left.play(graph.content.liveUrl(p, leftCh!!, streamFormat), live = true)
    }
    LaunchedEffect(rightCh?.key) {
        right.play(graph.content.liveUrl(p, rightCh!!, streamFormat), live = true)
    }
    // Only primary pane produces audio.
    LaunchedEffect(primary) {
        left.player.volume = if (primary == 0) 1f else 0f
        right.player.volume = if (primary == 1) 1f else 0f
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    BackHandler { nav.popBackStack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { primary = 0; toaster.info("Audio: ${leftCh?.name}"); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { primary = 1; toaster.info("Audio: ${rightCh?.name}"); true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                        scope.launch { swap(graph, p.id, primary, leftCh!!, rightCh!!, +1) { l, r -> leftCh = l; rightCh = r } }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        scope.launch { swap(graph, p.id, primary, leftCh!!, rightCh!!, -1) { l, r -> leftCh = l; rightCh = r } }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                        // Fullscreen the primary side by dropping the other pane and returning to Live.
                        val target = if (primary == 0) leftCh else rightCh
                        nav.navigate("live?ch=${target?.key.orEmpty()}") { popUpTo("home") }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            Pane(engine = left, isPrimary = primary == 0, channel = leftCh!!, modifier = Modifier.weight(1f).fillMaxHeight())
            Pane(engine = right, isPrimary = primary == 1, channel = rightCh!!, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(0.7f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row {
                Text(
                    "◀▶ audio · ▲▼ swap channel · OK fullscreen · BACK exit",
                    color = EnktelTextDim, fontSize = 11.sp,
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun Pane(engine: PlayerEngine, isPrimary: Boolean, channel: Channel, modifier: Modifier) {
    Box(modifier.background(EnktelSurface)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; setKeepContentOnPlayerReset(true) } },
            update = { it.player = engine.player },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(if (isPrimary) "🔊 ${channel.name}" else "🔇 ${channel.name}", if (isPrimary) EnktelBlue else EnktelLive.copy(0.6f))
        }
    }
}

private suspend fun swap(
    graph: AppGraph, profileId: Long, primary: Int,
    left: Channel, right: Channel, delta: Int,
    apply: (Channel, Channel) -> Unit,
) {
    val list = graph.content.channels(profileId).first()
    val current = if (primary == 0) left else right
    val idx = list.indexOfFirst { it.key == current.key }
    if (idx < 0 || list.isEmpty()) return
    val next = list[((idx + delta) % list.size + list.size) % list.size]
    apply(
        if (primary == 0) next else left,
        if (primary == 1) next else right,
    )
}
