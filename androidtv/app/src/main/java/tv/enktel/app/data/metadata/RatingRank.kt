package tv.enktel.app.data.metadata

import kotlin.math.roundToInt

/**
 * How good a title is, as one comparable number, and how to say it on screen.
 *
 * The catalogue already carried three separate facts — the panel's own rating,
 * IMDb's rating, and how many votes that rating rests on — and the library
 * sorted on the first of them. On an M3U lineup or a free catalogue the panel's
 * number is almost always zero, so "Top rated" ordered a shelf of zeroes while
 * a real rating sat unused in the same row.
 */
object RatingRank {

    /**
     * Votes below which a rating is treated as provisional.
     *
     * A raw sort puts a 10.0 from eleven voters above everything ever made,
     * which is how a "Top rated" rail fills up with titles nobody has heard
     * of. This is the point where a score stops being an opinion and starts
     * being a measurement.
     */
    const val CREDIBLE_VOTES = 1_000

    /**
     * What an unremarkable film scores, used as the prior.
     *
     * Ratings do not average 5 out of 10 — people mostly rate things they
     * chose to watch, so the distribution sits high. Pulling toward 5 would
     * push every lightly-voted title below every heavily-voted one regardless
     * of how good it is, which is a different distortion rather than a fix.
     */
    const val PRIOR = 6.8

    /**
     * One number to sort by, highest first. 0 when nothing is known.
     *
     * IMDb's rating wins when there is one, weighted toward [PRIOR] by how few
     * votes it rests on — the same shape as IMDb's own weighted rank. The
     * panel's number is the fallback and is used unweighted, because it
     * arrives with no vote count to weight it by.
     */
    fun score(imdbRating: Double, imdbVotes: Int, panelRating: Double): Double {
        if (imdbRating <= 0.0) return panelRating.coerceAtLeast(0.0)
        val v = imdbVotes.coerceAtLeast(0).toDouble()
        val m = CREDIBLE_VOTES.toDouble()
        // With no vote count at all, trust the rating as given rather than
        // discounting it to the prior: an unknown count is not the same
        // claim as a count of zero.
        if (imdbVotes <= 0) return imdbRating
        return (v / (v + m)) * imdbRating + (m / (v + m)) * PRIOR
    }

    /**
     * `2357891` → `2.4M`. Blank when there is no count to show.
     *
     * Compact because it sits inside a badge next to the rating, on a screen
     * read from across a room. The exact number is not the point — the order
     * of magnitude is, because that is what says whether the rating means
     * anything.
     */
    fun formatVotes(votes: Int): String = when {
        votes <= 0 -> ""
        votes < 1_000 -> votes.toString()
        votes < 1_000_000 -> {
            val k = votes / 1_000.0
            if (k < 10) "${trim1(k)}K" else "${k.roundToInt()}K"
        }
        else -> {
            val m = votes / 1_000_000.0
            if (m < 10) "${trim1(m)}M" else "${m.roundToInt()}M"
        }
    }

    /** One decimal, with a trailing `.0` dropped: 2.0 reads as noise. */
    private fun trim1(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    /**
     * The badge text: `IMDb 8.4 · 2.4M`, or "" when there is no rating.
     *
     * The vote count is attached to the rating rather than shown separately
     * because it is what makes the rating readable. 9.4 from forty people and
     * 9.4 from two million are the same badge without it, and the first one is
     * not a recommendation.
     */
    fun badge(imdbRating: Double, imdbVotes: Int): String {
        if (imdbRating <= 0.0) return ""
        val votes = formatVotes(imdbVotes)
        val rating = "IMDb %.1f".format(imdbRating)
        return if (votes.isEmpty()) rating else "$rating · $votes"
    }
}
