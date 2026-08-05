package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.EagleTrialClient
import okhttp3.OkHttpClient

/**
 * The trial signup broke on shape, not on logic, and nothing noticed.
 *
 * The app posts `{device_id, duration_hours, client, version}` and reads a
 * flat `{server_url, username, password, expires_at}` back. enktel.tv's
 * `/api/trial` required `{name, email}` and answered with a nested
 * `{subscription: {...}}` that carried no server field at all — so the button
 * returned HTTP 400 on every device, and would still have failed on the
 * response even if it had got that far. Both ends now speak both shapes;
 * these pin that down.
 */
class EagleTrialClientTest {
    private val client = EagleTrialClient(OkHttpClient())

    @Test
    fun `parses the flat panel shape`() {
        val creds = client.parseTrialResponse(
            """{"server_url":"http://api.elg-26.com","username":"u1","password":"p1",
               "expires_at":1786000000000}""",
        )
        assertEquals("http://api.elg-26.com", creds.serverUrl)
        assertEquals("u1", creds.username)
        assertEquals("p1", creds.password)
        assertEquals(1786000000000L, creds.expiresAt)
    }

    @Test
    fun `parses a data-wrapped panel response with Xtream exp_date seconds`() {
        val creds = client.parseTrialResponse(
            """{"data":{"server":"http://panel.example:8080","username":"u2",
               "password":"p2","exp_date":1786000000}}""",
        )
        assertEquals("http://panel.example:8080", creds.serverUrl)
        // exp_date is seconds; the app works in milliseconds throughout.
        assertEquals(1786000000000L, creds.expiresAt)
    }

    @Test
    fun `parses the website's nested subscription envelope`() {
        val creds = client.parseTrialResponse(
            """{"subscription":{"id":"ENK-1","username":"u3","password":"p3",
               "serverUrl":"http://api.elg-26.com",
               "m3uUrl":"http://api.elg-26.com/get.php?username=u3&password=p3",
               "endDate":"2026-08-06T00:00:00.000Z"}}""",
        )
        assertEquals("http://api.elg-26.com", creds.serverUrl)
        assertEquals("u3", creds.username)
        assertEquals("p3", creds.password)
    }

    @Test
    fun `recovers the panel host from m3uUrl when no server field is present`() {
        val creds = client.parseTrialResponse(
            """{"subscription":{"username":"u4","password":"p4",
               "m3uUrl":"http://api.elg-26.com/get.php?username=u4&password=p4&type=m3u_plus"}}""",
        )
        assertEquals("http://api.elg-26.com", creds.serverUrl)
    }

    @Test
    fun `prefers a top-level server_url over the nested envelope`() {
        // The fixed endpoint returns both, so the flat fields must win —
        // otherwise the app would read the envelope and ignore the answer
        // written for it.
        val creds = client.parseTrialResponse(
            """{"server_url":"http://flat.example","username":"flat","password":"fp",
               "expires_at":123,
               "subscription":{"username":"nested","password":"np",
               "serverUrl":"http://nested.example"}}""",
        )
        assertEquals("http://flat.example", creds.serverUrl)
        assertEquals("flat", creds.username)
    }

    @Test
    fun `defaults expiry to roughly 24 hours out when the server omits it`() {
        val before = System.currentTimeMillis()
        val creds = client.parseTrialResponse(
            """{"server_url":"http://x.example","username":"u","password":"p"}""",
        )
        val day = 24 * 60 * 60_000L
        assertTrue(
            "expiry should land about a day ahead, was ${creds.expiresAt - before}",
            creds.expiresAt - before in (day - 5_000)..(day + 5_000),
        )
    }

    @Test(expected = Exception::class)
    fun `rejects a response with no credentials at all`() {
        client.parseTrialResponse("""{"error":"nope"}""")
    }

    @Test(expected = Exception::class)
    fun `rejects an empty body`() {
        client.parseTrialResponse("")
    }
}
