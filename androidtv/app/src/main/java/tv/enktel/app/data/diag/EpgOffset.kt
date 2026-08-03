package tv.enktel.app.data.diag

import kotlin.math.abs

/**
 * Guide drift — "it says News, a film is playing".
 *
 * Three clocks have to agree for the guide to line up: the device, the panel,
 * and whatever wrote the EPG. They routinely do not, and the failure is silent
 * because every individual timestamp looks plausible. This measures the gaps
 * so the cause can be named instead of guessed at.
 *
 * Pure arithmetic, kept out of the network layer so it is testable.
 */
object EpgOffset {

    /** A drift big enough to move a programme boundary is worth reporting. */
    private const val NOTABLE_MS = 60_000L

    /** Beyond this the guide is unusable rather than merely off. */
    private const val SEVERE_MS = 15 * 60_000L

    data class Audit(
        val measured: Boolean = false,
        /** Panel clock minus device clock. Positive = panel ahead. */
        val serverSkewMs: Long = 0,
        /** EPG "now" programme midpoint minus device now. */
        val guideSkewMs: Long = 0,
        val programmesChecked: Int = 0,
        val error: String? = null,
    ) {
        val serverNotable: Boolean get() = measured && abs(serverSkewMs) >= NOTABLE_MS
        val guideNotable: Boolean get() = measured && abs(guideSkewMs) >= NOTABLE_MS
        val severe: Boolean get() = measured &&
            (abs(serverSkewMs) >= SEVERE_MS || abs(guideSkewMs) >= SEVERE_MS)

        /** Human summary, or null when the guide lines up. */
        val verdict: String?
            get() = when {
                !measured -> null
                severe && abs(guideSkewMs) >= abs(serverSkewMs) ->
                    "EPG is ${describe(guideSkewMs)} — the guide will show the wrong programme."
                serverNotable && !guideNotable ->
                    "Panel clock is ${describe(serverSkewMs)} the device. The guide itself lines " +
                        "up, so this mainly affects catch-up start times."
                guideNotable ->
                    "EPG data is ${describe(guideSkewMs)} — programme boundaries will be off."
                else -> null
            }

        private fun describe(ms: Long): String {
            val mins = abs(ms) / 60_000
            val unit = when {
                mins >= 120 -> "${mins / 60} hours"
                mins >= 1 -> "$mins min"
                else -> "${abs(ms) / 1000}s"
            }
            return if (ms > 0) "$unit ahead of" else "$unit behind"
        }
    }

    /**
     * @param serverEpochMs panel's own clock, if it reports one (0 = unknown)
     * @param deviceEpochMs device clock at the same moment
     * @param nowProgrammes start/end pairs the EPG claims are on air right now
     */
    fun audit(
        serverEpochMs: Long,
        deviceEpochMs: Long,
        nowProgrammes: List<Pair<Long, Long>>,
    ): Audit {
        if (deviceEpochMs <= 0) return Audit(error = "no device clock")

        val serverSkew = if (serverEpochMs > 0) serverEpochMs - deviceEpochMs else 0L

        // For each programme the guide says is live, how far its midpoint sits
        // from now. Using the midpoint rather than the start keeps a programme
        // that legitimately began 20 minutes ago from reading as drift.
        val skews = nowProgrammes
            .filter { (s, e) -> e > s && s > 0 }
            .map { (s, e) -> ((s + e) / 2) - deviceEpochMs }

        // Median, so one mislabelled programme does not swing the verdict.
        val guideSkew = if (skews.isEmpty()) 0L else skews.sorted()[skews.size / 2]

        return Audit(
            measured = serverEpochMs > 0 || skews.isNotEmpty(),
            serverSkewMs = serverSkew,
            guideSkewMs = guideSkew,
            programmesChecked = skews.size,
        )
    }
}
