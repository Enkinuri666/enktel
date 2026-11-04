package tv.enktel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.epg.EpgFreshness
import java.util.concurrent.TimeUnit

/**
 * When the app should fetch the guide on its own.
 *
 * > Users have to manually resync and refresh the EPG — make it auto-resync
 * > each time the app loads up.
 *
 * They did. The guide was fetched on a playlist's first sync and then only by
 * a twelve-hour WorkManager job that a television in standby between evenings
 * may go days without running, or by the button in Settings.
 *
 * Doing it literally every launch is the other half of the same viewer's
 * complaint, though: an XMLTV for a full line is tens of megabytes over the
 * connection they are about to stream on. These pin the middle.
 */
class EpgFreshnessTest {

    private val now = 1_800_000_000_000L
    private fun hours(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun mins(n: Long) = TimeUnit.MINUTES.toMillis(n)

    private fun check(
        lastSyncMs: Long,
        nowMs: Long = now,
        catalogueSynced: Boolean = true,
        guideEmpty: Boolean = false,
    ) = EpgFreshness.shouldRefreshOnLaunch(lastSyncMs, nowMs, catalogueSynced, guideEmpty)

    @Test
    fun `an old guide is refreshed on launch`() {
        // The reported case: open the app the next evening and the guide is
        // yesterday's until you go and press a button.
        assertTrue(check(lastSyncMs = now - hours(14)))
        assertTrue(check(lastSyncMs = now - hours(6)))
    }

    @Test
    fun `a fresh guide is left alone`() {
        assertFalse(check(lastSyncMs = now - hours(1)))
        assertFalse(check(lastSyncMs = now - hours(5)))
    }

    @Test
    fun `four launches in an evening cost one download, not four`() {
        // A stick that kills the process whenever the viewer switches apps
        // would otherwise refetch tens of megabytes each time they come back.
        assertFalse(check(lastSyncMs = now - mins(1)))
        assertFalse(check(lastSyncMs = now - mins(29)))
        assertTrue(check(lastSyncMs = now - hours(7)))
    }

    @Test
    fun `an empty guide is always old enough`() {
        // Someone looking at a blank grid does not care that the blank was
        // fetched twenty minutes ago — but the gap still applies, or a panel
        // that serves no guide at all would be refetched on every launch.
        assertTrue(check(lastSyncMs = now - hours(1), guideEmpty = true))
        assertFalse(check(lastSyncMs = now - mins(5), guideEmpty = true))
    }

    @Test
    fun `a first run is left to the sync that is already on screen`() {
        // Onboarding and HomeScreen fetch the guide as part of the first sync,
        // with a progress message. Starting a second download of the same file
        // behind that one helps nobody.
        assertFalse(check(lastSyncMs = 0L, catalogueSynced = false))
        assertFalse(check(lastSyncMs = 0L, catalogueSynced = false, guideEmpty = true))
    }

    @Test
    fun `a synced playlist that never got a guide fetches one`() {
        assertTrue(check(lastSyncMs = 0L, catalogueSynced = true, guideEmpty = true))
    }

    @Test
    fun `a clock that went backwards does not freeze the guide`() {
        // These boxes correct their time at boot, routinely by hours. A stamp
        // in the future reads as a negative age, and treating that as "fresh"
        // is how the guide stops refreshing until the clock happens to catch
        // up with it.
        assertTrue(check(lastSyncMs = now + hours(3)))
    }
}
