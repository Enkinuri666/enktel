package tv.enktel.app.ui.vod

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.WatchProgress
import tv.enktel.app.player.PlayerEngine
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.live.TrackPicker
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelTextDim
import java.util.Locale

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** Full-featured VOD / catch-up / recording player with DPAD seeking. */
@UnstableApi
@Composable
fun VodPlayerScreen(
    graph: AppGraph,
    nav: NavHostController,
    url: String,
    title: String,
    progressKey: String,
    isLive: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val engine = remember { PlayerEngine(context, graph.http, bufferProfile) }
    val playError by engine.error.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var trackMenu by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        val resume = if (progressKey.isNotBlank()) graph.content.progress(progressKey)?.positionMs ?: 0L else 0L
        engine.play(url, live = isLive, startPositionMs = if (!isLive) resume else 0)
    }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    // Position ticker + periodic progress persistence
    LaunchedEffect(Unit) {
        var lastSave = 0L
        while (true) {
            positionMs = engine.player.currentPosition.coerceAtLeast(0)
            durationMs = engine.player.duration.coerceAtLeast(0)
            playing = engine.player.isPlaying
            val now = System.currentTimeMillis()
            if (!isLive && progressKey.isNotBlank() && durationMs > 0 && now - lastSave > 10_000) {
                lastSave = now
                val nearEnd = positionMs > durationMs - 30_000
                if (nearEnd) graph.content.clearProgress(progressKey)
                else graph.content.saveProgress(
                    WatchProgress(
                        key = progressKey,
                        profileId = progressKey.substringBefore(':').toLongOrNull() ?: 0,
                        kind = progressKey.split(':').getOrElse(1) { "vod" },
                        refId = progressKey.substringAfterLast(':').toLongOrNull() ?: 0,
                        name = title, url = url,
                        positionMs = positionMs, durationMs = durationMs,
                    )
                )
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsTick) {
        if (showControls) {
            delay(5000)
            if (trackMenu.isEmpty()) showControls = false
        }
    }

    BackHandler {
        when {
            trackMenu.isNotEmpty() -> trackMenu = ""
            showControls -> showControls = false
            else -> nav.popBackStack()
        }
    }

    fun poke() { showControls = true; controlsTick++ }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(trackMenu, showControls) { if (trackMenu.isEmpty() && !showControls) rootFocus.requestFocus() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            // Touchscreen support: tap toggles the control bar.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = true; controlsTick++ })
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || trackMenu.isNotEmpty() || showControls) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { poke(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        engine.player.seekBack(); poke(); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        engine.player.seekForward(); poke(); true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                        poke(); true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false } },
            update = { view ->
                view.player = engine.player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (playError != null) {
            Text(
                "Playback error: $playError",
                color = EnktelLive,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(0.7f)).padding(16.dp),
            )
        }

        if (showControls) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.95f))))
                    .padding(horizontal = 48.dp, vertical = 24.dp),
            ) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                if (!isLive && durationMs > 0) {
                    ProgressBarThin(positionMs.toFloat() / durationMs, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtTime(positionMs), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(fmtTime(durationMs), color = EnktelTextDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton("-10s", onClick = { engine.player.seekBack(); controlsTick++ })
                    FocusButton(if (playing) "⏸ Pause" else "▶ Play", accent = true, onClick = {
                        if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                        controlsTick++
                    })
                    FocusButton("+30s", onClick = { engine.player.seekForward(); controlsTick++ })
                    FocusButton("Audio", onClick = { trackMenu = "audio" })
                    FocusButton("Subs", onClick = { trackMenu = "subs" })
                    if (!isLive) {
                        FocusButton("Speed ${speed}x", onClick = {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                            engine.player.setPlaybackSpeed(speed)
                            controlsTick++
                        })
                    }
                    FocusButton("Aspect", onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        controlsTick++
                    })
                }
            }
        }

        if (trackMenu.isNotEmpty()) {
            val type = if (trackMenu == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            TrackPicker(
                title = if (trackMenu == "audio") "Audio Track" else "Subtitles",
                allowOff = trackMenu == "subs",
                tracks = engine.tracksOf(type),
                onPick = { choice -> engine.selectTrack(type, choice); trackMenu = "" },
                onClose = { trackMenu = "" },
            )
        }
    }
}
