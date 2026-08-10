package tv.enktel.app.ui.vod

/**
 * When the next-episode card is up, and what its countdown says.
 *
 * Plain arithmetic, split out of VodPlayerScreen because this is where the
 * binge loop was broken and none of it needs a player to check.
 *
 * The card counted `(leftMs + 999) / 1000`, rounding *up*, and only ran at all
 * while `leftMs >= 1`. So the smallest number it could ever produce was 1: the
 * countdown reached "1s" and stopped, the auto-advance that fires at zero
 * never fired, and the episode ended with the card still sitting there. Every
 * roll-over in the app depended on the viewer pressing the button — which, for
 * separate reasons, they could not.
 */
object NextUp {

    /** How long before the end the card appears. */
    const val WINDOW_MS = 30_000L

    /**
     * Seconds of programme left, or null when the card should not be showing.
     *
     * Null for anything with no length (live, and VOD whose panel declares no
     * duration), and for titles no longer than the window itself — a 20-second
     * clip would otherwise offer to move on from its first frame.
     *
     * Zero is a real answer and the important one: it means the programme is
     * over and the next one should start.
     */
    fun secondsLeft(durationMs: Long, positionMs: Long, windowMs: Long = WINDOW_MS): Int? {
        if (durationMs <= windowMs) return null
        val left = durationMs - positionMs
        if (left > windowMs) return null
        return (left.coerceAtLeast(0L) / 1000L).toInt()
    }
}
