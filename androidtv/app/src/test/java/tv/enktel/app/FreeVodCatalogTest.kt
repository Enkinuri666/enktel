package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.m3u.ImportedPlaylist
import tv.enktel.app.data.m3u.M3uEntry
import tv.enktel.app.data.vod.FreeVodCatalog

class FreeVodCatalogTest {

    private fun entry(
        name: String,
        url: String = "https://archive.org/download/x/x.mp4",
        group: String = "Public Domain Movies — English",
        logo: String = "https://archive.org/services/img/x",
    ) = M3uEntry(
        name = name, url = url, tvgId = "", tvgName = name, logo = logo,
        group = group, chno = 0, catchupDays = 0,
    )

    @Test
    fun `splits a trailing year off the title`() {
        assertEquals("Abraham Lincoln" to 1930, FreeVodCatalog.titleAndYear("Abraham Lincoln (1930)"))
        assertEquals("Captain Z-ro" to 1955, FreeVodCatalog.titleAndYear("  Captain Z-ro (1955)  "))
    }

    @Test
    fun `a year in the middle of a title is left alone`() {
        // Only a trailing bracketed year is a date. Trimming on a looser match
        // would rename the film.
        assertEquals("1930 FIFA World Cup" to 0, FreeVodCatalog.titleAndYear("1930 FIFA World Cup"))
        assertEquals("Apollo 13 Remembered" to 0, FreeVodCatalog.titleAndYear("Apollo 13 Remembered"))
    }

    @Test
    fun `a bracketed number that is not a release year stays in the name`() {
        // The scraper takes whatever the Archive's title field says, and that
        // is not always a date in brackets.
        assertEquals("Studio Reel (1080)" to 0, FreeVodCatalog.titleAndYear("Studio Reel (1080)"))
        assertEquals("Future Cut" to 2049, FreeVodCatalog.titleAndYear("Future Cut (2049)"))
        assertEquals("Chapter (12)" to 0, FreeVodCatalog.titleAndYear("Chapter (12)"))
    }

    @Test
    fun `a title that is only a year keeps its name`() {
        // Stripping this would store a row with a blank name, which shows in
        // the library as an unlabelled poster.
        val (name, year) = FreeVodCatalog.titleAndYear("(1935)")
        assertEquals("(1935)", name)
        assertEquals(1935, year)
    }

    @Test
    fun `stream ids cannot collide with a panel's own catalogue or an attachment`() {
        // The row key is "profileId:streamId". A collision does not error — it
        // replaces, so a free title would silently overwrite a film on the
        // line the viewer pays for.
        val rows = FreeVodCatalog.movies(1L, List(5) { entry("Film $it (1940)") }, firstSeenAt = 0)
        for (m in rows) {
            assertTrue("panel ids are small: ${m.streamId}", m.streamId > 1_000_000L)
            assertTrue(
                "must stay below the attachment range: ${m.streamId}",
                m.streamId < 9_000_000_000L,
            )
        }
        // Pin the relationship rather than the constant, so moving either
        // range fails here instead of in the field.
        assertTrue(
            FreeVodCatalog.STREAM_ID_BASE + FreeVodCatalog.MAX_TITLES <
                ImportedPlaylist.MAX_CHANNELS + 9_000_000_000L,
        )
    }

    @Test
    fun `keys are unique across the whole playlist`() {
        val rows = FreeVodCatalog.movies(7L, List(200) { entry("Same Title (1940)") }, firstSeenAt = 0)
        assertEquals(200, rows.map { it.key }.distinct().size)
    }

    @Test
    fun `an entry with no url is dropped rather than stored unplayable`() {
        // A row that looks like a film until it is opened is worse than one
        // that was never listed.
        val rows = FreeVodCatalog.movies(
            1L,
            listOf(entry("Good (1940)"), entry("Bad (1941)", url = ""), entry("Also Good (1942)")),
            firstSeenAt = 0,
        )
        assertEquals(listOf("Good", "Also Good"), rows.map { it.name })
        // ...and the ids stay contiguous, so a dropped row does not leave a
        // hole that the next sync fills with a different film.
        assertEquals(
            listOf(FreeVodCatalog.STREAM_ID_BASE, FreeVodCatalog.STREAM_ID_BASE + 1),
            rows.map { it.streamId },
        )
    }

    @Test
    fun `artwork and playback url survive the mapping`() {
        val rows = FreeVodCatalog.movies(
            1L,
            listOf(entry("Film (1940)", url = "https://a.org/download/x/y.mp4", logo = "https://a.org/img/x")),
            firstSeenAt = 42L,
        )
        assertEquals("https://a.org/download/x/y.mp4", rows[0].url)
        assertEquals("https://a.org/img/x", rows[0].poster)
        assertEquals(42L, rows[0].firstSeenAt)
    }

    @Test
    fun `the file extension comes from the url rather than a default`() {
        val webm = FreeVodCatalog.movies(1L, listOf(entry("A (1940)", url = "https://a.org/x/y.webm")), 0)
        assertEquals("webm", webm[0].ext)
        // No extension at all still yields something playable to ask for.
        // Reading the whole URL rather than its last segment finds the dot in
        // the hostname and calls this an `org/` file.
        val bare = FreeVodCatalog.movies(1L, listOf(entry("B (1940)", url = "https://a.org/x/y")), 0)
        assertEquals("mp4", bare[0].ext)
        // A query string is not part of the filename.
        val q = FreeVodCatalog.movies(1L, listOf(entry("C (1940)", url = "https://a.org/x/y.mp4?t=1")), 0)
        assertEquals("mp4", q[0].ext)
        // A dot inside a filename is not a type.
        val dotted = FreeVodCatalog.movies(1L, listOf(entry("D (1940)", url = "https://a.org/x/Film.Part.2")), 0)
        assertEquals("mp4", dotted[0].ext)
    }

    @Test
    fun `categories are the playlist's groups, deduplicated and in order`() {
        val entries = listOf(
            entry("A (1940)", group = "Public Domain Movies — English"),
            entry("B (1941)", group = "Public Domain Series — English"),
            entry("C (1942)", group = "Public Domain Movies — English"),
        )
        val cats = FreeVodCatalog.categories(3L, entries)
        assertEquals(
            listOf("Public Domain Movies — English", "Public Domain Series — English"),
            cats.map { it.name },
        )
        assertTrue(cats.all { it.kind == "vod" && it.profileId == 3L })
        assertEquals(listOf(0, 1), cats.map { it.sortIdx })
    }

    @Test
    fun `an ungrouped entry still lands in a named category`() {
        // A blank categoryId would file the title under a rail with no name,
        // which the library renders as an empty heading.
        val entries = listOf(entry("A (1940)", group = ""))
        assertEquals("Public Domain", FreeVodCatalog.categories(1L, entries).single().name)
        assertEquals("Public Domain", FreeVodCatalog.movies(1L, entries, 0).single().categoryId)
    }

    @Test
    fun `every movie's category exists`() {
        // The library joins on categoryId. A row pointing at a category that
        // was never written disappears from every rail without erroring.
        val entries = listOf(
            entry("A (1940)", group = "One"),
            entry("B (1941)", group = ""),
            entry("C (1942)", group = "Two"),
        )
        val ids = FreeVodCatalog.categories(1L, entries).map { it.categoryId }.toSet()
        assertTrue(FreeVodCatalog.movies(1L, entries, 0).all { it.categoryId in ids })
    }
}
