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
    private var backoffMs: Long = 400

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
        backoffMs = 400
    }

    private fun beginNextTurn() {
        if (!active.value) return
        val s = scope ?: return
        loop = s.launch(Dispatchers.Main) {
            // Small delay so a cancelled Recognizer has time to fully tear down.
            delay(backoffMs)
            if (!active.value) return@launch
            try { recognizer?.destroy() } catch (_: Exception) {}
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    hearingMeter.value = (rmsdB.coerceIn(-2f, 10f) / 10f).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // Back off on network hiccups, retry fast on NO_MATCH / TIMEOUT.
                    backoffMs = when (error) {
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> (backoffMs * 2).coerceAtMost(5_000)
                        else -> 400
                    }
                    beginNextTurn()
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    // Check partials so we react as fast as possible; if we spotted
                    // "hey enki" in a partial we can stop and hand off immediately.
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    val payload = extractPayload(text) ?: return
                    handleWake(payload)
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    val payload = extractPayload(text)
                    if (payload != null) handleWake(payload)
                    else beginNextTurn()
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
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
