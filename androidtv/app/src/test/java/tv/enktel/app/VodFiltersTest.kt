package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.VodFilters

/**
 * The Movies / Series filters, including the three faults that made them look
 * broken on a real catalogue.
 */
class VodFiltersTest {

    // ---- "2026+" has to mean 2026 or later -------------------------------
    // It was evaluated as `year in 2026..2035`, a ten-year window, so the label
    // and the behaviour disagreed and 2025+ silently swallowed 2026+.

    @Test fun `open ended chip matches its own year and later`() {
        assertTrue(VodFilters.matchesYear(2026, 2026))
        assertTrue(VodFilters.matchesYear(2030, 2026))
        assertTrue(VodFilters.matchesYear(2099, 2026))
    }

    @Test fun `open ended chip excludes earlier years`() {
        assertFalse(VodFilters.matchesYear(2025, 2026))
        assertFalse(VodFilters.matchesYear(1999, 2026))
    }

    @Test fun `open ended chips nest rather than overlap`() {
        // Everything 2026+ is also 2025+, but not the reverse.
        assertTrue(VodFilters.matchesYear(2026, 2025))
        assertFalse(VodFilters.matchesYear(2025, 2026))
    }

    // ---- decade chips still select a decade ------------------------------

    @Test fun `decade chip selects exactly its decade`() {
        assertTrue(VodFilters.matchesYear(2010, 2010))
        assertTrue(VodFilters.matchesYear(2019, 2010))
        assertFalse(VodFilters.matchesYear(2009, 2010))
        assertFalse(VodFilters.matchesYear(2020, 2010))
    }

    @Test fun `older chip is everything before 1990`() {
        assertTrue(VodFilters.matchesYear(1989, VodFilters.OLDER))
        assertTrue(VodFilters.matchesYear(1954, VodFilters.OLDER))
        assertFalse(VodFilters.matchesYear(1990, VodFilters.OLDER))
    }

    // ---- unknown years ---------------------------------------------------
    // extractYear returns 0 when the panel supplies no year and the filename
    // carries no "(YYYY)". That is most of a typical catalogue.

    @Test fun `unknown year matches no chip`() {
        for (chip in VodFilters.YEAR_CHIPS) {
            assertFalse("chip $chip should not match year 0", VodFilters.matchesYear(0, chip))
        }
    }

    @Test fun `unknown year still matches Any`() {
        assertTrue(VodFilters.matchesYear(0, null))
    }

    @Test fun `unknown years are counted so the UI can explain itself`() {
        assertEquals(3, VodFilters.unknownYearCount(listOf(0, 2020, 0, 1999, 0)))
        assertEquals(0, VodFilters.unknownYearCount(listOf(2020, 1999)))
    }

    // ---- label and predicate share one rule -------------------------------

    @Test fun `labels match the behaviour they describe`() {
        assertEquals("2026+", VodFilters.label(2026))
        assertEquals("2025+", VodFilters.label(2025))
        assertEquals("2020s", VodFilters.label(2020))
        assertEquals("1990s", VodFilters.label(1990))
        assertEquals("Older", VodFilters.label(VodFilters.OLDER))
    }

    @Test fun `every plus labelled chip is open ended and every s labelled chip is not`() {
        for (chip in VodFilters.YEAR_CHIPS.filter { it != VodFilters.OLDER }) {
            val openEnded = VodFilters.matchesYear(chip + 50, chip)
            assertEquals(
                "label ${VodFilters.label(chip)} disagrees with its predicate",
                VodFilters.label(chip).endsWith("+"),
                openEnded,
            )
        }
    }

    // ---- Newest ordering --------------------------------------------------

    private data class Row(val name: String, val year: Int, val added: Long)

    @Test fun `newest orders by year then by ingest time`() {
        val rows = listOf(
            Row("no-year-old", 0, 100),
            Row("2020", 2020, 1),
            Row("no-year-new", 0, 900),
            Row("2024", 2024, 1),
        )
        val out = VodFilters.newest(rows, { it.year }, { it.added }).map { it.name }
        assertEquals(listOf("2024", "2020", "no-year-new", "no-year-old"), out)
    }

    @Test fun `newest does not drop unknown year rows`() {
        val rows = listOf(Row("a", 0, 5), Row("b", 0, 9))
        assertEquals(2, VodFilters.newest(rows, { it.year }, { it.added }).size)
    }
}
