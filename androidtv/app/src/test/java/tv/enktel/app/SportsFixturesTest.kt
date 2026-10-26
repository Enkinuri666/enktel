package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.repo.SportsEvent
import tv.enktel.app.data.repo.SportsRepository

/**
 * One match, one card.
 *
 * The Sports Hub listed a card per *feed*, and a real IPTV line carries one
 * fixture several times over — SD, HD, FHD, a backup, one per country prefix,
 * plus the league channel and Main Event both showing it. LIVE NOW was five
 * rows of the same match before it reached the second one, and the counter in
 * the header said five.
 *
 * Search had folded its duplicates since it was written. The fold is now one
 * routine serving both, so these tests are about the two things the hub needs
 * from it that search did not: which feed survives, and which rail the fixture
 * belongs in when the clock has moved on since the scan.
 */
class SportsFixturesTest {

    private var nextId = 1L
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun channel(
        name: String,
        archive: Boolean = false,
        epgId: String = name.lowercase().replace(' ', '.'),
    ) = Channel(
        key = "1:$name", profileId = 1, streamId = nextId++, name = name,
        categoryName = "UK | SPORTS", epgId = epgId, hasArchive = archive,
    )

    private fun event(
        title: String,
        on: Channel,
        startMs: Long = now - hour,
        endMs: Long = now + hour,
        phase: String = "LIVE",
    ) = SportsEvent(
        program = EpgProgram(
            id = nextId++, profileId = 1, epgId = on.epgId,
            startMs = startMs, endMs = endMs, title = title,
        ),
        channel = on, sport = "Football", phase = phase,
    )

    private fun fold(vararg events: SportsEvent) =
        SportsRepository.foldFeeds(events.map { it to SportsRepository.feedRank(it) })
            .map { it.first }

    // ---- which feeds are the same fixture ----------------------------------

    @Test
    fun `five lines carrying one match are one card`() {
        val fixtures = fold(
            event("Arsenal v Chelsea", channel("Sky Sports Main Event HD")),
            event("Arsenal v Chelsea", channel("Sky Sports Main Event FHD")),
            event("Arsenal v Chelsea", channel("UK: SKY SPORTS PL SD")),
            event("Arsenal v Chelsea", channel("Sky Sports Premier League")),
            event("Arsenal v Chelsea", channel("PPV 04 Arsenal vs Chelsea")),
        )
        assertEquals(1, fixtures.size)
        assertEquals("and the card says how many others there are", 4, fixtures[0].alsoOn)
        assertEquals(5, fixtures[0].feeds.size)
    }

    @Test
    fun `two different matches stay two cards`() {
        val fixtures = fold(
            event("Arsenal v Chelsea", channel("Sky Sports Main Event")),
            event("Ajax v PSV", channel("Viaplay 1")),
        )
        assertEquals(2, fixtures.size)
        assertTrue(fixtures.all { it.alsoOn == 0 })
    }

    @Test
    fun `the same title tomorrow is not another feed of tonight`() {
        // Two legs of a tie share a title. Folding them would hide one.
        val ch = channel("Sky Sports Main Event")
        val fixtures = fold(
            event("Arsenal v Chelsea", ch, now - hour, now + hour),
            event("Arsenal v Chelsea", ch, now + 24 * hour, now + 26 * hour),
        )
        assertEquals(2, fixtures.size)
    }

    @Test
    fun `kick-offs a minute apart are the same fixture`() {
        // Providers publish the same kick-off a few seconds apart across their
        // own duplicates, and the drift crosses a minute boundary as often as
        // not — which is the case a `startMs / 60_000` bucket gets wrong.
        val fixtures = fold(
            event("Ajax v PSV", channel("Viaplay 1"), now - hour, now + hour),
            event("Ajax v PSV", channel("Viaplay 1 HD"), now - hour + 40_000, now + hour),
        )
        assertEquals(1, fixtures.size)
    }

    // ---- which feed survives the fold --------------------------------------

    @Test
    fun `the feed with the archive wins, so replay still works`() {
        // The whole reason the survivor is chosen rather than taken: CATCH-UP
        // and REPLAYS play a finished match out of the provider's archive, and
        // only some lines carry one. Fold five feeds down to a line without
        // one and the replay button has nothing to open.
        val fixtures = fold(
            event("Arsenal v Chelsea", channel("Sky Sports Main Event")),
            event("Arsenal v Chelsea", channel("Sky Sports Main Event Extra Backup", archive = true)),
        )
        assertEquals(1, fixtures.size)
        assertTrue(fixtures[0].event.channel.hasArchive)
    }

    @Test
    fun `failing that, the least decorated line wins`() {
        // "Sky Sports Main Event" over "Sky Sports Main Event Extra": the
        // surplus word usually marks a different feed of the same brand.
        val fixtures = fold(
            event("Arsenal v Chelsea", channel("UK: Sky Sports Main Event Extra FHD")),
            event("Arsenal v Chelsea", channel("Sky Sports Main Event")),
        )
        assertEquals("Sky Sports Main Event", fixtures[0].event.channel.name)
    }

    @Test
    fun `quality decoration is not a surplus word`() {
        // "UK: SKY SPORTS MAIN EVENT FHD" and "Sky Sports Main Event" are the
        // same channel, so neither should outrank the other on name alone —
        // whichever wins, what must not happen is a tie broken by the HD tag.
        val plain = channel("Sky Sports Main Event")
        val dressed = channel("UK: SKY SPORTS MAIN EVENT FHD ᴴᴰ")
        assertEquals(
            SportsRepository.feedRank(event("Arsenal v Chelsea", plain)),
            SportsRepository.feedRank(event("Arsenal v Chelsea", dressed)),
        )
    }

    // ---- which rail it belongs in ------------------------------------------

    @Test
    fun `a match that kicks off while you watch moves to LIVE`() {
        // The scan buckets once. A hub left open waiting for a kick-off — the
        // most likely thing anyone does with this screen — kept the match in
        // UPCOMING afterwards, counting down to a time in the past, while LIVE
        // NOW stayed empty.
        val fixtures = fold(
            event(
                "Arsenal v Chelsea", channel("Sky Sports Main Event"),
                startMs = now + 10 * 60_000, endMs = now + 2 * hour,
                phase = "UPCOMING",
            ),
        )
        val fx = fixtures.single()
        assertEquals("UPCOMING", fx.phaseAt(now))
        assertEquals("LIVE", fx.phaseAt(now + 11 * 60_000))
        assertEquals("FINISHED", fx.phaseAt(now + 3 * hour))
    }

    // ---- whose fixtures come first -----------------------------------------

    @Test
    fun `a followed team comes to the top of its rail`() {
        // Settings has promised this in writing for several versions while
        // only the Live sport finder did it.
        val ch = channel("Sky Sports Main Event")
        val rail = fold(
            event("Ajax v PSV", ch, now, now + hour),
            event("Roma v Lazio", ch, now, now + hour),
            event("Arsenal v Chelsea", ch, now, now + hour),
        )
        val pinned = SportsRepository.pinFollowed(rail, listOf("arsenal"))
        assertEquals("Arsenal v Chelsea", pinned.first().event.title)
        assertEquals("every fixture is still there", rail.size, pinned.size)
    }

    @Test
    fun `pinning keeps kick-off order within each half`() {
        // A followed team should not reshuffle the rest of the rail.
        val ch = channel("Sky Sports Main Event")
        val rail = fold(
            event("Ajax v PSV", ch, now + hour, now + 2 * hour),
            event("Roma v Lazio", ch, now + 2 * hour, now + 3 * hour),
        ).sortedBy { it.event.startMs }
        assertEquals(rail.map { it.event.title }, SportsRepository.pinFollowed(rail, listOf("arsenal")).map { it.event.title })
    }

    @Test
    fun `a followed team named only on the channel still counts`() {
        // Pay-per-view and event lines carry the fixture in the channel name
        // and have no guide entry worth the name.
        val fx = fold(event("Live Boxing", channel("UK: PPV 04 | Usyk vs Fury"))).single()
        assertTrue(SportsRepository.followsAny(fx, listOf("usyk")))
    }

    @Test
    fun `following nothing pins nothing and filters nothing`() {
        val rail = fold(event("Ajax v PSV", channel("Viaplay 1")))
        assertEquals(rail, SportsRepository.pinFollowed(rail, emptyList()))
        assertFalse(SportsRepository.followsAny(rail.single(), emptyList()))
    }

    @Test
    fun `an empty scan folds to nothing rather than throwing`() {
        assertTrue(SportsRepository.foldFeeds(emptyList()).isEmpty())
        assertTrue(SportsRepository.pinFollowed(emptyList(), listOf("arsenal")).isEmpty())
    }
}
