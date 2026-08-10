package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.SimilarTitles
import tv.enktel.app.data.repo.SimilarTitles.Facets

/**
 * The Movie and Series pages described a title and stopped. Somebody who had
 * decided against it had only Back; somebody who liked it had no way to reach
 * its neighbours — while the app knew exactly what else was on the line.
 *
 * These pin what "alike" is allowed to mean. The failure worth guarding is not
 * an empty rail: it is a rail of six unrelated films under a heading that
 * promises otherwise, which costs more trust than showing nothing.
 */
class SimilarTitlesTest {

    private fun f(
        key: String,
        genres: List<String> = emptyList(),
        cast: List<String> = emptyList(),
        director: String = "",
        year: Int = 0,
        rating: Double = 0.0,
        hasArt: Boolean = true,
    ) = Facets(key, genres, cast, director, year, rating, hasArt)

    private val seed = f(
        key = "1:100",
        genres = listOf("Action", "Thriller"),
        cast = listOf("Keanu Reeves", "Ian McShane"),
        director = "Chad Stahelski",
        year = 2019,
        rating = 7.4,
    )

    // ── what must not appear ───────────────────────────────────────────

    @Test
    fun `the title being looked at is not similar to itself`() {
        assertEquals(0, SimilarTitles.score(seed, seed.copy(hasArt = true)))
    }

    @Test
    fun `a title with nothing in common scores nothing`() {
        // Same era, well rated, and utterly unrelated. Neither of those is
        // evidence of resemblance on its own — half a catalogue shares them.
        val unrelated = f("1:200", genres = listOf("Romance"), year = 2019, rating = 8.1)
        assertEquals(0, SimilarTitles.score(seed, unrelated))
    }

    @Test
    fun `a title with no poster is left out`() {
        val noArt = f("1:300", genres = listOf("Action", "Thriller"), hasArt = false)
        assertEquals(0, SimilarTitles.score(seed, noArt))
    }

    // ── what should ────────────────────────────────────────────────────

    @Test
    fun `sharing both genres beats sharing one`() {
        val both = f("1:400", genres = listOf("Action", "Thriller"))
        val one = f("1:401", genres = listOf("Action", "Comedy"))
        assertTrue(SimilarTitles.score(seed, both) > SimilarTitles.score(seed, one))
    }

    @Test
    fun `the same director counts for more than one shared genre`() {
        val sameDirector = f("1:500", director = "Chad Stahelski")
        val oneGenre = f("1:501", genres = listOf("Action"))
        assertTrue(
            "a director is a rarer and stronger signal than a genre",
            SimilarTitles.score(seed, sameDirector) > SimilarTitles.score(seed, oneGenre),
        )
    }

    @Test
    fun `a director match is not case sensitive`() {
        assertTrue(SimilarTitles.score(seed, f("1:502", director = "chad stahelski")) > 0)
    }

    @Test
    fun `a prolific actor cannot dominate the rail`() {
        // Without the cap, one cast list overlapping heavily would outrank a
        // genuine genre-and-director match everywhere in the app.
        val manyShared = f(
            "1:600",
            cast = listOf("Keanu Reeves", "Ian McShane", "Keanu Reeves", "Ian McShane", "Keanu Reeves"),
        )
        val capped = f("1:601", cast = listOf("Keanu Reeves", "Ian McShane", "Keanu Reeves"))
        assertEquals(SimilarTitles.score(seed, capped), SimilarTitles.score(seed, manyShared))
    }

    @Test
    fun `era nudges but never carries a title on its own`() {
        val sameEraNoLink = f("1:700", genres = listOf("Romance"), year = 2019)
        assertEquals(0, SimilarTitles.score(seed, sameEraNoLink))

        val genreThisEra = f("1:701", genres = listOf("Action"), year = 2019)
        val genreLongAgo = f("1:702", genres = listOf("Action"), year = 1975)
        assertTrue(SimilarTitles.score(seed, genreThisEra) > SimilarTitles.score(seed, genreLongAgo))
    }

    // ── ranking a pool ─────────────────────────────────────────────────

    @Test
    fun `the rail is ordered by resemblance and leads with the best`() {
        val pool = listOf(
            f("1:800", genres = listOf("Comedy")),
            f("1:801", genres = listOf("Action"), rating = 6.0),
            f("1:802", genres = listOf("Action", "Thriller"), director = "Chad Stahelski", rating = 8.0),
            f("1:803", genres = listOf("Action", "Thriller"), rating = 7.0),
            seed,
        )
        val ranked = SimilarTitles.rank(seed, pool, facets = { it })
        assertEquals(listOf("1:802", "1:803", "1:801"), ranked.map { it.key })
    }

    @Test
    fun `equally alike titles lead with the better one`() {
        val worse = f("1:900", genres = listOf("Action", "Thriller"), rating = 5.0)
        val better = f("1:901", genres = listOf("Action", "Thriller"), rating = 6.9)
        val ranked = SimilarTitles.rank(seed, listOf(worse, better), facets = { it })
        assertEquals("1:901", ranked.first().key)
    }

    @Test
    fun `the rail is capped`() {
        val pool = (1..80).map { f("1:$it", genres = listOf("Action", "Thriller")) }
        assertEquals(20, SimilarTitles.rank(seed, pool, facets = { it }).size)
        assertEquals(6, SimilarTitles.rank(seed, pool, limit = 6, facets = { it }).size)
    }

    @Test
    fun `a catalogue with nothing alike yields an empty rail rather than filler`() {
        val pool = listOf(f("1:1", genres = listOf("Romance")), f("1:2", genres = listOf("Documentary")))
        assertTrue(SimilarTitles.rank(seed, pool, facets = { it }).isEmpty())
    }

    // ── the panel's own formatting ─────────────────────────────────────

    @Test
    fun `cast lists are split the way panels actually write them`() {
        assertEquals(
            listOf("Keanu Reeves", "Ian McShane", "Halle Berry"),
            SimilarTitles.splitPeople("Keanu Reeves, Ian McShane , Halle Berry"),
        )
        assertEquals(
            listOf("A Name", "B Name"),
            SimilarTitles.splitPeople("A Name; B Name"),
        )
        assertEquals(emptyList<String>(), SimilarTitles.splitPeople(""))
        // A stray conjunction is not a person.
        assertEquals(listOf("A Name", "B Name"), SimilarTitles.splitPeople("A Name, and, B Name"))
    }
}
