package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.enktel.app.data.repo.EpisodeInfo
import tv.enktel.app.data.repo.NextEpisode

/**
 * What plays after the episode now playing.
 *
 * A tester reported next-episode autoplay "not functioning at all". Part of
 * that was the roll-over decision (see [NextUpTest]); the rest was here. The
 * player was handed a pre-built route for the next episode, and that route
 * could not itself carry a route for the one after it — so a binge advanced
 * exactly once and then sat still. From the sofa, one automatic episode
 * followed by silence is indistinguishable from none at all.
 *
 * These pin the successor rule now that the player applies it for itself.
 */
class NextEpisodeTest {

    private fun ep(id: Long, season: Int, number: Int) =
        EpisodeInfo(
            id = id, season = season, episode = number, title = "E$number",
            ext = "mkv", plot = "", durationSecs = 0, poster = "",
        )

    private val twoSeasons = mapOf(
        1 to listOf(ep(11, 1, 1), ep(12, 1, 2), ep(13, 1, 3)),
        2 to listOf(ep(21, 2, 1), ep(22, 2, 2)),
    )

    @Test
    fun `the next episode of the same season`() {
        assertEquals(12L, NextEpisode.after(twoSeasons, 11)?.id)
        assertEquals(13L, NextEpisode.after(twoSeasons, 12)?.id)
    }

    @Test
    fun `a finale rolls into the following season`() {
        // The whole point: stopping at the end of season one strands a viewer
        // on a cliffhanger that has a resolution sitting right there.
        val next = NextEpisode.after(twoSeasons, 13)
        assertEquals(21L, next?.id)
        assertEquals(2, next?.season)
    }

    @Test
    fun `the last episode of the series has nothing after it`() {
        assertNull(NextEpisode.after(twoSeasons, 22))
    }

    @Test
    fun `an episode that is not in the map has no successor`() {
        // Not "the first episode of season one", which is what an unguarded
        // indexOf returning -1 would have produced — replaying the pilot after
        // every episode would be a memorable way to fail.
        assertNull(NextEpisode.after(twoSeasons, 999))
    }

    @Test
    fun `seasons are followed in numeric order, not map order`() {
        // Panels return seasons in whatever order they please, and season 10
        // sorts before season 2 as text.
        val outOfOrder = linkedMapOf(
            10 to listOf(ep(101, 10, 1)),
            2 to listOf(ep(21, 2, 1)),
            1 to listOf(ep(11, 1, 1)),
        )
        assertEquals(21L, NextEpisode.after(outOfOrder, 11)?.id)
        assertEquals(101L, NextEpisode.after(outOfOrder, 21)?.id)
    }

    @Test
    fun `an empty season is stepped over rather than ending the run`() {
        // Panels list seasons they hold no episodes for. Treating one as the
        // end of the series would strand the viewer on it.
        val gapped = mapOf(
            1 to listOf(ep(11, 1, 1)),
            2 to emptyList(),
            3 to listOf(ep(31, 3, 1)),
        )
        assertEquals(31L, NextEpisode.after(gapped, 11)?.id)
    }

    @Test
    fun `a single episode series has no next`() {
        assertNull(NextEpisode.after(mapOf(1 to listOf(ep(11, 1, 1))), 11))
    }

    @Test
    fun `no episodes at all is not a crash`() {
        assertNull(NextEpisode.after(emptyMap(), 11))
    }

    @Test
    fun `the label and title read the way the cards want them`() {
        val e = ep(42, 2, 4)
        assertEquals("S2 E4 · E4", NextEpisode.label(e))
        assertEquals("Thrones S2E4 · E4", NextEpisode.title("Thrones", e))
    }
}
