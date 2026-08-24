package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.m3u.ImportMigration

/**
 * Folding pre-attachment imports back into the lineup they should have joined.
 *
 * Two ways to get this wrong, neither of which anything downstream would
 * report: attaching a viewer's files to a profile they never open, or
 * converting a real subscription and taking it out of the profile list. Both
 * are decided here, which is why the decision is a pure function.
 */
class ImportMigrationTest {

    private fun imported(id: Long, name: String = "A file") =
        Profile(id = id, name = name, kind = "m3u", m3uUrl = "file:///data/playlists/$id.m3u")

    private fun xtream(id: Long, name: String = "My line") =
        Profile(id = id, name = name, kind = "xtream", server = "https://panel.example")

    /** The build's own free-to-air playlist: `kind = "m3u"`, but subscribed. */
    private fun subscribed(id: Long) =
        Profile(id = id, name = "EnkTel Free", kind = "m3u", m3uUrl = "https://enktel.tv/playlists/free.m3u")

    // ---- telling an import from a provider -----------------------------

    /**
     * `file://` is the marker, not `kind`. A subscribed M3U profile is also
     * `kind = "m3u"`, and converting the free-to-air playlist would delete the
     * only source of channels a public install has.
     */
    @Test
    fun `only a copied file counts as an import`() {
        assertTrue(ImportMigration.isOldImport(imported(1)))
        assertFalse(ImportMigration.isOldImport(subscribed(2)))
        assertFalse(ImportMigration.isOldImport(xtream(3)))
    }

    // ---- choosing what to convert --------------------------------------

    @Test
    fun `nothing to do when there are no old imports`() {
        assertNull(ImportMigration.plan(listOf(xtream(1), subscribed(2)), activeId = 1L))
        assertNull(ImportMigration.plan(emptyList(), activeId = 0L))
    }

    @Test
    fun `an import attaches to the real provider`() {
        val plan = ImportMigration.plan(listOf(xtream(1), imported(2)), activeId = 1L)!!
        assertEquals(1L, plan.hostId)
        assertEquals(listOf(2L), plan.convert.map { it.id })
    }

    @Test
    fun `every import attaches to the same host`() {
        val plan = ImportMigration.plan(
            listOf(xtream(1), imported(2), imported(3), imported(4)),
            activeId = 1L,
        )!!
        assertEquals(1L, plan.hostId)
        assertEquals(listOf(2L, 3L, 4L), plan.convert.map { it.id })
    }

    /**
     * The profile the viewer actually uses wins, not merely the first one.
     * Attaching to an abandoned provider hides the files behind a switch the
     * viewer has no reason to make.
     */
    @Test
    fun `the profile in use is preferred as the host`() {
        val plan = ImportMigration.plan(
            listOf(xtream(1, "Old line"), xtream(5, "Current line"), imported(9)),
            activeId = 5L,
        )!!
        assertEquals(5L, plan.hostId)
    }

    @Test
    fun `with no active profile the oldest provider hosts`() {
        val plan = ImportMigration.plan(listOf(xtream(7), xtream(3), imported(9)), activeId = 0L)!!
        assertEquals(3L, plan.hostId)
    }

    // ---- a device that only ever had imports ---------------------------

    /**
     * There is no provider to attach to, so the oldest import keeps its place
     * as a profile and the rest join it. Converting all of them would leave a
     * set of attachments belonging to no profile — never read, and the viewer
     * left with nothing.
     */
    @Test
    fun `the oldest import hosts when there is no provider at all`() {
        val plan = ImportMigration.plan(listOf(imported(4), imported(2), imported(8)), activeId = 4L)!!
        assertEquals(2L, plan.hostId)
        assertEquals(listOf(4L, 8L), plan.convert.map { it.id })
    }

    @Test
    fun `a lone import is left exactly as it is`() {
        assertNull(ImportMigration.plan(listOf(imported(1)), activeId = 1L))
    }

    // ---- the active profile --------------------------------------------

    /** Leaving `activeProfile` on a deleted id shows an empty app. */
    @Test
    fun `converting the open profile moves the viewer to the host`() {
        val plan = ImportMigration.plan(listOf(xtream(1), imported(2)), activeId = 2L)!!
        assertEquals(1L, plan.activeMovesTo)
    }

    @Test
    fun `a viewer on an untouched profile is not moved`() {
        val plan = ImportMigration.plan(listOf(xtream(1), imported(2)), activeId = 1L)!!
        assertNull(plan.activeMovesTo)
    }

    @Test
    fun `the host is never among the profiles removed`() {
        for (active in listOf(0L, 1L, 2L, 3L)) {
            val plan = ImportMigration.plan(listOf(xtream(1), imported(2), imported(3)), active)
            if (plan != null) assertFalse(plan.convert.any { it.id == plan.hostId })
        }
    }

    // ---- running it more than once -------------------------------------

    /**
     * There is no "migration done" flag, so this has to be true rather than
     * assumed: after one pass the only local file left as a profile is the
     * host, and a host is never converted.
     */
    @Test
    fun `a second pass finds nothing left to do`() {
        val before = listOf(xtream(1), imported(2), imported(3))
        val first = ImportMigration.plan(before, activeId = 1L)!!
        val after = before.filter { p -> first.convert.none { it.id == p.id } }
        assertNull(ImportMigration.plan(after, activeId = first.hostId))
    }

    @Test
    fun `a second pass finds nothing left on an imports-only device`() {
        val before = listOf(imported(4), imported(2))
        val first = ImportMigration.plan(before, activeId = 4L)!!
        val after = before.filter { p -> first.convert.none { it.id == p.id } }
        assertNull(ImportMigration.plan(after, activeId = first.hostId))
    }
}
