package tv.enktel.app.ui.live

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Category
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.repo.NowNext
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.util.Pin
import tv.enktel.app.util.UnlockSession
import tv.enktel.app.dvr.RecordScheduler
import tv.enktel.app.player.PlayerEngine
import tv.enktel.app.player.StreamStats
import tv.enktel.app.player.TrackChoice
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

@UnstableApi
@Composable
fun LivePlayerScreen(graph: AppGraph, nav: NavHostController, initialChannelKey: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return

    val bufferProfile by graph.settings.bufferProfile.collectAsStateWithLifecycle(initialValue = "balanced")
    val streamFormat by graph.settings.streamFormat.collectAsStateWithLifecycle(initialValue = "hls")

    val engine = remember(p.id) { PlayerEngine(context, graph.http, bufferProfile) }
    DisposableEffect(engine) { onDispose { engine.release() } }

    val channels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by graph.content.categories(p.id, "live").collectAsStateWithLifecycle(initialValue = emptyList())

    var current by remember { mutableStateOf<Channel?>(null) }
    var nowNext by remember { mutableStateOf(NowNext(null, null)) }
    val stats by engine.stats.collectAsStateWithLifecycle()
    val playError by engine.error.collectAsStateWithLifecycle()

    // Overlay state
    var showInfo by remember { mutableStateOf(true) }
    var showChannels by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var trackMenu by remember { mutableStateOf("") } // "" | audio | subs
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var numberBuffer by remember { mutableStateOf("") }
    var recordingId by remember { mutableStateOf(0L) }
    var isFav by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    // Time-shift: 0 = watching live, otherwise the archive timestamp playback started from.
    var shiftedFrom by remember { mutableStateOf(0L) }
    var sleepUntil by remember { mutableStateOf(0L) }
    var pinPrompt by remember { mutableStateOf(false) }
    var pendingCategory by remember { mutableStateOf<String?>(null) }
    val toaster = tv.enktel.app.ui.components.LocalToaster.current
    val pinHash by graph.settings.parentalPinHash.collectAsStateWithLifecycle(initialValue = "")
    val lockedCats by graph.settings.lockedCategories.collectAsStateWithLifecycle(initialValue = emptySet())

    val anyOverlay = showChannels || showQuickMenu || trackMenu.isNotEmpty() || pinPrompt

    fun tune(ch: Channel) {
        current = ch
        showChannels = false
        showInfo = true
        infoTick++
        shiftedFrom = 0L
        engine.play(graph.content.liveUrl(p, ch, streamFormat), live = true)
        scope.launch {
            graph.settings.setLastChannel(ch.key)
            graph.settings.pushRecentChannel(ch.key)
            nowNext = graph.epg.nowNext(p.id, ch.epgId)
        }
    }

    /** Jump into the channel's archive at [startMs] (restart programme / rewind live TV). */
    fun playShifted(startMs: Long) {
        val ch = current ?: return
        if (p.kind != "xtream" || !ch.hasArchive) {
            toaster.error("This channel has no catch-up archive")
            return
        }
        val durMin = ((System.currentTimeMillis() - startMs) / 60_000 + 180).coerceAtLeast(30)
        engine.play(XtreamClient.timeshiftUrl(p, ch.streamId, startMs, durMin), live = false)
        shiftedFrom = startMs
        showInfo = true; infoTick++
        toaster.info("Time-shift · ${hhmm(startMs)}")
    }

    fun zap(delta: Int) {
        val list = channels
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.key == current?.key }
        val next = list[((idx + delta) % list.size + list.size) % list.size]
        tune(next)
    }

    // Docked-browse state: when true the video shrinks to the top half of the screen and
    // a rich category / channel / guide browser fills the remaining space.
    var browseMode by remember { mutableStateOf(false) }

    // Initial tune
    LaunchedEffect(channels, p.id) {
        if (current != null || channels.isEmpty()) return@LaunchedEffect
        val lastKey = graph.settings.lastChannel.first()
        val target = channels.firstOrNull { it.key == initialChannelKey }
            ?: channels.firstOrNull { it.key == lastKey }
            ?: channels.first()
        tune(target)
    }

    // Favorite state
    LaunchedEffect(current?.key) {
        val ch = current ?: return@LaunchedEffect
        graph.content.isFavoriteFlow(p.id, "live", ch.streamId).collect { isFav = it }
    }

    // Auto-hide info bar
    LaunchedEffect(infoTick, anyOverlay) {
        if (showInfo && !anyOverlay) {
            delay(6000)
            showInfo = false
        }
    }

    // Periodic refresh of stats + EPG progress
    LaunchedEffect(current?.key) {
        while (true) {
            engine.push()
            val ch = current
            if (ch != null && (nowNext.now?.endMs ?: Long.MAX_VALUE) < System.currentTimeMillis()) {
                nowNext = graph.epg.nowNext(p.id, ch.epgId)
            }
            delay(1000)
        }
    }

    // Sleep timer
    LaunchedEffect(sleepUntil) {
        if (sleepUntil <= 0) return@LaunchedEffect
        delay((sleepUntil - System.currentTimeMillis()).coerceAtLeast(0))
        if (sleepUntil > 0) {
            engine.player.pause()
            nav.popBackStack()
        }
    }

    // Number zapping
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isEmpty()) return@LaunchedEffect
        delay(1600)
        val num = numberBuffer.toIntOrNull()
        numberBuffer = ""
        if (num != null) {
            graph.content.channelByNum(p.id, num)?.let { tune(it) }
        }
    }

    val backAction by graph.settings.backAction.collectAsStateWithLifecycle(initialValue = "exit")
    BackHandler {
        when {
            trackMenu.isNotEmpty() -> trackMenu = ""
            showQuickMenu -> showQuickMenu = false
            showChannels -> showChannels = false
            browseMode -> browseMode = false
            showInfo -> showInfo = false
            backAction == "pip" -> {
                val entered = (context as? android.app.Activity)?.let { tv.enktel.app.player.PictureInPicture.enter(it) } ?: false
                if (!entered) nav.popBackStack()
            }
            backAction == "guide_dock" -> {
                // Docked mini-player over the TV Guide (mini-player itself is a follow-up piece
                // of work — for now we jump to the guide but keep audio playing thanks to
                // the shared PlayerEngine surviving until this composable is disposed).
                nav.navigate("guide")
            }
            else -> nav.popBackStack()
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(anyOverlay) { if (!anyOverlay) rootFocus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            // Touchscreen support: tap shows the info bar, long-press opens the channel list.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showInfo = true; infoTick++ },
                    onLongPress = { showChannels = true },
                )
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || anyOverlay) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                        if (showInfo) showChannels = true else { showInfo = true; infoTick++ }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { showChannels = true; true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MENU -> { showQuickMenu = true; true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (engine.player.isPlaying) engine.player.pause() else engine.player.play(); true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        // Rewind on live = jump into the archive 5 minutes back (if supported).
                        if (shiftedFrom > 0) engine.player.seekBack()
                        else playShifted(System.currentTimeMillis() - 5 * 60_000)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (shiftedFrom > 0) engine.player.seekForward()
                        true
                    }
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        numberBuffer += ('0' + (ev.key.nativeKeyCode - AndroidKeyEvent.KEYCODE_0))
                        showInfo = true; infoTick++
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (browseMode) {
            // Docked layout: video pane on top (aspect-locked so it never gets crushed on
            // portrait phones), browser panel underneath filling the rest. Uses Column so
            // DPAD focus travels naturally between the two panels.
            Column(Modifier.fillMaxSize().background(tv.enktel.app.ui.theme.EnktelBg)) {
                // Video pane in a rounded, subtle glass frame with an overlay ribbon showing
                // the currently-playing channel + programme so the user always knows what
                // they're browsing while previewing. Tapping the video returns to fullscreen,
                // TiVi Mate style.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        .aspectRatio(16f / 9f)
                        .tapClick { browseMode = false },
                ) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { useController = false; setKeepContentOnPlayerReset(true) } },
                        update = { view -> view.player = engine.player; view.resizeMode = resizeMode },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Fullscreen return button, top-right corner. Big enough to hit with a
                    // thumb on a phone and reachable with a single DPAD-UP on a remote.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(0.55f))
                            .tapClick { browseMode = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("⛶ Fullscreen", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    // Compact bottom ribbon over the video: channel + now-playing + progress
                    current?.let { ch ->
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(0.75f)),
                                    ),
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.background(EnktelLive, RoundedCornerShape(2.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) { Text("● LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                                if (ch.num > 0) Text("${ch.num}", color = EnktelBlue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text(
                                    ch.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                nowNext.now?.let { np ->
                                    Text("·", color = Color.White.copy(0.6f), fontSize = 12.sp)
                                    Text(
                                        np.title, color = Color.White.copy(0.9f), fontSize = 12.sp,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                            nowNext.now?.let { np ->
                                Spacer(Modifier.height(4.dp))
                                val frac = ((System.currentTimeMillis() - np.startMs).toFloat() /
                                    (np.endMs - np.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                                ProgressBarThin(frac, Modifier.fillMaxWidth(0.85f))
                            }
                        }
                    }
                }
                BrowseDock(
                    graph = graph, profileId = p.id, currentChannel = current,
                    onTune = { tune(it) },
                    onOpenGuide = { nav.navigate("guide") },
                    onClose = { browseMode = false },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { view ->
                    view.player = engine.player
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (playError != null) {
            Box(Modifier.align(Alignment.Center)) {
                Column(
                    Modifier.background(EnktelSurface.copy(0.9f), RoundedCornerShape(10.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Playback error: $playError", color = EnktelLive, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Press OK for channel list · UP/DOWN to zap", color = EnktelTextDim, fontSize = 12.sp)
                }
            }
        }

        if (numberBuffer.isNotEmpty()) {
            Text(
                numberBuffer,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(Color.Black.copy(0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        if (showStats && !browseMode) {
            StatsOverlay(stats, streamFormat, Modifier.align(Alignment.TopStart).padding(24.dp))
        }

        // InfoBar + action strip are for fullscreen playback only. When the user opens
        // the docked Browse mode we hide them so the BrowseDock isn't overlapped from
        // below — the docked-video ribbon and the dock itself already give the user all
        // the info + actions they need in that mode.
        if (showInfo && current != null && !browseMode) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                InfoBar(
                    channel = current!!,
                    nowNext = nowNext,
                    stats = stats,
                    recording = recordingId != 0L,
                    shiftedFrom = shiftedFrom,
                    sleepUntil = sleepUntil,
                    modifier = Modifier,
                )
                // On-screen action bar so touch users can reach every player control without
                // needing a remote/MENU button. Also visible on TV but designed to be tap-safe.
                // LazyRow so the strip scrolls horizontally on portrait phones instead of
                // clipping when the ⋯ More button falls off-screen.
                LazyRow(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Black.copy(0.92f))))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { FocusButton("▤ Browse", accent = true, onClick = { browseMode = true }) }
                    item { FocusButton("☰ Channels", onClick = { showChannels = true }) }
                    item { FocusButton("EPG", onClick = { nav.navigate("guide") }) }
                    item {
                        FocusButton(
                            if (recordingId != 0L) "■ REC" else "● Record",
                            accent = recordingId != 0L,
                            onClick = {
                                val ch = current!!
                                scope.launch {
                                    if (recordingId == 0L) {
                                        recordingId = tv.enktel.app.dvr.RecordScheduler.recordNow(
                                            context, p.id,
                                            title = nowNext.now?.title ?: ch.name,
                                            channelName = ch.name,
                                            streamUrl = graph.content.liveUrl(p, ch, "ts"),
                                        )
                                        toaster.success("Recording ${ch.name}")
                                    } else {
                                        tv.enktel.app.dvr.RecordScheduler.cancel(context, recordingId); recordingId = 0L
                                        toaster.info("Recording stopped")
                                    }
                                }
                            },
                        )
                    }
                    item {
                        FocusButton(if (isFav) "★" else "☆", accent = isFav, onClick = {
                            scope.launch { graph.content.toggleFavorite(p.id, "live", current!!.streamId) }
                        })
                    }
                    item { FocusButton("Audio", onClick = { trackMenu = "audio" }) }
                    item { FocusButton("Subs", onClick = { trackMenu = "subs" }) }
                    item {
                        FocusButton("Aspect", onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        })
                    }
                    item {
                        FocusButton("⧉ PiP", onClick = {
                            val ok = (context as? android.app.Activity)?.let { tv.enktel.app.player.PictureInPicture.enter(it) } ?: false
                            if (!ok) toaster.error("Picture-in-Picture not supported here")
                        })
                    }
                    item { FocusButton("⋯ More", onClick = { showQuickMenu = true }) }
                }
            }
        }

        if (showChannels) {
            ChannelPanel(
                categories = categories,
                channels = channels,
                currentKey = current?.key,
                graph = graph,
                profileId = p.id,
                isCategoryLocked = { catId ->
                    pinHash.isNotBlank() && !UnlockSession.unlocked && "live:$catId" in lockedCats
                },
                onLockedCategory = { catId -> pendingCategory = catId; pinPrompt = true },
                onPick = { tune(it) },
                onClose = { showChannels = false },
            )
        }

        if (pinPrompt) {
            tv.enktel.app.ui.components.PinDialog(
                title = "Locked category — enter PIN",
                onSubmit = { pin ->
                    if (Pin.matches(pin, pinHash)) {
                        UnlockSession.unlocked = true
                        pinPrompt = false
                        toaster.success("Unlocked")
                    } else {
                        toaster.error("Wrong PIN")
                    }
                },
                onDismiss = { pinPrompt = false; pendingCategory = null },
            )
        }

        if (showQuickMenu && current != null) {
            QuickMenu(
                channel = current!!,
                isFav = isFav,
                recording = recordingId != 0L,
                showStats = showStats,
                shifted = shiftedFrom > 0,
                canShift = p.kind == "xtream" && current!!.hasArchive,
                sleepUntil = sleepUntil,
                onRestartProgram = {
                    val start = nowNext.now?.startMs
                    if (start != null) { showQuickMenu = false; playShifted(start) }
                    else toaster.error("No EPG data for this programme")
                },
                onRewindLive = {
                    showQuickMenu = false
                    playShifted(if (shiftedFrom > 0) shiftedFrom - 5 * 60_000 else System.currentTimeMillis() - 5 * 60_000)
                },
                onBackToLive = { showQuickMenu = false; current?.let { tune(it) } },
                onSleep = {
                    sleepUntil = when {
                        sleepUntil <= 0 -> System.currentTimeMillis() + 30 * 60_000
                        sleepUntil - System.currentTimeMillis() < 35 * 60_000 -> System.currentTimeMillis() + 60 * 60_000
                        sleepUntil - System.currentTimeMillis() < 65 * 60_000 -> System.currentTimeMillis() + 90 * 60_000
                        sleepUntil - System.currentTimeMillis() < 95 * 60_000 -> System.currentTimeMillis() + 120 * 60_000
                        else -> 0L
                    }
                    toaster.info(
                        if (sleepUntil <= 0) "Sleep timer off"
                        else "Sleep in ${(sleepUntil - System.currentTimeMillis()) / 60_000} min"
                    )
                },
                onAudio = { trackMenu = "audio"; showQuickMenu = false },
                onSubs = { trackMenu = "subs"; showQuickMenu = false },
                onAspect = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onStats = { showStats = !showStats },
                onFavorite = {
                    scope.launch { graph.content.toggleFavorite(p.id, "live", current!!.streamId) }
                },
                onRecord = {
                    val ch = current!!
                    scope.launch {
                        if (recordingId == 0L) {
                            recordingId = RecordScheduler.recordNow(
                                context, p.id,
                                title = nowNext.now?.title ?: ch.name,
                                channelName = ch.name,
                                streamUrl = graph.content.liveUrl(p, ch, "ts"),
                                channelLogo = ch.logo,
                            )
                            toaster.success("Recording ${ch.name}")
                        } else {
                            RecordScheduler.cancel(context, recordingId)
                            recordingId = 0L
                            toaster.info("Recording stopped")
                        }
                    }
                },
                onCatchup = {
                    showQuickMenu = false
                    nav.navigate("catchup/${current!!.key}")
                },
                onGuide = { showQuickMenu = false; nav.navigate("guide") },
                onPip = {
                    showQuickMenu = false
                    val ok = (context as? android.app.Activity)?.let { tv.enktel.app.player.PictureInPicture.enter(it) } ?: false
                    if (!ok) toaster.info("Picture-in-Picture not supported here")
                },
                onMultiView = {
                    showQuickMenu = false
                    nav.navigate("multi?left=${current?.key.orEmpty()}&right=")
                },
                onClose = { showQuickMenu = false },
            )
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

@Composable
private fun InfoBar(
    channel: Channel,
    nowNext: NowNext,
    stats: StreamStats,
    recording: Boolean,
    shiftedFrom: Long = 0,
    sleepUntil: Long = 0,
    modifier: Modifier,
) {
    val now = nowNext.now
    val next = nowNext.next
    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val hPad = if (isMobile) 18.dp else 48.dp
    val vPad = if (isMobile) 18.dp else 26.dp
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.92f))))
            .padding(horizontal = hPad, vertical = vPad),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(EnktelSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Text(channel.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (channel.num > 0) Text("${channel.num}", color = EnktelBlue, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(channel.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (channel.hasArchive) Badge("CATCH-UP", EnktelOk)
                    if (recording) Badge("● REC", EnktelLive)
                    if (shiftedFrom > 0) Badge("⏪ TIMESHIFT ${hhmm(shiftedFrom)}", EnktelLive)
                    if (sleepUntil > 0) Badge("☾ ${((sleepUntil - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)}m")
                    if (stats.height > 0) Badge("${stats.height}p")
                }
                Spacer(Modifier.height(6.dp))
                if (now != null) {
                    // Bigger, prominent programme title on its own line.
                    Text(
                        now.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "${hhmm(now.startMs)}–${hhmm(now.endMs)}",
                        color = Color.White.copy(0.75f), fontSize = 12.sp,
                    )
                    if (now.desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            now.desc, color = Color.White.copy(0.75f), fontSize = 12.sp,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val frac = ((System.currentTimeMillis() - now.startMs).toFloat() /
                        (now.endMs - now.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                    ProgressBarThin(frac, Modifier.fillMaxWidth(0.6f))
                    if (next != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Up next · ${hhmm(next.startMs)}  ${next.title}", color = EnktelTextDim, fontSize = 12.sp, maxLines = 1)
                    }
                } else {
                    Text("No guide data — open Settings ▸ Refresh EPG", color = EnktelTextDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(hhmm(System.currentTimeMillis()), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (tv.enktel.app.BuildConfig.FLAVOR == "mobile")
                "Tap to hide · long-press for channel list"
            else "OK channels · ◀ list · ▶ options · ▲▼ zap · 0-9 number",
            color = EnktelTextDim.copy(0.8f), fontSize = 11.sp,
        )
    }
}

@Composable
private fun StatsOverlay(s: StreamStats, format: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.Black.copy(0.75f), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("STREAM STATS", color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("Resolution  ${s.width}×${s.height} @ ${"%.0f".format(s.frameRate)}fps", color = Color.White, fontSize = 12.sp)
        Text("Video  ${s.videoCodec.ifBlank { "—" }}  ·  Audio  ${s.audioCodec.ifBlank { "—" }}", color = Color.White, fontSize = 12.sp)
        Text("Bitrate  ${if (s.videoBitrate > 0) "${s.videoBitrate / 1000} kbps" else "—"}", color = Color.White, fontSize = 12.sp)
        Text("Network  ${s.bandwidthEstimate / 1000} kbps est.", color = Color.White, fontSize = 12.sp)
        Text("Buffer  ${s.bufferAheadMs / 1000}.${s.bufferAheadMs % 1000 / 100}s ahead", color = Color.White, fontSize = 12.sp)
        Text("Dropped frames  ${s.droppedFrames}", color = Color.White, fontSize = 12.sp)
        Text("Decoder  ${s.decoder.ifBlank { "—" }}  ·  ${format.uppercase()}", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun ChannelPanel(
    categories: List<Category>,
    channels: List<Channel>,
    currentKey: String?,
    graph: AppGraph,
    profileId: Long,
    isCategoryLocked: (String) -> Boolean = { false },
    onLockedCategory: (String) -> Unit = {},
    onPick: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    var selectedCat by remember { mutableStateOf<String?>(null) }
    val filtered = remember(channels, selectedCat) {
        if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(filtered) {
        val idx = filtered.indexOfFirst { it.key == currentKey }
        if (idx > 0) listState.scrollToItem(idx)
    }

    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(210.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(0.92f))
                .padding(vertical = 20.dp),
        ) {
            Text("CATEGORIES", color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn {
                item {
                    PanelRow("All channels (${channels.size})", selected = selectedCat == null) { selectedCat = null }
                }
                items(categories, key = { it.key }) { cat ->
                    val locked = isCategoryLocked(cat.categoryId)
                    PanelRow(
                        (if (locked) "🔒 " else "") + cat.name,
                        selected = selectedCat == cat.categoryId,
                    ) {
                        if (locked) onLockedCategory(cat.categoryId) else selectedCat = cat.categoryId
                    }
                }
            }
        }
        Column(
            Modifier
                .width(400.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(0.85f))
                .padding(vertical = 20.dp),
        ) {
            Text("CHANNELS", color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(state = listState) {
                items(filtered, key = { it.key }) { ch ->
                    ChannelRow(ch, active = ch.key == currentKey, graph = graph, profileId = profileId) { onPick(ch) }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) EnktelBlue.copy(0.25f) else Color.Transparent,
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = if (selected) Color.White else EnktelTextDim,
        ),
    ) {
        Text(text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

@Composable
private fun ChannelRow(ch: Channel, active: Boolean, graph: AppGraph, profileId: Long, onClick: () -> Unit) {
    var nowTitle by remember(ch.key) { mutableStateOf("") }
    LaunchedEffect(ch.key) {
        nowTitle = graph.epg.nowNext(profileId, ch.epgId).now?.title.orEmpty()
    }
    androidx.tv.material3.Surface(
        onClick = onClick,
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (active) EnktelBlue.copy(0.2f) else Color.Transparent,
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (ch.num > 0) "${ch.num}" else "·",
                color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(38.dp),
            )
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)).background(EnktelSurface)) {
                if (ch.logo.isNotBlank()) AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(ch.name, fontSize = 13.sp, maxLines = 1, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                if (nowTitle.isNotBlank()) Text(nowTitle, fontSize = 11.sp, color = EnktelTextDim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun QuickMenu(
    channel: Channel,
    isFav: Boolean,
    recording: Boolean,
    showStats: Boolean,
    shifted: Boolean,
    canShift: Boolean,
    sleepUntil: Long,
    onRestartProgram: () -> Unit,
    onRewindLive: () -> Unit,
    onBackToLive: () -> Unit,
    onSleep: () -> Unit,
    onAudio: () -> Unit,
    onSubs: () -> Unit,
    onAspect: () -> Unit,
    onStats: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    onCatchup: () -> Unit,
    onGuide: () -> Unit,
    onPip: () -> Unit,
    onMultiView: () -> Unit,
    onClose: () -> Unit,
) {
    val sleepLabel = if (sleepUntil <= 0) "Sleep timer: off"
    else "Sleep in ${((sleepUntil - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)} min"
    val menuScroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(280.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(0.92f))
                .padding(20.dp)
                .verticalScroll(menuScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(channel.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            if (shifted) FocusButton("🔴 Back to LIVE", accent = true, onClick = onBackToLive, modifier = Modifier.fillMaxWidth())
            if (canShift) {
                FocusButton("⏮ Restart programme", onClick = onRestartProgram, modifier = Modifier.fillMaxWidth())
                FocusButton("⏪ Back 5 minutes", onClick = onRewindLive, modifier = Modifier.fillMaxWidth())
            }
            FocusButton(if (isFav) "★ Remove favorite" else "☆ Add favorite", onClick = onFavorite, modifier = Modifier.fillMaxWidth())
            FocusButton(if (recording) "■ Stop recording" else "● Record now (DVR)", onClick = onRecord, modifier = Modifier.fillMaxWidth())
            if (channel.hasArchive) FocusButton("🗂 Catch-up archive", onClick = onCatchup, modifier = Modifier.fillMaxWidth())
            FocusButton(sleepLabel, onClick = onSleep, modifier = Modifier.fillMaxWidth())
            FocusButton("Audio track", onClick = onAudio, modifier = Modifier.fillMaxWidth())
            FocusButton("Subtitles", onClick = onSubs, modifier = Modifier.fillMaxWidth())
            FocusButton("Aspect ratio", onClick = onAspect, modifier = Modifier.fillMaxWidth())
            FocusButton(if (showStats) "Hide stream stats" else "Stream stats", onClick = onStats, modifier = Modifier.fillMaxWidth())
            FocusButton("⧉ Picture-in-Picture", onClick = onPip, modifier = Modifier.fillMaxWidth())
            FocusButton("▤▤ Multi-view", onClick = onMultiView, modifier = Modifier.fillMaxWidth())
            FocusButton("TV Guide", onClick = onGuide, modifier = Modifier.fillMaxWidth())
            FocusButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun TrackPicker(
    title: String,
    allowOff: Boolean,
    tracks: List<TrackChoice>,
    onPick: (TrackChoice?) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(360.dp)
                .background(EnktelSurface, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (tracks.isEmpty()) Text("No tracks available", color = EnktelTextDim, fontSize = 13.sp)
            if (allowOff) FocusButton("Off", onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth())
            tracks.forEach { t ->
                FocusButton(
                    (if (t.selected) "✓ " else "") + t.name,
                    onClick = { onPick(t) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FocusButton("Cancel", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Docked-player browser: sits under the shrunken video and lets the user pick a category,
 *  scroll categorised channels, and preview the current channel's upcoming programmes —
 *  all without leaving playback. */
@Composable
private fun BrowseDock(
    graph: AppGraph,
    profileId: Long,
    currentChannel: Channel?,
    onTune: (Channel) -> Unit,
    onOpenGuide: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories by graph.content.categories(profileId, "live").collectAsStateWithLifecycle(initialValue = emptyList())
    val allChannels by graph.content.channels(profileId).collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedCat by remember { mutableStateOf<String?>(currentChannel?.categoryId) }
    val channels = remember(allChannels, selectedCat) {
        if (selectedCat == null) allChannels else allChannels.filter { it.categoryId == selectedCat }
    }
    var upcoming by remember { mutableStateOf<List<tv.enktel.app.data.db.EpgProgram>>(emptyList()) }
    LaunchedEffect(currentChannel?.key) {
        upcoming = try { graph.epg.upcoming(profileId, currentChannel?.epgId.orEmpty(), 6) } catch (_: Throwable) { emptyList() }
    }
    Column(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.95f), tv.enktel.app.ui.theme.EnktelBg),
                ),
            )
            .border(
                1.dp,
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("BROWSE", color = EnktelTextDim, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text(
                    if (selectedCat != null) categories.firstOrNull { it.categoryId == selectedCat }?.name ?: "All Channels"
                    else "All Channels",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${channels.size}",
                color = EnktelBlue, fontSize = 20.sp, fontWeight = FontWeight.Black,
            )
            Text(" channels", color = EnktelTextDim, fontSize = 11.sp)
            Box(Modifier.weight(1f))
            // TiVi Mate style colour-remote-button row: quick actions the user's eye
            // learns as coloured shortcuts (red = full guide, grey = close).
            FocusButton("📅 Full Guide", accent = true, onClick = onOpenGuide)
            Spacer(Modifier.width(6.dp))
            FocusButton("✕", onClick = onClose)
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                tv.enktel.app.ui.components.GlassChip(
                    "All", selected = selectedCat == null,
                    onClick = { selectedCat = null },
                )
            }
            items(categories, key = { it.key }) { c ->
                tv.enktel.app.ui.components.GlassChip(
                    c.name, selected = selectedCat == c.categoryId,
                    onClick = {
                        selectedCat = if (selectedCat == c.categoryId) null else c.categoryId
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.weight(0.62f).fillMaxHeight(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.key }) { ch ->
                    BrowseChannelRow(channel = ch, active = ch.key == currentChannel?.key, onClick = { onTune(ch) })
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.08f)))
            Column(
                Modifier.weight(0.38f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    if (currentChannel != null) "GUIDE · ${currentChannel.name}" else "GUIDE",
                    color = EnktelTextDim, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(6.dp))
                if (upcoming.isEmpty()) {
                    Text("No guide data for this channel.", color = EnktelTextDim, fontSize = 12.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(upcoming) { prog ->
                            val now = System.currentTimeMillis()
                            val isNow = prog.startMs <= now && prog.endMs > now
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    hhmm(prog.startMs),
                                    color = if (isNow) EnktelBlue else EnktelTextDim,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        prog.title, color = Color.White, fontSize = 12.sp,
                                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (isNow && prog.desc.isNotBlank()) {
                                        Text(
                                            prog.desc, color = Color.White.copy(0.65f), fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseChannelRow(channel: Channel, active: Boolean, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tapClick(onClick),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (active) EnktelBlue.copy(0.22f) else Color.Transparent,
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left accent stripe (visible when active) — reinforces the selection.
            Box(
                Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) EnktelBlue else Color.Transparent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (channel.num > 0) "${channel.num}" else "·",
                color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp),
            )
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                    .background(tv.enktel.app.ui.theme.EnktelSurfaceHigh),
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                channel.name, fontSize = 12.sp, maxLines = 1,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Live-now dot pulses on the currently-playing row so the user's eye locks on it
            // instantly when the list is long.
            if (active) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(EnktelLive),
                )
            }
        }
    }
}
