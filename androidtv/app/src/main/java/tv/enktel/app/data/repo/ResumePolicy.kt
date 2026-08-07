package tv.enktel.app.data.repo

/**
 * When a title belongs in Continue Watching, and when it has been watched.
 *
 * Pulled out of the player because it decides what the rail contains, and the
 * rail was wrong in both directions: it collected titles nobody had really
 * started, and it never let go of titles they had finished.
 *
 * The old rule was one line — clear the resume point once playback passed
 * `duration - 30s`. Almost nobody watches to within thirty seconds of the end.
 * People stop when the credits roll, which on a feature is five to eight
 * minutes out, so a film watched all the way through sat at the top of
 * Continue Watching for ever, offering to resume at 94%. The only way to
 * dismiss it was to play it again and sit through the credits.
 *
 * Nothing here touches the database or the player, so the thresholds are
 * testable, which matters: every one of them is a judgement call about
 * somebody's viewing habits and the failure mode is a rail full of noise.
 */
object ResumePolicy {

    /**
     * Below this, opening something was a look rather than a watch.
     *
     * Matches the floor the details screen already used to decide whether to
     * offer a Resume button at all, so the rail and the button now agree —
     * before, a 20-second glance created a row that offered no way to resume
     * it.
     */
    const val MIN_RESUME_MS = 60_000L

    /**
     * Treat as finished once this close to the end, whatever the runtime.
     *
     * The absolute rule is what covers short content: on a 22-minute episode
     * 95% is still a minute and a half of credits away.
     */
    const val TAIL_MS = 120_000L

    /**
     * …or once this far through, whatever the remaining time.
     *
     * The percentage rule is what covers long content: two minutes from the
     * end of a three-hour film is deep into the credits, but 95% of it is not.
     */
    const val FINISHED_PCT = 95

    /**
     * True when playback got close enough to the end that the user is done.
     *
     * Unknown duration is never finished — a stream with no duration is
     * usually live or still being probed, and guessing "finished" there would
     * silently drop a resume point the user wanted.
     */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return positionMs >= durationMs - TAIL_MS ||
            positionMs * 100 >= durationMs * FINISHED_PCT
    }

    /** True when this position is worth storing as a resume point. */
    fun shouldSave(positionMs: Long, durationMs: Long): Boolean =
        positionMs >= MIN_RESUME_MS && !isFinished(positionMs, durationMs)

    /**
     * How far through, 0..100, or null when there is nothing to show.
     *
     * Null rather than 0 so a card can leave the bar off entirely for a title
     * whose duration was never known, instead of drawing an empty bar that
     * looks like "you have watched none of this".
     */
    fun percent(positionMs: Long, durationMs: Long): Int? {
        if (durationMs <= 0 || positionMs <= 0) return null
        return ((positionMs * 100) / durationMs).coerceIn(0, 100).toInt()
    }
}
