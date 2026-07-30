package tv.enktel.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last-known playback health, published globally.
 *
 * [PlayerEngine.stats] already carries everything the system monitor wants —
 * bandwidth estimate, dropped frames, how far ahead the buffer runs — but it
 * belongs to a single player instance that only exists while a screen is on
 * top. The monitor needs the numbers from wherever it is in the app, including
 * a few seconds after playback ends, so this mirrors them into one process-wide
 * snapshot as each engine pushes.
 *
 * Deliberately a plain mirror with no history of its own: the monitor decides
 * what to keep, and nothing here should keep a dead player's data alive.
 */
object PlaybackTelemetry {

    data class Snapshot(
        /** Rolling ExoPlayer bandwidth estimate, bits per second. */
        val bandwidthBps: Long = 0,
        /** Cumulative dropped video frames for the current playback session. */
        val droppedFrames: Int = 0,
        /** How much media is buffered ahead of the playhead, ms. */
        val bufferAheadMs: Long = 0,
        val width: Int = 0,
        val height: Int = 0,
        val videoCodec: String = "",
        val decoder: String = "",
        /** When these numbers were last refreshed; 0 means "never played". */
        val atMs: Long = 0,
    ) {
        /** True while the numbers are recent enough to describe what's on screen. */
        fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
            atMs > 0 && nowMs - atMs < 15_000
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun publish(stats: StreamStats) {
        _state.value = Snapshot(
            bandwidthBps = stats.bandwidthEstimate,
            droppedFrames = stats.droppedFrames,
            bufferAheadMs = stats.bufferAheadMs,
            width = stats.width,
            height = stats.height,
            videoCodec = stats.videoCodec,
            decoder = stats.decoder,
            atMs = System.currentTimeMillis(),
        )
    }
}
