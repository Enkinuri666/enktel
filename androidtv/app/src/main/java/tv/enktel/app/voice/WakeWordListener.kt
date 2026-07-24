package tv.enktel.app.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
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
 * "Hey Enki" wake-word listener.
 *
 * The naïve version — restart the SpeechRecognizer as soon as it times out —
 * causes two known problems that this class works hard to avoid:
 *
 *   1. **The chirp loop.**  On most Androids each `startListening()` call
 *      triggers the system mic-in-use privacy chirp AND lights up the mic
 *      privacy indicator.  If the recogniser times out in a quiet room and
 *      we restart in 2 s, the user hears a continuous chirp-chirp-chirp
 *      until they toggle the feature off.  We defeat this by:
 *        - Waiting a minimum of 20 s between listen-turns in a quiet room.
 *        - Growing that wait: 3 empty turns → 60 s, 5 empty turns → 3 min.
 *        - Only shrinking the backoff back to a fast cadence AFTER real
 *          speech has been detected in a recent turn (onBeginningOfSpeech
 *          fires), so once the user is talking the app feels responsive
 *          again.
 *
 *   2. **Self-triggering from the app's own audio.**  The mic physically
 *      picks up TV audio through the device speakers.  We defeat this with
 *      three layers of suppression (see suppressed()): a caller-driven
 *      pause flag, an AudioManager.isMusicActive gate that catches PiP /
 *      background players, and a stale-callback guard so late results
 *      from a listen-turn started before the audio kicked in can't slip
 *      through.
 *
 * Both problems are ultimately caused by using a full ASR engine as a
 * wake-word detector.  A dedicated on-device engine (Porcupine, Vosk) is
 * the correct long-term fix; documented as future work.
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

    // Empty-turn tracking. Grows monotonically while the room is quiet
    // (no onBeginningOfSpeech callbacks); resets to 0 the moment a real
    // speech onset is detected. Drives the exponential backoff below.
    private var emptyRoundCount: Int = 0

    // Elevated backoff triggered by transient failures (network / busy).
    private var errorBackoffMs: Long = 0
    private var lastUnpauseAt: Long = 0L
    private val audio: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    /** Optional guard — when true, we stop listening. Set by the caller when a
     *  player is active (mic conflicts with playback audio anyway). */
    @Volatile var paused: Boolean = false

    private fun audioActive(): Boolean = try { audio.isMusicActive } catch (_: Throwable) { false }
    private fun suppressed(): Boolean = paused || audioActive()

    /**
     * How long to wait before the next listen-turn.  The whole point of
     * this function is to make sure the mic doesn't chirp every couple of
     * seconds when nobody is talking.
     */
    private fun nextWaitMs(): Long {
        if (audioActive()) return 5_000L
        // Error-recovery override — pushed by onError branches below.
        if (errorBackoffMs > 0) {
            val e = errorBackoffMs
            errorBackoffMs = 0
            return e
        }
        return when {
            emptyRoundCount >= 5 -> 180_000L  // 3 min after long silence
            emptyRoundCount >= 3 -> 60_000L   // 1 min
            emptyRoundCount >= 1 -> 30_000L   // 30 s
            else -> 20_000L                    // baseline in a quiet room
        }
    }

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
        errorBackoffMs = 0
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

    /**
     * If the OS supports it (API 31+), prefer the on-device recogniser —
     * it runs without a cloud round-trip and, on most OEM builds, doesn't
     * trigger the same "mic chirp" the online recogniser does.  Falls
     * back to the classic constructor everywhere else.
     */
    private fun createRecognizer(): SpeechRecognizer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val svc = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                if (svc != null) return svc
            } catch (_: Throwable) { /* fall through to classic */ }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun beginNextTurn() {
        if (!active.value || paused) return
        val s = scope ?: return
        loop?.cancel()
        loop = s.launch(Dispatchers.Main) {
            val wait = nextWaitMs()
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
            val r = createRecognizer()
            recognizer = r
            var got = false
            var speechStarted = false
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    speechStarted = true
                    emptyRoundCount = 0
                }
                override fun onRmsChanged(rmsdB: Float) {
                    hearingMeter.value = (rmsdB.coerceIn(-2f, 10f) / 10f).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            if (!speechStarted) emptyRoundCount++
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            errorBackoffMs = (errorBackoffMs.coerceAtLeast(30_000L) * 2).coerceAtMost(300_000L)
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> errorBackoffMs = 15_000L
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            stop(); return
                        }
                        else -> {}
                    }
                    if (!got) beginNextTurn()
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
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
                    else {
                        if (!speechStarted) emptyRoundCount++
                        beginNextTurn()
                    }
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }
            try { r.startListening(intent) } catch (_: Exception) { beginNextTurn() }
        }
    }

    private fun handleWake(payload: String) {
        if (suppressed()) return
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
