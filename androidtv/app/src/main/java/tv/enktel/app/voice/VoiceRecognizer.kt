package tv.enktel.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around Android's [SpeechRecognizer] so the composables can start
 * listening + stop + release without dealing with the callback interface.
 *
 * Notes:
 *  - SpeechRecognizer must be constructed on the main thread.
 *  - We use FREE_FORM language model so the user can speak natural sentences
 *    ("turn the channel to Nine HD" rather than "channel:9-hd").
 *  - Partial results are surfaced so the UI can echo mid-utterance text.
 */
class VoiceRecognizer(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (Int) -> Unit,
        onEndOfSpeech: () -> Unit = {},
    ) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onEndOfSpeech() }
            override fun onError(error: Int) { onError(error) }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = list?.firstOrNull().orEmpty()
                if (best.isNotBlank()) onResult(best) else onError(SpeechRecognizer.ERROR_NO_MATCH)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        r.startListening(intent)
    }

    fun stop() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    fun release() = stop()
}
