package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.diag.CatchupScheme
import tv.enktel.app.data.diag.M3uAttrs

/**
 * Catch-up cannot be tested without first knowing how to build the request,
 * and that comes entirely from these attributes.
 */
class M3uAttrsTest {

    @Test fun `standard tvg attributes are read`() {
        val e = M3uAttrs.parseExtInf(
            """#EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://x/l.png" group-title="UK",BBC One HD""",
        )!!
        assertEquals("bbc1.uk", e.tvgId)
        assertEquals("BBC One", e.tvgName)
        assertEquals("http://x/l.png", e.tvgLogo)
        assertEquals("UK", e.groupTitle)
        assertEquals("BBC One HD", e.title)
    }

    @Test fun `catchup-days and timeshift are interchangeable`() {
        assertEquals(7, M3uAttrs.parseExtInf("""#EXTINF:-1 catchup-days="7",A""")!!.catchupDays)
        assertEquals(5, M3uAttrs.parseExtInf("""#EXTINF:-1 timeshift="5",A""")!!.catchupDays)
    }

    @Test fun `catchup and catchup-type are interchangeable`() {
        assertEquals("shift", M3uAttrs.parseExtInf("""#EXTINF:-1 catchup="shift",A""")!!.catchupType)
        assertEquals("append", M3uAttrs.parseExtInf("""#EXTINF:-1 catchup-type="append",A""")!!.catchupType)
    }

    @Test fun `an append template is recognised from its source`() {
        val e = M3uAttrs.parseExtInf(
            """#EXTINF:-1 catchup="append" catchup-source="?utc=${'$'}{start}&lutc=${'$'}{now}" catchup-days="3",A""",
        )!!
        assertEquals(CatchupScheme.APPEND, e.scheme)
        assertTrue(e.hasCatchup)
    }

    @Test fun `flussonic archive templates are recognised`() {
        val e = M3uAttrs.parseExtInf(
            """#EXTINF:-1 catchup="flussonic" catchup-source="http://x/ch/archive-${'$'}{start}-3600.m3u8",A""",
        )!!
        assertEquals(CatchupScheme.FLUSSONIC, e.scheme)
    }

    @Test fun `xtream timeshift templates are recognised`() {
        val e = M3uAttrs.parseExtInf(
            """#EXTINF:-1 catchup-source="http://x/timeshift/u/p/60/2026-01-01:00-00/9.ts",A""",
        )!!
        assertEquals(CatchupScheme.XTREAM_TIMESHIFT, e.scheme)
    }

    @Test fun `an entry with no catchup attributes reports none`() {
        val e = M3uAttrs.parseExtInf("""#EXTINF:-1 tvg-id="a",A""")!!
        assertFalse(e.hasCatchup)
        assertEquals(CatchupScheme.UNKNOWN, e.scheme)
    }

    @Test fun `a non-EXTINF line is not parsed`() {
        assertNull(M3uAttrs.parseExtInf("#EXTM3U"))
        assertNull(M3uAttrs.parseExtInf("http://example.com/a.ts"))
    }

    @Test fun `a missing attribute yields blank rather than throwing`() {
        assertEquals("", M3uAttrs.attr("""#EXTINF:-1 tvg-id="a",T""", "group-title"))
    }
}
