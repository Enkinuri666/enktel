package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.FreshCatalogue

/** "What did this sync actually add?" */
class FreshCatalogueTest {

    private val now = 1_700_000_000_000L

    @Test fun `a title carried over keeps its original stamp`() {
        val out = FreshCatalogue.stamp(
            incoming = listOf("dune", "arrival"),
            previous = mapOf("dune" to 100L, "arrival" to 200L),
            nowMs = now,
        )
        assertEquals(listOf(100L, 200L), out)
    }

    @Test fun `a title that was not there before is stamped now`() {
        val out = FreshCatalogue.stamp(
            incoming = listOf("dune", "sinners"),
            previous = mapOf("dune" to 100L),
            nowMs = now,
        )
        assertEquals(listOf(100L, now), out)
    }

    @Test fun `the first ever sync does not mark the whole library as new`() {
        // Otherwise "New this week" would be the entire catalogue on day one,
        // which tells the user nothing.
        val out = FreshCatalogue.stamp(
            incoming = listOf("a", "b", "c"),
            previous = emptyMap(),
            nowMs = now,
        )
        assertEquals(listOf(0L, 0L, 0L), out)
    }

    @Test fun `a sync that removes everything but one still keeps that one's stamp`() {
        val out = FreshCatalogue.stamp(
            incoming = listOf("b"),
            previous = mapOf("a" to 1L, "b" to 2L, "c" to 3L),
            nowMs = now,
        )
        assertEquals(listOf(2L), out)
    }

    @Test fun `identity ignores case spacing and punctuation`() {
        // Providers re-title constantly: "Dune: Part Two" one week,
        // "DUNE - PART TWO" the next. Treating those as different titles
        // would re-flag half the catalogue as new on every sync.
        assertEquals(
            FreshCatalogue.titleId("Dune: Part Two"),
            FreshCatalogue.titleId("DUNE - PART   TWO"),
        )
    }

    @Test fun `identity still separates genuinely different titles`() {
        assertFalse(FreshCatalogue.titleId("Dune") == FreshCatalogue.titleId("Dune 2"))
    }

    @Test fun `carrying over survives a re-titling only when identity matches`() {
        val out = FreshCatalogue.stamp(
            incoming = listOf(FreshCatalogue.titleId("DUNE - PART TWO")),
            previous = mapOf(FreshCatalogue.titleId("Dune: Part Two") to 55L),
            nowMs = now,
        )
        assertEquals(listOf(55L), out)
    }

    @Test fun `newness expires`() {
        assertTrue(FreshCatalogue.isNew(now - 1000, now))
        assertTrue(FreshCatalogue.isNew(now - FreshCatalogue.NEW_WINDOW_MS + 1, now))
        assertFalse(FreshCatalogue.isNew(now - FreshCatalogue.NEW_WINDOW_MS - 1, now))
    }

    @Test fun `an unstamped row is never new`() {
        // 0 means "was already here when we started tracking".
        assertFalse(FreshCatalogue.isNew(0, now))
    }
}
