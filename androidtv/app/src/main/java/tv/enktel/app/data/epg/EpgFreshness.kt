package tv.enktel.app.data.epg

import java.util.concurrent.TimeUnit

/**
 * Whether the guide should be fetched again when the app opens.
 *
 * ### The report
 *
 * > Users have to manually resync and refresh the EPG — make it auto-resync
 * > and refresh each time the app loads up.
 *
 * They did have to, and the reason is that nothing refreshed it on launch.
 * Two things touched the guide and neither covered the ordinary case:
 *
 *  - `HomeScreen` fetches it as part of the very first sync, and that block
 *    is gated on `profile.lastSync == 0L`. It runs once in the life of a
 *    playlist and never again.
 *  - [EpgRefreshWorker] is a periodic job on a twelve-hour cycle with a
 *    twelve-hour initial delay. WorkManager is free to defer it — and does,
 *    on a device that spends its time in standby, which is every television
 *    between evenings. A viewer who opens the app for an hour a night may go
 *    days without one of those cycles landing.
 *
 * So between the first sync and whenever WorkManager felt like it, the only
 * thing that refreshed the guide was the button in Settings. Hence the report.
 *
 * ### Why this is not simply "refresh every launch"
 *
 * An XMLTV file for a full line is tens of megabytes and the parse writes
 * every programme on every channel. Doing that on each cold start would cost
 * a viewer who opens the app four times in an evening four full downloads,
 * on the same connection they are about to stream over — which is the other
 * complaint in the same breath as this one.
 *
 * So: refresh when the guide is actually old, and never twice in quick
 * succession however often the app is restarted. An empty guide is always
 * old, because a viewer looking at a blank grid does not care that it was
 * fetched twenty minutes ago.
 *
 * Pure, and [tv.enktel.app.EpgFreshnessTest] pins it.
 */
object EpgFreshness {

    /** Older than this and the guide is refetched on launch. */
    val STALE_AFTER_MS: Long = TimeUnit.HOURS.toMillis(6)

    /**
     * Never two launch refreshes closer together than this.
     *
     * The floor that stops a restart loop — or a viewer flicking between apps
     * on a stick that kills the process each time — from re-downloading the
     * guide over and over. It also bounds the cost of the empty-guide rule
     * below, which would otherwise refetch on every launch for anyone whose
     * panel serves no guide at all.
     */
    val MIN_GAP_MS: Long = TimeUnit.MINUTES.toMillis(30)

    /**
     * Should the app fetch the guide now that it has opened?
     *
     * @param lastSyncMs when the guide was last fetched, 0 if never.
     * @param catalogueSynced whether the playlist itself has ever finished a
     *   sync. False means this is a first run, which `HomeScreen` and
     *   onboarding already handle with a progress message on screen; starting
     *   a second download of the same file behind that one helps nobody.
     * @param guideEmpty whether there are no programmes stored at all.
     */
    fun shouldRefreshOnLaunch(
        lastSyncMs: Long,
        nowMs: Long,
        catalogueSynced: Boolean,
        guideEmpty: Boolean,
    ): Boolean {
        if (!catalogueSynced) return false
        val age = nowMs - lastSyncMs
        // A clock that went backwards (a box correcting its time at boot,
        // which these do on every cold start) leaves a negative age. Treating
        // that as "fetched in the future, so fresh" is how a guide stops
        // refreshing until the stamp happens to come good again.
        if (age < 0) return true
        if (age < MIN_GAP_MS) return false
        if (lastSyncMs == 0L || guideEmpty) return true
        return age >= STALE_AFTER_MS
    }
}
