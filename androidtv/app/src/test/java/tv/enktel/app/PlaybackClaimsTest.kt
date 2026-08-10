package tv.enktel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.PlaybackClaims

/**
 * The next episode never started, and this is why.
 *
 * One engine is shared by every player screen, and a screen that goes away
 * while nothing is docked releases it. Rolling into the next episode navigates
 * from the VOD player to the VOD player, and Compose composes the incoming
 * screen before disposing the outgoing one — so the new screen took the engine,
 * asked it to play, and the old screen's teardown then released it. Black
 * screen, and the only way on was to leave playback and start the episode by
 * hand.
 *
 * These pin the hand-off rule: whoever claimed last owns the session.
 */
class PlaybackClaimsTest {

    @Test
    fun `a screen that claimed and was not replaced still owns the session`() {
        val claims = PlaybackClaims()
        val only = claims.claim()
        assertTrue("a lone player screen must still tear its engine down", claims.isOwner(only))
    }

    @Test
    fun `a superseded screen does not own the session`() {
        // Episode one mounts, episode two mounts over it, episode one disposes.
        val claims = PlaybackClaims()
        val episodeOne = claims.claim()
        val episodeTwo = claims.claim()
        assertFalse("the outgoing screen must not stop the incoming one", claims.isOwner(episodeOne))
        assertTrue(claims.isOwner(episodeTwo))
    }

    @Test
    fun `the last screen of a binge still tears down`() {
        // Four episodes in, the viewer backs out. Nothing has taken over from
        // the fourth, so it stops — which is the invariant the guard must not
        // break: an engine nobody can see is the defect this app shipped twice.
        val claims = PlaybackClaims()
        val tokens = List(4) { claims.claim() }
        tokens.dropLast(1).forEach { assertFalse(claims.isOwner(it)) }
        assertTrue(claims.isOwner(tokens.last()))
    }
}
