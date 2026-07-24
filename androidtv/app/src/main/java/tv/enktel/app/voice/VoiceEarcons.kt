package tv.enktel.app.voice

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Short audio cues ("earcons") used to close the voice-UX feedback loop.
 * Speech is fleeting, so a discrete tone tells the user "I heard you"
 * without waiting for a full TTS response.
 *
 * Uses ToneGenerator with STREAM_NOTIFICATION so the cue rides on the
 * user's notification volume (not media), and can't punch through when
 * the phone is on silent.  Kept intentionally short — under 200 ms —
 * so it never gets in the way of the recogniser hearing the command.
 */
class VoiceEarcons {
    private var tone: ToneGenerator? = null

    private fun t(): ToneGenerator? {
        val existing = tone
        if (existing != null) return existing
        return try {
            val n = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            tone = n
            n
        } catch (_: Throwable) { null }
    }

    /** Two-tone "ready" chime — plays when the wake word is heard. */
    fun wakeReady() {
        val g = t() ?: return
        try {
            g.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        } catch (_: Throwable) {}
    }

    /** Single soft ack — command accepted. */
    fun confirm() {
        val g = t() ?: return
        try { g.startTone(ToneGenerator.TONE_PROP_ACK, 80) } catch (_: Throwable) {}
    }

    /** Descending nak — command not understood. */
    fun error() {
        val g = t() ?: return
        try { g.startTone(ToneGenerator.TONE_PROP_NACK, 120) } catch (_: Throwable) {}
    }

    fun release() {
        try { tone?.release() } catch (_: Throwable) {}
        tone = null
    }
}
