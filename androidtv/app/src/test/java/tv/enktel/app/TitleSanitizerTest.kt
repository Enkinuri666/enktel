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

/**
 * The cases that were wrong before the sanitizer was reworked, plus the
 * titles that must survive it.
 *
 * Split out because the rule they encode is one-directional: a missed strip
 * leaves a title ugly, a wrong strip renames a film. Everything in
 * `must survive` is worth more than everything in the cleanup tests.
 */
class TitleSanitizerSceneTest {

    @Test fun `dot-separated scene names become readable`() {
        // Before: "The.Matrix.1999. . .x264-GROUP" — the strip passes punched
        // holes in the middle because a dot is not a word boundary, so the
        // output was measurably worse than the input.
        assertEquals("The Matrix 1999", TitleSanitizer.clean("The.Matrix.1999.1080p.BluRay.x264-GROUP"))
        assertEquals("Interstellar 2014", TitleSanitizer.clean("Interstellar.2014.2160p.UHD.BluRay.x265.HDR10"))
    }

    @Test fun `underscore-separated scene names become readable`() {
        assertEquals("Movie Name 2019", TitleSanitizer.clean("Movie_Name_2019_720p_WEB-DL_AAC"))
    }

    @Test fun `an initialism is not a scene name`() {
        // No spaces and two dots, but every segment is one character, so it is
        // punctuation rather than a word separator.
        assertEquals("W.A.R.", TitleSanitizer.clean("W.A.R."))
    }

    @Test fun `a title with spaces is never despaced`() {
        assertEquals("Mr. Robot S02E03", TitleSanitizer.clean("Mr. Robot S02E03"))
    }

    @Test fun `hyphenated titles keep their tail`() {
        // The release-group strip only runs on strings recognised as scene
        // names. Loose, it would turn Spider-Man into Spider.
        assertEquals("Spider-Man", TitleSanitizer.clean("Spider-Man"))
        assertEquals("Ant-Man and the Wasp", TitleSanitizer.clean("Ant-Man and the Wasp"))
        assertEquals("X-Men", TitleSanitizer.clean("X-Men"))
    }

    @Test fun `Dual is a film, not a language tag`() {
        // Bare `dual` used to be stripped, so this returned "2022".
        assertEquals("Dual 2022", TitleSanitizer.clean("Dual 2022"))
        // It is still junk when it qualifies something.
        assertEquals("Amelie (2001)", TitleSanitizer.clean("Amelie (2001) Dual Audio"))
    }

    @Test fun `codec and source tags are stripped`() {
        assertEquals("Sicario 2015", TitleSanitizer.clean("Sicario 2015 BRRip AC3"))
        assertEquals("Avatar The Way of Water 2022", TitleSanitizer.clean("Avatar The Way of Water 2022 HDTS"))
        assertEquals("Inception (2010)", TitleSanitizer.clean("Inception (2010) [1080p] [WEBRip] [x265]"))
    }

    @Test fun `three and four letter country prefixes are stripped`() {
        assertEquals("Breaking Bad S01E01", TitleSanitizer.clean("USA: Breaking Bad S01E01"))
        assertEquals("La Casa de Papel", TitleSanitizer.clean("ESP - La Casa de Papel"))
        assertEquals("Sport 1", TitleSanitizer.clean("EXYU| Sport 1"))
    }

    @Test fun `bracketed language prefixes are stripped`() {
        assertEquals("Top Gun Maverick", TitleSanitizer.clean("[EN] Top Gun Maverick"))
        assertEquals("Le Fabuleux Destin", TitleSanitizer.clean("(FR) Le Fabuleux Destin"))
    }

    @Test fun `channel names that look like country prefixes must survive`() {
        // The whole reason the long-prefix rule is an explicit list rather
        // than [A-Z]{3,4}: these are channel names, and a pattern cannot tell
        // them from a country code.
        assertEquals("MTV: Hits", TitleSanitizer.clean("MTV: Hits"))
        assertEquals("HBO: Originals", TitleSanitizer.clean("HBO: Originals"))
        assertEquals("TNT: Sports", TitleSanitizer.clean("TNT: Sports"))
    }

    @Test fun `HD and SD stay on channel names`() {
        // "BBC ONE HD" and "BBC ONE" are two different channels on most lines.
        assertEquals("BBC ONE HD", TitleSanitizer.clean("UK: BBC ONE HD"))
        assertEquals("ITV 1 SD", TitleSanitizer.clean("ITV 1 SD"))
    }

    @Test fun `edition markers are information, not noise`() {
        assertEquals("Blade Runner Final Cut", TitleSanitizer.clean("Blade Runner Final Cut 1080p"))
        assertEquals("Aliens Extended", TitleSanitizer.clean("Aliens Extended BluRay"))
        assertEquals("Dune IMAX", TitleSanitizer.clean("Dune IMAX 4K"))
    }

    @Test fun `US Marshals is not a country prefix`() {
        assertEquals("US Marshals", TitleSanitizer.clean("US Marshals"))
    }
}
