package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.AutoRefresh
import java.util.concurrent.TimeUnit

/**
 * When the app decides, on its own, to re-sync the playlist and the guide.
 *
 * The report: "make it auto-resync and refresh each time the app loads, and
 * let me set how often." So the default is every open, the viewer can widen
 * the interval or switch it off, and nothing re-downloads tens of megabytes
 * because someone flicked out to another app and back.
 */
class AutoRefreshTest {

    private val now = 1_800_000_000_000L
    private fun hours(n: Int) = TimeUnit.HOURS.toMillis(n.toLong())
    private val min = TimeUnit.MINUTES.toMillis(1)

    @Test
    fun `off never refreshes, even an empty guide`() {
        assertFalse(AutoRefresh.due(0, now, AutoRefresh.OFF))
        assertFalse(AutoRefresh.due(now - hours(100), now, AutoRefresh.OFF, dataEmpty = true))
    }

    @Test
    fun `every open refreshes once past the thirty-minute floor`() {
        // Just opened and synced two minutes ago: not again.
        assertFalse(AutoRefresh.due(now - 2 * min, now, AutoRefresh.EVERY_OPEN))
        // Opened an hour later: yes.
        assertTrue(AutoRefresh.due(now - hours(1), now, AutoRefresh.EVERY_OPEN))
        // Exactly at the floor.
        assertFalse(AutoRefresh.due(now - 29 * min, now, AutoRefresh.EVERY_OPEN))
        assertTrue(AutoRefresh.due(now - 31 * min, now, AutoRefresh.EVERY_OPEN))
    }

    @Test
    fun `the floor holds a flick out and back from re-downloading`() {
        // Opened, synced, backgrounded, foregrounded again seconds later.
        assertFalse(AutoRefresh.due(now - 10_000, now, AutoRefresh.EVERY_OPEN))
        assertFalse(AutoRefresh.due(now - 10_000, now, 6))
    }

    @Test
    fun `an interval waits the whole interval`() {
        assertFalse(AutoRefresh.due(now - hours(5), now, 6))
        assertTrue(AutoRefresh.due(now - hours(6), now, 6))
        assertTrue(AutoRefresh.due(now - hours(25), now, 24))
        assertFalse(AutoRefresh.due(now - hours(23), now, 24))
    }

    @Test
    fun `an empty store refreshes past the floor whatever the interval`() {
        // A blank guide on a 24-hour interval that synced an hour ago: the
        // viewer is looking at nothing, so fetch it.
        assertTrue(AutoRefresh.due(now - hours(1), now, 24, dataEmpty = true))
        // But not inside the floor — a sync that just failed and left it empty
        // should not be retried on a loop.
        assertFalse(AutoRefresh.due(now - 5 * min, now, 24, dataEmpty = true))
    }

    @Test
    fun `a clock that went backwards is treated as stale, not fresh`() {
        // The box corrected its time at boot and the stamp is now in the
        // future. Age is negative; without this the guide would never refresh
        // until the clock caught up.
        assertTrue(AutoRefresh.due(now + hours(3), now, 6))
        assertTrue(AutoRefresh.due(now + hours(3), now, AutoRefresh.EVERY_OPEN))
        assertFalse(AutoRefresh.due(now + hours(3), now, AutoRefresh.OFF))
    }

    @Test
    fun `the choices offered are sane and every open is first`() {
        assertEquals(AutoRefresh.EVERY_OPEN, AutoRefresh.CHOICES.first())
        assertTrue(AutoRefresh.OFF in AutoRefresh.CHOICES)
        // Each has a label, and they are distinct.
        val labels = AutoRefresh.CHOICES.map { AutoRefresh.label(it) }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals("Every open", AutoRefresh.label(AutoRefresh.EVERY_OPEN))
        assertEquals("Off", AutoRefresh.label(AutoRefresh.OFF))
        assertEquals("Daily", AutoRefresh.label(24))
    }
}
