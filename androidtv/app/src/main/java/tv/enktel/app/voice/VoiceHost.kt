package tv.enktel.app.voice

import android.Manifest
import android.content.pm.PackageManager
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
fun VoiceHost(bus: VoiceCommandBus, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val recognizer = remember { VoiceRecognizer(context) }
    val speaker = remember { VoiceSpeaker(context) }
    DisposableEffect(Unit) {
        onDispose { recognizer.release(); speaker.release() }
    }

    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var lastIntentLabel by remember { mutableStateOf<String?>(null) }
    var currentAnswer by remember { mutableStateOf<VoiceAnswer?>(null) }

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

    Box(Modifier.fillMaxSize()) {
        content()
        // Mic FAB sits bottom-right, above the mobile bottom-nav on phones and
        // above the system gesture bar everywhere. That corner is empty on every
        // screen — the previous top-right position clashed with the Home clock
        // and the Search icon on the mobile hero.
        val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
        val bottomOffset = if (isMobile) 90.dp else 24.dp
        MicFab(
            listening = listening,
            onTap = { toggleListening() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = bottomOffset),
        )
        if (listening) {
            ListeningOverlay(
                partial = partial,
                onCancel = { recognizer.stop(); listening = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = bottomOffset + 60.dp),
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
    VoiceIntent.OpenMovies -> "Movies"
    VoiceIntent.OpenSeries -> "Series"
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
}

private fun spokenReply(intent: VoiceIntent): String = when (intent) {
    is VoiceIntent.TuneChannel -> "Turning to ${intent.query}"
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
    VoiceIntent.OpenMovies -> ""
    VoiceIntent.OpenSeries -> ""
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
}

