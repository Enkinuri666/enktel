package tv.enktel.app.player

/**
 * Rebuilding a live cushion on a stream that is delivered in real time.
 *
 * ### The report
 *
 * > Live TV keeps buffering.
 *
 * The stats overlay on the channel in question: 1280×720 @ 50 fps AVC in
 * MPEG-TS, network estimated at 15 Mbps, **4.6 s of buffer**, 3 dropped
 * frames. Not a decode problem, and on the face of it not a bandwidth problem
 * either — three times the headroom a 720p50 feed needs.
 *
 * ### Why the buffer will not grow
 *
 * A raw `.ts` live URL is not a file. The panel writes the broadcast into the
 * socket at the rate the broadcast happens, so the client receives one second
 * of video per second no matter how much spare bandwidth it has. The window
 * [BufferProfiles] asks for — twelve seconds on the balanced profile — is a
 * ceiling the player is never given the chance to reach: it holds whatever the
 * provider happened to send ahead, which on most panels is a handful of
 * seconds, and that is the entire safety margin.
 *
 * From there every hiccup is terminal. A dip spends some of the margin, and
 * because the stream arrives at exactly playback rate there is no surplus to
 * earn it back with. The margin only ever ratchets down, and the viewer sees a
 * channel that buffers, recovers, and buffers again a minute later.
 *
 * **The only way to gain buffer on a stream paced at 1× is to play slower than
 * 1×.** Three per cent is imperceptible — it is the figure Media3's own live
 * speed control uses — and it earns about two seconds of cushion a minute.
 *
 * ### Why Media3 does not already do this
 *
 * It does, for HLS and DASH, and [PlayerEngine] asks it to: every live
 * MediaItem carries a `LiveConfiguration` with a six-second target offset and
 * a 0.97–1.03 speed range, and [BufferProfiles.LIVE_TARGET_OFFSET_MS]
 * documents why at length.
 *
 * On a raw TS stream none of it runs. `ExoPlayerImplInternal`'s
 * `shouldUseLivePlaybackSpeedControl` requires three things of the timeline's
 * window, and `ProgressiveMediaSource` — which is what plays a `.ts` URL —
 * satisfies exactly one:
 *
 * | condition | progressive TS |
 * | :--- | :--- |
 * | `window.isLive()` | **true** — `ProgressiveMediaPeriod` reports live for a stream of unknown duration, so the MediaItem's `LiveConfiguration` is carried onto the window |
 * | `window.isDynamic` | **false** — `SinglePeriodTimeline` is constructed with it hardcoded false |
 * | `windowStartTimeMs != C.TIME_UNSET` | **unset** — a progressive stream has no wall-clock anchor |
 *
 * So the live configuration is attached, inspected, and discarded. The target
 * offset and both speed bounds have only ever reached `.m3u8` channels, and
 * the raw-TS channels that make up most of an IPTV line have been running with
 * no cushion mechanism at all. That is not visible from anywhere in the app,
 * which is why it survived: the stats overlay reports the buffer, and a buffer
 * that never recovers looks identical to a connection that is merely slow.
 *
 * This object is the same idea driven by the signal a progressive stream does
 * have. There is no live edge to measure against, but there is a buffer level,
 * and the buffer level is what the viewer actually feels.
 *
 * Pure, so the hysteresis can be tested rather than listened for.
 */
object LiveBufferPacer {

    /** Normal playback. */
    const val NORMAL_SPEED = 1.0f

    /**
     * The speed used while the cushion is being rebuilt.
     *
     * Deliberately the same 3 % Media3's `DefaultLivePlaybackSpeedControl`
     * uses, and the same figure [PlayerEngine] already asks for on HLS. A
     * bigger correction would rebuild faster and start to be audible; the
     * point of this is that nobody notices it happening.
     */
    const val REBUILD_SPEED = 0.97f

    /** How often the engine re-evaluates. */
    const val TICK_MS = 1_000L

    /** No cushion is rebuilt below this, whatever the profile asks for. */
    const val MIN_FLOOR_MS = 1_500

    /**
     * Does Media3's own live speed control apply to this timeline?
     *
     * The three conditions from `shouldUseLivePlaybackSpeedControl`, so the
     * two mechanisms can never both be adjusting the speed. On HLS and DASH
     * this answers true and the pacer stands down; on progressive it answers
     * false and the pacer takes over.
     *
     * Asked of the live timeline rather than of the URL: a panel that serves a
     * playlist from an extensionless path is routed to `HlsMediaSource` by
     * [PlayerEngine]'s retry, and what the URL looked like says nothing about
     * which media source ended up playing it.
     */
    fun media3PacesThis(isLive: Boolean, isDynamic: Boolean, windowStartTimeMs: Long): Boolean =
        isLive && isDynamic && windowStartTimeMs != UNSET_TIME

    /** `C.TIME_UNSET`, without dragging a Media3 import into a pure object. */
    const val UNSET_TIME = Long.MIN_VALUE + 1

    /**
     * Below this the cushion is too thin and playback eases off.
     *
     * Half of what the load control considers a healthy buffer, so the pacer
     * is idle in normal running and only engages once a real dip has happened.
     */
    fun lowWatermarkMs(window: BufferProfiles.Window): Int =
        maxOf(window.minMs / 2, MIN_FLOOR_MS).coerceAtMost(window.minMs)

    /**
     * At or above this the cushion is healthy again and speed returns to 1×.
     *
     * The load control's own minimum: the level at which it stops treating the
     * buffer as short. Using the same number means the pacer and the load
     * control agree about what "enough" is, rather than each having a private
     * opinion that the other quietly undoes.
     */
    fun highWatermarkMs(window: BufferProfiles.Window): Int = window.minMs

    /**
     * The speed to play at, given how much buffer there is.
     *
     * The gap between the watermarks is the hysteresis, and it is the whole
     * design. A single threshold would flip the speed on every sample around
     * it, and a playback speed oscillating once a second is audible in a way
     * that holding 0.97 for a minute is not.
     *
     * @param currentSpeed what the player is doing now; returned unchanged
     *   inside the band, which is what makes the band hysteresis rather than
     *   decoration.
     */
    fun speedFor(
        bufferedMs: Long,
        currentSpeed: Float,
        window: BufferProfiles.Window,
    ): Float = when {
        bufferedMs < lowWatermarkMs(window) -> REBUILD_SPEED
        bufferedMs >= highWatermarkMs(window) -> NORMAL_SPEED
        else -> currentSpeed
    }

    /** True when [speed] is the rebuild speed, for the stats overlay. */
    fun isRebuilding(speed: Float): Boolean = speed < NORMAL_SPEED - 0.001f
}
