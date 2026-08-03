package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.metadata.TitleSanitizer

class TitleSanitizerTest {

    @Test fun `blank input is returned untouched`() {
        assertEquals("", TitleSanitizer.clean(""))
        assertEquals("   ", TitleSanitizer.clean("   "))
    }

    @Test fun `a clean title is left alone`() {
        assertEquals("The Matrix (1999)", TitleSanitizer.clean("The Matrix (1999)"))
    }

    @Test fun `stripping everything falls back to the original`() {
        // Whatever the pattern list grows into, it must never hand back an
        // empty title — an unnamed row in a rail is worse than a noisy one.
        val junk = "|||"
        assertTrue(TitleSanitizer.clean(junk).isNotBlank())
    }

    @Test fun `programme titles lose a trailing broadcast stamp`() {
        assertEquals("ARENA ESPORT HD", TitleSanitizer.cleanProgramme("ARENA ESPORT HD 09:00 28-07-2026"))
        assertEquals("ARENA ESPORT HD", TitleSanitizer.cleanProgramme("ARENA ESPORT HD 28/07/2026"))
        assertEquals("ARENA ESPORT HD", TitleSanitizer.cleanProgramme("ARENA ESPORT HD 2026-07-28"))
    }

    @Test fun `a bare trailing time is not a broadcast stamp`() {
        // The date is what makes it junk. "Sky News At 10:00" is a real name
        // and must survive.
        assertEquals("Sky News At 10:00", TitleSanitizer.cleanProgramme("Sky News At 10:00"))
        assertEquals("News at Ten", TitleSanitizer.cleanProgramme("News at Ten"))
    }

    @Test fun `keywords drop separators and one-character tokens`() {
        val k = TitleSanitizer.keywords("The X-Files: Season 2 [HD]")
        assertTrue(k.contains("the"))
        assertTrue(k.contains("files"))
        assertTrue(k.contains("season"))
        assertTrue(k.contains("hd"))
        assertTrue("single characters carry no search signal", k.none { it.length < 2 })
    }
}
