package tv.enktel.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.metadata.ImdbLinks
import tv.enktel.app.data.metadata.OmdbClient
import tv.enktel.app.data.metadata.TmdbClient

/**
 * IMDb data arrives from two services that disagree about shape.
 *
 * TMDB puts the id in a different place for a film than for a series, and OMDb
 * returns every value — including the numbers — as a string, with thousands
 * separators in the vote count and the literal text "N/A" wherever it has
 * nothing. Both are the sort of thing that compiles perfectly and produces a
 * blank badge on a device.
 */
class ImdbTest {

    private fun json(text: String) =
        Json.parseToJsonElement(text) as JsonObject

    // ---- TMDB: where the id lives -------------------------------------

    @Test
    fun `a film reports its imdb id at the top level`() {
        val payload = json("""{"id":550,"imdb_id":"tt0137523","title":"Fight Club"}""")
        assertEquals("tt0137523", TmdbClient.imdbIdOf(payload))
    }

    /** A series carries it only under external_ids, which is why we append it. */
    @Test
    fun `a series reports its imdb id under external_ids`() {
        val payload = json("""{"id":1396,"external_ids":{"imdb_id":"tt0903747"}}""")
        assertEquals("tt0903747", TmdbClient.imdbIdOf(payload))
    }

    @Test
    fun `a title TMDB has no imdb mapping for yields nothing`() {
        assertEquals("", TmdbClient.imdbIdOf(json("""{"id":1,"imdb_id":null}""")))
        assertEquals("", TmdbClient.imdbIdOf(json("""{"id":1,"imdb_id":""}""")))
        assertEquals("", TmdbClient.imdbIdOf(json("""{"id":1}""")))
    }

    /** Anything that is not an IMDb id is a link to a 404, so it is refused. */
    @Test
    fun `a malformed id is not accepted`() {
        assertFalse(TmdbClient.looksLikeImdbId("nm0000138"))   // a person, not a title
        assertFalse(TmdbClient.looksLikeImdbId("tt123"))       // too short
        assertFalse(TmdbClient.looksLikeImdbId("0137523"))     // no prefix
        assertFalse(TmdbClient.looksLikeImdbId(""))
        assertTrue(TmdbClient.looksLikeImdbId("tt0137523"))
        assertTrue(TmdbClient.looksLikeImdbId("tt12345678"))   // newer, 8 digits
    }

    // ---- OMDb: everything is a string ---------------------------------

    @Test
    fun `a rating parses out of OMDb's all-strings payload`() {
        val r = OmdbClient.parseRating(
            """{"Title":"Fight Club","imdbRating":"8.8","imdbVotes":"2,357,891",
               "imdbID":"tt0137523","Response":"True"}""",
        )
        assertEquals("tt0137523", r?.imdbId)
        assertEquals(8.8, r?.rating ?: 0.0, 0.001)
        // The commas are OMDb's, and toInt() rejects them.
        assertEquals(2_357_891, r?.votes)
    }

    /** OMDb answers HTTP 200 for a title it does not have. */
    @Test
    fun `a not-found response is not a rating of zero`() {
        assertNull(OmdbClient.parseRating("""{"Response":"False","Error":"Incorrect IMDb ID."}"""))
    }

    /** "N/A" is OMDb's empty, and must not become 0.0. */
    @Test
    fun `an unrated title yields nothing rather than a zero`() {
        assertNull(
            OmdbClient.parseRating(
                """{"imdbID":"tt0137523","imdbRating":"N/A","imdbVotes":"N/A","Response":"True"}""",
            ),
        )
    }

    @Test
    fun `a missing vote count still yields the rating`() {
        val r = OmdbClient.parseRating(
            """{"imdbID":"tt0137523","imdbRating":"7.1","imdbVotes":"N/A","Response":"True"}""",
        )
        assertEquals(7.1, r?.rating ?: 0.0, 0.001)
        assertEquals(0, r?.votes)
    }

    @Test
    fun `a rating off IMDb's scale is a bad parse, not a bad film`() {
        assertNull(OmdbClient.parseRating("""{"imdbID":"tt0137523","imdbRating":"88","Response":"True"}"""))
        assertNull(OmdbClient.parseRating("""{"imdbID":"tt0137523","imdbRating":"0","Response":"True"}"""))
    }

    @Test
    fun `junk does not throw`() {
        assertNull(OmdbClient.parseRating(""))
        assertNull(OmdbClient.parseRating("<html>gateway timeout</html>"))
        assertNull(OmdbClient.parseRating("null"))
    }

    // ---- Deep links ----------------------------------------------------

    @Test
    fun `both destinations are built, app scheme first`() {
        assertEquals(
            listOf("imdb:///title/tt0137523/", "https://www.imdb.com/title/tt0137523/"),
            ImdbLinks.targets("tt0137523"),
        )
    }

    /** No id means no button, rather than a button onto a 404. */
    @Test
    fun `an unusable id produces no destinations`() {
        assertTrue(ImdbLinks.targets("").isEmpty())
        assertTrue(ImdbLinks.targets("N/A").isEmpty())
        assertNull(ImdbLinks.appUri("tt12"))
        assertNull(ImdbLinks.webUrl("tt12"))
    }

    @Test
    fun `surrounding whitespace does not break a link`() {
        assertEquals("https://www.imdb.com/title/tt0137523/", ImdbLinks.webUrl("  tt0137523 "))
    }
}
