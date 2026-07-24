package tv.enktel.app.voice

import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.Player

/**
 * Global handle for whichever player screen is currently on-screen. Voice
 * commands and PiP can talk to the active player without threading a reference
 * through the whole compose tree. Each player screen registers itself with a
 * DisposableEffect when mounted and clears it on dispose.
 *
 * [active] is a Compose state so overlays (like the voice mic FAB) can hide
 * themselves during playback without polling.
 */
object ActivePlayerRef {
    @Volatile var player: Player? = null
    val active = mutableStateOf(false)

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
}
