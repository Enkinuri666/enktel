package tv.enktel.app.data.repo

/**
 * How long until a film is out, to the second.
 *
 * [EnktelFeed.Upcoming.countdown] already answers this in the register a rail
 * wants — "In 3 weeks" — which is right for a card in a row you are scrolling
 * past. A screen built around waiting for something wants the other register:
 * a number that visibly moves.
 *
 * ### What it counts to
 *
 * Local midnight at the start of the release day. The feed carries a date and
 * not a time, so that is the whole of what is known — anything more precise
 * would be invented. A film dated tomorrow therefore reads as the hours left
 * of today, which is what a person means when they ask how long until
 * tomorrow.
 *
 * Pure arithmetic on epoch days and milliseconds, so it needs no clock, no
 * zone database and no Android, and the boundaries can be tested rather than
 * observed.
 */
object ReleaseCountdown {

    const val MS_PER_DAY = 86_400_000L

    /** Time left, split for display. */
    data class Remaining(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        /**
         * True once the release day has started.
         *
         * Carried rather than derived from the four fields being zero. Half a
         * second before midnight every field is already zero — the display
         * should read 00:00:00 there, which is a countdown arriving, not a film
         * that is out. Inferring it would have called that one wrong.
         */
        val released: Boolean,
    ) {
        val out: Boolean get() = released
    }

    /**
     * Time from [nowMs] until local midnight beginning [releaseEpochDay].
     *
     * [zoneOffsetMs] is the device's offset from UTC, so a release "on the
     * 14th" means the 14th where the viewer is rather than in Greenwich.
     *
     * Zero in every field when the day has arrived or passed. A countdown that
     * goes negative is a bug people notice immediately, and "out now" is the
     * true statement at that point anyway.
     */
    fun remaining(nowMs: Long, releaseEpochDay: Long, zoneOffsetMs: Long): Remaining {
        val releaseUtcMs = releaseEpochDay * MS_PER_DAY - zoneOffsetMs
        var left = releaseUtcMs - nowMs
        if (left <= 0) return Remaining(0, 0, 0, 0, released = true)
        val days = left / MS_PER_DAY
        left %= MS_PER_DAY
        val hours = left / 3_600_000L
        left %= 3_600_000L
        val minutes = left / 60_000L
        val seconds = (left % 60_000L) / 1000L
        return Remaining(days, hours, minutes, seconds, released = false)
    }

    /**
     * The countdown as a clock: "12d 04:37:11", or "04:37:11" inside a day.
     *
     * Days are dropped once there are none rather than shown as "0d", because
     * the last day is the one people watch and a leading zero unit reads as
     * padding.
     */
    fun format(r: Remaining): String {
        if (r.out) return "Out now"
        val clock = "%02d:%02d:%02d".format(r.hours, r.minutes, r.seconds)
        return if (r.days > 0) "${r.days}d $clock" else clock
    }

    /** Convenience: the formatted countdown straight from a release day. */
    fun format(nowMs: Long, releaseEpochDay: Long, zoneOffsetMs: Long): String =
        format(remaining(nowMs, releaseEpochDay, zoneOffsetMs))

    /**
     * How often a countdown needs redrawing.
     *
     * A second, while it is close enough that seconds are the thing being
     * watched. A minute after that — nobody is watching the seconds tick on
     * something three months away, and a phone redrawing a list every second
     * for no reason is a battery cost with nothing bought by it.
     */
    fun tickMs(r: Remaining): Long = if (r.days == 0L) 1_000L else 60_000L
}
