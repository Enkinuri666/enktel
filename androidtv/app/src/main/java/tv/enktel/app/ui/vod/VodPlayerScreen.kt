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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChanged
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.WatchProgress
import tv.enktel.app.player.PlayerEngine
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.cinematicScrim
import tv.enktel.app.ui.components.glassChip
import tv.enktel.app.ui.components.glassSurface
import tv.enktel.app.ui.components.rememberDominantColor
import tv.enktel.app.ui.live.TrackPicker
import tv.enktel.app.ui.player.AspectMode
import tv.enktel.app.ui.player.SubtitleOverlay
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelTextDim
import java.util.Locale
import tv.enktel.app.ui.components.tvRailFocus

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

// How long before the end the "next episode" card appears — thirty seconds,
// roughly one set of closing credits — now lives on NextUp with the arithmetic
// that reads it. See NextUp.WINDOW_MS.

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
    /** Route for the following episode, or empty. See vodPlayerRoute. */
    nextRoute: String = "",
    /** "S2 E4 · The Bells" — what the countdown card announces. */
    nextLabel: String = "",
    /** Artwork for this title, stored with the resume point. See vodPlayerRoute. */
    posterUrl: String = "",
    /** Series this episode belongs to, or 0 for a film. See vodPlayerRoute. */
    seriesId: Long = 0,
    /** Which episode of [seriesId] is playing. */
    episodeId: Long = 0,
    /** The series' own name, used to title episodes resolved here. */
    seriesName: String = "",
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
    // v1.38.0 — process-owned engine, so leaving this screen can dock the film
    // into the mini window rather than tearing playback down. See
    // PlaybackSession, which also owns the engine settings snapshot that used
    // to rebuild this engine mid-startup as DataStore values resolved.
    val session = graph.playback
    // This screen also serves catch-up, which is a live-edge stream despite
    // arriving through the VOD player, so the kind follows the route flag
    // rather than the screen.
    val engine = session.engine(live = isLive)
    // Claimed during composition, deliberately: rolling into the next episode
    // mounts a second copy of this screen before the first one is disposed, and
    // the claim has to be taken before that disposal runs. See PlaybackClaims.
    val claim = remember { session.claim() }
    LaunchedEffect(Unit) { session.expand() }
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
    var speed by remember { mutableFloatStateOf(1f) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    var positionMs by remember { mutableLongStateOf(0L) }
    /** Seconds left on the countdown card, or null when it is not showing. */
    var nextUpSecs by remember { mutableStateOf<Int?>(null) }
    /** Set by the player's own listener when the media reports it finished. */
    var playbackEnded by remember(url) { mutableStateOf(false) }
    /** Raised by the ticker when it is time to roll into the next episode. */
    var wantsNext by remember(url) { mutableStateOf(false) }
    /** Set when the viewer dismisses the card, so it stays dismissed. */
    var nextUpCancelled by remember { mutableStateOf(false) }

    // What follows this episode, when the caller could not say.
    //
    // The series screen seeds the first hop because it already holds the season
    // map, but a route cannot carry a route that carries a route — so from the
    // second episode on it arrives blank, and a binge used to stop dead after
    // one automatic roll-over. Resolved here instead, once, on entry rather
    // than when the card is due: the answer is wanted minutes later, so a slow
    // panel costs nothing, and a failed lookup simply leaves the card off.
    var resolvedRoute by remember(url) { mutableStateOf("") }
    var resolvedLabel by remember(url) { mutableStateOf("") }
    LaunchedEffect(url, seriesId, episodeId) {
        if (nextRoute.isNotBlank() || seriesId == 0L || episodeId == 0L) return@LaunchedEffect
        val p = graph.playlists.activeProfile() ?: return@LaunchedEffect
        val details = runCatching { graph.content.seriesDetails(p, seriesId) }.getOrNull()
            ?: return@LaunchedEffect
        val next = tv.enktel.app.data.repo.NextEpisode.after(details.seasons, episodeId)
            ?: return@LaunchedEffect
        resolvedRoute = tv.enktel.app.vodPlayerRoute(
            tv.enktel.app.data.xtream.XtreamClient.episodeUrl(p, next.id, next.ext),
            tv.enktel.app.data.repo.NextEpisode.title(seriesName, next),
            "${p.id}:episode:${next.id}",
            poster = posterUrl,
            seriesId = seriesId,
            episodeId = next.id,
            seriesName = seriesName,
        )
        resolvedLabel = tv.enktel.app.data.repo.NextEpisode.label(next)
    }
    /** The seeded hop if there is one, otherwise the one resolved above. */
    val upNextRoute = nextRoute.ifBlank { resolvedRoute }
    val upNextLabel = nextLabel.ifBlank { resolvedLabel }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }

    /** Position auto-resumed to on entry, or 0. Drives the "Start over" chip. */
    var resumedFromMs by remember(progressKey) { mutableLongStateOf(0L) }

    // Null until this screen has started something itself. Distinguishes
    // "mounting over a stream that's already running" (expanding the dock —
    // adopt it) from "a setting changed under us" (re-play, as before).
    var startedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url, vodForceMp4) {
        val adopt = startedUrl == null && session.isLoaded(url)
        startedUrl = url
        session.setNowPlaying(
            tv.enktel.app.player.PlaybackSession.NowPlaying(
                kind = tv.enktel.app.player.PlaybackSession.Kind.VOD,
                contentId = url,
                title = title,
                subtitle = if (isLive) "Live" else "",
                returnRoute = tv.enktel.app.vodPlayerRoute(url, title, progressKey, isLive, poster = posterUrl),
            )
        )
        // Replaying here would drop the user back to the start of the film and
        // make it re-buffer, every time they came back from the dock.
        if (adopt) return@LaunchedEffect
        val resume = if (progressKey.isNotBlank()) graph.content.progress(progressKey)?.positionMs ?: 0L else 0L
        // Surfaced so the player can tell the user it jumped, and offer the
        // way back. Auto-resuming silently is right most of the time and
        // baffling the rest — when it resumes something you'd finished with, or
        // resumes the wrong episode, there was previously no way to undo it
        // short of scrubbing back by hand.
        resumedFromMs = if (!isLive && resume > 60_000) resume else 0L
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
            // What the system's transport controls announce. `title` is already
            // the fully-formed "Series S1E2 · Name" the player shows.
            title = title,
            artworkUrl = posterUrl,
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
    // Leaving this screen ends playback — unless we deliberately docked, which
    // is the one case where audio with no fullscreen player is legitimate,
    // because the mini window is on screen and closable.
    //
    // The engine now outlives this composable, so the teardown that used to
    // happen implicitly has to be stated. Getting this wrong is precisely the
    // v1.35.1 audio-after-exit defect.
    //
    // ...and getting it *too* right is the next-episode defect: this screen
    // navigating to itself means the incoming copy has already claimed the
    // session and started the next episode by the time this runs, so an
    // unconditional stop() released the engine that was mid-play. Hence
    // stopIfOwner: the last screen standing tears down, a superseded one
    // doesn't. Both the reset and the stop are gated, because resetting the
    // display mode would blank the television the new episode is playing on.
    DisposableEffect(Unit) {
        onDispose {
            if (session.mode.value != tv.enktel.app.player.PlaybackSession.Mode.DOCKED) {
                if (session.stopIfOwner(claim)) {
                    (ctxForRefresh as? android.app.Activity)?.let {
                        tv.enktel.app.player.RefreshRateMatcher.reset(it)
                    }
                }
            }
        }
    }

    /**
     * Write (or clear) the resume point for this title.
     *
     * Clearing near the end is deliberate: a film you watched to the credits
     * should not reappear in Continue Watching offering to resume the last
     * thirty seconds.
     */
    suspend fun persistProgress(atMs: Long, totalMs: Long) {
        if (isLive || progressKey.isBlank() || totalMs <= 0) return
        if (atMs > totalMs - 30_000) {
            graph.content.clearProgress(progressKey)
            return
        }
        graph.content.saveProgress(
            WatchProgress(
                key = progressKey,
                profileId = progressKey.substringBefore(':').toLongOrNull() ?: 0,
                kind = progressKey.split(':').getOrElse(1) { "vod" },
                refId = progressKey.substringAfterLast(':').toLongOrNull() ?: 0,
                name = title, url = url, poster = posterUrl,
                positionMs = atMs, durationMs = totalMs,
                // Stored so resuming from Continue Watching an hour later can
                // still work out what follows. Nothing else joins an episode
                // id back to its series.
                seriesId = seriesId, seriesName = seriesName,
            )
        )
    }

    // Position ticker + periodic progress persistence.
    //
    // 250 ms rather than 500: the elapsed clock and the scrubber thumb are both
    // driven from here, and at half-second granularity the bar visibly steps
    // instead of moving.
    LaunchedEffect(Unit) {
        var lastSave = 0L
        // How long the position has stood still while playback was wanted.
        // Only counted when the player is not paused: a viewer who stops
        // twenty seconds from the end has not finished the episode, and
        // rolling them into the next one would be the worst kind of help.
        var lastPos = -1L
        var stalledMs = 0L
        while (true) {
            val tickMs = if (playing) 250L else 1_000L
            positionMs = engine.player.currentPosition.coerceAtLeast(0)
            durationMs = engine.player.duration.coerceAtLeast(0)
            stalledMs = if (engine.player.playWhenReady && positionMs == lastPos) {
                stalledMs + tickMs
            } else {
                0L
            }
            lastPos = positionMs
            // Binge countdown.
            //
            // Driven off position rather than STATE_ENDED: closing credits are
            // part of the runtime, so waiting for the stream to actually end
            // means the card appears after the picture has already gone black,
            // which is too late to be the thing that keeps someone watching.
            //
            // Not shown when the user has scrubbed backwards into the last
            // thirty seconds on purpose — they are looking at something, and a
            // countdown over it is an interruption rather than a convenience.
            if (upNextRoute.isNotBlank() && !isLive) {
                val secs = NextUp.secondsLeft(durationMs, positionMs)
                nextUpSecs = secs
                // Scrubbing back out of the window dismisses it, and a later
                // approach shows it again.
                if (secs == null) {
                    nextUpCancelled = false
                    playbackEnded = false
                }
                // The single place the roll-over is decided. It used to be
                // decided in two — a countdown that had to hit exactly zero,
                // and an end-of-stream callback — and a file whose declared
                // runtime is a second longer than its content satisfies
                // neither. See NextUp.shouldAdvance.
                if (autoplayNextEp && !nextUpCancelled &&
                    NextUp.shouldAdvance(durationMs, positionMs, playbackEnded, stalledMs)
                ) {
                    wantsNext = true
                }
            }
            playing = engine.player.isPlaying
            val now = System.currentTimeMillis()
            if (now - lastSave > 10_000) {
                lastSave = now
                persistProgress(positionMs, durationMs)
            }
            // Only worth 250 ms while something is actually moving. Paused (or
            // docked to the mini player with the scrubber off-screen) the same
            // rate is four wake-ups a second that write back identical values,
            // which a Fire TV Stick can ill afford.
            delay(tickMs)
        }
    }

    // Save on the way out as well as on the timer.
    //
    // The 10-second tick alone loses up to ten seconds of every session, and
    // loses the position entirely for anyone who backs out in the first ten
    // seconds — so "resume where I left off" quietly resumed somewhere else.
    // This runs before the teardown effect below, because effects dispose in
    // reverse declaration order and the engine has to still be alive to be
    // asked where it got to.
    DisposableEffect(Unit) {
        onDispose {
            val at = engine.player.currentPosition.coerceAtLeast(0)
            val total = engine.player.duration.coerceAtLeast(0)
            // Application-scoped on purpose: rememberCoroutineScope is
            // cancelled with the composition, which is precisely the moment
            // this write happens, so the save would never land.
            graph.appScope.launch { persistProgress(at, total) }
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


    val backAction by graph.settings.backAction.collectAsStateWithLifecycle(initialValue = "exit")

    /** Shrink to the mini window and open [route] — playback carries on. */
    fun dockAndBrowse(route: String) {
        if (!session.dock()) { nav.navigate(route) { launchSingleTop = true }; return }
        trackMenu = ""
        nav.navigate(route) { launchSingleTop = true }
    }

    BackHandler {
        when {
            trackMenu.isNotEmpty() -> trackMenu = ""
            // Back dismisses the card, the same as "Not now" — otherwise the
            // only way out of it was to leave playback entirely.
            nextUpSecs != null && !nextUpCancelled -> { nextUpCancelled = true; nextUpSecs = null }
            showControls -> showControls = false
            backAction == "dock" -> dockAndBrowse("home")
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

    /** True while the next-episode card is on screen. It owns the remote. */
    val nextUpShowing = nextUpSecs != null && !nextUpCancelled

    /**
     * Roll into the next episode.
     *
     * Hoisted out of the card so the countdown, the button and the end of the
     * stream can all reach it — the auto-advance used to live inside the card
     * and fire on a countdown that could not reach zero.
     */
    fun playNext() {
        if (upNextRoute.isBlank()) return
        nextUpCancelled = true
        // popUpTo the current entry so a binged season does not build a back
        // stack twenty episodes deep — Back should return to the series, not
        // walk backwards through everything just watched.
        nav.navigate(upNextRoute) {
            popUpTo(
                "vodPlayer?url={url}&title={title}&pk={pk}&live={live}&nr={nr}&nl={nl}&ps={ps}" +
                    "&sid={sid}&eid={eid}&sn={sn}",
            ) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    /** Which card button the remote is on: 0 = Play now, 1 = Not now. */
    var nextUpChoice by remember { mutableIntStateOf(0) }
    // The card is a fresh decision every time it appears.
    LaunchedEffect(nextUpShowing) { if (nextUpShowing) { nextUpChoice = 0; showControls = false } }

    // End of stream is the backstop for the countdown. A panel that reports a
    // duration a second or two short of the real runtime — common enough on
    // Xtream VOD — would otherwise sit at "0s" with the picture still running.
    DisposableEffect(engine) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) playbackEnded = true
            }
        }
        engine.player.addListener(listener)
        onDispose { engine.player.removeListener(listener) }
    }

    // Navigation happens here rather than on the ticker, so it runs once per
    // decision on the composition's own scope instead of inside a loop.
    LaunchedEffect(wantsNext) { if (wantsNext) playNext() }

    val seekSupport by engine.seekSupport.collectAsStateWithLifecycle()
    val canSeek = seekSupport != tv.enktel.app.player.PlayerEngine.SeekSupport.CONTAINER &&
        seekSupport != tv.enktel.app.player.PlayerEngine.SeekSupport.NO_LENGTH &&
        seekSupport != tv.enktel.app.player.PlayerEngine.SeekSupport.NO_RANGES

    // A container with no index, on a panel that does serve ranges, is usually
    // fixed by asking the same panel for the same film in a different
    // container — StreamUrlResolver already queues .mp4/.mkv/.ts/.avi. The
    // chain only ever advanced on a playback *error*, so a title that played
    // perfectly but couldn't be seeked never got there.
    //
    // Only automatic in the first 90 seconds. Past that the user is watching,
    // and yanking them back to the start to gain a scrub bar is a bad trade.
    var autoSwitched by remember(url) { mutableStateOf(false) }
    LaunchedEffect(seekSupport, url) {
        if (autoSwitched || isLive) return@LaunchedEffect
        if (seekSupport != tv.enktel.app.player.PlayerEngine.SeekSupport.CONTAINER) return@LaunchedEffect
        if (positionMs > 90_000 || !engine.hasAlternateSource) return@LaunchedEffect
        autoSwitched = true
        engine.tryNextCandidate()
    }

    val toaster = tv.enktel.app.ui.components.LocalToaster.current

    /**
     * Every seek in this screen goes through here.
     *
     * Media3 answers a seek on an item it considers unseekable by jumping to
     * the default position — the start of the film. Xtream VOD hits that
     * constantly (raw .ts, or an MP4 whose panel serves no byte ranges), which
     * is why `+30s` could throw you back to the beginning. Refusing the seek
     * and saying so is the honest outcome; silently restarting is not.
     */
    fun seekTo(target: Long) {
        // No toast on refusal. It fired on every press, and because the toast
        // host draws over the HUD it covered the very buttons the user was
        // pressing. Seekability is a property of the title, not of the press,
        // so it belongs in one persistent line above the controls.
        if (engine.seekToSafe(target)) positionMs = target.coerceAtLeast(0)
        poke()
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    // Route the remote's transport keys through the same guarded path as the
    // on-screen controls. MainActivity intercepts those keys globally, so
    // without this they reach a raw seekTo and restart unseekable media.
    DisposableEffect(engine) {
        tv.enktel.app.voice.ActivePlayerRef.seekHandler = { target ->
            val ok = engine.seekToSafe(target)
            if (ok) positionMs = target.coerceAtLeast(0)
            poke()
            ok
        }
        onDispose { tv.enktel.app.voice.ActivePlayerRef.seekHandler = null }
    }

    val rootFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    LaunchedEffect(trackMenu, showControls, durationMs > 0) {
        if (trackMenu.isEmpty() && !showControls) {
            runCatching { rootFocus.requestFocus() }
        } else if (trackMenu.isEmpty() && showControls && !isLive) {
            // Put the remote on the scrubber the moment the HUD opens. Nothing
            // claimed focus here before, so on TV the controls appeared and the
            // D-pad still had nowhere to go — the scrub bar was reachable in
            // code and unreachable in practice.
            //
            // The scrubber only exists when the media has a length, so when it
            // does not, the same reasoning points at the transport row instead:
            // aiming at a composable that is not there would retry ten times
            // and then leave the remote stranded, which is the very fault this
            // block was written to fix.
            val target = if (durationMs > 0) seekFocus else controlsFocus
            repeat(10) {
                if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(40)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    var gestureLevel by remember { mutableStateOf<Triple<String, Float, Boolean>?>(null) }
    LaunchedEffect(gestureLevel) { if (gestureLevel != null) { delay(900); gestureLevel = null } }
    var dragBrightness by remember { mutableStateOf(true) }
    var boxWidthPx by remember { mutableFloatStateOf(1f) }
    var boxHeightPx by remember { mutableFloatStateOf(1f) }
    // Snapshot at drag-start + accumulated Y delta lets us set an absolute target
    // on each drag event. Per-event nudging via adjustVolume() got truncated to
    // zero by Android's integer-quantised stream volume API (typically 0-15
    // steps), so short drags did nothing — this is the same start-snapshot
    // pattern PlayerGestureLayer already uses.
    var dragStartVolume by remember { mutableFloatStateOf(0f) }
    var dragStartBrightness by remember { mutableFloatStateOf(0.5f) }
    var accumulatedFraction by remember { mutableFloatStateOf(0f) }

    // Vibrancy for the glass panels below. A video surface cannot be sampled,
    // so the poster stands in for the backdrop — it is the same picture, one
    // frame of it, and it is already decoded in the image cache.
    val accent = rememberDominantColor(posterUrl.ifBlank { null })

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            // Touchscreen support: tap toggles the control bar.
            .pointerInput(Unit) {
                // Not while the next-episode card is up: opening the HUD is
                // what covered the card and made a tap on it look ignored.
                detectTapGestures(onTap = {
                    if (nextUpSecs == null || nextUpCancelled) { showControls = true; controlsTick++ }
                })
            }
            // Left half of the screen tunes brightness, right half tunes volume.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        boxWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        boxHeightPx = size.height.toFloat().coerceAtLeast(1f)
                        dragBrightness = off.x < boxWidthPx / 2f
                        accumulatedFraction = 0f
                        dragStartVolume = tv.enktel.app.player.PlayerGestures.currentVolumeFraction(context)
                        dragStartBrightness = (context as? android.app.Activity)
                            ?.let { tv.enktel.app.player.PlayerGestures.currentBrightness(it) } ?: 0.5f
                    },
                    onVerticalDrag = { _, dy ->
                        // 1.5x multiplier so a modest thumb swipe covers the full range —
                        // matches MX Player / VLC on Android.
                        accumulatedFraction += -dy / boxHeightPx * 1.5f
                        if (dragBrightness) {
                            (context as? android.app.Activity)?.let { act ->
                                val target = (dragStartBrightness + accumulatedFraction).coerceIn(0.05f, 1f)
                                val next = tv.enktel.app.player.PlayerGestures.setBrightness(act, target)
                                gestureLevel = Triple("☀ Brightness", next, true)
                            }
                        } else {
                            val target = (dragStartVolume + accumulatedFraction).coerceIn(0f, 1f)
                            val next = tv.enktel.app.player.PlayerGestures.setVolumeFraction(context, target)
                            gestureLevel = Triple("🔊 Volume", next, false)
                        }
                    },
                )
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || trackMenu.isNotEmpty()) return@onPreviewKeyEvent false
                // The next-episode card takes the remote for as long as it is up.
                //
                // It used to be given focus instead, and focus never arrived.
                // Every arrow key was consumed here — up/down to wake the HUD,
                // left/right to skip — so nothing could travel off this Box; and
                // when that was fixed by standing aside, the card was still a
                // descendant of a focusable full-screen Box competing with the
                // transport row for the same presses. Driving the two buttons
                // from a selection index removes the whole question: this
                // handler already sees every key before anything else does,
                // because a preview handler runs from the root down.
                if (nextUpShowing) {
                    return@onPreviewKeyEvent when (ev.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { nextUpChoice = 0; true }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { nextUpChoice = 1; true }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            if (nextUpChoice == 0) playNext() else { nextUpCancelled = true; nextUpSecs = null }
                            true
                        }
                        // Swallowed rather than passed on: waking the HUD over
                        // the card is what made it look unresponsive.
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                        else -> false
                    }
                }
                if (showControls) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { poke(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-10_000); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(30_000); true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                        poke(); true
                    }
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        // Number keys jump straight to 0–90% of the video.
                        if (!isLive && durationMs > 0) {
                            val digit = ev.key.nativeKeyCode - AndroidKeyEvent.KEYCODE_0
                            seekTo(durationMs * digit / 10)
                            true
                        } else false
                    }
                    else -> false
                }
            },
    ) {
        // SURFACE_VIEW for the same reason as the live player: full-bleed,
        // never clipped, never animated. The transport controls, the track
        // menus and the next-episode card are Compose siblings drawn after it
        // in this Box, so they land on top of it.
        ContentFrame(
            player = engine.player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            contentScale = aspect.scale,
            keepContentOnReset = true,
            modifier = Modifier.fillMaxSize(),
        )

        // Captions. PlayerView built its own SubtitleView and this screen
        // reached into it to style it; SubtitleOverlay is the same SubtitleView
        // with the same styling, owned by the composition instead.
        SubtitleOverlay(
            player = engine.player,
            scalePct = subScalePct,
            color = subColor,
            edge = subEdge,
            bgAlpha = subBgAlpha,
            modifier = Modifier.fillMaxSize(),
        )

        // Next-episode card, bottom right over the closing credits.
        nextUpSecs?.takeIf { !nextUpCancelled }?.let { secs ->
            // Auto-advance at zero, if the viewer left it alone and has not
            // turned auto-play off in Settings → Playback. That setting was
            // read by this screen and never consulted — the card rolled over
            // regardless, or rather it would have, had the countdown been able
            // to reach zero at all.
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    // Above the transport controls, which are a later sibling in
                    // this Box and so were painted over the card.
                    .zIndex(2f)
                    .padding(32.dp)
                    // Denser than the other panels: this one carries a
                    // countdown the viewer is deciding against, so it has to
                    // hold its own over whatever the closing scene is doing.
                    .glassSurface(alpha = 0.88f, accent = accent)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    "UP NEXT", color = tv.enktel.app.ui.theme.EnktelBlue, fontSize = 10.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    upNextLabel.ifBlank { "Next episode" }, color = Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (autoplayNextEp) "Playing in ${secs}s" else "Press OK to play",
                    color = EnktelTextDim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                // `accent` is the selection the remote is on, set by the key
                // handler above rather than by focus. onClick stays for touch.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tv.enktel.app.ui.components.FocusButton(
                        "▶ Play now",
                        accent = nextUpChoice == 0,
                        onClick = { playNext() },
                    )
                    // Dismissal has to stick. Without the flag the card would
                    // reappear on the next position tick, a quarter of a second
                    // later, and the viewer could not get rid of it at all.
                    tv.enktel.app.ui.components.FocusButton(
                        "✕ Not now",
                        accent = nextUpChoice == 1,
                        onClick = { nextUpCancelled = true; nextUpSecs = null },
                    )
                }
            }
        }

        if (playError != null) {
            Text(
                "Playback error: $playError",
                color = EnktelLive,
                modifier = Modifier.align(Alignment.Center)
                    .glassSurface(alpha = 0.80f)
                    .padding(16.dp),
            )
        }

        gestureLevel?.let { (label, frac, isBright) ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .glassSurface(accent = accent)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.height(90.dp).width(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height((90f * frac.coerceIn(0f, 1f)).dp)
                            .clip(RoundedCornerShape(4.dp))
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

        // ---- Resumed-from chip ----------------------------------------------
        // Shown briefly after an auto-resume. Says what happened and offers the
        // one action the user might want instead.
        var resumeChipDismissed by remember(progressKey) { mutableStateOf(false) }
        val showResumeChip = resumedFromMs > 0 && !resumeChipDismissed &&
            positionMs < resumedFromMs + 20_000
        LaunchedEffect(resumedFromMs) {
            if (resumedFromMs > 0) { delay(12_000); resumeChipDismissed = true }
        }
        if (showResumeChip) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 32.dp)
                    .glassChip(accent = accent)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "▶  Resumed from ${fmtTime(resumedFromMs)}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(tv.enktel.app.ui.theme.EnktelBlue)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                seekTo(0)
                                resumeChipDismissed = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("Start over", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .pointerInput(Unit) { detectTapGestures { resumeChipDismissed = true } }
                        .padding(horizontal = 4.dp),
                ) {
                    Text("✕", color = EnktelTextDim, fontSize = 13.sp)
                }
            }
        }

        // ---- Skip Intro pill ------------------------------------------------
        // Netflix-style floating chip.  Shown between 5 s and 90 s into VOD
        // playback so users can bypass series intro sequences with one tap
        // (or by saying "skip intro"). Dismissed once tapped, once the
        // player crosses the 90 s mark, or when it's a live stream.
        var skipIntroDismissed by remember(progressKey) { androidx.compose.runtime.mutableStateOf(false) }
        val showSkipIntro = !isLive && !skipIntroDismissed && canSeek &&
            positionMs in 5_000L..90_000L && durationMs > 180_000L
        if (showSkipIntro) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = if (showControls) 140.dp else 40.dp)
                    .glassChip(accent = accent)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            seekTo(90_000L)
                            skipIntroDismissed = true
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
                    .cinematicScrim(maxAlpha = 0.95f)
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
                // One line, above the controls rather than over them, and only
                // when seeking genuinely isn't available. Each cause has a
                // different answer, so each gets its own wording — "not
                // supported" alone left the user with nothing to do.
                if (!isLive && !canSeek) {
                    val (why, offerSwitch) = when (seekSupport) {
                        tv.enktel.app.player.PlayerEngine.SeekSupport.NO_LENGTH ->
                            "This panel streams the film without a length, so no player can jump to a time in it." to false
                        tv.enktel.app.player.PlayerEngine.SeekSupport.NO_RANGES ->
                            "This panel won't serve partial requests, so seeking isn't possible on it." to false
                        else ->
                            "This copy has no seek index. Another version of the same file may." to true
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("⚠", color = EnktelLive, fontSize = 13.sp)
                        Text(why, color = EnktelTextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (offerSwitch && engine.hasAlternateSource) {
                            FocusButton("Try another source", accent = true, onClick = {
                                engine.tryNextCandidate(); poke()
                            })
                        }
                        // A downloaded file is a local file, and a local file is
                        // always seekable — the one reliable answer when the
                        // panel simply won't cooperate.
                        FocusButton("⬇ Download to seek", onClick = {
                            graph.downloads.enqueue(
                                tv.enktel.app.data.db.DownloadEntry(
                                    id = progressKey.ifBlank { url.hashCode().toString() },
                                    profileId = progressKey.substringBefore(':').toLongOrNull() ?: 0L,
                                    kind = "movie",
                                    refId = progressKey.substringAfterLast(':').toLongOrNull() ?: 0L,
                                    title = title,
                                    sourceUrl = url,
                                )
                            )
                            poke()
                        })
                    }
                }
                // A scrubber needs a length. Without one it is not a scrubber.
                //
                // v1.32.0 made the bar render at durationMs == 0 so it would be
                // in place the moment a duration arrived. On panels that never
                // declare one it never arrives, and what is left looks like a
                // seek bar and is not: frac pins to 0 so the rail is empty and
                // the thumb sits hard left; `pointerInput` returns early, so
                // drag and tap do nothing; the D-pad path clamps its target to
                // `coerceAtMost(0)`, so scrubbing does nothing either; and the
                // right-hand label reads "live buffer" — on a film.
                //
                // Nor does the warning above cover it, because `canSeek` is
                // about whether the panel serves byte ranges, which is a
                // different question from whether it said how long the file is.
                // A panel can answer ranges perfectly and still declare no
                // length, and that combination produced a dead rail with no
                // explanation next to it. Reported as the seek bar being
                // missing, which is exactly what it looks like.
                //
                // So: a real bar when there is a length, and an honest line
                // when there is not. The skip buttons work either way — they
                // seek relative to the current position and never needed a
                // duration.
                if (!isLive && durationMs > 0) {
                    SeekBar(
                        focusRequester = seekFocus,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { target ->
                            seekTo(target)
                            controlsTick++
                        },
                        onInteract = { controlsTick++ },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtTime(positionMs), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text("-${fmtTime(durationMs - positionMs)}", color = EnktelTextDim, fontSize = 12.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(fmtTime(durationMs), color = EnktelTextDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                } else if (!isLive) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(fmtTime(positionMs), color = Color.White, fontSize = 12.sp)
                        Text(
                            "Length unknown — this panel didn't declare one, so there's " +
                                "nothing to scrub along. Use −10s and +30s.",
                            color = EnktelTextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // v1.26.0 — hoist Discord state above the LazyRow. LazyListScope's
                // `item {}` builder isn't @Composable, so remember* / collectAsState
                // calls have to happen in the parent composable and be captured.
                val shareScope = androidx.compose.runtime.rememberCoroutineScope()
                val discordUrl by graph.settings.discordWebhook.collectAsStateWithLifecycle(initialValue = "")
                val voiceChan by graph.settings.discordVoiceChannel.collectAsStateWithLifecycle(initialValue = "Richard's Hangout")
                val hudToaster = tv.enktel.app.ui.components.LocalToaster.current
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.focusRequester(controlsFocus).tvRailFocus(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        FocusButton(if (playing) "⏸  Pause" else "▶  Play", accent = true, onClick = {
                            if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                            controlsTick++
                        })
                    }
                    // Reachable with a remote, unlike the resumed-from chip,
                    // which is a touch affordance. Only offered while the
                    // resume point is still where you'd want to undo it from.
                    if (resumedFromMs > 0 && positionMs < resumedFromMs + 20_000) {
                        item { FocusButton("⟲ Start over", accent = true, onClick = { seekTo(0) }) }
                    }
                    item { FocusButton("−10s", onClick = { seekBy(-10_000) }) }
                    item { FocusButton("+30s", onClick = { seekBy(30_000) }) }
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
                            aspect = aspect.next()
                            toaster.info("Aspect: ${aspect.label}")
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
    focusRequester: FocusRequester,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    // Consecutive held key-repeats, so scrubbing accelerates instead of
    // crawling. A fixed 15 s step means a two-hour film needs 240 presses to
    // cross, which is why reaching "the exact moment you want" felt impossible
    // with a remote.
    var repeats by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrubTarget) {
        if (scrubTarget == null) { repeats = 0; return@LaunchedEffect }
        delay(700)
        repeats = 0 // let go for a moment and the step resets to fine
    }
    fun step(): Long = when {
        repeats > 24 -> 300_000L
        repeats > 12 -> 60_000L
        repeats > 5 -> 30_000L
        else -> 10_000L
    }
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
            // 22 dp was the whole hit area, thumb included — under the ~48 dp
            // Android asks for and small enough that a thumb press often
            // missed it entirely. The rail still draws thin; only the target
            // grew.
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused && scrubTarget != null) { onSeek(scrubTarget!!); scrubTarget = null }
                }
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            repeats++
                            scrubTarget = ((scrubTarget ?: positionMs) - step()).coerceAtLeast(0)
                            onInteract(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            repeats++
                            scrubTarget = ((scrubTarget ?: positionMs) + step())
                                .coerceAtMost(durationMs.coerceAtLeast(0))
                            onInteract(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            scrubTarget?.let(onSeek); scrubTarget = null; true
                        }
                        else -> false
                    }
                }
                // One gesture handler, not two.
                //
                // This used to be a `detectTapGestures` block followed by a
                // separate `detectHorizontalDragGestures` block on the same
                // node, and they fought: the drag detector waits for the touch
                // to travel past the system slop before it reports anything, so
                // the first ~10 dp of every drag went nowhere, and the tap
                // detector — which resolves only on *release* — then fired a
                // seek to wherever the finger happened to lift. The result was
                // a scrubber that ignored small movements and jumped on
                // release. Which is what "not responsive" is.
                //
                // Awaiting the pointer directly means the thumb latches on
                // touch-down and tracks the finger from the first pixel, with
                // no slop and no ambiguity about which detector owns the
                // gesture. A tap is simply a drag that never moved.
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun at(x: Float): Long =
                                (durationMs * x / size.width.coerceAtLeast(1))
                                    .toLong().coerceIn(0, durationMs)
                            scrubTarget = at(down.position.x)
                            onInteract()
                            var pointer = down
                            while (pointer.pressed) {
                                val event = awaitPointerEvent()
                                pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (pointer.positionChanged()) {
                                    scrubTarget = at(pointer.position.x)
                                    onInteract()
                                }
                                pointer.consume()
                            }
                            scrubTarget?.let(onSeek)
                            scrubTarget = null
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
