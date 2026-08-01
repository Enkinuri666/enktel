package tv.enktel.app.voice

import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.Player

/**
 * Global handle for whichever player screen is currently on-screen. Voice
 * commands and PiP can talk to the active player without threading a reference
 * through the whole compose tree.
 *
 * Since v1.38.0 the registration is owned by
 * [tv.enktel.app.player.PlaybackSession], not by the player screens: playback
 * now outlives them in the docked mini window, and the media keys have to keep
 * working there too.
 *
 * [active] is a Compose state so overlays (like the voice mic FAB) can hide
 * themselves during playback without polling.
 */
object ActivePlayerRef {
    @Volatile var player: Player? = null
    val active = mutableStateOf(false)
    // v1.30.0 — LivePlayerScreen registers a channel-zap handler when it
    // mounts; MainActivity.dispatchKeyEvent routes the KEYCODE_CHANNEL_UP
    // and KEYCODE_CHANNEL_DOWN hardware keys through it. Null when no
    // live screen is up (VOD screens leave it null so those keys pass
    // through to the OS).
    @Volatile var channelZapHandler: ((Int) -> Unit)? = null
    // v1.30.0 — LivePlayerScreen also registers a favorite-toggle handler
    // so a long-press on the D-Pad center flips the star on the current
    // channel without needing a menu round-trip.
    @Volatile var toggleFavHandler: (() -> Unit)? = null

    fun register(p: Player) {
        player = p
        active.value = true
    }

    fun unregister(p: Player) {
        if (player === p) {
            player = null
            active.value = false
        }
    }

    fun channelZap(delta: Int) { try { channelZapHandler?.invoke(delta) } catch (_: Throwable) {} }
    fun toggleFavorite() { try { toggleFavHandler?.invoke() } catch (_: Throwable) {} }

    fun pause() { try { player?.pause() } catch (_: Throwable) {} }
    fun resume() { try { player?.play() } catch (_: Throwable) {} }
    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (_: Throwable) { false }
    fun seekForward(seconds: Int) { try {
        player?.let { it.seekTo((it.currentPosition + seconds * 1000L).coerceAtLeast(0)) }
    } catch (_: Throwable) {} }
    fun seekBack(seconds: Int) { try {
        player?.let { it.seekTo((it.currentPosition - seconds * 1000L).coerceAtLeast(0)) }
    } catch (_: Throwable) {} }
    fun seekToMinutes(minutes: Int) { try {
        player?.seekTo((minutes * 60_000L).coerceAtLeast(0))
    } catch (_: Throwable) {} }
    fun restart() { try { player?.seekTo(0) } catch (_: Throwable) {} }
    fun next() { try { player?.seekToNext() } catch (_: Throwable) {} }
    fun previous() { try { player?.seekToPrevious() } catch (_: Throwable) {} }

    private val languageToIso = mapOf(
        "english" to "eng", "spanish" to "spa", "french" to "fre", "german" to "ger",
        "italian" to "ita", "portuguese" to "por", "arabic" to "ara", "russian" to "rus",
        "hindi" to "hin", "mandarin" to "chi", "chinese" to "chi", "japanese" to "jpn",
        "korean" to "kor", "dutch" to "dut", "polish" to "pol", "turkish" to "tur",
    )

    /**
     * Voice-driven audio-track switch: "set audio to Spanish".  Uses Media3's
     * built-in preferred-audio-language mechanism (setPreferredAudioLanguage)
     * rather than PlayerEngine's manual TrackChoice API, since that's a
     * plain [Player] capability and ActivePlayerRef only holds the raw
     * player reference — no need to thread the whole PlayerEngine through
     * the voice layer for this one feature.
     */
    fun setAudioLanguage(language: String): Boolean {
        val iso = languageToIso[language.lowercase()] ?: return false
        return try {
            val p = player ?: return false
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(iso)
                .build()
            true
        } catch (_: Throwable) { false }
    }
}
