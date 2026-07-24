package tv.enktel.app.ui.components

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Sonic-branding earcons for navigation events.  Kept separate from the
 * voice-command earcons (VoiceEarcons) so the master UI-sounds toggle in
 * settings gates only the navigation cues without silencing voice
 * feedback.  Uses ToneGenerator on STREAM_NOTIFICATION so the cues
 * respect the user's silent-mode preference.
 *
 * All calls no-op unless [enabled] is true — controlled by the
 * uiSoundsEnabled setting from SettingsStore, wired up in the UI layer.
 */
object NavSounds {
    @Volatile var enabled: Boolean = true

    private var tone: ToneGenerator? = null
    private fun t(): ToneGenerator? {
        if (!enabled) return null
        val existing = tone
        if (existing != null) return existing
        return try {
            val n = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30)
            tone = n
            n
        } catch (_: Throwable) { null }
    }

    /** Soft click on rail-tile focus / EPG-cell change. */
    fun click() {
        val g = t() ?: return
        try { g.startTone(ToneGenerator.TONE_PROP_BEEP, 30) } catch (_: Throwable) {}
    }

    /** Gentle chime when a horizontal rail reaches its end. */
    fun railEnd() {
        val g = t() ?: return
        try { g.startTone(ToneGenerator.TONE_PROP_ACK, 60) } catch (_: Throwable) {}
    }

    /** Small "open" swell when launching a details page. */
    fun open() {
        val g = t() ?: return
        try { g.startTone(ToneGenerator.TONE_PROP_ACK, 90) } catch (_: Throwable) {}
    }

    fun release() {
        try { tone?.release() } catch (_: Throwable) {}
        tone = null
    }
}
