package tv.enktel.app.data.epg

/**
 * How far through the programme a live channel is, and how much is left.
 *
 * ### Why this exists
 *
 * The channel grid already knew what was on — it fetches a now-playing
 * programme per channel in one query — and printed the title. What it could
 * not say was *where in it you are*, which on a live grid is most of the
 * information: "Premier League: Arsenal v Chelsea" tells you nothing about
 * whether you have missed it. A progress bar and a line saying how long is
 * left is the difference between a channel list and a live guide, and it is
 * the thing every OTT live grid has that this one did not.
 *
 * Pure, so the arithmetic can be tested rather than watched. All of it is
 * edge cases: a programme whose guide data has it ending before it starts, a
 * clock that has drifted past the end, a zero-length entry. Each of those
 * divides by zero or produces a bar longer than its track if written the
 * obvious way.
 */
object NowPlaying {

    /**
     * Progress through a programme, 0f..1f.
     *
     * Clamped at both ends rather than trusted. A guide that says a
     * programme ran from 20:00 to 19:00 is not rare — it is what a provider
     * publishes when its timezone handling is wrong — and the naive
     * `(now - start) / (end - start)` returns a negative fraction for it,
     * which draws a bar extending leftwards out of its track.
     *
     * @return 0f when the programme has not started, has no duration, or the
     *   times are inverted; 1f once it has finished.
     */
    fun progress(startMs: Long, endMs: Long, nowMs: Long): Float {
        val span = endMs - startMs
        if (span <= 0L) return 0f
        val through = nowMs - startMs
        return (through.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * "32 min left", "1h 05m left", or null when there is nothing to say.
     *
     * Null rather than "0 min left" for a finished programme: a card should
     * drop the line, not print a countdown that has run out. Null also for a
     * programme that has not begun — the grid only ever shows what is on now,
     * so that case means the guide and the clock disagree, and inventing a
     * number from it would be worse than saying nothing.
     */
    fun remaining(endMs: Long, nowMs: Long): String? {
        val leftMs = endMs - nowMs
        if (leftMs <= 0L) return null
        val minutes = (leftMs + 59_999L) / 60_000L
        if (minutes > MAX_SENSIBLE_MINUTES) return null
        return minutesLeft(minutes)
    }

    /**
     * How a positive number of minutes is worded — "32 min left", "1h 05m
     * left" — wherever that is printed.
     *
     * Shared with the player overlay's own countdown rather than written
     * twice: the overlay says more at the two boundaries, because it has the
     * room for "Ends in under a minute" and a card does not, but the stretch
     * in between has to read identically in both or the same programme
     * appears to have two different amounts of time left.
     */
    fun minutesLeft(minutes: Long): String = when {
        minutes < 60 -> "$minutes min left"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "${h}h left" else "${h}h ${m.toString().padStart(2, '0')}m left"
        }
    }

    /**
     * Beyond this, the guide is wrong rather than the programme long.
     *
     * Twelve hours. A panel that publishes one entry per channel per day —
     * common on lines with no real EPG — would otherwise have every card
     * reading "9h 42m left", which is not information and crowds out the
     * title. Overnight films and sports marathons fit well inside it.
     */
    const val MAX_SENSIBLE_MINUTES = 12 * 60L
}
