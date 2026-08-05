package tv.enktel.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Text-to-speech wrapper. Lazily initialises the engine on first [speak] and
 * silently drops speak requests if TTS didn't come up on the device — voice
 * commands still work, the app just doesn't talk back.
 */
class VoiceSpeaker(context: Context) {
    private var ready: Boolean = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.getDefault()
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        } catch (_: Exception) {}
    }

    /** Immediate barge-in stop — used when the wake word interrupts a reply. */
    fun stop() {
        try { tts.stop() } catch (_: Exception) {}
    }

    fun release() {
        try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
    }
}
