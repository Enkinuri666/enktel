package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.enktel.app.data.repo.FtsQuery

/**
 * FTS4 `MATCH` takes a query language, not a string — `"`, `*`, `-`, `^`, `(`
 * and the bare words OR/AND/NEAR all mean something, and a malformed
 * expression makes SQLite *raise* rather than return no rows. A user typing an
 * apostrophe into the search box would have taken the screen down with it.
 */
class FtsQueryTest {

    @Test
    fun `words are ANDed with a prefix on the one being typed`() {
        assertEquals("bat*", FtsQuery.toMatch("bat"))
        assertEquals("bat man*", FtsQuery.toMatch("bat man"))
        assertEquals("the dark knight*", FtsQuery.toMatch("The Dark Knight"))
    }

    @Test
    fun `apostrophes are absorbed rather than splitting the word`() {
        // "handmaid's" must tokenise as one term, not "handmaid" plus "s".
        assertEquals("the handmaids tale*", FtsQuery.toMatch("The Handmaid's Tale"))
        assertEquals("the handmaids tale*", FtsQuery.toMatch("The Handmaid’s Tale"))
    }

    @Test
    fun `FTS operators in user input cannot reach SQLite`() {
        assertEquals("batman*", FtsQuery.toMatch("\"batman\""))
        assertEquals("bat man*", FtsQuery.toMatch("bat - man"))
        assertEquals("bat man*", FtsQuery.toMatch("bat^man"))
        // FTS4's operators are case-sensitive — only uppercase OR/AND/NEAR
        // are operators. Lowercasing the whole query is what defuses them, so
        // "OR" arrives as the ordinary term "or" and matches nothing special.
        assertEquals("a or b*", FtsQuery.toMatch("(a OR b)"))
        assertEquals("near me*", FtsQuery.toMatch("NEAR me"))
    }

    @Test
    fun `punctuation-only input yields no query rather than a broken one`() {
        assertNull(FtsQuery.toMatch("\"\""))
        assertNull(FtsQuery.toMatch("***"))
        assertNull(FtsQuery.toMatch("   "))
        assertNull(FtsQuery.toMatch(null))
    }

    @Test
    fun `a single character falls through to the LIKE path`() {
        assertNull(FtsQuery.toMatch("b"))
        assertEquals("ba*", FtsQuery.toMatch("ba"))
    }

    @Test
    fun `digits are searchable so a year still matches`() {
        assertEquals("blade runner 2049*", FtsQuery.toMatch("Blade Runner 2049"))
    }

    @Test
    fun `accented titles keep their letters`() {
        assertEquals("amélie*", FtsQuery.toMatch("Amélie"))
    }
}
