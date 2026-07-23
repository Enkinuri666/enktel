package tv.enktel.app.voice

import androidx.media3.common.Player

/**
 * Global handle for whichever player screen is currently on-screen. Voice
 * commands and PiP can talk to the active player without threading a reference
 * through the whole compose tree. Each player screen registers itself with a
 * DisposableEffect when mounted and clears it on dispose.
 */
object ActivePlayerRef {
    @Volatile var player: Player? = null

    fun pause() { try { player?.pause() } catch (_: Throwable) {} }
    fun resume() { try { player?.play() } catch (_: Throwable) {} }
    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (_: Throwable) { false }
}
