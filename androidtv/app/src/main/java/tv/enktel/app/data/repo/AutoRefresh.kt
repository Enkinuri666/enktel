package tv.enktel.app.data.repo

import java.util.concurrent.TimeUnit

/**
 * When the app should re-sync the playlist and re-fetch the guide on its own.
 *
 * ### The report
 *
 * > Users have to manually resync and refresh the EPG. Add an option to set
 * > how often it happens, and have the playlist resync and the guide refresh
 * > each time the app is opened after being closed.
 *
 * Before this, nothing re-synced the catalogue after a playlist's first sync,
 * and the guide was refreshed only by a twelve-hour background job that a
 * television in standby routinely skipped, or by hand in Settings. So the two
 * things a viewer most wants kept current — what the line carries and what is
 * on — both went stale until someone pressed a button.
 *
 * ### One interval, two jobs
 *
 * The viewer sets a single interval; it governs the catalogue and the guide
 * alike, each measured from its own last successful sync. The special values
 * are the useful ones:
 *
 *  - [EVERY_OPEN]: refresh whenever the app comes to the foreground. This is
 *    what "each time the app is opened" asks for, and it is the default.
 *  - [OFF]: never on its own; the Sync and Refresh buttons in Settings still
 *    work.
 *  - any positive number of hours: refresh only once that long has passed.
 *
 * ### The floor
 *
 * Even on EVERY_OPEN there is a thirty-minute floor ([MIN_GAP_MS]). An XMLTV
 * file for a full line is tens of megabytes and a catalogue sync walks the
 * whole account, both over the same connection the viewer is about to stream
 * on; re-running either because they flicked out to another app and back
 * helps nobody. The floor is what makes "every open" safe to be the default.
 *
 * Pure, and [tv.enktel.app.AutoRefreshTest] pins it.
 */
object AutoRefresh {

    /** Refresh every time the app is opened (past the floor). The default. */
    const val EVERY_OPEN = 0

    /** Never refresh automatically; the manual buttons still work. */
    const val OFF = -1

    /** Never two automatic refreshes of the same thing closer together than this. */
    val MIN_GAP_MS: Long = TimeUnit.MINUTES.toMillis(30)

    /** The intervals offered in Settings, in the order they are shown. */
    val CHOICES: List<Int> = listOf(EVERY_OPEN, 3, 6, 12, 24, OFF)

    /** What a choice is called on its chip. */
    fun label(hours: Int): String = when (hours) {
        EVERY_OPEN -> "Every open"
        OFF -> "Off"
        24 -> "Daily"
        1 -> "Hourly"
        else -> "${hours}h"
    }

    /**
     * Is a thing last synced at [lastSyncMs] due to be refreshed now?
     *
     * @param intervalHours the viewer's setting: [EVERY_OPEN], [OFF], or hours.
     * @param dataEmpty true when there is nothing stored at all (an empty
     *   guide, an empty catalogue). Empty is always stale — a viewer looking
     *   at a blank grid does not care that it was fetched twenty minutes ago —
     *   so it refreshes past the floor whatever the interval, unless refresh
     *   is switched off entirely.
     */
    fun due(
        lastSyncMs: Long,
        nowMs: Long,
        intervalHours: Int,
        dataEmpty: Boolean = false,
    ): Boolean {
        if (intervalHours == OFF) return false
        val age = nowMs - lastSyncMs
        // A clock that went backwards — these boxes correct their time at boot
        // on every cold start — leaves a negative age. Treating that as
        // "synced in the future, so fresh" is how a guide stops refreshing
        // until the stamp happens to come good again.
        if (age < 0) return true
        if (age < MIN_GAP_MS) return false
        if (dataEmpty) return true
        if (intervalHours == EVERY_OPEN) return true
        return age >= TimeUnit.HOURS.toMillis(intervalHours.toLong())
    }
}
