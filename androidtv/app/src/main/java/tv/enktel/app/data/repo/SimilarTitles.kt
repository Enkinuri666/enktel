package tv.enktel.app.data.repo

import kotlin.math.abs

/**
 * "More like this" — other titles in *this* catalogue that resemble the one
 * being looked at.
 *
 * The detail screens described a title and then stopped. Someone who had
 * decided against a film had nowhere to go but Back, and someone who had
 * enjoyed one had no way to find its neighbours; the app knew perfectly well
 * what else was on the line and never said.
 *
 * ### Only what can actually be played
 *
 * The pool is the user's own catalogue, not a recommendation service. A rail
 * of titles the line does not carry would be worse than no rail — it is the
 * difference between a suggestion and an advert for someone else's library.
 *
 * ### What counts as alike
 *
 * Genre first, because it is the signal every panel supplies and the one a
 * viewer means. Then the people: a shared director is a strong steer and rare
 * enough to be worth more than a genre; shared cast is weaker and capped, or a
 * prolific character actor starts dominating every rail in the app. Era and
 * quality only nudge, because they are true of thousands of titles and would
 * otherwise fill the rail with things that merely came out at the same time.
 *
 * A title with no signal at all scores zero and is left out. Six unrelated
 * films under "More like this" costs more trust than an absent rail.
 */
object SimilarTitles {

    /** Everything the ranking looks at, pulled out of whatever row it came from. */
    data class Facets(
        val key: String,
        val genres: List<String>,
        val cast: List<String>,
        val director: String,
        val year: Int,
        val rating: Double,
        /** False for a row with no poster — a rail of blank cards is not a rail. */
        val hasArt: Boolean,
    )

    private const val GENRE = 10
    private const val DIRECTOR = 14
    private const val CAST = 6
    private const val MAX_CAST_MATCHES = 3
    private const val SAME_ERA = 4
    private const val NEAR_ERA = 2
    private const val WELL_RATED = 2

    /** How alike [other] is to [seed]. Zero means "do not show this". */
    fun score(seed: Facets, other: Facets): Int {
        if (other.key == seed.key || !other.hasArt) return 0

        val seedGenres = seed.genres.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val otherGenres = other.genres.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val sharedGenres = seedGenres.intersect(otherGenres).size

        val seedCast = seed.cast.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val sharedCast = other.cast
            .map { it.trim().lowercase() }
            .count { it.isNotBlank() && it in seedCast }
            .coerceAtMost(MAX_CAST_MATCHES)

        val sameDirector = seed.director.isNotBlank() &&
            seed.director.trim().equals(other.director.trim(), ignoreCase = true)

        // Nothing in common with the title on screen. Era and rating are not
        // evidence of resemblance on their own — half the catalogue shares them.
        if (sharedGenres == 0 && sharedCast == 0 && !sameDirector) return 0

        var total = sharedGenres * GENRE + sharedCast * CAST
        if (sameDirector) total += DIRECTOR
        if (seed.year > 0 && other.year > 0) {
            val gap = abs(seed.year - other.year)
            total += when {
                gap <= 3 -> SAME_ERA
                gap <= 8 -> NEAR_ERA
                else -> 0
            }
        }
        if (other.rating >= 7.0) total += WELL_RATED
        return total
    }

    /**
     * The pool, ranked, best first, with the seed and everything unrelated
     * removed.
     *
     * Ties break on rating so that when two titles are equally alike, the
     * better one leads — a rail is only as good as its first three cards.
     */
    fun <T> rank(
        seed: Facets,
        pool: List<T>,
        limit: Int = 20,
        facets: (T) -> Facets,
    ): List<T> =
        pool.asSequence()
            .map { it to facets(it) }
            .map { (row, f) -> Triple(row, score(seed, f), f.rating) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<T, Int, Double>> { it.second }.thenByDescending { it.third })
            .take(limit)
            .map { it.first }
            .toList()

    /** Facets of a film row. */
    fun of(m: tv.enktel.app.data.db.Movie): Facets = Facets(
        key = m.key,
        genres = ContentRepository.splitGenres(m.genre),
        cast = splitPeople(m.cast),
        director = m.director,
        year = m.year,
        rating = m.rating,
        hasArt = m.poster.isNotBlank(),
    )

    /** Facets of a series row. */
    fun of(s: tv.enktel.app.data.db.Series): Facets = Facets(
        key = s.key,
        genres = ContentRepository.splitGenres(s.genre),
        cast = splitPeople(s.cast),
        director = s.director,
        year = s.year,
        rating = s.rating,
        hasArt = s.poster.isNotBlank(),
    )

    /** Panels write cast lists comma-separated, occasionally with a stray "and". */
    fun splitPeople(raw: String): List<String> =
        raw.split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("and", ignoreCase = true) }
}
