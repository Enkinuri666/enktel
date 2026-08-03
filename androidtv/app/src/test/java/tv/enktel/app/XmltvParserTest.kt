package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.epg.XmltvParser

/**
 * XMLTV timestamps decide where every programme lands in the guide, and
 * parseTime is called twice per programme — so it is both the hottest and the
 * most consequential pure function in the sync path.
 */
class XmltvParserTest {

    @Test fun `parses a timestamp with an explicit zone`() {
        // 2026-07-16 20:30:00 +0000
        assertEquals(1_784_233_800_000L, XmltvParser.parseTime("20260716203000 +0000"))
    }

    @Test fun `a non-UTC offset shifts the instant`() {
        val utc = XmltvParser.parseTime("20260716203000 +0000")
        val plusTwo = XmltvParser.parseTime("20260716203000 +0200")
        assertEquals(2 * 3_600_000L, utc - plusTwo)
    }

    @Test fun `handles surrounding whitespace`() {
        assertEquals(
            XmltvParser.parseTime("20260716203000 +0000"),
            XmltvParser.parseTime("  20260716203000 +0000  "),
        )
    }

    @Test fun `blank and malformed values yield zero rather than throwing`() {
        assertEquals(0L, XmltvParser.parseTime(null))
        assertEquals(0L, XmltvParser.parseTime(""))
        assertEquals(0L, XmltvParser.parseTime("   "))
        assertEquals(0L, XmltvParser.parseTime("not-a-timestamp"))
    }
}
