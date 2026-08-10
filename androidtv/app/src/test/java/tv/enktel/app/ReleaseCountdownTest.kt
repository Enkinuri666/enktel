package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.ReleaseCountdown

/**
 * The countdown on the Coming Soon screen.
 *
 * The feed carries a date and no time, so the target is local midnight at the
 * start of the release day — anything more precise would be invented. The
 * cases worth pinning are the boundaries: a countdown that goes negative, or
 * that lands on the wrong day because somebody forgot the viewer is not in
 * Greenwich, is the kind of fault people notice in the first five seconds.
 */
class ReleaseCountdownTest {

    private val day = ReleaseCountdown.MS_PER_DAY

    /** Midnight UTC beginning epoch day [d]. */
    private fun utcMidnight(d: Long) = d * day

    @Test
    fun `a release tomorrow counts the hours left of today`() {
        // 18:00 UTC on day 100, released on day 101, viewer at UTC.
        val now = utcMidnight(100) + 18 * 3_600_000L
        val r = ReleaseCountdown.remaining(now, 101, zoneOffsetMs = 0)
        assertEquals(0, r.days)
        assertEquals(6, r.hours)
        assertEquals(0, r.minutes)
        assertEquals(0, r.seconds)
        assertEquals("06:00:00", ReleaseCountdown.format(r))
    }

    @Test
    fun `days are shown only when there are some`() {
        val now = utcMidnight(100)
        assertEquals("12d 00:00:00", ReleaseCountdown.format(now, 112, 0))
        // A leading "0d" reads as padding on the day people actually watch.
        assertFalse(ReleaseCountdown.format(now, 101, 0).contains("0d"))
    }

    @Test
    fun `it never counts past zero`() {
        val now = utcMidnight(100)
        // Released today.
        assertTrue(ReleaseCountdown.remaining(now, 100, 0).out)
        // Released last year.
        assertTrue(ReleaseCountdown.remaining(now, 1, 0).out)
        assertEquals("Out now", ReleaseCountdown.format(now, 99, 0))
    }

    @Test
    fun `the release day is the viewer's day, not Greenwich's`() {
        // 23:00 UTC on day 100 is 10:00 on day 101 in Sydney (+11), so a film
        // released on day 101 is already out there and eleven hours away for
        // somebody in London. Getting this wrong shows a countdown to a film
        // that is already playing.
        val now = utcMidnight(100) + 23 * 3_600_000L
        val sydney = 11 * 3_600_000L
        assertTrue(ReleaseCountdown.remaining(now, 101, sydney).out)
        assertFalse(ReleaseCountdown.remaining(now, 101, 0).out)
    }

    @Test
    fun `a negative offset delays the release rather than bringing it forward`() {
        // Los Angeles is UTC-8: midnight there is 08:00 UTC, so at 02:00 UTC
        // on release day there are still six hours to go.
        val now = utcMidnight(101) + 2 * 3_600_000L
        val la = -8 * 3_600_000L
        val r = ReleaseCountdown.remaining(now, 101, la)
        assertFalse(r.out)
        assertEquals(6, r.hours)
    }

    @Test
    fun `the seconds actually move`() {
        val now = utcMidnight(100)
        val a = ReleaseCountdown.remaining(now, 101, 0)
        val b = ReleaseCountdown.remaining(now + 1_000, 101, 0)
        assertEquals(59, b.seconds)
        assertEquals(0, a.seconds)
    }

    @Test
    fun `it redraws every second only when seconds are worth watching`() {
        // Inside the final day. Exactly 24 hours out still reads as one day,
        // which is right — the seconds are not what anyone is looking at yet.
        val lastDay = utcMidnight(100) + 6 * 3_600_000L
        assertEquals(1_000L, ReleaseCountdown.tickMs(ReleaseCountdown.remaining(lastDay, 101, 0)))
        // Nobody is watching the seconds on something three months out, and a
        // phone redrawing a list every second buys nothing for the battery it
        // spends.
        val now = utcMidnight(100)
        assertEquals(60_000L, ReleaseCountdown.tickMs(ReleaseCountdown.remaining(now, 190, 0)))
    }

    @Test
    fun `the last second before midnight is a countdown, not a release`() {
        // Every field is zero here. Inferring "out" from that would call a film
        // released while it still had a second to go — which is why the flag is
        // carried rather than derived.
        val almost = ReleaseCountdown.remaining(utcMidnight(101) - 1, 101, 0)
        assertFalse(almost.out)
        assertEquals("00:00:00", ReleaseCountdown.format(almost))
        assertTrue(ReleaseCountdown.remaining(utcMidnight(101), 101, 0).out)
    }
}
