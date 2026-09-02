package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.debrid.Magnets

class MagnetsTest {

    private val hex = "c9e15763f722f23e98a29decdfae341b98d53056"

    @Test
    fun `a plain magnet yields its hash and name`() {
        val m = Magnets.parse("magnet:?xt=urn:btih:$hex&dn=Some+Film+2022&tr=udp://tr.example:80")!!
        assertEquals(hex, m.infoHash)
        assertEquals("Some Film 2022", m.displayName)
    }

    @Test
    fun `the hash is lowercased because that is what the service expects`() {
        val m = Magnets.parse("magnet:?xt=urn:btih:${hex.uppercase()}")!!
        assertEquals(hex, m.infoHash)
    }

    @Test
    fun `a base32 hash is accepted and converted`() {
        // Plenty of links still use the 32-character spelling, and refusing
        // them would read to the viewer as the link being bad rather than as
        // this being fussy. Both spellings carry the same 160 bits.
        val b32 = "ZHQVOY7XELZD5GFCTXWN7LRUDOMNKMCW"
        val m = Magnets.parse("magnet:?xt=urn:btih:$b32")
        assertTrue("base32 hash was rejected", m != null)
        assertEquals(40, m!!.infoHash.length)
        assertTrue(m.infoHash.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `something that is not a magnet is null, not a half-built one`() {
        // A magnet with no infohash cannot be added and cannot be checked for
        // availability. Returning an object that looks usable would push the
        // failure to the point where it is least explicable.
        assertNull(Magnets.parse(""))
        assertNull(Magnets.parse("https://example.com/file.torrent"))
        assertNull(Magnets.parse("magnet:?dn=No+Hash+Here"))
        assertNull(Magnets.parse("magnet:?xt=urn:btih:tooshort"))
        assertNull(Magnets.parse("magnet:?xt=urn:sha1:$hex"))
    }

    @Test
    fun `surrounding whitespace does not stop it parsing`() {
        assertTrue(Magnets.isMagnet("  magnet:?xt=urn:btih:$hex  \n"))
    }

    @Test
    fun `a link with no name still parses`() {
        val m = Magnets.parse("magnet:?xt=urn:btih:$hex")!!
        assertEquals("", m.displayName)
        assertEquals(hex, m.infoHash)
    }

    @Test
    fun `a percent encoded name is readable`() {
        val m = Magnets.parse("magnet:?xt=urn:btih:$hex&dn=Ocean%27s%20Eleven")!!
        assertEquals("Ocean's Eleven", m.displayName)
    }

    @Test
    fun `a plus in the name is a space`() {
        // Magnet display names come from filenames with the spaces replaced,
        // so form decoding is the right reading here even though it is the
        // wrong one for a URL path.
        val m = Magnets.parse("magnet:?xt=urn:btih:$hex&dn=The+Batman+2022")!!
        assertEquals("The Batman 2022", m.displayName)
    }

    @Test
    fun `the v1 hash wins when a link carries several xt values`() {
        // A modern link often carries a v2 hash beside the v1 one. Only the
        // v1 btih is what the service indexes on.
        val m = Magnets.parse(
            "magnet:?xt=urn:btmh:1220abc&xt=urn:btih:$hex&dn=Thing",
        )!!
        assertEquals(hex, m.infoHash)
    }

    @Test
    fun `the original link is carried through unchanged`() {
        // The service is handed the link as given rather than one this
        // reassembled, so a tracker list or private flag is not quietly lost.
        val raw = "magnet:?xt=urn:btih:$hex&dn=Thing&tr=udp://a:80&tr=udp://b:80"
        assertEquals(raw, Magnets.parse(raw)!!.uri)
    }

    @Test
    fun `isMagnet agrees with parse`() {
        assertTrue(Magnets.isMagnet("magnet:?xt=urn:btih:$hex"))
        assertFalse(Magnets.isMagnet("not a magnet"))
    }
}
