package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.ZapPlan

/**
 * When it is safe to hold a warm connection open for the next channel.
 *
 * The arithmetic is small and the consequence of getting it wrong is not: a
 * request to a stream URL is a session on an Xtream panel, lines are sold with
 * a cap on those, and a panel that is over its cap answers by dropping the
 * session already in progress. That presents as the stream cutting out while
 * the viewer sits perfectly still, which is close to the least debuggable
 * symptom this app can produce.
 */
class ZapPlanTest {

    @Test
    fun `a single-connection line is never warmed`() {
        // The zap itself already needs two sessions at its peak.
        assertFalse(ZapPlan.shouldWarm(1))
    }

    @Test
    fun `a two-connection line is not warmed either`() {
        // The rule this replaces excluded only maxConnections == 1, so a
        // two-connection line warmed two neighbours: three sessions against a
        // cap of two before the viewer had touched anything.
        assertFalse(ZapPlan.shouldWarm(2))
    }

    @Test
    fun `three connections is the first that fits`() {
        // One playing, one warm, and headroom for the overlap during a zap.
        assertTrue(ZapPlan.shouldWarm(3))
        assertTrue(ZapPlan.shouldWarm(10))
    }

    @Test
    fun `a panel that does not say gets the benefit of the doubt`() {
        // Every M3U line reports nothing, and so do plenty of Xtream panels.
        // Treating silence as "no room" would disable zap warming for most
        // profiles to protect the few that are actually capped.
        assertTrue(ZapPlan.shouldWarm(0))
    }

    @Test
    fun `a nonsense cap is treated as unknown rather than as a limit`() {
        // Negative cannot happen from a well-behaved panel, and refusing to
        // warm on it would be a silent behaviour change driven by a typo in
        // somebody's user_info.
        assertFalse(ZapPlan.shouldWarm(-1))
    }

    @Test
    fun `warming follows the direction of travel`() {
        // Somebody who just pressed channel-up is likelier to press it again.
        assertEquals(6, ZapPlan.target(size = 10, currentIndex = 5, direction = 1))
        assertEquals(4, ZapPlan.target(size = 10, currentIndex = 5, direction = -1))
    }

    @Test
    fun `up is the convention before any zap has happened`() {
        assertEquals(6, ZapPlan.target(size = 10, currentIndex = 5, direction = 0))
    }

    @Test
    fun `the ends of the list wrap`() {
        assertEquals(0, ZapPlan.target(size = 10, currentIndex = 9, direction = 1))
        assertEquals(9, ZapPlan.target(size = 10, currentIndex = 0, direction = -1))
    }

    @Test
    fun `there is nothing to warm in a list of one, or none`() {
        assertNull(ZapPlan.target(size = 1, currentIndex = 0, direction = 1))
        assertNull(ZapPlan.target(size = 0, currentIndex = 0, direction = 1))
    }

    @Test
    fun `an unknown current channel warms nothing`() {
        // indexOfFirst returns -1 when the playing channel is not in the list —
        // a filtered category, a favourites view. Warming index 0 there would
        // be a guess with a session attached.
        assertNull(ZapPlan.target(size = 10, currentIndex = -1, direction = 1))
        assertNull(ZapPlan.target(size = 10, currentIndex = 10, direction = 1))
    }

    @Test
    fun `a capped line lets go before it takes hold`() {
        // One connection means every single zap is over the limit, because a
        // channel change momentarily wants two. Two is the same story with one
        // fewer failure mode.
        assertTrue(ZapPlan.needsReleaseBeforeAcquire(1))
        assertTrue(ZapPlan.needsReleaseBeforeAcquire(2))
    }

    @Test
    fun `a line with room keeps its warm connections`() {
        // Forcing a teardown here would slow every zap to fix a problem these
        // lines do not have.
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(3))
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(10))
    }

    @Test
    fun `an unreported cap is not treated as capped`() {
        // Same reasoning as shouldWarm: silence is the common case, and
        // penalising it would slow zapping for most profiles.
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(0))
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(-1))
    }

    @Test
    fun `warming and releasing are opposites for every cap a panel can report`() {
        // A line either has room to hold a spare connection open, or it needs
        // the old one gone first. If a cap fell in both, a line would warm a
        // connection it was about to be told to drop.
        listOf(0, 1, 2, 3, 4, 10, 100).forEach { cap ->
            assertTrue(
                "cap $cap is in both or neither",
                ZapPlan.shouldWarm(cap) != ZapPlan.needsReleaseBeforeAcquire(cap),
            )
        }
    }

    @Test
    fun `a negative cap is the one case that is neither`() {
        // No well-behaved panel reports one. Garbage is not evidence that a
        // line is capped, and not evidence that it has room either, so both
        // answers are no — which is deliberate and the single exception to the
        // rule above.
        assertFalse(ZapPlan.shouldWarm(-1))
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(-1))
    }

    @Test
    fun `the release grace is short enough not to feel like a stall`() {
        assertTrue(ZapPlan.RELEASE_GRACE_MS in 100..800)
    }

    @Test
    fun `playlists are told apart from raw transport streams`() {
        // The two need different handling: a playlist ends, a .ts response is
        // the broadcast and does not.
        assertTrue(ZapPlan.isPlaylist("http://p:8080/live/u/p/1.m3u8"))
        assertTrue(ZapPlan.isPlaylist("http://p:8080/live/u/p/1.M3U8?token=x"))
        assertFalse(ZapPlan.isPlaylist("http://p:8080/live/u/p/1.ts"))
        // Extensionless is the common Xtream shape and is not a playlist.
        assertFalse(ZapPlan.isPlaylist("http://p:8080/live/u/p/1"))
    }
}
