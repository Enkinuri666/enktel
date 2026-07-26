package tv.enktel.app.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Session-scoped hub for voice commands. Not tied to AppGraph so we can wire
 * it once at the MainActivity root and let any downstream player screen
 * collect from [intents].
 */
/** Structured voice answer that the "personal content guide" query intents produce. */
data class VoiceAnswerLine(val title: String, val subtitle: String = "", val route: String? = null)
data class VoiceAnswer(
    val eyebrow: String,
    val heading: String,
    val lines: List<VoiceAnswerLine>,
    /** Short sentence for TTS. Kept < ~140 chars so it doesn't drone. */
    val spoken: String,
)

class VoiceCommandBus {
    val intents = MutableSharedFlow<VoiceIntent>(extraBufferCapacity = 8)
    /** Emissions here are rendered by VoiceHost as an on-screen answer card + TTS. */
    val answers = MutableSharedFlow<VoiceAnswer>(extraBufferCapacity = 4)
    /** Search query pushed from a voice "search for X" — SearchScreen collects
     *  this so its input field actually receives the spoken query. */
    val searchQueries = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** External trigger for tap-to-talk — the mobile nav bar's Mic tab emits
     *  into this, VoiceHost collects and starts listening. */
    val micActivate = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/**
 * Composable that renders:
 *   - the child [content] (whatever the app is showing)
 *   - a floating mic button, top-right, always accessible
 *   - a listening / result overlay while the user is talking
 *
 * On tap, requests RECORD_AUDIO permission if needed, starts the
 * SpeechRecognizer, parses the transcription, publishes the intent onto
 * [bus.intents] and (optionally) speaks a short confirmation.
 */
@Composable
fun VoiceHost(bus: VoiceCommandBus, wakeWordEnabled: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val recognizer = remember { VoiceRecognizer(context) }
    val speaker = remember { VoiceSpeaker(context) }
    val earcons = remember { VoiceEarcons() }

    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var lastIntentLabel by remember { mutableStateOf<String?>(null) }
    var currentAnswer by remember { mutableStateOf<VoiceAnswer?>(null) }

    // "Hey Enki" wake-word listener. When it hears the wake phrase, it stops
    // itself and feeds the payload (everything after "hey enki") straight
    // into the same intent-parse-and-handle path as a mic tap.
    val wakeWord = remember {
        WakeWordListener(context) { payload ->
            // Barge-in: kill any in-flight TTS the moment the wake phrase
            // lands so the recogniser can hear the follow-up cleanly.
            speaker.stop()
            // Short "I heard you" chime — closes the feedback loop without
            // waiting for the full TTS reply.
            earcons.wakeReady()
            if (payload.isNotBlank()) {
                val intent = VoiceIntentParser.parse(payload)
                lastIntentLabel = "🎙 \"hey enki, $payload\"  →  ${describe(intent, payload)}"
                if (intent is VoiceIntent.Unknown) {
                    earcons.error()
                } else {
                    earcons.confirm()
                }
                speaker.speak(spokenReply(intent))
                if (intent !is VoiceIntent.Unknown) {
                    scope.launch { bus.intents.emit(intent) }
                }
            } else {
                toaster.info("Listening…")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { recognizer.release(); speaker.release(); wakeWord.stop(); earcons.release() }
    }
    val playerActiveForWake by androidx.compose.runtime.remember { ActivePlayerRef.active }
    androidx.compose.runtime.LaunchedEffect(wakeWordEnabled) {
        if (wakeWordEnabled) {
            val ok = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (ok) wakeWord.start() else toaster.info("Grant microphone access to use Hey Enki")
        } else {
            wakeWord.stop()
        }
    }
    // Lifecycle-driven pause: back-off the mic when the app goes to the
    // background OR the device enters power-save mode.  Screen-off / low
    // battery still leaves the app resumed on some devices, so we watch
    // ACTION_POWER_SAVE_MODE_CHANGED and ACTION_BATTERY_LOW too.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var appBackgrounded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var powerConstrained by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> appBackgrounded = true
                androidx.lifecycle.Lifecycle.Event.ON_START -> appBackgrounded = false
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    androidx.compose.runtime.DisposableEffect(context) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        powerConstrained = try { pm.isPowerSaveMode } catch (_: Throwable) { false }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                when (i?.action) {
                    android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                        powerConstrained = try { pm.isPowerSaveMode } catch (_: Throwable) { false }
                    android.content.Intent.ACTION_BATTERY_LOW -> powerConstrained = true
                    android.content.Intent.ACTION_BATTERY_OKAY -> powerConstrained = false
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(android.content.Intent.ACTION_BATTERY_LOW)
            addAction(android.content.Intent.ACTION_BATTERY_OKAY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Throwable) {} }
    }
    // Combine every reason we might want the wake-word paused into a
    // single derived flag.  applyPause() no-ops when the state doesn't
    // change, so we can emit freely on each recomposition.
    androidx.compose.runtime.LaunchedEffect(
        playerActiveForWake, appBackgrounded, powerConstrained, wakeWordEnabled,
    ) {
        wakeWord.applyPause(playerActiveForWake || appBackgrounded || powerConstrained)
    }

    // Structured answer cards come from the "personal guide" query intents that
    // MainNav resolves. We render + speak them here so the plumbing stays local.
    androidx.compose.runtime.LaunchedEffect(bus) {
        bus.answers.collect { answer ->
            currentAnswer = answer
            speaker.speak(answer.spoken)
        }
    }


    fun handleTranscription(text: String) {
        val intent = VoiceIntentParser.parse(text)
        val label = describe(intent, text)
        lastIntentLabel = "🎙 \"$text\"  →  $label"
        speaker.speak(spokenReply(intent))
        if (intent !is VoiceIntent.Unknown) {
            scope.launch { bus.intents.emit(intent) }
        }
    }

    fun beginListening() {
        if (!recognizer.isAvailable()) {
            toaster.error("Voice recognition isn't available on this device")
            return
        }
        listening = true
        partial = ""
        recognizer.start(
            onPartial = { partial = it },
            onResult = { text ->
                listening = false
                handleTranscription(text)
            },
            onError = { code ->
                listening = false
                partial = ""
                val msg = when (code) {
                    android.speech.SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Voice needs a network connection"
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Grant microphone access first"
                    else -> "Voice error"
                }
                toaster.error(msg)
            },
            onEndOfSpeech = { /* leave listening true until result/error */ },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) beginListening() else toaster.error("Microphone permission needed for voice commands") }

    fun toggleListening() {
        if (listening) {
            recognizer.stop()
            listening = false
            return
        }
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) beginListening()
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Collect nav-bar Mic-tap trigger. Placed here so `toggleListening` is in scope.
    androidx.compose.runtime.LaunchedEffect(bus) {
        bus.micActivate.collect { toggleListening() }
    }

    // Player-active → left-edge peek only. On mobile builds the standalone FAB
    // is gone entirely — the mic lives inside the bottom nav bar (see
    // MobileShell). TV builds still get the peek/pill because they have no
    // nav bar.
    val playerActive by androidx.compose.runtime.remember { ActivePlayerRef.active }
    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"

    Box(Modifier.fillMaxSize()) {
        content()

        // While a player is on-screen we do NOT paint a mic peek/FAB on top
        // of the video. Voice is still available via the remote's mic key,
        // the "Hey Enki" wake word (Settings → Voice), and — on mobile — the
        // bottom-nav Mic tab once the user exits playback. Overlaying an
        // always-visible mic glyph over the picture was surfaced as an
        // "obstructing on-screen bubble" complaint, and users watching a
        // channel or a movie almost never want a persistent tap target
        // stealing screen space.
        if (playerActive) {
            // no-op — see block comment above
        } else if (!isMobile) {
            MicFab(
                listening = listening,
                onTap = { toggleListening() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, bottom = 84.dp),
            )
        }

        if (listening) {
            // While playing, anchor the card to the top-start out of the
            // main viewing area so the picture isn't blocked. Elsewhere the
            // card lives near the mic FAB so the eye can follow it.
            ListeningOverlay(
                partial = partial,
                onCancel = { recognizer.stop(); listening = false },
                modifier = if (playerActive)
                    Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp)
                else Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 14.dp, bottom = 140.dp),
            )
        }
        lastIntentLabel?.let { label ->
            IntentToast(
                label = label,
                onDismiss = { lastIntentLabel = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            )
        }
        currentAnswer?.let { ans ->
            AnswerCard(
                answer = ans,
                onDismiss = { currentAnswer = null },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun AnswerCard(answer: VoiceAnswer, onDismiss: () -> Unit, modifier: Modifier) {
    // Auto-hide after ~8 s; taps outside dismiss immediately.
    androidx.compose.runtime.LaunchedEffect(answer) {
        kotlinx.coroutines.delay(8500)
        onDismiss()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .width(420.dp)
                .background(EnktelSurfaceHigh, RoundedCornerShape(16.dp))
                .border(
                    1.dp, EnktelBlue.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .pointerInput(Unit) { detectTapGestures { /* absorb */ } },
        ) {
            Text(answer.eyebrow.uppercase(), color = EnktelBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                answer.heading, color = androidx.compose.ui.graphics.Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(12.dp))
            if (answer.lines.isEmpty()) {
                Text(
                    "No results right now.", color = EnktelTextDim, fontSize = 12.sp,
                )
            } else {
                answer.lines.take(6).forEach { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(3.dp).height(24.dp)
                                .background(EnktelBlue, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.title, color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                            if (line.subtitle.isNotBlank()) {
                                Text(line.subtitle, color = EnktelTextDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "(tap outside to dismiss)", color = EnktelTextDim, fontSize = 9.sp,
            )
        }
    }
}

/** Left-edge tap-to-open peek shown while a player is on-screen. Almost
 *  invisible until the user needs it, but always reachable with a single tap. */
@Suppress("unused") // Retained in case we re-introduce an opt-in "show mic during playback" preference.
@Composable
private fun MicEdgePeek(listening: Boolean, onTap: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(width = 22.dp, height = 56.dp)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(
                (if (listening) EnktelLive else EnktelBlue).copy(alpha = if (listening) 0.85f else 0.55f),
            )
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            )
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (listening) "●" else "🎙",
            fontSize = 12.sp, fontWeight = FontWeight.Black,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
private fun MicFab(listening: Boolean, onTap: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(if (listening) 56.dp else 44.dp)
            .background(
                if (listening) EnktelLive else EnktelBlue.copy(alpha = 0.85f),
                CircleShape,
            )
            .border(2.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (listening) "●" else "🎙",
            fontSize = if (listening) 20.sp else 22.sp,
            fontWeight = FontWeight.Black,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
private fun ListeningOverlay(partial: String, onCancel: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .width(340.dp)
            .background(EnktelSurfaceHigh, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .pointerInput(Unit) { detectTapGestures { onCancel() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(EnktelLive, CircleShape))
            Text("Listening…", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            partial.ifBlank { "Try: \"Turn on Nine HD\", \"Find live sports\", \"Pause the show\"" },
            color = if (partial.isBlank()) EnktelTextDim else androidx.compose.ui.graphics.Color.White,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text("(tap to cancel)", color = EnktelTextDim, fontSize = 10.sp)
    }
}

@Composable
private fun IntentToast(label: String, onDismiss: () -> Unit, modifier: Modifier) {
    androidx.compose.runtime.LaunchedEffect(label) {
        kotlinx.coroutines.delay(3200)
        onDismiss()
    }
    Row(
        modifier
            .fillMaxWidth(0.85f)
            .background(EnktelSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
    }
}

private fun describe(intent: VoiceIntent, heard: String): String = when (intent) {
    is VoiceIntent.TuneChannel -> "Turn to ${intent.query}"
    is VoiceIntent.TuneChannelWithAudio -> "Turn to ${intent.channel} · audio ${intent.language}"
    is VoiceIntent.SetAudioLanguage -> "Audio → ${intent.language}"
    is VoiceIntent.Search -> "Search for \"${intent.query}\""
    VoiceIntent.Pause -> "Pause"
    VoiceIntent.Resume -> "Resume"
    is VoiceIntent.SetVolume -> "Volume ${(intent.fraction * 100).toInt()}%"
    VoiceIntent.VolumeUp -> "Louder"
    VoiceIntent.VolumeDown -> "Quieter"
    VoiceIntent.Mute -> "Mute"
    VoiceIntent.RecordNow -> "Record now"
    VoiceIntent.FindSports -> "Open Sports Hub"
    VoiceIntent.OpenHome -> "Home"
    VoiceIntent.OpenGuide -> "TV Guide"
    VoiceIntent.OpenLiveTv -> "Live TV"
    VoiceIntent.OpenMovies -> "Movies"
    VoiceIntent.OpenSeries -> "Series"
    is VoiceIntent.SearchMovies -> "Search Movies for \"${intent.query}\""
    is VoiceIntent.SearchSeries -> "Search Series for \"${intent.query}\""
    VoiceIntent.OpenWatchlist -> "Watchlist"
    VoiceIntent.OpenRecordings -> "Recordings"
    VoiceIntent.OpenSettings -> "Settings"
    VoiceIntent.ChannelUp -> "Channel up"
    VoiceIntent.ChannelDown -> "Channel down"
    VoiceIntent.Suggest -> "Suggest something"
    VoiceIntent.Fullscreen -> "Fullscreen"
    VoiceIntent.WhatSportsIsOn -> "What sports is on"
    VoiceIntent.LatestMovies -> "Latest movies"
    VoiceIntent.UpcomingMovies -> "Upcoming movies"
    VoiceIntent.LatestSeries -> "Latest series"
    VoiceIntent.WhatsOnNow -> "What's on now"
    is VoiceIntent.WhatsOnChannel -> "What's on ${intent.channel}"
    is VoiceIntent.TellMeAbout -> "Tell me about ${intent.query}"
    is VoiceIntent.Unknown -> "Didn't catch that"
    is VoiceIntent.SeekForward -> "Forward ${intent.seconds}s"
    is VoiceIntent.SeekBack -> "Back ${intent.seconds}s"
    is VoiceIntent.SeekTo -> "Jump to ${intent.minutes}m"
    VoiceIntent.Restart -> "Restart"
    VoiceIntent.SkipIntro -> "Skip intro"
    VoiceIntent.NextEpisode -> "Next episode"
    VoiceIntent.PreviousEpisode -> "Previous episode"
    VoiceIntent.EnterPip -> "Enter picture-in-picture"
    VoiceIntent.CastNow -> "Cast to TV"
    VoiceIntent.PlayRandomMovie -> "Random movie"
    VoiceIntent.PlayRandomSeries -> "Random series"
    VoiceIntent.ResumeLast -> "Resume last"
    VoiceIntent.ContinueWatching -> "Continue watching"
    is VoiceIntent.AddToWatchlist -> "Add ${intent.query} to watchlist"
    is VoiceIntent.RemoveFromWatchlist -> "Remove ${intent.query} from watchlist"
    is VoiceIntent.MoreLike -> "More like ${intent.query}"
    is VoiceIntent.WhoIsIn -> "Cast of ${intent.query}"
    is VoiceIntent.WhoDirected -> "Director of ${intent.query}"
    is VoiceIntent.WhatYear -> "Year of ${intent.query}"
    is VoiceIntent.WhatRating -> "Rating of ${intent.query}"
    is VoiceIntent.WhatGenre -> "Genre of ${intent.query}"
    is VoiceIntent.PlotOf -> "Plot of ${intent.query}"
    VoiceIntent.WhatsOnTonight -> "Tonight's TV"
    VoiceIntent.WhatsOnTomorrow -> "Tomorrow's TV"
    is VoiceIntent.WhenIsOn -> "When is ${intent.query} on"
    VoiceIntent.TrendingNow -> "Trending now"
    VoiceIntent.RefreshPlaylist -> "Refresh playlist"
    VoiceIntent.RefreshEpg -> "Refresh EPG"
    VoiceIntent.ToggleTheme -> "Toggle theme"
    VoiceIntent.OpenSports -> "Sports Hub"
    is VoiceIntent.ShowChannelKind -> "${intent.keyword} channels"
    is VoiceIntent.PlayTeamGame -> "Find ${intent.team} game"
    is VoiceIntent.RemindWhenOn -> "Remind me: ${intent.query}"
    is VoiceIntent.FilteredMovieSearch -> buildString {
        append("Movies")
        intent.genre?.let { append(" · ").append(it) }
        intent.year?.let { append(" · ").append(it) }
        intent.decade?.let { append(" · ").append(it).append("s") }
        intent.actor?.let { append(" · ").append(it) }
    }
}

private fun spokenReply(intent: VoiceIntent): String = when (intent) {
    is VoiceIntent.TuneChannel -> "Turning to ${intent.query}"
    is VoiceIntent.TuneChannelWithAudio -> "Turning to ${intent.channel} with ${intent.language} audio"
    is VoiceIntent.SetAudioLanguage -> "Switching audio to ${intent.language}"
    is VoiceIntent.Search -> "Searching for ${intent.query}"
    VoiceIntent.Pause -> "Paused"
    VoiceIntent.Resume -> "Playing"
    is VoiceIntent.SetVolume -> "Volume set to ${(intent.fraction * 100).toInt()} percent"
    VoiceIntent.VolumeUp -> "Louder"
    VoiceIntent.VolumeDown -> "Quieter"
    VoiceIntent.Mute -> "Muted"
    VoiceIntent.RecordNow -> "Recording"
    VoiceIntent.FindSports -> "Here are the sports"
    VoiceIntent.OpenHome -> ""
    VoiceIntent.OpenGuide -> ""
    VoiceIntent.OpenLiveTv -> "Opening Live TV"
    VoiceIntent.OpenMovies -> ""
    VoiceIntent.OpenSeries -> ""
    is VoiceIntent.SearchMovies -> "Searching movies for ${intent.query}"
    is VoiceIntent.SearchSeries -> "Searching series for ${intent.query}"
    VoiceIntent.OpenWatchlist -> ""
    VoiceIntent.OpenRecordings -> ""
    VoiceIntent.OpenSettings -> ""
    VoiceIntent.ChannelUp -> "Next channel"
    VoiceIntent.ChannelDown -> "Previous channel"
    VoiceIntent.Suggest -> "Here's something I think you'll like"
    VoiceIntent.Fullscreen -> ""
    VoiceIntent.WhatSportsIsOn, VoiceIntent.LatestMovies, VoiceIntent.UpcomingMovies,
    VoiceIntent.LatestSeries, VoiceIntent.WhatsOnNow,
    is VoiceIntent.WhatsOnChannel, is VoiceIntent.TellMeAbout -> "" // handler speaks the actual answer
    is VoiceIntent.Unknown -> "Sorry, I didn't catch that"
    is VoiceIntent.SeekForward -> "Skipping ahead"
    is VoiceIntent.SeekBack -> "Going back"
    is VoiceIntent.SeekTo -> "Jumping to ${intent.minutes} minutes"
    VoiceIntent.Restart -> "Starting over"
    VoiceIntent.SkipIntro -> "Skipping the intro"
    VoiceIntent.NextEpisode -> "Next episode"
    VoiceIntent.PreviousEpisode -> "Previous episode"
    VoiceIntent.EnterPip -> "Going to picture in picture"
    VoiceIntent.CastNow -> "Opening the cast picker"
    VoiceIntent.PlayRandomMovie -> "Rolling the dice on movies"
    VoiceIntent.PlayRandomSeries -> "Rolling the dice on series"
    VoiceIntent.ResumeLast -> "Resuming"
    VoiceIntent.ContinueWatching -> "" // handler speaks the actual answer
    is VoiceIntent.AddToWatchlist -> "" // handler speaks after DB write
    is VoiceIntent.RemoveFromWatchlist -> ""
    is VoiceIntent.MoreLike -> ""
    is VoiceIntent.WhoIsIn, is VoiceIntent.WhoDirected, is VoiceIntent.WhatYear,
    is VoiceIntent.WhatRating, is VoiceIntent.WhatGenre, is VoiceIntent.PlotOf -> ""
    VoiceIntent.WhatsOnTonight, VoiceIntent.WhatsOnTomorrow -> ""
    is VoiceIntent.WhenIsOn -> ""
    VoiceIntent.TrendingNow -> ""
    VoiceIntent.RefreshPlaylist -> "Refreshing your playlist"
    VoiceIntent.RefreshEpg -> "Refreshing the TV guide"
    VoiceIntent.ToggleTheme -> "Toggling theme"
    VoiceIntent.OpenSports -> ""
    is VoiceIntent.ShowChannelKind -> "Filtering to ${intent.keyword} channels"
    is VoiceIntent.PlayTeamGame -> "Looking for the ${intent.team} match"
    is VoiceIntent.RemindWhenOn -> "" // handler speaks after searching EPG
    is VoiceIntent.FilteredMovieSearch -> "Filtering movies"
}

