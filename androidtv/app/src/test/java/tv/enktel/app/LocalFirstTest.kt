package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.m3u.LocalFirst

/**
 * Ordering the shared free-to-air lineup so the viewer's own country lands
 * first. 84% of that playlist is American; from anywhere else most of it is a
 * 403, and the channels that work were buried underneath it.
 */
class LocalFirstTest {

    @Test
    fun `countryOf reads the CC prefix build-lineup emits`() {
        assertEquals("AU", LocalFirst.countryOf("AU - Sports"))
        assertEquals("US", LocalFirst.countryOf("US - News"))
        assertEquals("", LocalFirst.countryOf("Undefined"))
        assertEquals("", LocalFirst.countryOf("Movies"))
        // Lower case is not the emitted form and must not be mistaken for one.
        assertEquals("", LocalFirst.countryOf("au - Sports"))
        assertEquals("", LocalFirst.countryOf(""))
        assertEquals("", LocalFirst.countryOf("A - B"))
    }

    @Test
    fun `rank puts local first, unlabelled next, foreign last`() {
        assertEquals(0, LocalFirst.rank("AU - Sports", "AU"))
        assertEquals(1, LocalFirst.rank("Undefined", "AU"))
        assertEquals(2, LocalFirst.rank("US - Sports", "AU"))
    }

    /** No country to sort by is not a reason to shuffle anything. */
    @Test
    fun `an unknown device country leaves the order untouched`() {
        val groups = listOf("US - News", "AU - Sports", "Undefined")
        assertEquals(groups, LocalFirst.sort(groups, "") { it })
    }

    @Test
    fun `sort brings the viewer's country to the top`() {
        val groups = listOf("US - News", "GB - Sports", "AU - Sports", "US - Movies", "AU - News")
        assertEquals(
            listOf("AU - Sports", "AU - News", "US - News", "GB - Sports", "US - Movies"),
            LocalFirst.sort(groups, "AU") { it },
        )
    }

    /** Within a band the lineup's own country/genre/name order must survive. */
    @Test
    fun `sort is stable within each band`() {
        val groups = listOf("US - News", "US - Sports", "US - Movies", "AU - News", "AU - Sports")
        assertEquals(
            listOf("AU - News", "AU - Sports", "US - News", "US - Sports", "US - Movies"),
            LocalFirst.sort(groups, "AU") { it },
        )
    }

    @Test
    fun `unlabelled groups sort above foreign ones`() {
        val groups = listOf("US - News", "Undefined", "AU - Sports")
        assertEquals(
            listOf("AU - Sports", "Undefined", "US - News"),
            LocalFirst.sort(groups, "AU") { it },
        )
    }
}
