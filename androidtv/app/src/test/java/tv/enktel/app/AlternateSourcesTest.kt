package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.AlternateSources

/**
 * The index is fetched over the network and its URLs are handed straight to a
 * media player, so "whatever was in the JSON" is not a good enough contract.
 * Every case here is a shape the file can actually arrive in — including the
 * ones where it did not arrive at all.
 */
class AlternateSourcesTest {

    @Test
    fun `the index sits beside the playlist it belongs to`() {
        assertEquals(
            "https://enktel.tv/playlists/enktel-alternates.json",
            AlternateSources.indexUrlFor("https://enktel.tv/playlists/enktel-lineup.m3u"),
        )
    }

    /** An imported file has no sibling to look next to. */
    @Test
    fun `a local playlist has no index`() {
        assertNull(AlternateSources.indexUrlFor("file:///data/user/0/tv.enktel.app/files/a.m3u"))
        assertNull(AlternateSources.indexUrlFor(""))
        assertNull(AlternateSources.indexUrlFor("enktel-lineup.m3u"))
    }

    @Test
    fun `an index parses into channels and urls`() {
        val parsed = AlternateSources.parse(
            """{"HRT1.hr@SD":["https://a.example/1.m3u8","http://b.example/2.m3u8"],
               "BBCOne.uk":["https://c.example/3.m3u8"]}""",
        )
        assertEquals(2, parsed.size)
        assertEquals(
            listOf("https://a.example/1.m3u8", "http://b.example/2.m3u8"),
            parsed["HRT1.hr@SD"],
        )
    }

    /**
     * A media player will happily be handed a `javascript:` or `file:` URL.
     * Anything that is not http(s) is dropped rather than passed along.
     */
    @Test
    fun `only http urls survive`() {
        val parsed = AlternateSources.parse(
            """{"A":["javascript:alert(1)","file:///etc/passwd","https://ok.example/1.m3u8"],
               "B":["ftp://nope.example/x"]}""",
        )
        assertEquals(listOf("https://ok.example/1.m3u8"), parsed["A"])
        // Every entry dropped means the channel is dropped, not left empty.
        assertTrue(parsed["B"] == null)
    }

    @Test
    fun `a malformed index is empty rather than fatal`() {
        assertTrue(AlternateSources.parse("").isEmpty())
        assertTrue(AlternateSources.parse("<html>404</html>").isEmpty())
        assertTrue(AlternateSources.parse("[1,2,3]").isEmpty())
        assertTrue(AlternateSources.parse("""{"A":"not-a-list"}""").isEmpty())
        assertTrue(AlternateSources.parse("""{"A":[null,42]}""").isEmpty())
        assertTrue(AlternateSources.parse("""{"":["https://a.example/1"]}""").isEmpty())
    }

    @Test
    fun `duplicates within one channel collapse`() {
        val parsed = AlternateSources.parse(
            """{"A":["https://a.example/1","https://a.example/1","https://b.example/2"]}""",
        )
        assertEquals(listOf("https://a.example/1", "https://b.example/2"), parsed["A"])
    }

    // ---- storage round trip -------------------------------------------

    @Test
    fun `a list survives being stored and read back`() {
        val urls = listOf("https://a.example/1.m3u8", "https://b.example/2.ts")
        assertEquals(urls, AlternateSources.decode(AlternateSources.encode(urls)))
    }

    /** Every channel that has no alternates stores this, so it has to be cheap. */
    @Test
    fun `an empty column decodes to nothing`() {
        assertEquals(emptyList<String>(), AlternateSources.decode(""))
        assertEquals(emptyList<String>(), AlternateSources.decode("\n\n  \n"))
        assertEquals(emptyList<String>(), AlternateSources.encode(emptyList()).let(AlternateSources::decode))
    }
}
