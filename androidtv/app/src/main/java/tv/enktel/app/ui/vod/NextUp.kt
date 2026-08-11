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

    /**
     * Close enough to the declared end to call it finished.
     *
     * Panels routinely declare a runtime that does not match the file they
     * serve — off by a second or two either way is normal, and a title that
     * stops 1.5 s short of its own duration is not a title that was
     * interrupted. Waiting for the position to reach the number exactly is
     * waiting for something that frequently never happens.
     */
    const val END_SLACK_MS = 1_500L

    /**
     * How long the position may stand still, inside the closing window and
     * with the player *not* paused, before the programme is treated as over.
     *
     * The last resort. Some panels serve a file that simply stops several
     * seconds short and report no end-of-stream at all: the picture holds, the
     * position stops moving, and nothing ever tells the app the episode
     * finished. Four seconds is long enough that ordinary rebuffering does not
     * trip it and short enough that nobody sits looking at a frozen frame
     * wondering whether the app has crashed.
     */
    const val STALL_MS = 4_000L

    /**
     * Whether the next episode should start now.
     *
     * One decision with three ways in, because relying on any single one of
     * them is how this feature kept not working:
     *
     *  - **[playbackEnded]** — the player says the media finished. The clean
     *    case, and the only one that is certain.
     *  - **The position reached the end**, within [END_SLACK_MS]. Needed
     *    because a countdown that only fires on an exact zero never fires on a
     *    file whose declared runtime is a second longer than its content.
     *  - **The position stopped moving** inside the closing window while the
     *    player was still meant to be playing, for [STALL_MS]. Needed because
     *    some panels end a file without ending the stream, so nothing is ever
     *    reported at all.
     *
     * [stalledMs] must be measured only while playback is *wanted* — a viewer
     * who pauses twenty seconds from the end has not finished the episode, and
     * jumping them to the next one would be the worst kind of helpfulness.
     */
    fun shouldAdvance(
        durationMs: Long,
        positionMs: Long,
        playbackEnded: Boolean,
        stalledMs: Long,
        windowMs: Long = WINDOW_MS,
    ): Boolean {
        if (playbackEnded) return true
        if (durationMs <= windowMs) return false
        val left = durationMs - positionMs
        if (left <= END_SLACK_MS) return true
        return left <= windowMs && stalledMs >= STALL_MS
    }
}
