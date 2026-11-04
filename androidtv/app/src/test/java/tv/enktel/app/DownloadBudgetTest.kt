package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.download.DownloadBudget

/**
 * How many downloads a line can actually carry at once.
 *
 * > Download speeds and connections keep getting metered and slow down,
 * > having users manually pause and continue downloads every few seconds.
 *
 * Nothing limited concurrency. "Download entire series" started every
 * episode, 60 ms apart, and each opened up to four ranged GETs — eighty
 * sockets at a panel that sells two. The surplus is refused the instant it
 * opens, the downloader reads that as a connection cap and ratchets each
 * download down to a single stream, and the line spends itself on handshakes
 * for sockets about to be hung up on.
 *
 * Pausing some by hand freed the line and the rest sped up. That is the
 * workaround in the report, and it is the viewer operating this by hand.
 */
class DownloadBudgetTest {

    @Test
    fun `a two-connection line downloads one file on one stream`() {
        // The common reseller plan. One connection is held back so pressing
        // play on live TV is never refused by the viewer's own downloads,
        // which leaves exactly one for downloading.
        val plan = DownloadBudget.plan(maxConnections = 2)
        assertEquals(1, plan.concurrent)
        assertEquals(1, plan.streamsEach)
    }

    @Test
    fun `a one-connection line still gets a download`() {
        // Reserving from a cap of one would leave zero, and a download that
        // can never start is worse than one that competes with the player.
        val plan = DownloadBudget.plan(maxConnections = 1)
        assertEquals(1, plan.concurrent)
        assertEquals(1, plan.streamsEach)
    }

    @Test
    fun `a line that publishes no cap keeps the long-standing four`() {
        // Every plain M3U line and plenty of Xtream panels report nothing.
        // Inventing a reservation against a number nobody stated would make
        // those lines slower than they have always been.
        val plan = DownloadBudget.plan(maxConnections = 0)
        assertEquals(1, plan.concurrent)
        assertEquals(DownloadBudget.DEFAULT_BUDGET, plan.streamsEach)
        assertEquals(DownloadBudget.plan(0), DownloadBudget.plan(-3))
    }

    @Test
    fun `one file at full width beats five files at a fifth`() {
        // The heart of it. On a four-connection line both finish the season
        // at the same moment, but one of them has the first episode on disk
        // in minutes rather than all of them arriving at the end.
        val plan = DownloadBudget.plan(maxConnections = 5)
        assertEquals(1, plan.concurrent)
        assertEquals(4, plan.streamsEach)
    }

    @Test
    fun `a line with room to spare runs a second download`() {
        // Only once one download cannot use the budget on its own.
        val plan = DownloadBudget.plan(maxConnections = 9)
        assertEquals(2, plan.concurrent)
        assertEquals(DownloadBudget.MAX_STREAMS, plan.streamsEach)
    }

    @Test
    fun `no plan ever asks for more sockets than the line sells`() {
        // The invariant the whole thing exists for. Everything above is a
        // judgement call; this is the one that must hold for every input.
        for (cap in 1..24) {
            val plan = DownloadBudget.plan(cap)
            assertTrue("cap=$cap plan=$plan", plan.concurrent >= 1 && plan.streamsEach >= 1)
            assertTrue(
                "cap=$cap asks for ${plan.sockets} sockets",
                plan.sockets <= maxOf(cap, 1),
            )
        }
    }

    @Test
    fun `a bigger line is never served worse than a smaller one`() {
        var previous = 0
        for (cap in 1..24) {
            val sockets = DownloadBudget.plan(cap).sockets
            assertTrue("cap=$cap went backwards: $previous -> $sockets", sockets >= previous)
            previous = sockets
        }
    }

    @Test
    fun `slots are what is left, never a negative`() {
        val plan = DownloadBudget.plan(9) // two concurrent
        assertEquals(2, DownloadBudget.slotsFree(plan, running = 0))
        assertEquals(1, DownloadBudget.slotsFree(plan, running = 1))
        assertEquals(0, DownloadBudget.slotsFree(plan, running = 2))
        // Start-up reconciliation can briefly believe more are running than a
        // plan allows; the pump must read that as "none free", not as a
        // negative it then loops on.
        assertEquals(0, DownloadBudget.slotsFree(plan, running = 7))
    }
}
