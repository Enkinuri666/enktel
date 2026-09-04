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
        // Port 0 is not offered by start(), so try a few high ones to avoid a
        // collision with anything else on the build machine.
        var s: LanShareServer.Started? = null
        for (p in 18787..18797) {
            s = server.start("127.0.0.1", listOf(shared), p)
            if (s != null) break
        }
        started = requireNotNull(s) { "could not bind a port" }
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

    @Test fun `nothing is served once the server stops`() {
        val cookie = signIn()
        server.stop()
        val failed = runCatching { open("/f/$token", cookie).responseCode }.isFailure
        assertTrue("server still answering after stop", failed)
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
