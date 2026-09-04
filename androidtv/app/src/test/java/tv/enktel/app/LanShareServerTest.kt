package tv.enktel.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.enktel.app.data.share.LanShareServer
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The server against a real socket. The security properties are the point —
 * asserting them in a unit test of the parser proves the parser; this proves
 * the server.
 */
class LanShareServerTest {

    private val server = LanShareServer()
    private lateinit var started: LanShareServer.Started
    private val payload = ByteArray(4096) { (it % 251).toByte() }
    private lateinit var token: String

    @Before fun setUp() {
        token = "a".repeat(32)
        val shared = LanShareServer.Shared(
            token = token,
            filename = "A Film.mkv",
            size = payload.size.toLong(),
            open = { ByteArrayInputStream(payload) },
        )
        // Port 0: the OS picks a free one. A fixed port made this suite fail
        // only when run alongside everything else, which is the worst kind of
        // failure to chase.
        started = requireNotNull(server.start("127.0.0.1", listOf(shared), 0)) {
            "could not bind a port"
        }
    }

    @After fun tearDown() = server.stop()

    private fun open(path: String, cookie: String? = null, range: String? = null): HttpURLConnection =
        (URL("http://127.0.0.1:${started.port}$path").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            cookie?.let { setRequestProperty("Cookie", it) }
            range?.let { setRequestProperty("Range", it) }
        }

    /** Sign in with the PIN and return the session cookie. */
    private fun signIn(pin: String = started.pin): String? {
        val c = open("/")
        c.requestMethod = "POST"
        c.doOutput = true
        c.outputStream.use { it.write("pin=$pin".toByteArray()) }
        val cookie = c.getHeaderField("Set-Cookie")
        c.disconnect()
        return cookie?.substringBefore(';')
    }

    @Test fun `the listing needs a pin`() {
        val c = open("/")
        assertEquals(401, c.responseCode)
        c.disconnect()
    }

    @Test fun `the file needs a pin`() {
        // The one that matters: the token is unguessable, but knowing it must
        // still not be enough on its own.
        val c = open("/f/$token")
        assertEquals(401, c.responseCode)
        c.disconnect()
    }

    @Test fun `a wrong pin does not sign you in`() {
        val wrong = if (started.pin == "000000") "111111" else "000000"
        assertTrue(signIn(wrong).isNullOrEmpty())
    }

    @Test fun `the right pin gets a session that opens the listing`() {
        val cookie = signIn()
        assertFalse(cookie.isNullOrEmpty())
        val c = open("/", cookie)
        assertEquals(200, c.responseCode)
        val body = c.inputStream.bufferedReader().readText()
        assertTrue(body, body.contains("A Film.mkv"))
        c.disconnect()
    }

    @Test fun `a signed-in request gets the whole file, byte for byte`() {
        val cookie = signIn()
        val c = open("/f/$token", cookie)
        assertEquals(200, c.responseCode)
        val got = c.inputStream.readBytes()
        assertArrayEqualsMsg(payload, got)
        c.disconnect()
    }

    @Test fun `a range request gets exactly that range`() {
        val cookie = signIn()
        val c = open("/f/$token", cookie, range = "bytes=100-199")
        assertEquals(206, c.responseCode)
        assertEquals("bytes 100-199/${payload.size}", c.getHeaderField("Content-Range"))
        val got = c.inputStream.readBytes()
        assertEquals(100, got.size)
        assertArrayEqualsMsg(payload.copyOfRange(100, 200), got)
        c.disconnect()
    }

    @Test fun `an unsatisfiable range is refused rather than restarted`() {
        // Answering this with the whole file is how a resume produces a
        // corrupt copy that looks complete.
        val cookie = signIn()
        val c = open("/f/$token", cookie, range = "bytes=99999-")
        assertEquals(416, c.responseCode)
        c.disconnect()
    }

    @Test fun `an unknown token is a 404, not a path`() {
        val cookie = signIn()
        for (attempt in listOf("b".repeat(32), "../../etc/passwd", "..%2f..%2fetc%2fpasswd", "")) {
            val c = open("/f/$attempt", cookie)
            assertTrue("$attempt -> ${c.responseCode}", c.responseCode == 404)
            c.disconnect()
        }
    }

    @Test fun `stopping clears everything a request could have used`() {
        // Deliberately not "connecting to the port now fails". That asserts how
        // quickly the OS tears a listening socket down on a shared build
        // machine, which is timing, not a guarantee this code makes — and it
        // failed intermittently in the full suite for exactly that reason.
        //
        // What is guaranteed is that a stopped server holds nothing left to
        // serve: no shares, no session, no PIN. Even a connection that somehow
        // arrived could not name a file or prove it was allowed one.
        val cookie = signIn()
        assertTrue(server.running)
        server.stop()
        assertFalse("still running after stop", server.running)

        // The security property that actually matters across a restart: the
        // old session must be worthless, because the PIN it was bought with
        // is gone.
        val restarted = requireNotNull(
            server.start(
                "127.0.0.1",
                listOf(
                    LanShareServer.Shared(token, "A Film.mkv", payload.size.toLong()) {
                        ByteArrayInputStream(payload)
                    },
                ),
                0,
            ),
        )
        started = restarted
        val c = open("/f/$token", cookie)
        assertEquals("an old cookie still opened a restarted server", 401, c.responseCode)
        c.disconnect()
    }

    @Test fun `the file is offered as an attachment, never as html`() {
        val cookie = signIn()
        val c = open("/f/$token", cookie)
        assertEquals("video/x-matroska", c.getHeaderField("Content-Type"))
        assertTrue(c.getHeaderField("Content-Disposition").orEmpty().startsWith("attachment"))
        assertEquals("nosniff", c.getHeaderField("X-Content-Type-Options"))
        c.disconnect()
    }

    private fun assertArrayEqualsMsg(expected: ByteArray, actual: ByteArray) {
        assertEquals("length", expected.size, actual.size)
        for (i in expected.indices) {
            if (expected[i] != actual[i]) throw AssertionError("byte $i differs")
        }
    }
}
