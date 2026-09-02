package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.debrid.DebridSearch

class DebridSearchTest {

    private val batman = "The.Batman.2022.2160p.WEB-DL.x265-GROUP.mkv"
    private val bear = "The_Bear_S03E01_1080p.mkv"
    private val oceans = "Ocean's.Eleven.2001.1080p.mkv"

    @Test
    fun `typing spaces finds a name written with dots`() {
        // The reason this class exists. Debrid files are named the way release
        // files are named, so a plain contains() on "the batman" matches
        // nothing — a search box that looks like it works and never finds
        // anything is worse than no search box.
        assertTrue(DebridSearch.matches(batman, "the batman"))
        assertTrue(DebridSearch.matches(bear, "the bear"))
    }

    @Test
    fun `word order does not matter`() {
        // Nobody remembers whether the year came before the resolution.
        assertTrue(DebridSearch.matches(batman, "batman 2022"))
        assertTrue(DebridSearch.matches(batman, "2022 batman"))
        assertTrue(DebridSearch.matches(batman, "x265 the batman"))
    }

    @Test
    fun `a half typed word still narrows`() {
        // Typing on a remote is slow, so the list has to react before the word
        // is finished or the box is not worth opening.
        assertTrue(DebridSearch.matches(batman, "bat"))
        assertTrue(DebridSearch.matches(batman, "the batm"))
    }

    @Test
    fun `every word has to appear, not just one`() {
        // "batman superman" must not match a file that only contains one of
        // them, or the filter stops narrowing as you type.
        assertFalse(DebridSearch.matches(batman, "batman superman"))
        assertFalse(DebridSearch.matches(batman, "the bear"))
    }

    @Test
    fun `a blank query is not a filter`() {
        assertTrue(DebridSearch.matches(batman, ""))
        assertTrue(DebridSearch.matches(batman, "   "))
        assertTrue(DebridSearch.matches(batman, "..."))
    }

    @Test
    fun `an apostrophe stays part of the word`() {
        // Splitting on it would make "Ocean's" match a search for "ocean s",
        // which is not something anyone typed.
        assertTrue(DebridSearch.matches(oceans, "ocean's eleven"))
        assertTrue(DebridSearch.matches(oceans, "ocean"))
        assertFalse(DebridSearch.matches(oceans, "ocean s eleven"))
    }

    @Test
    fun `case is ignored on both sides`() {
        assertTrue(DebridSearch.matches(batman, "THE BATMAN"))
        assertTrue(DebridSearch.matches(batman.uppercase(), "the batman"))
    }

    @Test
    fun `an unnamed file does not match a real query`() {
        assertFalse(DebridSearch.matches("", "batman"))
        // ...but it survives an empty one, so a blank box still lists it.
        assertTrue(DebridSearch.matches("", ""))
    }

    @Test
    fun `season and episode codes are searchable`() {
        assertTrue(DebridSearch.matches(bear, "bear s03"))
        assertTrue(DebridSearch.matches(bear, "s03e01"))
    }

    @Test
    fun `filtering keeps the original order`() {
        val items = listOf(batman, bear, oceans)
        assertEquals(listOf(batman, oceans), DebridSearch.filter(items, "20") { it })
        assertEquals(items, DebridSearch.filter(items, "") { it })
    }
}
