package tv.enktel.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Hey Enki" wake-word listener.  Runs an on-device SpeechRecognizer in a
 * short-utterance restart loop: each utterance is scanned for the wake phrase
 * ("hey enki", "hi enki", "enki") and, on match, the payload (everything after
 * the wake phrase) is fed into the main command bus so users can chain
 * requests like "hey enki, turn to Nine HD".
 *
 * Multi-layer defence against self-triggering while playback is active:
 *  - [paused] flag: caller pauses the listener when a Player screen is on
 *    (see VoiceHost's `LaunchedEffect(playerActiveForWake)`).
 *  - Audio-active gate: before starting a listen turn, we ask AudioManager
 *    whether STREAM_MUSIC is currently playing.  If it is (PiP, background
 *    audio, or an ExoPlayer we don't know about) we defer the turn and try
 *    again later — the mic would otherwise pick up the app's own TV audio
 *    through the device speakers and hallucinate "hey enki".
 *  - Stale-callback guard: every RecognitionListener callback checks
 *    [paused]/[audioActive] again before acting on the transcription, so a
 *    result from a listen-turn that started before the pause landed can't
 *    slip through and fire a false wake.
 *  - Grace period: after leaving the paused state we wait 800 ms before
 *    starting the next turn so the audio pipeline has a chance to release.
 *
 * Notes / limitations:
 *  - Android's stock SpeechRecognizer is designed for one-shot utterances and
 *    will off-load audio to the vendor speech service.  Battery cost is
 *    real; the toggle in Settings defaults to OFF.  Users who want proper
 *    always-on wake-word detection should install a dedicated on-device
 *    engine (Porcupine / Vosk) — future work.
 */
class WakeWordListener(
    private val context: Context,
    private val onWake: (payload: String) -> Unit,
) {
    val active = mutableStateOf(false)
    val hearingMeter = mutableStateOf(0f) // 0..1, updates on RMS
    private var recognizer: SpeechRecognizer? = null
    private var scope: CoroutineScope? = null
    private var loop: Job? = null
    private var backoffMs: Long = 2_500
    private var emptyRoundCount: Int = 0
    private var lastUnpauseAt: Long = 0L
    private val audio: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    /** Optional guard — when true, we stop listening. Set by the caller when a
     *  player is active (mic conflicts with playback audio anyway). */
    @Volatile var paused: Boolean = false

    /** True if the app (or anything else) is currently outputting on the media
     *  stream.  We treat this as an implicit pause because the recogniser would
     *  otherwise pick up the device speakers. */
    private fun audioActive(): Boolean = try { audio.isMusicActive } catch (_: Throwable) { false }

    /** True if the listener should not be acting right now for any reason. */
    private fun suppressed(): Boolean = paused || audioActive()

    fun start() {
        if (active.value) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        active.value = true
        scope = MainScope()
        beginNextTurn()
    }

    fun stop() {
        active.value = false
        loop?.cancel()
        loop = null
        try { recognizer?.stopListening(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        scope?.cancel(); scope = null
        backoffMs = 2_500
        emptyRoundCount = 0
    }

    fun applyPause(p: Boolean) {
        if (paused == p) return
        paused = p
        if (p) {
            try { recognizer?.stopListening(); recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        } else if (active.value) {
            lastUnpauseAt = SystemClock.elapsedRealtime()
            beginNextTurn()
        }
    }

    private fun beginNextTurn() {
        if (!active.value || paused) return
        val s = scope ?: return
        loop?.cancel()
        loop = s.launch(Dispatchers.Main) {
            // Aggressive back-off if we've had a run of empty turns: the mic
            // shouldn't visibly toggle on and off every second when the user's
            // room is silent.  After 3 empty turns we sit for 8s, after 6 we
            // wait 20s.  Also: if audio is currently playing, poll every 3s
            // until it stops (instead of opening the mic on top of speakers).
            val wait = when {
                audioActive() -> 3_000L
                emptyRoundCount >= 6 -> 20_000L
                emptyRoundCount >= 3 -> 8_000L
                else -> backoffMs
            }
            // Grace period after unpause — give the audio pipeline time to
            // release the mic and let any tail-end playback stop echoing.
            val sinceUnpause = SystemClock.elapsedRealtime() - lastUnpauseAt
            val grace = (800L - sinceUnpause).coerceAtLeast(0L)
            delay(wait + grace)
            // Recheck after the delay — pause state or audio activity may have
            // changed while we slept.  Reschedule instead of opening the mic.
            if (!active.value || paused) return@launch
            if (audioActive()) { beginNextTurn(); return@launch }
            try { recognizer?.destroy() } catch (_: Exception) {}
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            var got = false
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { emptyRoundCount = 0 }
                override fun onRmsChanged(rmsdB: Float) {
                    hearingMeter.value = (rmsdB.coerceIn(-2f, 10f) / 10f).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // NO_MATCH / SPEECH_TIMEOUT are the "quiet room" cases — those
                    // count toward the empty-round backoff.  Everything else
                    // resets it so we don't punish a network blip.
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> emptyRoundCount++
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            backoffMs = 5_000
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            // Permission gone — stop entirely; the toggle will restart us.
                            stop(); return
                        }
                        else -> {}
                    }
                    if (!got) beginNextTurn()
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    // Stale-callback guard: if we've been paused or audio is
                    // now playing, ignore this — the recogniser is almost
                    // certainly hearing our own TV audio through the speakers.
                    if (suppressed()) return
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    val payload = extractPayload(text) ?: return
                    got = true
                    handleWake(payload)
                }
                override fun onResults(results: Bundle?) {
                    if (suppressed()) return
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    val payload = extractPayload(text)
                    if (payload != null) { got = true; handleWake(payload) }
                    else { emptyRoundCount++; beginNextTurn() }
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Long silence timeouts so the recognizer stays open for a
                // meaningful window (~15s) instead of flipping the mic every
                // second in a quiet room.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }
            try { r.startListening(intent) } catch (_: Exception) { beginNextTurn() }
        }
    }

    private fun handleWake(payload: String) {
        // Final belt-and-braces: if suppression flipped on between the
        // suppressed() check in the callback and here, drop the wake silently.
        if (suppressed()) return
        // Stop the loop, notify listener, resume once caller finishes with a
        // fresh call to start().  We deliberately don't auto-restart here so
        // callers can pop up their command UI without racing us for the mic.
        stop()
        onWake(payload)
    }

    /** Returns the phrase after the wake word if [text] contains one; else null. */
    private fun extractPayload(text: String): String? {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return null
        val patterns = listOf(
            "hey enki", "hi enki", "hey inky", "hi inky",
            "enkitel", "hey enkitel", "enki",
        )
        for (p in patterns) {
            val idx = t.indexOf(p)
            if (idx >= 0) return t.substring(idx + p.length).trim(',', '.', '!', '?', ' ')
        }
        return null
    }
}
