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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement as LayoutArrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight as TextFontWeight
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
    val bufferProfileRaw by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val bufferProfile = if (bufferProfileRaw == "auto")
        tv.enktel.app.data.net.NetworkClass.suggestedBufferProfile
    else bufferProfileRaw
    // Power-user playback controls MUST come before the engine remember() —
    // Kotlin doesn't allow forward references to local vals, and the engine
    // ctor reads decoderMode + minBufferMs, and the LaunchedEffect below
    // reads dialogueBoost.
    val decoderMode by graph.settings.decoderMode.collectAsStateWithLifecycle(initialValue = "hwplus")
    val minBufferMsRaw by graph.settings.minBufferMs.collectAsStateWithLifecycle(initialValue = 0)
    val companionMode by graph.settings.companionMode.collectAsStateWithLifecycle(initialValue = false)
    val dialogueBoost by graph.settings.dialogueBoost.collectAsStateWithLifecycle(initialValue = "off")
    // v1.26.0 — Streaming Companion Mode: forces a 30 s min-buffer floor so
    // the Discord viewer never sees a stall while the local player rebuffers.
    // Overrides whatever the user set via the min-buffer slider only when
    // that value is lower (respect a user who's already turned it up high).
    val minBufferMs = if (companionMode) maxOf(minBufferMsRaw, 30_000) else minBufferMsRaw
    val engine = remember(decoderMode, minBufferMs, companionMode) {
        PlayerEngine(
            context, graph.http, bufferProfile,
            decoderMode = decoderMode,
            minBufferOverrideMs = minBufferMs,
            lockToTopBitrate = companionMode,
        )
    }
    LaunchedEffect(engine, dialogueBoost) { engine.setDialogueBoost(dialogueBoost) }
    val playError by engine.error.collectAsStateWithLifecycle()
    val extSubUrl by graph.settings.extSubUrl.collectAsStateWithLifecycle(initialValue = "")
    val loudnessOn by graph.settings.loudnessOn.collectAsStateWithLifecycle(initialValue = false)
    val autoplayNextEp by graph.settings.autoplayNextEp.collectAsStateWithLifecycle(initialValue = true)
    val skipIntroSec by graph.settings.skipIntroSec.collectAsStateWithLifecycle(initialValue = 0)
    val subScalePct by graph.settings.subScalePct.collectAsStateWithLifecycle(initialValue = 100)
    val subColor by graph.settings.subColor.collectAsStateWithLifecycle(initialValue = "white")
    val subEdge by graph.settings.subEdge.collectAsStateWithLifecycle(initialValue = "outline")
    val subBgAlpha by graph.settings.subBgAlpha.collectAsStateWithLifecycle(initialValue = 0)
    val hudAutoHideSec by graph.settings.hudAutoHideSec.collectAsStateWithLifecycle(initialValue = 8)
    val vodForceMp4 by graph.settings.vodForceMp4.collectAsStateWithLifecycle(initialValue = false)

    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var trackMenu by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(url, vodForceMp4) {
        val resume = if (progressKey.isNotBlank()) graph.content.progress(progressKey)?.positionMs ?: 0L else 0L
        // If the user has set an intro-skip length, honour it on the first play (not on resumes).
        val start = if (!isLive && resume <= 0 && skipIntroSec > 0) skipIntroSec * 1000L else resume
        // Force MP4 extractor for VOD only when the user opted in via
        // Settings → Playback → Force MP4 fallback (VOD). Live streams
        // always let ExoPlayer sniff so HLS/TS auto-detect keeps working.
        val forceMime = if (!isLive && vodForceMp4) androidx.media3.common.MimeTypes.VIDEO_MP4 else ""
        engine.play(
            url = url,
            live = isLive,
            startPositionMs = if (!isLive) start else 0,
            externalSubUrl = extSubUrl,
            forceMimeType = forceMime,
        )
        engine.setLoudnessOn(loudnessOn)
    }
    LaunchedEffect(loudnessOn) { engine.setLoudnessOn(loudnessOn) }
    // Presence tracker: seed on first mount, then throttle position updates
    // to once every couple of seconds so we don't churn the webhook debounce.
    LaunchedEffect(title, isLive) {
        if (!isLive) tv.enktel.app.data.net.PresenceTracker.setVod(title = title)
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (!isLive && durationMs > 0) {
                tv.enktel.app.data.net.PresenceTracker.updateVodPosition(positionMs, durationMs)
            }
            delay(4_000)
        }
    }
    val ctxForRefresh = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(engine) {
        engine.videoFrameRate.collect { fps ->
            if (fps > 0f) {
                (ctxForRefresh as? android.app.Activity)?.let {
                    tv.enktel.app.player.RefreshRateMatcher.match(it, fps)
                }
            }
        }
    }
    // Keep the OS display awake while a movie/episode is playing — mirrors
    // what the live player does. Cleared on dispose.
    DisposableEffect(Unit) {
        val activity = ctxForRefresh as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(Unit) {
        tv.enktel.app.voice.ActivePlayerRef.register(engine.player)
        onDispose {
            (ctxForRefresh as? android.app.Activity)?.let {
                tv.enktel.app.player.RefreshRateMatcher.reset(it)
            }
            tv.enktel.app.data.net.PresenceTracker.clear()
            tv.enktel.app.voice.ActivePlayerRef.unregister(engine.player)
            engine.release()
        }
    }

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

    LaunchedEffect(controlsTick, hudAutoHideSec) {
        // 0 = never auto-hide (only the user's back/tap dismisses the HUD).
        if (showControls && hudAutoHideSec > 0) {
            delay(hudAutoHideSec * 1000L)
            if (trackMenu.isEmpty()) showControls = false
        }
    }

    val pipOn by graph.settings.pipEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoPipOnBack by graph.settings.autoPipOnBack.collectAsStateWithLifecycle(initialValue = true)
    val autoPipOnHome by graph.settings.autoPipOnHome.collectAsStateWithLifecycle(initialValue = true)

    DisposableEffect(pipOn, autoPipOnHome) {
        tv.enktel.app.player.PictureInPicture.playerActive = true
        tv.enktel.app.player.PictureInPicture.userWantsPipOnBack = pipOn && autoPipOnHome
        onDispose {
            tv.enktel.app.player.PictureInPicture.playerActive = false
            tv.enktel.app.player.PictureInPicture.userWantsPipOnBack = false
        }
    }

    BackHandler {
        when {
            trackMenu.isNotEmpty() -> trackMenu = ""
            showControls -> showControls = false
            else -> {
                val entered = if (pipOn && autoPipOnBack && engine.player.isPlaying) {
                    (context as? android.app.Activity)?.let {
                        tv.enktel.app.player.PictureInPicture.enter(it)
                    } ?: false
                } else false
                if (!entered) nav.popBackStack()
            }
        }
    }

    fun poke() { showControls = true; controlsTick++ }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(trackMenu, showControls) { if (trackMenu.isEmpty() && !showControls) rootFocus.requestFocus() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    var gestureLevel by remember { mutableStateOf<Triple<String, Float, Boolean>?>(null) }
    LaunchedEffect(gestureLevel) { if (gestureLevel != null) { delay(900); gestureLevel = null } }
    var dragBrightness by remember { mutableStateOf(true) }
    var boxWidthPx by remember { mutableStateOf(1f) }
    var boxHeightPx by remember { mutableStateOf(1f) }

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
            // Left half of the screen tunes brightness, right half tunes volume.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        boxWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        boxHeightPx = size.height.toFloat().coerceAtLeast(1f)
                        dragBrightness = off.x < boxWidthPx / 2f
                    },
                    onVerticalDrag = { _, dy ->
                        val delta = -dy / boxHeightPx
                        if (dragBrightness) {
                            (context as? android.app.Activity)?.let { act ->
                                val next = tv.enktel.app.player.PlayerGestures.setBrightness(
                                    act, tv.enktel.app.player.PlayerGestures.currentBrightness(act) + delta,
                                )
                                gestureLevel = Triple("☀ Brightness", next, true)
                            }
                        } else {
                            val next = tv.enktel.app.player.PlayerGestures.adjustVolume(context, delta)
                            gestureLevel = Triple("🔊 Volume", next, false)
                        }
                    },
                )
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
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        // Number keys jump straight to 0–90% of the video.
                        if (!isLive && durationMs > 0) {
                            val digit = ev.key.nativeKeyCode - AndroidKeyEvent.KEYCODE_0
                            engine.player.seekTo(durationMs * digit / 10)
                            poke()
                            true
                        } else false
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
                view.subtitleView?.let { sv ->
                    tv.enktel.app.player.Subtitles.apply(sv, subScalePct, subColor, subEdge, subBgAlpha)
                }
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

        gestureLevel?.let { (label, frac, isBright) ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.height(90.dp).width(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height((90f * frac.coerceIn(0f, 1f)).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isBright) tv.enktel.app.ui.theme.EnktelOk else tv.enktel.app.ui.theme.EnktelBlue),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("${(frac * 100).toInt()}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }

        // Stream-health chip — only when the transport controls are up
        // (mirroring the live player) so it doesn't paint over the picture
        // during unattended viewing.
        if (showControls || trackMenu.isNotEmpty()) {
            tv.enktel.app.ui.components.StreamHealthChip(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp),
            )
        }

        // ---- Skip Intro pill ------------------------------------------------
        // Netflix-style floating chip.  Shown between 5 s and 90 s into VOD
        // playback so users can bypass series intro sequences with one tap
        // (or by saying "skip intro"). Dismissed once tapped, once the
        // player crosses the 90 s mark, or when it's a live stream.
        var skipIntroDismissed by remember(progressKey) { androidx.compose.runtime.mutableStateOf(false) }
        val showSkipIntro = !isLive && !skipIntroDismissed &&
            positionMs in 5_000L..90_000L && durationMs > 180_000L
        if (showSkipIntro) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = if (showControls) 140.dp else 40.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            engine.player.seekTo(90_000L)
                            skipIntroDismissed = true
                            showControls = true
                            controlsTick++
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⏭", color = Color.White, fontSize = 15.sp)
                Text(
                    "Skip Intro",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (showControls) {
            // Mobile gets tighter side padding so the seek bar reaches closer to the
            // screen edges on a phone, which is where the thumb naturally goes.
            val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
            val hPad = if (isMobile) 20.dp else 48.dp
            val vPad = if (isMobile) 18.dp else 24.dp
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.95f))))
                    .padding(horizontal = hPad, vertical = vPad),
            ) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                // v1.32.0 — the scrubber used to be gated on `durationMs > 0`,
                // so VOD streams whose HLS manifest hadn't declared a duration
                // yet (Xtream catch-up, live episode chunks, unended playlists)
                // rendered the controls without any timeline at all. The user
                // couldn't drag-seek or see elapsed time. Now: whenever the
                // caller marked this as VOD (not live), the SeekBar renders.
                // When we know a real duration, it shows the numeric timestamps
                // too; when we don't, we still show the drag/tap scrubber and
                // the elapsed timestamp so ±10 s / drag / DPAD scrub keep
                // working the moment the media is prepared.
                if (!isLive) {
                    SeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { target ->
                            engine.player.seekTo(target)
                            positionMs = target
                            controlsTick++
                        },
                        onInteract = { controlsTick++ },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtTime(positionMs), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        if (durationMs > 0) {
                            Text("-${fmtTime(durationMs - positionMs)}", color = EnktelTextDim, fontSize = 12.sp)
                            Spacer(Modifier.width(14.dp))
                            Text(fmtTime(durationMs), color = EnktelTextDim, fontSize = 12.sp)
                        } else {
                            Text("live buffer", color = EnktelTextDim, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // v1.26.0 — hoist Discord state above the LazyRow. LazyListScope's
                // `item {}` builder isn't @Composable, so remember* / collectAsState
                // calls have to happen in the parent composable and be captured.
                val shareScope = androidx.compose.runtime.rememberCoroutineScope()
                val discordUrl by graph.settings.discordWebhook.collectAsStateWithLifecycle(initialValue = "")
                val voiceChan by graph.settings.discordVoiceChannel.collectAsStateWithLifecycle(initialValue = "Richard's Hangout")
                val hudToaster = tv.enktel.app.ui.components.LocalToaster.current
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        FocusButton(if (playing) "⏸  Pause" else "▶  Play", accent = true, onClick = {
                            if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                            controlsTick++
                        })
                    }
                    item { FocusButton("−10s", onClick = { engine.player.seekBack(); controlsTick++ }) }
                    item { FocusButton("+30s", onClick = { engine.player.seekForward(); controlsTick++ }) }
                    item { FocusButton("Quality", onClick = { trackMenu = "video" }) }
                    item { FocusButton("Audio", onClick = { trackMenu = "audio" }) }
                    item { FocusButton("Subs", onClick = { trackMenu = "subs" }) }
                    if (!isLive) {
                        item {
                            FocusButton("Speed ${speed}x", onClick = {
                                speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                                engine.player.setPlaybackSpeed(speed)
                                controlsTick++
                            })
                        }
                    }
                    item {
                        FocusButton("Aspect", onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            controlsTick++
                        })
                    }
                    item {
                        FocusButton("⧉ PiP", onClick = {
                            (context as? android.app.Activity)?.let { tv.enktel.app.player.PictureInPicture.enter(it) }
                        })
                    }
                    item {
                        FocusButton("📺 Cast", onClick = {
                            tv.enktel.app.player.CastToTv.open(context)
                        })
                    }
                    if (discordUrl.isNotBlank()) {
                        item {
                            FocusButton("🎧 Share to $voiceChan", onClick = {
                                graph.discord.share(
                                    shareScope,
                                    tv.enktel.app.data.net.DiscordAnnouncer.Kind.Vod(
                                        title = title, year = 0, poster = "", genre = "",
                                    ),
                                )
                                hudToaster.success("Shared to Discord")
                                controlsTick++
                            })
                        }
                    }
                }
            }
        }

        if (trackMenu.isNotEmpty()) {
            val type = when (trackMenu) {
                "audio" -> C.TRACK_TYPE_AUDIO
                "video" -> C.TRACK_TYPE_VIDEO
                else -> C.TRACK_TYPE_TEXT
            }
            val title = when (trackMenu) {
                "audio" -> "Audio Track"
                "video" -> "Video Quality"
                else -> "Subtitles"
            }
            TrackPicker(
                title = title,
                allowOff = trackMenu == "subs" || trackMenu == "video",
                offLabel = if (trackMenu == "video") "Auto (adaptive)" else "Off",
                tracks = engine.tracksOf(type),
                onPick = { choice -> engine.selectTrack(type, choice); trackMenu = "" },
                onClose = { trackMenu = "" },
            )
        }
    }
}

/**
 * Focusable, scrubbable timeline. DPAD left/right nudges a preview position in 15s steps
 * (OK commits); touch supports tap-to-seek and drag scrubbing with a live time preview.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    // v1.32.0 — accept durationMs == 0 (media not prepared yet). frac stays
    // at 0 so the bar just renders as an empty rail until duration lands.
    // Drag/tap math checks `durationMs > 0` before firing onSeek so we don't
    // accidentally seek to position 0 during load.
    val safeDuration = durationMs.coerceAtLeast(1L)
    val shown = (scrubTarget ?: positionMs).coerceIn(0, safeDuration)
    val frac = if (durationMs > 0) shown.toFloat() / safeDuration else 0f

    Column(Modifier.fillMaxWidth()) {
        if (scrubTarget != null) {
            Text(
                "⇥ ${fmtTime(scrubTarget!!)}",
                color = Color.White, fontSize = 13.sp, fontWeight = TextFontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused && scrubTarget != null) { onSeek(scrubTarget!!); scrubTarget = null }
                }
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            scrubTarget = ((scrubTarget ?: positionMs) - 15_000).coerceAtLeast(0)
                            onInteract(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            scrubTarget = ((scrubTarget ?: positionMs) + 15_000).coerceAtMost(durationMs)
                            onInteract(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            scrubTarget?.let(onSeek); scrubTarget = null; true
                        }
                        else -> false
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            onSeek((durationMs * offset.x / size.width.coerceAtLeast(1)).toLong().coerceIn(0, durationMs))
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (durationMs > 0) {
                                scrubTarget = (durationMs * offset.x / size.width.coerceAtLeast(1)).toLong().coerceIn(0, durationMs)
                            }
                        },
                        onDragEnd = { scrubTarget?.let(onSeek); scrubTarget = null },
                        onDragCancel = { scrubTarget = null },
                    ) { change, _ ->
                        if (durationMs > 0) {
                            scrubTarget = (durationMs * change.position.x / size.width.coerceAtLeast(1)).toLong().coerceIn(0, durationMs)
                            onInteract()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            ProgressBarThin(frac, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(if (frac > 0.005f) frac else 0.005f),
                horizontalArrangement = LayoutArrangement.End,
            ) {
                Box(
                    Modifier
                        .height(if (focused || scrubTarget != null) 16.dp else 10.dp)
                        .width(if (focused || scrubTarget != null) 16.dp else 10.dp)
                        .background(
                            if (focused || scrubTarget != null) tv.enktel.app.ui.theme.EnktelBlue else Color.White,
                            CircleShape,
                        ),
                )
            }
        }
    }
}
