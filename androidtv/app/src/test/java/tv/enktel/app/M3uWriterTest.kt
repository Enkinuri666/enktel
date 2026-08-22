package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.m3u.M3uParser
import tv.enktel.app.data.m3u.M3uWriter

/**
 * The writer is the inverse of the parser, so the test that matters is the
 * round trip. A field written but not read — or read but not written — is how
 * the radio flag went missing on the scraper side.
 */
class M3uWriterTest {

    private fun channel(
        id: Long = 1,
        name: String = "BBC One",
        url: String = "http://example.com/1.m3u8",
        epgId: String = "BBCOne.uk",
        logo: String = "http://example.com/1.png",
        group: String = "GB - Entertainment",
        num: Int = 101,
        radio: Boolean = false,
        userAgent: String = "",
    ) = Channel(
        key = "1:$id",
        profileId = 1,
        streamId = id,
        name = name,
        num = num,
        logo = logo,
        categoryId = group,
        categoryName = group,
        epgId = epgId,
        url = url,
        isRadio = radio,
        userAgent = userAgent,
    )

    private fun roundTrip(text: String) =
        M3uParser.parse(text.reader().buffered())

    @Test
    fun `attributes survive a round trip`() {
        val text = M3uWriter.write(listOf(channel()), epgUrl = "http://example.com/epg.xml")
        val parsed = roundTrip(text)

        assertEquals("http://example.com/epg.xml", parsed.epgUrl)
        assertEquals(1, parsed.entries.size)
        val e = parsed.entries[0]
        assertEquals("BBC One", e.name)
        assertEquals("BBCOne.uk", e.tvgId)
        assertEquals("http://example.com/1.png", e.logo)
        assertEquals("GB - Entertainment", e.group)
        assertEquals(101, e.chno)
        assertEquals("http://example.com/1.m3u8", e.url)
    }

    /** The flag whose loss put every station into Live TV. */
    @Test
    fun `the radio flag survives a round trip`() {
        val text = M3uWriter.write(listOf(channel(name = "BBC Radio 2", radio = true)))
        assertTrue(roundTrip(text).entries[0].isRadio)

        val tv = M3uWriter.write(listOf(channel()))
        assertFalse(roundTrip(tv).entries[0].isRadio)
    }

    @Test
    fun `a per-channel user agent survives a round trip`() {
        val text = M3uWriter.write(listOf(channel(userAgent = "VLC/3.0.20")))
        assertEquals("VLC/3.0.20", roundTrip(text).entries[0].userAgent)
    }

    /** An Xtream line stores no per-row URL, so the caller supplies one. */
    @Test
    fun `urlOf decides the written URL`() {
        val text = M3uWriter.write(listOf(channel(url = ""))) { "http://panel/live/u/p/1.ts" }
        assertEquals("http://panel/live/u/p/1.ts", roundTrip(text).entries[0].url)
    }

    /** A row with nowhere to point would produce a file that misparses. */
    @Test
    fun `channels with no URL are skipped`() {
        val text = M3uWriter.write(listOf(channel(url = ""), channel(id = 2)))
        assertEquals(1, roundTrip(text).entries.size)
    }

    /**
     * M3U has no escape for a quote inside an attribute: it simply ends the
     * value and corrupts every attribute after it on the line.
     */
    @Test
    fun `a quote in a name cannot break the following attributes`() {
        val text = M3uWriter.write(listOf(channel(name = "Hell\"s Kitchen", group = "US - Reality")))
        val e = roundTrip(text).entries[0]
        assertEquals("US - Reality", e.group)
        assertEquals("http://example.com/1.m3u8", e.url)
    }

    @Test
    fun `an empty list still writes a valid header`() {
        assertEquals("#EXTM3U\n", M3uWriter.write(emptyList()))
        assertEquals(0, roundTrip(M3uWriter.write(emptyList())).entries.size)
    }
}
