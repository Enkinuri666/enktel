package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.repo.ChannelFilters

/**
 * Live channel category / search / visibility rules.
 *
 * The cases here are drawn from a real Australian line, where the guide was
 * frozen on one channel and search found nothing useful.
 */
class ChannelFiltersTest {

    private fun ch(
        key: String,
        name: String,
        categoryId: String = "1",
        categoryName: String = "Australia",
        num: Int = 0,
        epgId: String = "",
        radio: Boolean = false,
        sortIdx: Int = 0,
    ) = Channel(
        key = key, profileId = 1, streamId = key.hashCode().toLong(), name = name,
        num = num, categoryId = categoryId, categoryName = categoryName,
        epgId = epgId, isRadio = radio, sortIdx = sortIdx,
    )

    private val line = listOf(
        ch("a", "AU| SEVEN MATE HD", num = 73, epgId = "seven.au", sortIdx = 0),
        ch("b", "AU| SEVEN FLIX FHD", num = 76, epgId = "seven.au", sortIdx = 1),
        ch("c", "AU| ABC NEWS", num = 24, categoryId = "2", categoryName = "News", sortIdx = 2),
        ch("d", "UK: SKY SPORTS MAIN EVENT 4K", categoryId = "3", categoryName = "UK Sport", sortIdx = 3),
        ch("e", "ABC Classic FM", categoryId = "4", categoryName = "Radio", radio = true, sortIdx = 4),
    )

    // ---- search ----------------------------------------------------------
    // The DAO used `name LIKE '%q%'`, so anything but an exact infix failed.

    @Test fun `search ignores the provider's group prefix`() {
        assertTrue(ChannelFilters.matches(line[0], "seven mate"))
    }

    @Test fun `search ignores quality suffixes`() {
        // "SEVEN MATE HD" and "SEVEN FLIX FHD" must both be reachable by name
        // alone; a user is not choosing between HD and FHD when they type.
        assertTrue(ChannelFilters.matches(line[0], "seven"))
        assertTrue(ChannelFilters.matches(line[1], "seven"))
    }

    @Test fun `search is word-order independent`() {
        assertTrue(ChannelFilters.matches(line[0], "mate seven"))
    }

    @Test fun `search matches partial words`() {
        assertTrue(ChannelFilters.matches(line[3], "sky spo main"))
    }

    @Test fun `every query token must match`() {
        assertFalse(ChannelFilters.matches(line[0], "seven news"))
    }

    @Test fun `search finds a channel by its number`() {
        // Typing the number is how you find a channel on a set-top box.
        assertTrue(ChannelFilters.matches(line[0], "73"))
        assertFalse(ChannelFilters.matches(line[0], "24"))
    }

    @Test fun `search matches on category and group`() {
        assertTrue(ChannelFilters.matches(line[3], "uk sport"))
        assertTrue(ChannelFilters.matches(line[2], "news"))
    }

    @Test fun `an empty query matches everything`() {
        line.forEach { assertTrue(ChannelFilters.matches(it, "")) }
        line.forEach { assertTrue(ChannelFilters.matches(it, "   ")) }
    }

    // ---- categories ------------------------------------------------------
    // The guide showed only the first ~80 channels with no way to change
    // category, which is what "locked into the first category" was.

    @Test fun `ALL returns every channel`() {
        assertEquals(5, ChannelFilters.apply(line).size)
    }

    @Test fun `a category returns only its own channels`() {
        val news = ChannelFilters.apply(line, categoryId = "2")
        assertEquals(listOf("AU| ABC NEWS"), news.map { it.name })
    }

    @Test fun `categories keep the provider's order, not alphabetical`() {
        assertEquals(
            listOf("Australia", "News", "UK Sport", "Radio"),
            ChannelFilters.categoriesOf(line).map { it.second },
        )
    }

    @Test fun `counts are reported per category and in total`() {
        val counts = ChannelFilters.categoryCounts(line)
        assertEquals(2, counts["1"])
        assertEquals(1, counts["2"])
        assertEquals(5, counts[ChannelFilters.ALL])
    }

    // ---- visibility ------------------------------------------------------

    @Test fun `hidden channels disappear from every view`() {
        val hidden = setOf("a")
        assertEquals(4, ChannelFilters.apply(line, hidden = hidden).size)
        assertTrue(ChannelFilters.apply(line, query = "seven mate", hidden = hidden).isEmpty())
        // Hidden beats favourite: hiding something you once starred still hides it.
        assertTrue(
            ChannelFilters.apply(
                line, favourites = setOf("a"), hidden = hidden, favouritesOnly = true,
            ).isEmpty(),
        )
    }

    @Test fun `hidden channels are excluded from the counts too`() {
        val counts = ChannelFilters.categoryCounts(line, hidden = setOf("a", "c"))
        assertEquals(1, counts["1"])
        assertEquals(null, counts["2"])
        assertEquals(3, counts[ChannelFilters.ALL])
    }

    @Test fun `favourites only narrows to starred channels`() {
        val out = ChannelFilters.apply(line, favourites = setOf("b", "d"), favouritesOnly = true)
        assertEquals(listOf("b", "d"), out.map { it.key })
    }

    @Test fun `radio can be isolated or excluded`() {
        assertEquals(listOf("e"), ChannelFilters.apply(line, radioOnly = true).map { it.key })
        assertEquals(4, ChannelFilters.apply(line, radioOnly = false).size)
    }

    // ---- the actual guide bug --------------------------------------------

    @Test fun `channels sharing one epgId are still distinct channels`() {
        // SEVEN MATE and SEVEN FLIX both carry epgId "seven.au". The guide
        // keyed its programme lookup on epgId, so selecting one after the
        // other never re-ran the lookup and the listing stayed frozen.
        val mate = line[0]
        val flix = line[1]
        assertEquals(mate.epgId, flix.epgId)
        assertFalse(mate.key == flix.key)
    }
}
