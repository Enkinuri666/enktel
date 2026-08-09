package tv.enktel.app.player

import kotlin.math.abs

/**
 * Deciding what frame rate a stream is actually running at.
 *
 * Split out of PlayerEngine because this is the piece that got it wrong, and
 * plain Kotlin with no player and no Android in it is a piece that can be
 * tested. The fault was not in measuring frames — that part worked — but in
 * turning a noisy measurement into a decision, which is the only part with an
 * edge case in it.
 *
 * ### Why any of this is delicate
 *
 * The consumer is [RefreshRateMatcher], which sets `preferredDisplayModeId`.
 * That is an HDMI mode change: the television resyncs and the picture is gone
 * for a second or two. It is worth doing once, to put a 25 fps broadcast onto
 * a 50 Hz mode instead of juddering at 60. It is ruinous to do repeatedly.
 *
 * A tester reported a live channel that played, cut out, recovered and cut out
 * again, with a full 15 s buffer and zero dropped frames — a player that was
 * not short of data at any point. The cause was here: 24 and 25 fps are one
 * frame apart, the snap tolerance was ±1.5, so the boundary between them sat
 * at 24.5 and ordinary sampling noise crossed it every so often. Each crossing
 * looked like a new frame rate and triggered another resync.
 */
object FrameRates {

    /**
     * Rates that real content is shot or broadcast at.
     *
     * 23.976 and 29.97 are the NTSC pulldowns of 24 and 30 and are genuinely
     * distinct — a 23.976 stream on a 24 Hz mode drifts a frame every 41
     * seconds — so they are listed separately rather than rounded away.
     */
    val KNOWN = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f, 100f, 120f)

    /**
     * How close a measurement must be to a known rate to be called that rate.
     *
     * Strictly less than half the gap between the closest pair of distinct
     * rates, 24 and 25 — so there is a band in the middle that belongs to
     * neither and is left unnamed.
     *
     * That band is the whole point. The original tolerance of 1.5 named every
     * reading: 24.5 came back as 24, 24.6 as 25, and the answer tracked the
     * noise rather than the stream. Half the gap is no better — the boundary
     * merely moves to the midpoint and readings still land either side of it.
     * Only a tolerance below half leaves ambiguous readings ambiguous, and an
     * unnamed reading never repeats exactly, so it never satisfies
     * [AGREE_SAMPLES], so a rate we cannot identify never changes a display
     * mode. Refusing to answer is the correct answer here.
     */
    const val SNAP_TOLERANCE = 0.4f

    /**
     * Consecutive one-second samples that must agree before the rate is
     * trusted enough to act on.
     *
     * Five rides out the burst of buffers a decoder releases after a flush —
     * a seek, a track change, a reconnect — and still settles early enough
     * that rate matching happens near the start of playback rather than
     * halfway through the programme.
     */
    const val AGREE_SAMPLES = 5

    /** The nearest real rate to [fps], or [fps] itself when none is near enough. */
    fun snap(fps: Float): Float {
        val nearest = KNOWN.minByOrNull { abs(it - fps) } ?: return fps
        return if (abs(nearest - fps) <= SNAP_TOLERANCE) nearest else fps
    }

    /** True when [fps] was close enough to a known rate to be named. */
    fun isKnown(fps: Float): Boolean = KNOWN.any { abs(it - fps) <= 0.001f }

    /**
     * Running agreement over successive samples.
     *
     * Holds the candidate and how many times in a row it has come back the
     * same. Once [settled] is true the caller publishes once and stops asking;
     * there is no path back, by design — a stream does not change its frame
     * rate mid-play, and pretending it might is what caused the resync loop.
     */
    class Latch {
        var candidate: Float = 0f
            private set
        var agreement: Int = 0
            private set

        /** Feed one measurement. Returns the settled rate, or 0 while unsure. */
        fun offer(measured: Float): Float {
            if (measured <= 0f) return 0f
            val snapped = snap(measured)
            if (snapped == candidate) {
                agreement++
            } else {
                candidate = snapped
                agreement = 1
            }
            return if (settled) candidate else 0f
        }

        val settled: Boolean get() = agreement >= AGREE_SAMPLES && isKnown(candidate)

        fun reset() {
            candidate = 0f
            agreement = 0
        }
    }
}
