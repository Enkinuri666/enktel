package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.repo.SportsRepository

/**
 * The sport half of unified search.
 *
 * Search had four rails — channels, movies, series and the guide — and a
 * fixture landed in the guide rail as an untyped programme title, sorted by
 * start time behind whatever else that channel happened to be showing. So
 * "arsenal" found the match only if you read far enough, and never said it was
 * football, on now, or on three other channels as well.
 *
 * [SportsRepository.searchHits] cuts the fixtures out of the guide hits the
 * screen already has. Every assertion below is about a way that classification
 * can be wrong in a way nobody would notice: a film mistaken for a match, a
 * match hidden behind five duplicates of itself, or a fixture on a channel the
 * keyword list has never heard of.
 */
class SportsSearchTest {

    private var nextId = 1L

    private fun channel(
        name: String,
        category: String = "",
        epgId: String = name.lowercase().replace(' ', '.'),
    ) = Channel(
        key = "1:$name", profileId = 1, streamId = nextId++, name = name,
        categoryName = category, epgId = epgId,
    )

    private fun programme(
        title: String,
        on: Channel,
        startMs: Long = 0,
        endMs: Long = 0,
        desc: String = "",
    ) = EpgProgram(
        id = nextId++, profileId = 1, epgId = on.epgId,
        startMs = startMs, endMs = endMs, title = title, desc = desc,
    )

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `a named competition is a fixture, and carries its sport`() {
        val ch = channel("Sky Sports Main Event", "UK | SPORTS")
        val hits = SportsRepository.searchHits(
            listOf(programme("Premier League: Arsenal v Chelsea", ch, now - hour, now + hour)),
            listOf(ch),
            now,
        )
        assertEquals(1, hits.size)
        assertEquals("Football", hits[0].event.sport)
        assertEquals("LIVE", hits[0].event.phase)
    }

    @Test
    fun `a fixture with no league named still counts on a sports channel`() {
        // "Sky Sports Football HD" showing nothing but two team names is the
        // common case, and the one a keyword list alone cannot answer.
        val ch = channel("Sky Sports Football HD", "UK | SPORTS")
        val hits = SportsRepository.searchHits(
            listOf(programme("Ajax - PSV", ch, now + hour, now + 3 * hour)),
            listOf(ch),
            now,
        )
        assertEquals(1, hits.size)
        assertEquals("Football", hits[0].event.sport)
        assertEquals("UPCOMING", hits[0].event.phase)
    }

    @Test
    fun `Kramer vs Kramer is not a fixture`() {
        // The "A v B" shape is only trusted on a channel that shows sport.
        // Without this the film rail and the sport rail both claim it, and the
        // sport one is wrong.
        val film = channel("Sky Cinema Greats", "UK | MOVIES")
        val hits = SportsRepository.searchHits(
            listOf(programme("Kramer vs. Kramer", film, now + hour, now + 3 * hour)),
            listOf(film),
            now,
        )
        assertTrue("a film is not a fixture: $hits", hits.isEmpty())
    }

    @Test
    fun `one fixture on five feeds is one row`() {
        // A panel carries the same match on the flagship, the league channel
        // and an SD duplicate. Five rows for one thing to watch reads as a
        // fault in the search rather than as a choice of feeds.
        val feeds = listOf(
            channel("Sky Sports Main Event", "UK | SPORTS"),
            channel("Sky Sports Premier League", "UK | SPORTS"),
            channel("Sky Sports Main Event SD", "UK | SPORTS"),
            channel("BT Sport 1", "UK | SPORTS"),
            channel("TNT Sports 1 HD", "UK | SPORTS"),
        )
        val progs = feeds.map {
            programme("Premier League: Arsenal v Chelsea", it, now - hour, now + hour)
        }
        val hits = SportsRepository.searchHits(progs, feeds, now)
        assertEquals("the five feeds fold into one row", 1, hits.size)
        assertEquals("and the row says how many others there are", 4, hits[0].alsoOn)
    }

    @Test
    fun `feeds that disagree about the start time by seconds are still one fixture`() {
        // Panels routinely publish the same kick-off a few seconds apart
        // across their own duplicates, which is enough to defeat an exact
        // match on startMs.
        val a = channel("beIN SPORTS 1", "SPORTS")
        val b = channel("beIN SPORTS 1 HD", "SPORTS")
        val hits = SportsRepository.searchHits(
            listOf(
                programme("Serie A: Milan v Inter", a, now + hour, now + 3 * hour),
                programme("Serie A: Milan v Inter", b, now + hour + 40_000, now + 3 * hour),
            ),
            listOf(a, b),
            now,
        )
        assertEquals(1, hits.size)
        assertEquals(1, hits[0].alsoOn)
    }

    @Test
    fun `what is on now comes before what is on later`() {
        val ch = channel("ESPN", "US | SPORTS")
        val later = programme("NBA: Lakers v Celtics", ch, now + 5 * hour, now + 8 * hour)
        val onNow = programme("NBA: Heat v Knicks", ch, now - hour, now + hour)
        val hits = SportsRepository.searchHits(listOf(later, onNow), listOf(ch), now)
        assertEquals(listOf("LIVE", "UPCOMING"), hits.map { it.event.phase })
        assertEquals("NBA: Heat v Knicks", hits[0].event.title)
    }

    @Test
    fun `a programme whose channel is not in the list is dropped rather than guessed at`() {
        val ch = channel("Sky Sports Main Event", "UK | SPORTS")
        val orphan = programme("Premier League: Arsenal v Chelsea", ch, now, now + hour)
        assertTrue(SportsRepository.searchHits(listOf(orphan), emptyList(), now).isEmpty())
    }

    @Test
    fun `the news is not a fixture just because it matched`() {
        val ch = channel("BBC News", "UK | NEWS")
        val hits = SportsRepository.searchHits(
            listOf(programme("Sportsday", ch, now, now + hour)),
            listOf(ch),
            now,
        )
        // "sport" appears in the title, but nothing here is a competition, a
        // fixture, or a channel that exists to show one.
        assertTrue("news is not sport: $hits", hits.isEmpty())
    }

    @Test
    fun `anything on a clearly-sports channel is surfaced rather than dropped`() {
        // The weakest rule, and deliberately kept: these rows have already
        // matched what the viewer typed, so "it is on a sports channel" is
        // evidence rather than the guess it would be during a blind scan.
        val ch = channel("DAZN 1", "SPORTS")
        val hits = SportsRepository.searchHits(
            listOf(programme("Fight Camp Weigh-In", ch, now, now + hour)),
            listOf(ch),
            now,
        )
        assertEquals(1, hits.size)
    }

    @Test
    fun `an empty search costs nothing`() {
        assertTrue(SportsRepository.searchHits(emptyList(), emptyList(), now).isEmpty())
        assertTrue(
            SportsRepository.searchHits(emptyList(), listOf(channel("ESPN", "SPORTS")), now)
                .isEmpty(),
        )
    }

    @Test
    fun `the rail is bounded`() {
        val ch = channel("Sky Sports Main Event", "UK | SPORTS")
        val many = (1..200).map {
            programme("Premier League: Team $it v Rivals $it", ch, now + it * hour, now + (it + 2) * hour)
        }
        assertEquals(40, SportsRepository.searchHits(many, listOf(ch), now).size)
        assertEquals(5, SportsRepository.searchHits(many, listOf(ch), now, limit = 5).size)
    }

    @Test
    fun `a channel with no guide id cannot host a fixture`() {
        // An m3u line with no tvg-id has no EPG rows to match, and joining on
        // a blank id would attach every unattributed programme to it.
        val blank = channel("Sky Sports Main Event", "UK | SPORTS", epgId = "")
        val prog = EpgProgram(
            id = 99, profileId = 1, epgId = "", startMs = now, endMs = now + hour,
            title = "Premier League: Arsenal v Chelsea",
        )
        assertTrue(SportsRepository.searchHits(listOf(prog), listOf(blank), now).isEmpty())
    }
}
