package tv.enktel.app.player

/**
 * How much video to hold in flight, and why live and VOD cannot share an
 * answer.
 *
 * ### The bug this exists to fix
 *
 * The window used to be chosen once, from a single `bufferProfile` setting, at
 * the moment the engine was constructed — before anything knew whether the
 * stream about to play was a film or a live channel. On a TV the "auto" profile
 * asked for a 90-second maximum, and that number was applied to live IPTV too.
 *
 * Two things go wrong when you buffer ninety seconds of a live channel:
 *
 *  1. **You are ninety seconds behind.** For a football match that is the
 *     difference between watching a goal and hearing the neighbours react to
 *     it first.
 *  2. **The segments are gone.** A live HLS server publishes a sliding window,
 *     commonly six segments — under a minute. Asking for more than it retains
 *     is a request for a file that has been deleted, and the answer is 404. The
 *     player treats that as a load error and stalls, so buffering *harder*
 *     makes live playback *less* reliable. This is the counter-intuitive part,
 *     and it is why "just raise the buffer" is the wrong instinct for live.
 *
 * VOD wants the opposite: the file is complete and static, nothing 404s, and
 * every second held in memory is a second of survivable bandwidth dip later.
 *
 * ### Everything here is pure
 *
 * No Android types, no player. These four numbers decide whether channel
 * surfing feels instant and whether a film survives a wobbly connection, they
 * interact in ways that are easy to get subtly wrong, and [BufferProfilesTest]
 * pins both the values and the invariants ExoPlayer asserts on.
 */
object BufferProfiles {

    /**
     * The four numbers `DefaultLoadControl.Builder.setBufferDurationsMs` takes.
     *
     * @param minMs below this, the player resumes pulling from the network
     * @param maxMs the player never holds more than this
     * @param playMs how much is needed before playback starts — this is
     *   time-to-first-frame, and on live it is how fast a channel zap feels
     * @param rebufMs how much is needed to restart after a stall
     */
    data class Window(val minMs: Int, val maxMs: Int, val playMs: Int, val rebufMs: Int)

    /**
     * Live windows are capped here regardless of profile.
     *
     * Fifteen seconds is the top of the range that stays inside a typical
     * provider's segment window. A user who picks "Large" for VOD should not
     * silently get a live configuration that 404s, so the cap is applied after
     * the profile rather than being the profile's responsibility.
     */
    const val LIVE_MAX_CEILING_MS = 15_000

    /**
     * Live buffering is meaningless without somewhere to put it, but a big
     * allocation on a 1 GB stick is how you get an OOM instead of a stall.
     * 2 MB matches the chunk size ExoPlayer's own guidance suggests for
     * high-bitrate sources; the low-RAM path halves it.
     */
    fun allocationChunkBytes(lowRam: Boolean): Int = if (lowRam) 1024 * 1024 else 2 * 1024 * 1024

    /**
     * The window for [profile], adjusted for what is actually playing.
     *
     * @param profile "low" | "balanced" | "large" — "auto" is resolved by the
     *   caller before it gets here, because that decision needs a live
     *   bandwidth estimate this object deliberately knows nothing about.
     * @param live true for a live channel or a catch-up stream still on the
     *   live edge; false for a film, an episode or a download
     * @param isTv leaves more headroom on a set-top box, which is mains
     *   powered and usually on a better connection than a phone
     * @param lowRam a 1 GB Fire TV Stick Lite cannot hold a three-minute 4K
     *   window no matter which profile is selected
     */
    fun window(profile: String, live: Boolean, isTv: Boolean, lowRam: Boolean = false): Window {
        val w = if (live) liveWindow(profile, isTv) else vodWindow(profile, isTv)
        val capped = if (live) w.copy(maxMs = minOf(w.maxMs, LIVE_MAX_CEILING_MS)) else w
        val scaled = if (lowRam) {
            // Halve the ceiling rather than the whole window: cutting minMs too
            // would make the player start pulling again almost immediately and
            // spend its time in short, frequent fetches.
            capped.copy(maxMs = maxOf(capped.maxMs / 2, capped.minMs + 2_000))
        } else {
            capped
        }
        return normalise(scaled)
    }

    /**
     * Live: stay near the edge, start fast, never outrun the provider's
     * segment window.
     */
    private fun liveWindow(profile: String, isTv: Boolean): Window = when (profile) {
        // Lowest latency. For sport, and for the users who complain about
        // being behind the broadcast.
        "low" -> Window(minMs = 2_000, maxMs = 8_000, playMs = 500, rebufMs = 1_500)
        // Most tolerant of a bad line, still inside the segment window.
        "large" -> Window(minMs = 6_000, maxMs = 15_000, playMs = 1_500, rebufMs = 3_000)
        else -> if (isTv) {
            Window(minMs = 4_000, maxMs = 12_000, playMs = 1_000, rebufMs = 2_000)
        } else {
            // A phone changes networks mid-stream in a way a set-top box does
            // not, so it starts a little more conservatively.
            Window(minMs = 4_000, maxMs = 12_000, playMs = 1_200, rebufMs = 2_500)
        }
    }

    /** VOD: hoard while the network is good, start without a long spinner. */
    private fun vodWindow(profile: String, isTv: Boolean): Window = when (profile) {
        "low" -> Window(minMs = 10_000, maxMs = 30_000, playMs = 1_500, rebufMs = 3_000)
        "large" -> Window(minMs = 30_000, maxMs = 180_000, playMs = 2_500, rebufMs = 5_000)
        else -> if (isTv) {
            Window(minMs = 25_000, maxMs = 120_000, playMs = 2_000, rebufMs = 5_000)
        } else {
            Window(minMs = 20_000, maxMs = 90_000, playMs = 2_000, rebufMs = 4_000)
        }
    }

    /**
     * Apply a user's explicit minimum, and keep the result legal.
     *
     * `DefaultLoadControl.Builder` does not clamp — it asserts, and throws
     * `IllegalArgumentException` from the constructor if `maxMs < minMs` or if
     * either start threshold exceeds `minMs`. That is a crash on the first
     * frame of playback, from a settings screen slider, so the clamping has to
     * happen here rather than being left to the caller to remember.
     */
    fun withMinOverride(w: Window, overrideMs: Int): Window =
        if (overrideMs <= 0) w else normalise(w.copy(minMs = overrideMs))

    private fun normalise(w: Window): Window {
        val min = w.minMs.coerceAtLeast(1_000)
        val max = w.maxMs.coerceAtLeast(min)
        return Window(
            minMs = min,
            maxMs = max,
            playMs = w.playMs.coerceIn(250, min),
            rebufMs = w.rebufMs.coerceIn(250, min),
        )
    }
}
