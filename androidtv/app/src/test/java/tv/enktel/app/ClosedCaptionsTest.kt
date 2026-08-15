package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.ClosedCaptions

/**
 * Language matching for live-TV closed captions.
 *
 * The interesting cases are all about how inconsistently real streams tag
 * themselves, which is not something that can be checked by reading the code —
 * it needs the actual codes written down.
 */
class ClosedCaptionsTest {

    @Test
    fun `the modes are the four the picker offers`() {
        assertEquals(
            listOf(ClosedCaptions.OFF, ClosedCaptions.AUTO, ClosedCaptions.ENGLISH, ClosedCaptions.CROATIAN),
            ClosedCaptions.MODES,
        )
        assertFalse(ClosedCaptions.enabled(ClosedCaptions.OFF))
        assertTrue(ClosedCaptions.enabled(ClosedCaptions.AUTO))
        assertFalse("an unknown mode must not count as on", ClosedCaptions.enabled("swahili"))
    }

    @Test
    fun `off asks for nothing`() {
        // Not merely empty — the engine keys the whole feature off this, and an
        // accidental non-empty list here would change VOD subtitle selection.
        assertTrue(ClosedCaptions.preferredLanguages(ClosedCaptions.OFF).isEmpty())
        assertFalse(ClosedCaptions.allowUndetermined(ClosedCaptions.OFF))
        assertFalse(ClosedCaptions.matches("en", ClosedCaptions.OFF))
    }

    @Test
    fun `croatian matches the withdrawn codes as well as the current ones`() {
        // The specific reason a Croatian channel can be captioned and still look
        // as though it is not: ex-Yugoslav muxes emit `scr`, a code withdrawn in
        // the 1990s, and a player matching only `hr` finds nothing.
        val langs = ClosedCaptions.preferredLanguages(ClosedCaptions.CROATIAN)
        listOf("hr", "hrv", "scr", "sh", "hbs").forEach {
            assertTrue("Croatian should be recognised as '$it'", it in langs)
        }
    }

    @Test
    fun `an exact croatian track always outranks an intelligible neighbour`() {
        val langs = ClosedCaptions.preferredLanguages(ClosedCaptions.CROATIAN)
        val worstCroatian = ClosedCaptions.CROATIAN_CODES.maxOf { langs.indexOf(it) }
        val bestNeighbour = ClosedCaptions.NEIGHBOURS.minOf { langs.indexOf(it) }
        assertTrue(
            "Serbian or Bosnian would be chosen over Croatian — the fallback has become a synonym",
            worstCroatian < bestNeighbour,
        )
    }

    @Test
    fun `croatian outranks english when croatian was asked for`() {
        val langs = ClosedCaptions.preferredLanguages(ClosedCaptions.CROATIAN)
        assertTrue(langs.indexOf("hr") < langs.indexOf("en"))
        // English still appears: a viewer who turned captions on is better served
        // by the wrong language than by silence, and can switch by hand.
        assertTrue("English should remain as a last resort", "en" in langs)
    }

    @Test
    fun `english mode leads with english`() {
        val langs = ClosedCaptions.preferredLanguages(ClosedCaptions.ENGLISH)
        assertEquals("en", langs.first())
        assertTrue("eng" in langs)
        assertTrue(langs.indexOf("en") < langs.indexOf("hr"))
    }

    @Test
    fun `automatic follows the device`() {
        assertEquals("hr", ClosedCaptions.preferredLanguages(ClosedCaptions.AUTO, "hr").first())
        assertEquals("en", ClosedCaptions.preferredLanguages(ClosedCaptions.AUTO, "en").first())
        // A device in neither language still gets captions rather than nothing.
        val other = ClosedCaptions.preferredLanguages(ClosedCaptions.AUTO, "de")
        assertTrue(other.isNotEmpty())
        assertTrue("en" in other && "hr" in other)
    }

    @Test
    fun `language tags are normalised the way streams actually write them`() {
        assertEquals("en", ClosedCaptions.normalise("EN"))
        assertEquals("en", ClosedCaptions.normalise("en-GB"))
        assertEquals("en", ClosedCaptions.normalise("en_US"))
        assertEquals("hr", ClosedCaptions.normalise("  HR  "))
        assertEquals("hr", ClosedCaptions.normalise("hr-HR"))
        assertEquals("", ClosedCaptions.normalise(null))
        assertEquals("", ClosedCaptions.normalise(""))
    }

    @Test
    fun `an untagged track is acceptable while captions are on`() {
        // The single behaviour that makes embedded captions work at all. CEA-608
        // has no field for a language, so every such track arrives untagged, and
        // a selector told to prefer "en" declines all of them.
        assertTrue(ClosedCaptions.allowUndetermined(ClosedCaptions.ENGLISH))
        assertTrue(ClosedCaptions.matches(null, ClosedCaptions.ENGLISH))
        assertTrue(ClosedCaptions.matches("", ClosedCaptions.CROATIAN))
        assertTrue(ClosedCaptions.matches("und", ClosedCaptions.CROATIAN))
    }

    @Test
    fun `a track in an unrelated language is not claimed as a match`() {
        assertFalse(ClosedCaptions.matches("de", ClosedCaptions.CROATIAN))
        assertFalse(ClosedCaptions.matches("fr", ClosedCaptions.ENGLISH))
        // But a case- or region-decorated tag still matches.
        assertTrue(ClosedCaptions.matches("HR-hr", ClosedCaptions.CROATIAN))
        assertTrue(ClosedCaptions.matches("eng", ClosedCaptions.ENGLISH))
    }

    @Test
    fun `both caption standards are declared`() {
        // These are the fallback for a stream whose PMT does not describe its
        // captions — the normal state of an IPTV re-mux. Media3 extracts nothing
        // at all in that case unless a list is supplied, so both standards go in:
        // which one the broadcaster used is exactly what the missing descriptor
        // would have told us.
        assertTrue("application/cea-608" in ClosedCaptions.TS_CAPTION_MIME_TYPES)
        assertTrue("application/cea-708" in ClosedCaptions.TS_CAPTION_MIME_TYPES)
        assertEquals(2, ClosedCaptions.TS_CAPTION_MIME_TYPES.size)
    }

    @Test
    fun `every mode has a label and none of them leak the raw code`() {
        ClosedCaptions.MODES.forEach { m ->
            val label = ClosedCaptions.label(m)
            assertTrue("mode '$m' has no label", label.isNotBlank())
            if (m != ClosedCaptions.OFF) {
                assertTrue("label for '$m' is just the code", label != m)
            }
        }
        assertEquals("Hrvatski", ClosedCaptions.label(ClosedCaptions.CROATIAN))
    }
}
