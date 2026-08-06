package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.enktel.app.data.repo.parseProxyKey

/**
 * Trailers were gated behind the user pasting a TMDB API key into Settings,
 * which essentially nobody does — so every trailer path returned null and
 * no-opped in silence. The lookup now falls back to enktel.tv's server-side
 * key; these pin the reading of its answer, including the shapes that mean
 * "no trailer" and must not be mistaken for one.
 */
class TrailerProxyTest {

    @Test
    fun `a normal answer yields the youtube id`() {
        val body = """{"key":"dQw4w9WgXcQ","name":"Official Trailer","site":"youtube","tmdb":550}"""
        assertEquals("dQw4w9WgXcQ", parseProxyKey(body))
    }

    @Test
    fun `a null key is not a trailer`() {
        assertNull(parseProxyKey("""{"key":null,"reason":"no trailer on TMDB","tmdb":550}"""))
    }

    @Test
    fun `an empty key is not a trailer`() {
        assertNull(parseProxyKey("""{"key":"","reason":""}"""))
    }

    @Test
    fun `an unconfigured server is not a trailer`() {
        assertNull(parseProxyKey("""{"key":null,"reason":"trailer lookup is not configured on the server"}"""))
    }

    @Test
    fun `garbage does not throw`() {
        assertNull(parseProxyKey("<html>502 Bad Gateway</html>"))
        assertNull(parseProxyKey(""))
        assertNull(parseProxyKey("[]"))
    }

    @Test
    fun `a numeric key is refused rather than stringified`() {
        // A key that arrives as a number is a malformed answer, and coercing it
        // would build a YouTube URL that cannot work.
        assertNull(parseProxyKey("""{"key":12345}"""))
    }
}
