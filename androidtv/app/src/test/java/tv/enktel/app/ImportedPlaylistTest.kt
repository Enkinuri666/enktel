package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.m3u.ImportedPlaylist

/**
 * Importing a file used to replace the viewer's channels with it.
 *
 * The mechanism was a profile switch, and the reason it went unnoticed is that
 * nothing was lost — the old channels stayed in the database under a profile
 * that was no longer active, so there was no error to report and no empty
 * table to find. What follows pins the two rules that make an import additive
 * instead: its rows cannot collide with the ones already there, and they carry
 * a category that says where they came from.
 */
class ImportedPlaylistTest {

    private fun playlist(slot: Int, name: String = "My list") =
        ImportedPlaylist(id = 1L, profileId = 7L, name = name, url = "file:///x", slot = slot)

    // ---- the id range --------------------------------------------------

    /**
     * The failure this prevents is silent. A row key is `profileId:streamId`,
     * inserted with REPLACE, so an imported channel landing on an id the host
     * profile already uses does not error — it overwrites, and one of the
     * viewer's channels quietly becomes a different one.
     */
    @Test
    fun `imported ids sit clear of what a real source issues`() {
        // The largest id any real source in this app plausibly issues: an M3U
        // profile numbers its rows from 1, and panels hand out stream ids in
        // the tens of thousands to low millions.
        val realistic = 2_500_000L
        // Every id an attachment can produce, at both ends of its range and at
        // the highest slot the app will hand out before ids would overlap.
        assertTrue(playlist(slot = 0).streamIdFor(0) > realistic)
        assertTrue(playlist(slot = 999).streamIdFor(0) > realistic)
        assertTrue(playlist(slot = 999).streamIdFor(ImportedPlaylist.MAX_CHANNELS - 1) > realistic)
    }

    @Test
    fun `two files on the same profile cannot produce the same id`() {
        val a = playlist(slot = 0)
        val b = playlist(slot = 1)
        // Even at the extremes of one file's range.
        assertNotEquals(a.streamIdFor(ImportedPlaylist.MAX_CHANNELS - 1), b.streamIdFor(0))
        for (i in listOf(0, 1, 500, 999_999)) {
            assertNotEquals(a.streamIdFor(i), b.streamIdFor(i))
        }
    }

    @Test
    fun `a channel keeps its id across syncs`() {
        assertEquals(playlist(slot = 2).streamIdFor(41), playlist(slot = 2).streamIdFor(41))
    }

    // ---- slots ---------------------------------------------------------

    /**
     * Slots are per profile and reused, so removing one file does not renumber
     * the others. Positional numbering would: dropping the first of three
     * would shift the other two onto ids that favourites were already stored
     * against, moving every one of them onto a different channel.
     */
    @Test
    fun `a slot is not taken from a file that already holds it`() {
        val existing = listOf(playlist(slot = 0), playlist(slot = 1))
        assertEquals(2, ImportedPlaylist.nextSlot(existing, profileId = 7L))
    }

    @Test
    fun `a freed slot is used again`() {
        val existing = listOf(playlist(slot = 0), playlist(slot = 2))
        assertEquals(1, ImportedPlaylist.nextSlot(existing, profileId = 7L))
    }

    @Test
    fun `slots on one profile do not constrain another`() {
        val existing = listOf(playlist(slot = 0), playlist(slot = 1))
        assertEquals(0, ImportedPlaylist.nextSlot(existing, profileId = 99L))
    }

    @Test
    fun `the first file on a profile takes slot zero`() {
        assertEquals(0, ImportedPlaylist.nextSlot(emptyList(), profileId = 7L))
    }

    // ---- categories ----------------------------------------------------

    /**
     * The prefix is what keeps an import visible as an import. Without it a
     * file whose groups are "News" and "Sports" would merge into the lineup's
     * own News and Sports, and there would be no way to tell what had been
     * added or to find it again.
     */
    @Test
    fun `a file's groups are kept but marked as its own`() {
        val p = playlist(slot = 0, name = "Balkans")
        assertEquals("Balkans · Sport", p.categoryFor("Sport"))
        assertEquals("Balkans · HR - News", p.categoryFor("HR - News"))
    }

    @Test
    fun `an ungrouped file becomes one category named after itself`() {
        val p = playlist(slot = 0, name = "Balkans")
        assertEquals("Balkans", p.categoryFor(""))
        assertEquals("Balkans", p.categoryFor("   "))
    }

    /** Two files with the same group name still land in different categories. */
    @Test
    fun `two files do not share a category`() {
        assertNotEquals(
            playlist(slot = 0, name = "One").categoryFor("Sport"),
            playlist(slot = 1, name = "Two").categoryFor("Sport"),
        )
    }

    // ---- the stored record ---------------------------------------------

    @Test
    fun `a record survives the round trip`() {
        val items = listOf(
            ImportedPlaylist(id = 1L, profileId = 7L, name = "My list", url = "file:///a.m3u", slot = 0),
            ImportedPlaylist(id = 2L, profileId = 7L, name = "Balkans", url = "file:///b.m3u", slot = 1),
        )
        assertEquals(items, ImportedPlaylist.decode(ImportedPlaylist.encode(items)))
    }

    /**
     * A document's name is whatever the viewer called the file. A tab or a
     * newline in it would split the record across fields and lose the entry
     * entirely, so it is flattened rather than trusted.
     */
    @Test
    fun `a name that would break the format is flattened`() {
        val awkward = listOf(
            ImportedPlaylist(id = 1L, profileId = 7L, name = "a\tb\nc", url = "file:///a", slot = 0),
        )
        val back = ImportedPlaylist.decode(ImportedPlaylist.encode(awkward))
        assertEquals(1, back.size)
        assertEquals("a b c", back[0].name)
    }

    @Test
    fun `an unnamed file still gets a name`() {
        val blank = listOf(ImportedPlaylist(id = 1L, profileId = 7L, name = "  ", url = "file:///a", slot = 0))
        assertEquals("Imported playlist", ImportedPlaylist.decode(ImportedPlaylist.encode(blank))[0].name)
    }

    @Test
    fun `nothing stored reads as nothing attached`() {
        assertEquals(emptyList<ImportedPlaylist>(), ImportedPlaylist.decode(null))
        assertEquals(emptyList<ImportedPlaylist>(), ImportedPlaylist.decode(""))
    }

    /** A half-written or older record is skipped rather than crashing a sync. */
    @Test
    fun `a malformed line is dropped, and the good ones survive`() {
        val raw = "1\t7\tGood\tfile:///a.m3u\t0\n" +
            "not a record\n" +
            "2\t7\tNoUrl\t\t1\n" +
            "x\t7\tBadId\tfile:///c.m3u\t2\n" +
            "3\t7\tAlsoGood\tfile:///d.m3u\t3"
        val back = ImportedPlaylist.decode(raw)
        assertEquals(listOf("Good", "AlsoGood"), back.map { it.name })
    }
}
