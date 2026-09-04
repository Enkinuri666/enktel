package tv.enktel.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.enktel.app.data.share.LanShareApi
import tv.enktel.app.data.share.LanShareServer
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The API the PC client speaks, against a real socket.
 *
 * The browser flow has its own suite; this one exists because the API added a
 * second way in — a bearer token instead of a cookie — and a second way in is
 * exactly the thing that quietly turns out not to be checked. Every route is
 * asserted to refuse an unpaired caller, and the remote-control routes are
 * asserted to refuse an unknown id, because "pause download 4" arriving from
 * the home network is the most powerful thing this server does.
 */
class LanShareRemoteTest {

    private val server = LanShareServer()
    private lateinit var started: LanShareServer.Started
    private val fileToken = "f".repeat(32)

    /** Records what it was told to do, so the assertions can be about the wire. */
    private class FakeRemote : LanShareApi.Remote {
        val calls = mutableListOf<Pair<String, LanShareApi.Action>>()
        var jobs = listOf(
            LanShareApi.Job(
                id = "job-1", title = "A Film", subtitle = "", status = "RUNNING",
                progressPct = 40, sizeBytes = 100, downloadedBytes = 40,
                speedBps = 1_024, error = "",
            ),
        )

        override fun jobs(): List<LanShareApi.Job> = jobs

        override fun act(id: String, action: LanShareApi.Action): Boolean {
            if (jobs.none { it.id == id }) return false
            calls += id to action
            return true
        }
    }

    private val remote = FakeRemote()

    @Before fun setUp() {
        val shared = LanShareServer.Shared(
            token = fileToken,
            filename = "A Film.mkv",
            size = 8,
            open = { ByteArrayInputStream(ByteArray(8)) },
        )
        started = requireNotNull(
            server.start(
                ip = "127.0.0.1",
                shared = listOf(shared),
                port = 0,
                remote = remote,
                deviceName = "Test Phone",
                appVersion = "9.9.9",
            ),
        ) { "could not bind a port" }
    }

    @After fun tearDown() = server.stop()

    private fun conn(path: String, bearer: String? = null): HttpURLConnection =
        (URL("http://127.0.0.1:${started.port}$path").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

    private fun post(path: String, body: String, bearer: String? = null): Pair<Int, String> {
        val c = conn(path, bearer)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText().orEmpty()
        c.disconnect()
        return code to text
    }

    private fun get(path: String, bearer: String? = null): Pair<Int, String> {
        val c = conn(path, bearer)
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText().orEmpty()
        c.disconnect()
        return code to text
    }

    /** Pair with the real PIN and return the bearer token. */
    private fun pairUp(): String {
        val (code, body) = post("/api/pair", """{"pin":"${started.pin}"}""")
        assertEquals(body, 200, code)
        return Regex("\"token\":\"([0-9a-f]+)\"").find(body)!!.groupValues[1]
    }

    // ── pairing ────────────────────────────────────────────────────────

    @Test fun `pairing with the right pin returns a token and names the device`() {
        val (code, body) = post("/api/pair", """{"pin":"${started.pin}"}""")
        assertEquals(200, code)
        assertTrue(body, body.contains("\"device\":\"Test Phone\""))
        assertTrue(body, body.contains("\"app\":\"9.9.9\""))
        assertTrue(body, body.contains("\"version\":1"))
    }

    @Test fun `pairing with the wrong pin is refused and hands out nothing`() {
        val wrong = if (started.pin == "000000") "111111" else "000000"
        val (code, body) = post("/api/pair", """{"pin":"$wrong"}""")
        assertEquals(401, code)
        assertFalse(body, body.contains("\"token\""))
    }

    @Test fun `a browser form post still pairs, so both clients share one route`() {
        val c = conn("/api/pair")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write("pin=${started.pin}".toByteArray()) }
        assertEquals(200, c.responseCode)
        c.disconnect()
    }

    // ── authorisation ──────────────────────────────────────────────────

    @Test fun `every api route refuses a caller that has not paired`() {
        assertEquals(401, get("/api/files").first)
        assertEquals(401, get("/api/downloads").first)
        assertEquals(401, post("/api/downloads/act", """{"id":"job-1","action":"pause"}""").first)
        // And the file itself, which is the one that actually costs something.
        assertEquals(401, get("/f/$fileToken").first)
    }

    @Test fun `a made-up bearer token is refused`() {
        val real = pairUp()
        assertEquals(401, get("/api/files", "0".repeat(real.length)).first)
        // A prefix of the real token must not pass either — the comparison
        // walks the whole thing rather than stopping at the first difference.
        assertEquals(401, get("/api/files", real.dropLast(1)).first)
        assertEquals(401, get("/api/files", "").first)
    }

    @Test fun `a token stops working once sharing is stopped`() {
        val bearer = pairUp()
        assertEquals(200, get("/api/files", bearer).first)
        server.stop()
        assertFalse(server.running)
    }

    @Test fun `a refusal on an api route is json, not a login page`() {
        // A client parsing HTML where it expected JSON reports "unexpected
        // token <", which tells whoever is holding the PC nothing at all.
        val (code, body) = get("/api/files")
        assertEquals(401, code)
        assertTrue(body, body.trimStart().startsWith("{"))
        assertTrue(body, body.contains("\"error\""))
    }

    // ── reading ────────────────────────────────────────────────────────

    @Test fun `a paired client can list the files and fetch one`() {
        val bearer = pairUp()
        val (code, body) = get("/api/files", bearer)
        assertEquals(200, code)
        assertTrue(body, body.contains("\"name\":\"A Film.mkv\""))
        assertTrue(body, body.contains("\"token\":\"$fileToken\""))

        val file = conn("/f/$fileToken", bearer)
        assertEquals(200, file.responseCode)
        assertEquals(8, file.inputStream.readBytes().size)
        file.disconnect()
    }

    @Test fun `the queue comes back with the fields the client draws`() {
        val (code, body) = get("/api/downloads", pairUp())
        assertEquals(200, code)
        assertTrue(body, body.contains("\"id\":\"job-1\""))
        assertTrue(body, body.contains("\"status\":\"RUNNING\""))
        assertTrue(body, body.contains("\"progressPct\":40"))
        assertTrue(body, body.contains("\"speedBps\":1024"))
    }

    // ── driving ────────────────────────────────────────────────────────

    @Test fun `each action reaches the queue exactly once`() {
        val bearer = pairUp()
        listOf("pause", "resume", "retry", "cancel").forEach { action ->
            val (code, body) = post(
                "/api/downloads/act", """{"id":"job-1","action":"$action"}""", bearer,
            )
            assertEquals(body, 200, code)
            assertTrue(body, body.contains("\"ok\":true"))
        }
        assertEquals(
            listOf(
                "job-1" to LanShareApi.Action.PAUSE,
                "job-1" to LanShareApi.Action.RESUME,
                "job-1" to LanShareApi.Action.RETRY,
                "job-1" to LanShareApi.Action.CANCEL,
            ),
            remote.calls,
        )
    }

    @Test fun `an unknown download is a 404, not a silent success`() {
        val (code, body) = post(
            "/api/downloads/act", """{"id":"nope","action":"pause"}""", pairUp(),
        )
        assertEquals(404, code)
        assertTrue(body, body.contains("\"ok\":false"))
        assertTrue(remote.calls.isEmpty())
    }

    @Test fun `an action the queue does not have is refused before it gets there`() {
        val bearer = pairUp()
        listOf("delete", "wipe", "", "PAUSE; DROP").forEach { action ->
            val (code, _) = post(
                "/api/downloads/act", """{"id":"job-1","action":"$action"}""", bearer,
            )
            assertEquals("accepted '$action'", 400, code)
        }
        assertTrue(remote.calls.toString(), remote.calls.isEmpty())
    }

    @Test fun `an unknown api route is a json 404 rather than a page`() {
        val (code, body) = get("/api/whatever", pairUp())
        assertEquals(404, code)
        assertTrue(body, body.contains("\"error\""))
    }
}
