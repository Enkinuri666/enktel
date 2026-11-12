package tv.enktel.app

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.Series
import tv.enktel.app.data.repo.ContentRepository
import tv.enktel.app.data.repo.SimilarTitles
import java.io.File

/**
 * The narrow reads return what the wide ones did.
 *
 * Several screens used to collect an entire table to use a sliver of it:
 * Home took every channel to show thirty, a details page took every film to
 * rank twenty, each Coming Soon card took every film to look for one title,
 * and the screensaver took every film to pick twenty at random. On a line
 * with a six-figure library each of those is tens of megabytes, read and
 * held, on a device with one gigabyte for everything.
 *
 * Each now asks the database for the sliver. That is only an improvement if
 * the sliver is the *same* sliver, so every case here computes the answer the
 * old way — from the full table — and compares.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NarrowReadsTest {

    private fun graph() = AppGraph(ApplicationProvider.getApplicationContext())

    private fun seed(g: AppGraph): Long = runBlocking {
        val p = g.db.profileDao().insert(
            Profile(name = "line", kind = "xtream", server = "https://example.invalid", username = "u", password = "p"),
        )
        g.db.contentDao().upsertChannels(
            // Inserted out of order: the rail must follow sortIdx, not rowid.
            (0 until 80).map { i ->
                val order = (i * 37) % 80
                Channel(
                    key = "$p:$i", profileId = p, streamId = i.toLong(), name = "Channel $i",
                    num = i + 1, categoryId = "c", categoryName = "C", sortIdx = order,
                )
            },
        )
        val genres = listOf("Action", "Drama", "Comedy", "Crime")
        g.db.contentDao().upsertMovies(
            (0 until 120).map { i ->
                Movie(
                    key = "$p:m$i", profileId = p, streamId = 1000L + i,
                    // Deliberate duplicates and mixed case and padding: the
                    // title index has to pick the same winner the old scan did.
                    name = when (i % 10) {
                        0 -> "The Northern Line"
                        1 -> "  the northern line "
                        else -> "Film $i"
                    },
                    year = 1990 + i % 30,
                    genre = genres[i % 4] + ", " + genres[(i / 4) % 4],
                    cast = "Mira ${i % 7}, Tomas ${i % 5}",
                    director = "Ana ${i % 9}",
                    rating = (i % 90) / 10.0,
                    poster = if (i % 3 == 0) "" else "https://example.invalid/p$i.jpg",
                    plot = "x".repeat(200),
                )
            },
        )
        g.db.contentDao().upsertSeries(
            (0 until 40).map { i ->
                Series(
                    key = "$p:s$i", profileId = p, seriesId = 2000L + i, name = "Show $i",
                    year = 2000 + i % 20, genre = genres[i % 4],
                    cast = "Mira ${i % 3}", director = "Ana ${i % 4}", rating = (i % 80) / 10.0,
                    poster = if (i % 4 == 0) "" else "https://example.invalid/s$i.jpg",
                )
            },
        )
        p
    }

    @Test
    fun `the head of the channel list is the first of the whole list`() = runBlocking {
        val g = graph(); val p = seed(g)
        val all = g.content.channels(p).first()
        val head = g.content.channelsHead(p, 30).first()
        assertEquals(30, head.size)
        assertEquals(all.take(30).map { it.key }, head.map { it.key })
        // And it really is playlist order rather than insertion order.
        assertTrue(head.map { it.sortIdx } == head.map { it.sortIdx }.sorted())
        assertTrue("the fixture inserted in sortIdx order, so this proves nothing",
            all.map { it.key } != all.sortedBy { it.streamId }.map { it.key })
    }

    @Test
    fun `asking for more channels than exist returns them all`() = runBlocking {
        val g = graph(); val p = seed(g)
        assertEquals(80, g.content.channelsHead(p, 500).first().size)
    }

    @Test
    fun `more like this is the same rail from eight columns as from whole rows`() = runBlocking {
        val g = graph(); val p = seed(g)
        val all = g.content.movies(p).first()
        var compared = 0
        // Several seeds, including ones with and without artwork of their own.
        listOf(1, 2, 3, 17, 44, 90).forEach { i ->
            val seedMovie = all.first { it.key == "$p:m$i" }
            val wide = SimilarTitles.rank(SimilarTitles.of(seedMovie), all) { SimilarTitles.of(it) }
            val narrow = SimilarTitles.rank(SimilarTitles.of(seedMovie), g.content.movieFacets(p)) { SimilarTitles.of(it) }
            assertEquals("seed m$i", wide.map { it.key }, narrow.map { it.key })
            if (wide.isNotEmpty()) compared++
        }
        assertTrue("no seed had anything similar — the comparison was of empty lists", compared >= 4)
    }

    @Test
    fun `series are ranked the same way`() = runBlocking {
        val g = graph(); val p = seed(g)
        val all = g.content.series(p).first()
        val seedShow = all.first { it.key == "$p:s5" }
        val wide = SimilarTitles.rank(SimilarTitles.of(seedShow), all) { SimilarTitles.of(it) }
        val narrow = SimilarTitles.rank(SimilarTitles.of(seedShow), g.content.seriesFacets(p)) { SimilarTitles.of(it) }
        assertTrue(wide.isNotEmpty())
        assertEquals(wide.map { it.key }, narrow.map { it.key })
    }

    @Test
    fun `the projection leaves out exactly the titles that could never be listed`() = runBlocking {
        val g = graph(); val p = seed(g)
        val all = g.content.movies(p).first()
        val facets = g.content.movieFacets(p)
        assertEquals(all.count { it.poster.isNotBlank() }, facets.size)
        assertTrue(facets.all { it.poster.isNotBlank() })
        assertTrue("every film had a poster, so the filter was never exercised", facets.size < all.size)
    }

    @Test
    fun `the title index finds the film the full scan found`() = runBlocking {
        val g = graph(); val p = seed(g)
        val all = g.content.movies(p).first()
        val index = g.content.movieTitleIndex(p)
        // What ComingSoon's card used to do, verbatim.
        fun old(title: String) = all.firstOrNull { it.name.trim().equals(title.trim(), ignoreCase = true) }

        listOf("The Northern Line", "the northern line", "  THE NORTHERN LINE  ", "Film 23", "film 23", "Not On This Line").forEach { title ->
            assertEquals(
                "title '$title'",
                old(title)?.key,
                index[ContentRepository.titleKey(title)],
            )
        }
        assertNull(index[ContentRepository.titleKey("Not On This Line")])
        // Twenty-four rows share that one title; the index holds one of them.
        assertTrue(all.count { it.name.trim().equals("The Northern Line", ignoreCase = true) } > 1)
    }

    @Test
    fun `the screensaver is handed only films with artwork, and no more than it asked for`() = runBlocking {
        val g = graph(); val p = seed(g)
        val picked = g.content.randomPosters(p, 20)
        assertEquals(20, picked.size)
        assertTrue(picked.all { it.poster.isNotBlank() })
        assertEquals("the same film twice", 20, picked.map { it.key }.toSet().size)
        // Fewer than asked for when there are fewer to give.
        assertEquals(0, g.content.randomPosters(p + 999, 20).size)
    }

    // ---- the route ----------------------------------------------------------

    @Test
    fun `a series route carries the key it was given`() {
        assertEquals("seriesDetails/7:4012", tv.enktel.app.ui.vod.seriesDetailsRoute("7:4012"))
    }

    @Test
    fun `no route is built from an escaped dollar sign`() {
        // The bug this guards: `"seriesDetails/${'$'}{it.key}"` is not an
        // interpolation. `${'$'}` is a literal dollar, so the route was the
        // text "seriesDetails/" followed by a dollar and "{it.key}" — and
        // every "More like this" card on a series page opened a series that
        // does not exist. It compiles, it runs, and it is a blank screen.
        val root = listOf(
            "src/main/java/tv/enktel/app",
            "app/src/main/java/tv/enktel/app",
            "androidtv/app/src/main/java/tv/enktel/app",
        ).map(::File).firstOrNull { it.isDirectory } ?: return
        val escaped = "\${'$'}{"
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.trim()
                    val isComment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                    if (!isComment && escaped in line && "navigate(" in line) "${f.name}:${i + 1}  $code" else null
                }
            }.toList()
        assertTrue(
            "these navigate to a route containing a literal dollar-brace instead of a value:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
