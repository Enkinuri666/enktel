package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.epg.ProgrammeGenres

class ProgrammeGenresTest {

    @Test
    fun `keeps ordinary categories in the order the guide gave them`() {
        assertEquals("Movie, Drama", ProgrammeGenres.normalise(listOf("Movie", "Drama")))
    }

    @Test
    fun `nothing usable yields blank rather than punctuation`() {
        // Every caller treats "" as "no genre". Returning ", " or a stray
        // separator would render as a label with nothing in it.
        assertEquals("", ProgrammeGenres.normalise(emptyList()))
        assertEquals("", ProgrammeGenres.normalise(listOf("", "   ")))
        assertEquals("", ProgrammeGenres.normalise(listOf("-", "//", "|")))
    }

    @Test
    fun `bare DVB numeric codes are dropped`() {
        // Guides emit the numeric genre code alongside, or instead of, a name.
        // "16" is meaningful to a decoder and meaningless on screen.
        assertEquals("Drama", ProgrammeGenres.normalise(listOf("16", "Drama", "0x20")))
    }

    @Test
    fun `a synopsis in the wrong element is not shown as a genre`() {
        // Seen in real guides: the whole description repeated inside
        // <category>. Printing it under the title is worse than printing
        // nothing, and it would push the title off the row.
        val essay = "A retired detective returns to the coastal town he grew up in"
        assertEquals("Crime", ProgrammeGenres.normalise(listOf(essay, "Crime")))
    }

    @Test
    fun `the same genre in three spellings appears once`() {
        // The single most common shape in the wild: one idea, three casings,
        // because the guide was assembled from three upstream feeds.
        assertEquals("Drama", ProgrammeGenres.normalise(listOf("drama", "DRAMA", "Drama")))
    }

    @Test
    fun `shouting and mumbling are both normalised for display`() {
        assertEquals("Drama", ProgrammeGenres.normalise(listOf("DRAMA")))
        assertEquals("Drama", ProgrammeGenres.normalise(listOf("drama")))
        assertEquals("Science Fiction", ProgrammeGenres.normalise(listOf("SCIENCE FICTION")))
        // Hyphens start a new word, so this must not come out "Sci-fi".
        assertEquals("Sci-Fi", ProgrammeGenres.normalise(listOf("sci-fi")))
    }

    @Test
    fun `a deliberately cased value is left exactly as it came`() {
        // Mixed case means someone chose it. Re-casing "BBC News" to "Bbc News"
        // is the change that makes things worse.
        assertEquals("BBC News", ProgrammeGenres.normalise(listOf("BBC News")))
        assertEquals("iPlayer Exclusive", ProgrammeGenres.normalise(listOf("iPlayer Exclusive")))
    }

    @Test
    fun `a programme tagged with everything is capped`() {
        val many = listOf("Drama", "Crime", "Thriller", "Mystery", "Series", "TV", "Fiction")
        val out = ProgrammeGenres.normalise(many)
        assertEquals(ProgrammeGenres.MAX, out.split(", ").size)
        // The cap keeps the first ones rather than an arbitrary subset: the
        // guide lists the most specific category first.
        assertEquals("Drama, Crime, Thriller", out)
    }

    @Test
    fun `the cap counts distinct genres, not raw elements`() {
        // Deduplication happens before the cap, so a guide that repeats itself
        // still fills the line with three real genres rather than one.
        val out = ProgrammeGenres.normalise(
            listOf("Drama", "DRAMA", "drama", "Crime", "CRIME", "Thriller"),
        )
        assertEquals("Drama, Crime, Thriller", out)
    }

    @Test
    fun `surrounding separators from a bad export are stripped`() {
        assertEquals("Drama, Crime", ProgrammeGenres.normalise(listOf(" Drama, ", "/Crime/")))
    }

    @Test
    fun `output never contains a line break`() {
        // It is rendered on one line beside the title; a newline from a
        // malformed guide would break the row layout.
        val out = ProgrammeGenres.normalise(listOf("Drama\nCrime", "Comedy"))
        assertTrue(out, !out.contains('\n'))
    }
}
