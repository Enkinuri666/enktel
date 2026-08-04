package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.enktel.app.data.epg.XmltvParser

/**
 * XMLTV timestamps, including the three legal spellings that used to parse to
 * 0 and drop the programme into January 1970 — where it vanishes from the guide
 * rather than failing visibly.
 */
class XmltvTimeTest {

    /** 2026-07-16 20:30:00 UTC. */
    private val utc = 1_784_233_800_000L

    @Test fun `space separated numeric offset`() {
        assertEquals(utc, XmltvParser.parseTime("20260716203000 +0000"))
    }

    @Test fun `offset is applied not ignored`() {
        // +0100 means the wall clock is an hour ahead of UTC, so the instant is
        // an hour earlier.
        assertEquals(utc - 3_600_000L, XmltvParser.parseTime("20260716203000 +0100"))
        assertEquals(utc + 3_600_000L, XmltvParser.parseTime("20260716203000 -0100"))
    }

    @Test fun `bare Z means UTC`() {
        assertEquals(utc, XmltvParser.parseTime("20260716203000 Z"))
        assertEquals(utc, XmltvParser.parseTime("20260716203000Z"))
    }

    @Test fun `offset without a separating space`() {
        assertEquals(utc, XmltvParser.parseTime("20260716203000+0000"))
        assertEquals(utc - 7_200_000L, XmltvParser.parseTime("20260716203000+0200"))
    }

    @Test fun `named zone still works`() {
        assertEquals(utc, XmltvParser.parseTime("20260716203000 GMT"))
        assertEquals(utc, XmltvParser.parseTime("20260716203000 UTC"))
    }

    @Test fun `zoneless stamp is accepted`() {
        assertNotEquals(0L, XmltvParser.parseTime("20260716203000"))
    }

    @Test fun `junk returns zero rather than a wrong instant`() {
        assertEquals(0L, XmltvParser.parseTime(null))
        assertEquals(0L, XmltvParser.parseTime(""))
        assertEquals(0L, XmltvParser.parseTime("not a timestamp"))
        assertEquals(0L, XmltvParser.parseTime("2026071620"))
    }
}
