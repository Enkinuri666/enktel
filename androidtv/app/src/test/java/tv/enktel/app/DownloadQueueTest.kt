package tv.enktel.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import tv.enktel.app.data.db.DownloadEntry
import tv.enktel.app.data.db.Profile

/**
 * The queue has to drain, not just hold things back.
 *
 * [tv.enktel.app.data.download.DownloadBudget] decides how many downloads may
 * run at once, and its arithmetic is tested next door. This is the other
 * half, and the half that is dangerous: a gate that admits nothing, or that
 * forgets to admit the next one when a slot frees, turns "downloads are slow"
 * into "downloads never start" — which is a far worse bug than the one being
 * fixed.
 *
 * So this drives the real [tv.enktel.app.data.download.DownloadHub] against a
 * port nothing is listening on. Every download fails immediately (the ranged
 * probe is not retried), which is exactly what is wanted: it makes each slot
 * free within milliseconds and asks whether the pump keeps going.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DownloadQueueTest {

    /** Refused instantly rather than left to time out. */
    private val deadUrl = "http://127.0.0.1:1/film.mkv"

    /**
     * A server that accepts and then says nothing.
     *
     * Needed because a refused connection fails too fast to observe: the
     * first version of the concurrency test below passed with the gate taken
     * out, because no two downloads were ever in flight at the same instant
     * to be counted. A socket that hangs holds each download in RUNNING long
     * enough for the question to mean something.
     */
    private class StalledServer : AutoCloseable {
        private val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        private val held = java.util.Collections.synchronizedList(ArrayList<java.net.Socket>())
        @Volatile private var running = true
        val url: String get() = "http://127.0.0.1:${server.localPort}/film.mkv"

        init {
            Thread {
                while (running) {
                    try { held += server.accept() } catch (_: Throwable) { return@Thread }
                }
            }.apply { isDaemon = true }.start()
        }

        override fun close() {
            running = false
            runCatching { server.close() }
            synchronized(held) { held.forEach { runCatching { it.close() } } }
        }
    }

    /**
     * A graph whose downloads are allowed to run.
     *
     * Robolectric reports the network as metered, and the mobile flavour
     * defaults to Wi-Fi-only downloads, so without this every entry parks as
     * "Waiting for Wi-Fi" before the queue is ever consulted — which is how
     * the first version of these tests passed with the gate taken out.
     */
    private fun graph(): AppGraph =
        AppGraph(ApplicationProvider.getApplicationContext()).also {
            runBlocking { it.settings.setDownloadsWifiOnly(false) }
        }

    private fun seedProfile(g: AppGraph, maxConnections: Int): Long = runBlocking {
        g.db.profileDao().insert(
            Profile(
                name = "line", kind = "xtream", server = "https://example.invalid",
                username = "u", password = "p",
                expiresAt = System.currentTimeMillis() + 30L * 86_400_000,
                maxConnections = maxConnections,
            ),
        )
    }

    private fun entry(profileId: Long, n: Int, url: String = deadUrl) = DownloadEntry(
        id = "dl-$n", profileId = profileId, kind = "movie", refId = n.toLong(),
        title = "Film $n", sourceUrl = url, addedAt = n.toLong(),
    )

    /** Polls until [predicate] holds, or gives up — these run on real threads. */
    private fun awaitUntil(what: String, timeoutMs: Long = 20_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private fun statuses(g: AppGraph): List<String> = runBlocking {
        (1..5).mapNotNull { g.db.downloadDao().byId("dl-$it")?.status }
    }

    @Test
    fun `a season does not start every episode at once`() {
        // The reported behaviour: "Download entire series" started all of
        // them, each opening up to four sockets, at a panel selling two.
        StalledServer().use { server ->
            val g = graph()
            val p = seedProfile(g, maxConnections = 2)
            val hub = g.downloads

            hub.enqueueMany((1..5).map { entry(p, it, server.url) })

            // Wait until the gate has had every chance to let them through:
            // all five rows present, and one of them actually in flight.
            awaitUntil("five rows, one of them running") {
                val s = statuses(g)
                s.size == 5 && s.contains("RUNNING")
            }
            // A two-connection line allows exactly one download at a time.
            repeat(40) {
                val s = statuses(g)
                val running = s.count { it == "RUNNING" }
                assertTrue("$running in flight on a 2-connection line: $s", running <= 1)
                Thread.sleep(25)
            }
            assertEquals(
                "the rest should be waiting, not running",
                4,
                statuses(g).count { it == "QUEUED" },
            )
        }
    }

    @Test
    fun `a wider line really does run more than one`() {
        // The other side of the same measurement, so "one at a time" is shown
        // to be the plan being honoured rather than the hub only ever
        // managing one download whatever it is told.
        StalledServer().use { server ->
            val g = graph()
            val p = seedProfile(g, maxConnections = 9) // budget 8 -> 2 concurrent
            val hub = g.downloads

            hub.enqueueMany((1..5).map { entry(p, it, server.url) })
            awaitUntil("two downloads in flight") {
                statuses(g).count { it == "RUNNING" } == 2
            }
            repeat(20) {
                val running = statuses(g).count { it == "RUNNING" }
                assertTrue("$running in flight on a 9-connection line", running <= 2)
                Thread.sleep(25)
            }
        }
    }

    @Test
    fun `the queue drains when each download finishes`() {
        // The dangerous half. A pump that does not run again when a slot
        // frees leaves four of five downloads sitting in QUEUED for ever,
        // which from the outside is the app simply not downloading.
        val g = graph()
        val p = seedProfile(g, maxConnections = 2)
        val hub = g.downloads

        hub.enqueueMany((1..5).map { entry(p, it) })

        awaitUntil("every download reached a terminal state") {
            val s = statuses(g)
            s.size == 5 && s.none { it == "QUEUED" || it == "RUNNING" }
        }
        val s = statuses(g)
        assertEquals("all five should have been attempted", 5, s.size)
        assertTrue("none left waiting: $s", s.none { it == "QUEUED" })
    }

}
