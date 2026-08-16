package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.ConnectionSlot
import tv.enktel.app.player.ZapPlan

class ConnectionSlotTest {

    /**
     * The case the feature exists for: viewer walks away from the television
     * with nothing keeping playback alive on purpose.
     */
    @Test
    fun `leaving the app hands the session back`() {
        assertTrue(
            ConnectionSlot.shouldReleaseOnBackground(
                backgroundAudio = false, pictureInPicture = false, docked = false,
            )
        )
    }

    /**
     * Three ways playback legitimately continues off-screen, each an explicit
     * choice by the viewer. Releasing under any of them would be breaking one
     * feature to fix another.
     */
    @Test
    fun `deliberate background playback keeps its session`() {
        assertFalse(
            ConnectionSlot.shouldReleaseOnBackground(
                backgroundAudio = true, pictureInPicture = false, docked = false,
            )
        )
        assertFalse(
            ConnectionSlot.shouldReleaseOnBackground(
                backgroundAudio = false, pictureInPicture = true, docked = false,
            )
        )
        assertFalse(
            ConnectionSlot.shouldReleaseOnBackground(
                backgroundAudio = false, pictureInPicture = false, docked = true,
            )
        )
    }

    /**
     * Any one of them is enough on its own — this is an opt-out, so the checks
     * must not need agreement to take effect.
     */
    @Test
    fun `any single reason to keep playing is enough`() {
        for (bg in listOf(true, false)) for (pip in listOf(true, false)) for (dock in listOf(true, false)) {
            val release = ConnectionSlot.shouldReleaseOnBackground(bg, pip, dock)
            assertEquals("bg=$bg pip=$pip dock=$dock", !(bg || pip || dock), release)
        }
    }

    /**
     * The deliberate divergence from [ZapPlan.needsReleaseBeforeAcquire].
     *
     * That rule is gated on the cap because a teardown there costs the next
     * tune a handshake, so uncapped lines should not pay it. Stopping watching
     * has no next tune to slow down, so the cap is irrelevant and this rule
     * must not consult it — including for the uncapped and unknown cases where
     * ZapPlan deliberately declines.
     */
    @Test
    fun `releasing on background does not depend on the cap`() {
        // ZapPlan declines for 0 (unknown) and for anything above 2.
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(0))
        assertFalse(ZapPlan.needsReleaseBeforeAcquire(8))
        // This rule releases regardless — there is no cap parameter to pass.
        assertTrue(
            ConnectionSlot.shouldReleaseOnBackground(
                backgroundAudio = false, pictureInPicture = false, docked = false,
            )
        )
    }

    @Test
    fun `at capacity when the line is full`() {
        assertEquals(true, ConnectionSlot.atCapacity(activeConnections = 1, maxConnections = 1))
        assertEquals(true, ConnectionSlot.atCapacity(activeConnections = 2, maxConnections = 2))
        // Panels have been seen to report more sessions than the cap allows.
        assertEquals(true, ConnectionSlot.atCapacity(activeConnections = 3, maxConnections = 2))
        assertEquals(false, ConnectionSlot.atCapacity(activeConnections = 0, maxConnections = 1))
        assertEquals(false, ConnectionSlot.atCapacity(activeConnections = 1, maxConnections = 2))
    }

    /**
     * "Cannot tell" must stay distinct from "no". Claiming a line is at its
     * limit when the panel never published one sends the viewer hunting for a
     * device that is not streaming.
     */
    @Test
    fun `unknown capacity is null, not false`() {
        // Panel published no cap — every plain M3U line, and many Xtream ones.
        assertNull(ConnectionSlot.atCapacity(activeConnections = 1, maxConnections = 0))
        assertNull(ConnectionSlot.atCapacity(activeConnections = 0, maxConnections = 0))
        assertNull(ConnectionSlot.atCapacity(activeConnections = 1, maxConnections = -1))
        // Not asked yet, or the call failed — AccountBanner's -1.
        assertNull(ConnectionSlot.atCapacity(activeConnections = -1, maxConnections = 1))
        assertNull(ConnectionSlot.atCapacity(activeConnections = -1, maxConnections = 0))
    }

    /**
     * The honest limit of the feature. Nothing the app can call ends a session
     * on another device, so a line that is full while this device is idle is a
     * situation to explain, not one to offer a button for.
     */
    @Test
    fun `only a session held here can be freed from here`() {
        assertTrue(ConnectionSlot.canFreeFromHere(playingHere = true))
        assertFalse(ConnectionSlot.canFreeFromHere(playingHere = false))
    }
}
