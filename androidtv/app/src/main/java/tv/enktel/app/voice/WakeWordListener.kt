package tv.enktel.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Notes / limitations:
 *  - Android's stock SpeechRecognizer is designed for one-shot utterances and
 *    will off-load audio to the vendor speech service.  Battery cost is
 *    real; the toggle in Settings defaults to OFF.  Users who want proper
 *    always-on wake-word detection should install a dedicated on-device
 *    engine (Porcupine / Vosk) — future work.
 *  - We reduce silence-timeouts so the loop restarts fast enough to feel
 *    continuous, and back off with an exponential delay after ERROR_NETWORK
 *    to avoid slamming the vendor service.
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
    /** Optional guard — when true, we stop listening. Set by the caller when a
     *  player is active (mic conflicts with playback audio anyway). */
    @Volatile var paused: Boolean = false

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
            // wait 20s.
            val wait = when {
                emptyRoundCount >= 6 -> 20_000L
                emptyRoundCount >= 3 -> 8_000L
                else -> backoffMs
            }
            delay(wait)
            if (!active.value || paused) return@launch
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
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    val payload = extractPayload(text) ?: return
                    got = true
                    handleWake(payload)
                }
                override fun onResults(results: Bundle?) {
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
