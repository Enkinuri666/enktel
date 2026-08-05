package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.repo.MovieRails

/**
 * The home rail rules.
 *
 * These exist mainly as a drift guard. Every rule here was previously written
 * twice — once in a single-rail helper, once inline in `homeRails` — and two
 * of them had already diverged in ways a user could see.
 */
class MovieRailsTest {

    private fun m(
        name: String,
        rating: Double = 0.0,
        year: Int = 0,
        added: Long = 0,
        genre: String = "",
        tags: String = "",
        enriched: Long = 0,
        poster: String = "p.jpg",
    ) = Movie(
        key = "1:$name", profileId = 1, streamId = name.hashCode().toLong(), name = name,
        poster = poster, rating = rating, year = year, addedAt = added,
        genre = genre, tags = tags, enrichedAt = enriched,
    )

    // ---- renderable ------------------------------------------------------

    @Test fun `titles with no artwork are dropped before any rail runs`() {
        val out = MovieRails.renderable(listOf(m("has art"), m("none", poster = "")))
        assertEquals(listOf("has art"), out.map { it.name })
    }

    // ---- trending --------------------------------------------------------

    @Test fun `trending applies its rating floor`() {
        val out = MovieRails.trending(listOf(m("good", 7.0), m("meh", 6.4)))
        assertEquals(listOf("good"), out.map { it.name })
    }

    @Test fun `trending is stable when ratings tie`() {
        // Hundreds of titles routinely share a round rating. Without a
        // deterministic tie-break the rail reshuffles between reads and looks
        // broken; year desc is that tie-break.
        val rows = listOf(m("older", 7.0, year = 2001), m("newer", 7.0, year = 2020))
        assertEquals(listOf("newer", "older"), MovieRails.trending(rows).map { it.name })
        assertEquals(
            MovieRails.trending(rows).map { it.name },
            MovieRails.trending(rows.reversed()).map { it.name },
        )
    }

    @Test fun `trending does not order by ingest date`() {
        // The old helper sorted by `rating + addedAt / 1_000_000`, which let a
        // recently-ingested lower-rated title outrank a better one. That is
        // what made the voice assistant and the Home rail disagree.
        val rows = listOf(m("better", 9.0, added = 1), m("newer", 6.6, added = 9_000_000))
        assertEquals(listOf("better", "newer"), MovieRails.trending(rows).map { it.name })
    }

    // ---- topPicks --------------------------------------------------------

    @Test fun `top picks ignores un-enriched ratings`() {
        // An un-enriched rating comes from the panel and is often a
        // placeholder, so a "best of" rail built on it shows whatever the
        // provider happened to type.
        val out = MovieRails.topPicks(
            listOf(m("tmdb", 8.0, enriched = 1L), m("panel", 9.9, enriched = 0L)),
        )
        assertEquals(listOf("tmdb"), out.map { it.name })
    }

    // ---- newThisWeek -----------------------------------------------------

    private val now = 1_700_000_000L // seconds

    @Test fun `new this week uses the panel stamp in seconds`() {
        val recent = now - 3 * 24 * 60 * 60
        val old = now - 40L * 24 * 60 * 60
        val out = MovieRails.newThisWeek(listOf(m("old", added = old), m("new", added = recent)), now)
        assertEquals(listOf("new"), out.map { it.name })
    }

    @Test fun `a catalogue with no added stamps yields an empty rail`() {
        // Exactly what an M3U line looks like: the format carries no `added`
        // field at all. Empty is the honest answer.
        assertTrue(MovieRails.newThisWeek(listOf(m("a"), m("b")), now).isEmpty())
    }

    // ---- latestReleases / comingSoon -------------------------------------

    @Test fun `latest releases orders by release year, not by arrival`() {
        val rows = listOf(m("old film, just added", year = 1994, added = 999),
                          m("new film, long here", year = 2026, added = 1))
        assertEquals(
            listOf("new film, long here", "old film, just added"),
            MovieRails.latestReleases(rows).map { it.name },
        )
    }

    @Test fun `latest releases drops rows with no year`() {
        assertTrue(MovieRails.latestReleases(listOf(m("unknown"))).isEmpty())
    }

    @Test fun `coming soon is strictly future years`() {
        val rows = listOf(m("this year", year = 2026), m("next year", year = 2027))
        assertEquals(listOf("next year"), MovieRails.comingSoon(rows, 2026).map { it.name })
    }

    // ---- mood ------------------------------------------------------------

    @Test fun `mood searches tags as well as genre`() {
        // IPTV genre metadata is patchy and often one word. A genre-only match
        // left most mood rails empty on a real catalogue — which is what the
        // now-deleted helper did, while the inline copy searched both.
        val byTag = m("tagged", 7.0, genre = "Feature", tags = "neo-noir,crime")
        val out = MovieRails.mood(listOf(byTag), MovieRails.MOOD_GRITTY, 6.8)
        assertEquals(listOf("tagged"), out.map { it.name })
    }

    @Test fun `mood applies its rating floor`() {
        val out = MovieRails.mood(listOf(m("weak", 5.0, genre = "action")),
                                  MovieRails.MOOD_FAST_PACED, 6.5)
        assertTrue(out.isEmpty())
    }

    // ---- documentaries ---------------------------------------------------

    @Test fun `documentaries matches the genre however it is spelled`() {
        val rows = listOf(m("a", genre = "Documentary"), m("b", genre = "documentaries"),
                          m("c", genre = "Drama"))
        assertEquals(setOf("a", "b"), MovieRails.documentaries(rows).map { it.name }.toSet())
    }

    // ---- the drift guard -------------------------------------------------

    @Test fun `every rail returns a stable order for the same input`() {
        val rows = List(40) { i ->
            m("t$i", rating = 6.0 + (i % 5), year = 2000 + (i % 25),
              added = (i % 7).toLong(), genre = "action drama", enriched = 1L)
        }
        fun runAll(src: List<Movie>) = listOf(
            MovieRails.trending(src), MovieRails.topPicks(src),
            MovieRails.latestReleases(src), MovieRails.topRated(src),
            MovieRails.mood(src, MovieRails.MOOD_GRITTY, 6.0),
        ).map { r -> r.map { it.name } }
        assertEquals(runAll(rows), runAll(rows.shuffled()))
    }

    @Test fun `rails do not silently drop everything on an empty catalogue`() {
        // Empty in, empty out — and no exception, which is what a rail on a
        // freshly-created profile actually sees.
        val none = emptyList<Movie>()
        assertTrue(MovieRails.trending(none).isEmpty())
        assertTrue(MovieRails.latestReleases(none).isEmpty())
        assertTrue(MovieRails.newThisWeek(none, now).isEmpty())
        assertFalse(MovieRails.MOOD_GRITTY.isEmpty())
    }
}
