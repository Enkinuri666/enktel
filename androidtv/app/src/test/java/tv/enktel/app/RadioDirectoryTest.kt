package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.RadioDirectory
import tv.enktel.app.data.net.RadioDirectory.toChannel

class RadioDirectoryTest {

    // Trimmed from a real de1.api.radio-browser.info response, including the
    // awkward cases: a thirty-tag station, a dead entry, a duplicate stream
    // under a different name, and one with no country.
    private val body = """
    [
      {"name":"102.7 KIIS FM","url":"http://x/1","url_resolved":"https://stream.example/kiis",
       "countrycode":"US","tags":"pop,top 40","codec":"AAC","bitrate":128,
       "favicon":"https://example/kiis.png","lastcheckok":1},
      {"name":"Classic Vinyl HD","url":"http://x/2","url_resolved":"https://icecast.example/classic",
       "countrycode":"US","tags":"1930,1940,big band,classic hits,jazz,lounge,oldies,swing",
       "codec":"MP3","bitrate":320,"lastcheckok":1},
      {"name":"BBC Radio 1","url":"http://x/3","url_resolved":"https://stream.example/bbc1",
       "countrycode":"GB","tags":"pop,chart","codec":"AAC","bitrate":96,"lastcheckok":1},
      {"name":"Kiss FM (duplicate listing)","url":"http://x/4","url_resolved":"https://stream.example/kiis",
       "countrycode":"US","tags":"pop","codec":"AAC","bitrate":128,"lastcheckok":1},
      {"name":"Dead Station","url":"http://x/5","url_resolved":"https://gone.example/s",
       "countrycode":"DE","tags":"rock","codec":"MP3","bitrate":128,"lastcheckok":0},
      {"name":"No URL Station","url":"","url_resolved":"","countrycode":"FR",
       "tags":"news","codec":"MP3","bitrate":128,"lastcheckok":1},
      {"name":"Hip Hop Nation","url":"http://x/7","url_resolved":"https://stream.example/hhn",
       "countrycode":"","tags":"hip hop,rap","codec":"AAC","bitrate":128,"lastcheckok":1},
      {"name":"Radio Beograd 1","url":"http://x/8","url_resolved":"https://stream.example/rb1",
       "countrycode":"RS","tags":"news,talk","codec":"MP3","bitrate":128,"lastcheckok":1}
    ]
    """.trimIndent()

    @Test
    fun `drops dead, urlless and duplicate stations`() {
        val s = RadioDirectory.parse(body)
        assertEquals(listOf(
            "102.7 KIIS FM", "Classic Vinyl HD", "BBC Radio 1", "Hip Hop Nation", "Radio Beograd 1",
        ), s.map { it.name })
    }

    @Test
    fun `hip hop does not get filed under pop`() {
        // "pop" appears inside "hip hop" — a substring match would put every
        // rap station in Pop, which is why matching is per-tag.
        val s = RadioDirectory.parse(body).first { it.name == "Hip Hop Nation" }
        assertEquals("Hip-Hop & R&B", s.genre)
    }

    @Test
    fun `picks the most significant bucket from a long tag list`() {
        val s = RadioDirectory.parse(body).first { it.name == "Classic Vinyl HD" }
        // jazz outranks oldies in the bucket order, and both beat "1930"
        assertEquals("Jazz & Blues", s.genre)
    }

    @Test
    fun `news and talk beats nothing else in the list`() {
        assertEquals("News & Talk", RadioDirectory.genreOf("news,talk"))
        assertEquals("Sport", RadioDirectory.genreOf("sports,football"))
        assertEquals("Classical", RadioDirectory.genreOf("classical"))
    }

    @Test
    fun `untagged stations land in Other rather than being dropped`() {
        assertEquals("Other", RadioDirectory.genreOf(""))
        assertEquals("Other", RadioDirectory.genreOf("   "))
    }

    @Test
    fun `country codes become readable names`() {
        val s = RadioDirectory.parse(body)
        assertEquals("United Kingdom", s.first { it.name == "BBC Radio 1" }.country)
        assertEquals("Serbia", s.first { it.name == "Radio Beograd 1" }.country)
        // Unknown codes pass through rather than showing blank.
        assertEquals("ZZ", RadioDirectory.countryName("zz"))
    }

    @Test
    fun `genre and country chips are ordered by size`() {
        val s = RadioDirectory.parse(body)
        val genres = RadioDirectory.genres(s)
        assertTrue("expected chips, got $genres", genres.isNotEmpty())
        // Descending by count, so the biggest bucket is offered first.
        assertTrue(genres.zipWithNext().all { (a, b) -> a.second >= b.second })

        val countries = RadioDirectory.countries(s)
        assertTrue(countries.zipWithNext().all { (a, b) -> a.second >= b.second })
        // The station with a blank country code is not offered as a chip.
        assertTrue(countries.none { it.first.isBlank() })
    }

    @Test
    fun `converting to a channel keeps the stream and marks it radio`() {
        val s = RadioDirectory.parse(body).first()
        val ch = with(RadioDirectory) { s.toChannel(profileId = 7L, index = 0) }
        assertEquals("https://stream.example/kiis", ch.url)
        assertTrue(ch.isRadio)
        assertEquals(7L, ch.profileId)
        assertEquals("Pop", ch.categoryName)
        // No EPG id: inventing one would send the guide hunting for listings
        // that cannot exist for an internet radio stream.
        assertEquals("", ch.epgId)
    }

    @Test
    fun `channel keys are unique across a result set`() {
        val s = RadioDirectory.parse(body)
        val keys = with(RadioDirectory) { s.mapIndexed { i, st -> st.toChannel(1L, i).key } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `a malformed body yields nothing rather than throwing`() {
        assertEquals(emptyList<RadioDirectory.Station>(), RadioDirectory.parse("{\"not\":\"an array\"}"))
    }
}
