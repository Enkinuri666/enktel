package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Series
import tv.enktel.app.data.metadata.RatingRank
import tv.enktel.app.data.repo.ContentRepository
import tv.enktel.app.data.repo.VodFilters
import tv.enktel.app.data.repo.VodShelf

/**
 * [VodShelf] arranges a library exactly as the browse screens used to, only
 * without doing it on the UI thread or redoing work that has not changed.
 *
 * "Exactly" is the claim, so it is the test. [oldMovies] and [oldSeries] are
 * the expressions that lived inline in `MoviesScreen` and `SeriesScreen`,
 * copied here unchanged, and every case below asks for the same thing both
 * ways and compares the answers title for title. A faster shelf that puts one
 * film in a different place is a regression somebody will notice and nobody
 * will be able to explain.
 */
class VodShelfTest {

    // ---- the library --------------------------------------------------------

    private val words = listOf(
        "Northern", "Line", "Harbour", "Light", "Quarry", "Road", "Salt", "Marsh",
        "Winter", "Fold", "Cold", "Coast", "Bright", "Water", "Nine", "Pin",
    )
    private val genrePool = listOf("Action", "Drama", "Comedy", "Documentary", "Crime", "Sci-Fi")

    private val movies: List<Movie> = (0 until 600).map { i ->
        Movie(
            key = "1:m$i", profileId = 1, streamId = i.toLong(),
            // Mixed case on purpose, and plenty of exact duplicates: ties are
            // where an unstable or differently-keyed sort shows itself.
            name = (if (i % 3 == 0) "the " else "The ") + words[i % words.size] + " " + words[(i / 7) % words.size],
            year = if (i % 9 == 0) 0 else 1985 + i % 42,
            categoryId = "v${i % 8}",
            genre = when (i % 5) {
                0 -> ""
                1 -> genrePool[i % genrePool.size]
                else -> genrePool[i % genrePool.size] + " / " + genrePool[(i / 3) % genrePool.size].lowercase()
            },
            // Not everyone is in everything: a search for "mira" has to
            // return some films and not others, or it tests nothing.
            cast = (if (i % 2 == 0) "Mira" else "Lena") + " ${words[(i * 5) % words.size]}, " +
                "Tomas ${words[(i * 11) % words.size]}",
            director = "Ana ${words[(i * 3) % words.size]}",
            addedAt = 1_700_000_000_000L + (i * 7919L % 600) * 60_000L,
            rating = if (i % 4 == 0) 0.0 else (i % 97) / 10.0,
            imdbRating = if (i % 6 == 0) (i % 83) / 10.0 + 1.0 else 0.0,
            imdbVotes = if (i % 6 == 0) (i * 37) % 250_000 else 0,
        )
    }

    private val series: List<Series> = (0 until 240).map { i ->
        Series(
            key = "1:s$i", profileId = 1, seriesId = i.toLong(),
            name = (if (i % 2 == 0) "" else "the ") + words[(i * 3) % words.size] + " " + words[i % words.size],
            year = if (i % 7 == 0) 0 else 1995 + i % 31,
            genre = genrePool[i % genrePool.size] + ", " + genrePool[(i / 4) % genrePool.size],
            rating = (i % 91) / 10.0,
            imdbRating = if (i % 5 == 0) 6.0 + (i % 30) / 10.0 else 0.0,
            imdbVotes = if (i % 5 == 0) i * 113 else 0,
        )
    }

    // ---- the code this replaced, verbatim -----------------------------------

    private fun oldMovies(raw: List<Movie>, sort: String, genreFilter: String?, decadeFilter: Int?, query: String): List<Movie> {
        val needle = query.trim().lowercase()
        return raw.asSequence()
            .filter { m -> genreFilter == null || m.genre.contains(genreFilter, ignoreCase = true) }
            .filter { m -> VodFilters.matchesYear(m.year, decadeFilter) }
            .filter { m ->
                if (needle.isEmpty()) true
                else m.name.lowercase().contains(needle) ||
                    m.cast.lowercase().contains(needle) ||
                    m.director.lowercase().contains(needle)
            }
            .let { seq ->
                when (sort) {
                    "rating" -> seq.sortedByDescending { RatingRank.score(it.imdbRating, it.imdbVotes, it.rating) }
                    "added" -> seq.sortedByDescending { it.addedAt }
                    "year" -> VodFilters.newest(seq.toList(), { it.year }, { it.addedAt }).asSequence()
                    else -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }

    private fun oldSeries(raw: List<Series>, sort: String, genreFilter: String?, decadeFilter: Int?, query: String): List<Series> {
        val needle = query.trim().lowercase()
        return raw.asSequence()
            .filter { s -> genreFilter == null || s.genre.contains(genreFilter, ignoreCase = true) }
            .filter { s -> VodFilters.matchesYear(s.year, decadeFilter) }
            .filter { s -> needle.isEmpty() || s.name.lowercase().contains(needle) }
            .let { seq ->
                when (sort) {
                    "rating" -> seq.sortedByDescending { RatingRank.score(it.imdbRating, it.imdbVotes, it.rating) }
                    "year", "added" -> seq.sortedByDescending { it.year }
                    else -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }

    private val sorts = listOf("name", "rating", "added", "year", "something-unknown")
    private val genres = listOf(null, "Drama", "drama", "Sci-Fi", "Western")
    private val decades = listOf(null) + VodFilters.YEAR_CHIPS
    private val queries = listOf("", "  ", "the", "NORTHERN", "mira", "ana salt", "zzz", " road ")

    // ---- equivalence --------------------------------------------------------

    @Test
    fun `films come out in exactly the order they used to`() {
        val index = VodShelf.movies(movies)
        var checked = 0
        for (sort in sorts) for (genre in genres) for (decade in decades) for (q in queries) {
            assertEquals(
                "sort=$sort genre=$genre decade=$decade query='$q'",
                oldMovies(movies, sort, genre, decade, q).map { it.key },
                VodShelf.arrange(index, sort, genre, decade, q).map { it.key },
            )
            checked++
        }
        assertTrue("the grid of cases collapsed to $checked", checked > 1_000)
    }

    @Test
    fun `series come out in exactly the order they used to`() {
        val index = VodShelf.series(series)
        for (sort in sorts) for (genre in genres) for (decade in decades) for (q in queries) {
            assertEquals(
                "sort=$sort genre=$genre decade=$decade query='$q'",
                oldSeries(series, sort, genre, decade, q).map { it.key },
                VodShelf.arrange(index, sort, genre, decade, q).map { it.key },
            )
        }
    }

    @Test
    fun `the comparison is not vacuous`() {
        // If the fixture were such that every case returned everything, or
        // nothing, the two tests above would pass for any implementation.
        val index = VodShelf.movies(movies)
        val all = VodShelf.arrange(index, "name")
        assertEquals(movies.size, all.size)
        assertTrue(VodShelf.arrange(index, "name", query = "mira").size in 1 until movies.size)
        assertTrue(VodShelf.arrange(index, "name", genre = "Drama").size in 1 until movies.size)
        assertTrue(VodShelf.arrange(index, "name", decade = 2010).size in 1 until movies.size)
        assertEquals(0, VodShelf.arrange(index, "name", query = "zzz").size)
        // And the sorts really do disagree with each other.
        val byName = VodShelf.arrange(index, "name").map { it.key }
        assertTrue(byName != VodShelf.arrange(index, "added").map { it.key })
        assertTrue(byName != VodShelf.arrange(index, "rating").map { it.key })
        assertTrue(byName != VodShelf.arrange(index, "year").map { it.key })
    }

    // ---- the properties worth stating on their own --------------------------

    @Test
    fun `a film is found by its cast and its director, a series only by its name`() {
        val film = movies.first()
        val index = VodShelf.movies(listOf(film))
        assertEquals(1, VodShelf.arrange(index, "name", query = "mira").size)
        assertEquals(1, VodShelf.arrange(index, "name", query = "ana ").size)
        assertEquals(1, VodShelf.arrange(index, "name", query = film.name.uppercase()).size)
    }

    @Test
    fun `a search cannot match across two fields`() {
        // The searchable text is one string, so "…line" + "mira…" must not be
        // findable as "linemira". The fields are joined with a character that
        // cannot be typed.
        val film = Movie(
            key = "1:x", profileId = 1, streamId = 1, name = "Northern Line",
            cast = "Mira Salt", director = "Ana Road",
        )
        val index = VodShelf.movies(listOf(film))
        assertEquals(0, VodShelf.arrange(index, "name", query = "linemira").size)
        assertEquals(0, VodShelf.arrange(index, "name", query = "line mira").size)
        assertEquals(0, VodShelf.arrange(index, "name", query = "saltana").size)
    }

    @Test
    fun `the genre chips are the library's commonest, most frequent first`() {
        val expected = movies.flatMap { ContentRepository.splitGenres(it.genre) }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(18).map { it.key }
        assertEquals(expected, VodShelf.movies(movies).genres)
        assertTrue(expected.isNotEmpty())
    }

    @Test
    fun `an empty library arranges to nothing and offers no chips`() {
        val index = VodShelf.movies(emptyList())
        assertEquals(0, index.size)
        assertTrue(index.genres.isEmpty())
        assertTrue(VodShelf.arrange(index, "name", query = "x").isEmpty())
    }
}
