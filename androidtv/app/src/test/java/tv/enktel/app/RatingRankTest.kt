package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.metadata.RatingRank

class RatingRankTest {

    @Test
    fun `a lightly voted perfect score does not outrank a well voted great one`() {
        // The whole reason this exists. Sorting on the raw number puts a 10.0
        // from eleven voters above everything ever made, which is how a "Top
        // rated" rail fills with titles nobody has heard of.
        val obscure = RatingRank.score(imdbRating = 10.0, imdbVotes = 11, panelRating = 0.0)
        val classic = RatingRank.score(imdbRating = 8.9, imdbVotes = 2_000_000, panelRating = 0.0)
        assertTrue("$obscure should rank below $classic", obscure < classic)
    }

    @Test
    fun `among well voted titles the better rating still wins`() {
        // The weighting must not flatten real differences once both ratings
        // rest on enough votes to mean something.
        val better = RatingRank.score(9.0, 500_000, 0.0)
        val worse = RatingRank.score(7.5, 500_000, 0.0)
        assertTrue(better > worse)
    }

    @Test
    fun `more votes on the same rating ranks higher`() {
        val confident = RatingRank.score(8.0, 900_000, 0.0)
        val provisional = RatingRank.score(8.0, 1_200, 0.0)
        assertTrue(confident > provisional)
    }

    @Test
    fun `the panel's rating is the fallback, not an also-ran`() {
        // On an M3U lineup the panel number is often all there is, and before
        // this it was the only thing the sort looked at.
        assertEquals(7.2, RatingRank.score(0.0, 0, 7.2), 0.0001)
        // ...but a real IMDb rating beats it when both exist.
        assertTrue(RatingRank.score(8.5, 100_000, 1.0) > RatingRank.score(0.0, 0, 1.0))
    }

    @Test
    fun `nothing known scores zero rather than something misleading`() {
        assertEquals(0.0, RatingRank.score(0.0, 0, 0.0), 0.0001)
        // A negative from a malformed feed must not sort above an unrated title.
        assertEquals(0.0, RatingRank.score(0.0, 0, -3.0), 0.0001)
    }

    @Test
    fun `an unknown vote count is not treated as a count of zero`() {
        // Some sources give a rating with no count. Discounting that all the
        // way to the prior would bury it beneath titles that are worse but
        // better documented — a different distortion, not a fix.
        assertEquals(8.4, RatingRank.score(8.4, 0, 0.0), 0.0001)
    }

    // ── display ────────────────────────────────────────────────────────

    @Test
    fun `vote counts read as magnitudes, not digits`() {
        assertEquals("940", RatingRank.formatVotes(940))
        assertEquals("2.4K", RatingRank.formatVotes(2_400))
        assertEquals("12K", RatingRank.formatVotes(12_345))
        assertEquals("2.4M", RatingRank.formatVotes(2_357_891))
        assertEquals("12M", RatingRank.formatVotes(12_000_000))
    }

    @Test
    fun `a round number does not carry a pointless decimal`() {
        assertEquals("2K", RatingRank.formatVotes(2_000))
        assertEquals("1M", RatingRank.formatVotes(1_000_000))
    }

    @Test
    fun `no votes prints nothing rather than a zero`() {
        assertEquals("", RatingRank.formatVotes(0))
        assertEquals("", RatingRank.formatVotes(-5))
    }

    @Test
    fun `the badge attaches the count to the rating`() {
        // 9.4 from forty people and 9.4 from two million are the same badge
        // without it, and only one of them is a recommendation.
        assertEquals("IMDb 8.4 · 2.4M", RatingRank.badge(8.4, 2_357_891))
        assertEquals("IMDb 9.4 · 40", RatingRank.badge(9.4, 40))
    }

    @Test
    fun `a rating with no count still shows the rating`() {
        assertEquals("IMDb 8.4", RatingRank.badge(8.4, 0))
    }

    @Test
    fun `no rating means no badge at all`() {
        assertEquals("", RatingRank.badge(0.0, 2_000_000))
    }
}
