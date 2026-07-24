package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.enktel.app.data.db.Movie
import java.util.concurrent.TimeUnit

/**
 * Simple on-device recommender: uses the user's watch history + favorites to score movies
 * by shared genre/decade. Zero external calls — everything stays local.
 */
class RecommendationsRepository(private val content: ContentRepository) {

    /** Top N movies sharing genres with recently watched items, minus the ones already watched. */
    suspend fun becauseYouWatched(profileId: Long, n: Int = 15): List<Movie> = withContext(Dispatchers.Default) {
        val history = content.continueWatching(profileId, 30).first()
        if (history.isEmpty()) return@withContext emptyList()
        val seedIds = history.map { it.refId }.toHashSet()
        val allMovies = content.movies(profileId).first()
        val seed = allMovies.filter { it.streamId in seedIds }
        if (seed.isEmpty()) return@withContext emptyList()

        val seedGenres = seed.flatMap { ContentRepository.splitGenres(it.genre) }
            .groupingBy { it.lowercase() }.eachCount()

        allMovies.asSequence()
            .filter { it.streamId !in seedIds && it.genre.isNotBlank() }
            .map { m ->
                val overlap = ContentRepository.splitGenres(m.genre)
                    .sumOf { seedGenres[it.lowercase()] ?: 0 }
                val ratingBoost = (m.rating / 2).toInt()
                m to (overlap * 3 + ratingBoost)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
            .toList()
    }

    /** Movies added in the last 14 days, most recent first. */
    suspend fun newThisWeek(profileId: Long, n: Int = 15): List<Movie> = withContext(Dispatchers.Default) {
        val cutoff = (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)) / 1000
        content.movies(profileId).first().asSequence()
            .filter { it.addedAt > cutoff }
            .sortedByDescending { it.addedAt }
            .take(n)
            .toList()
    }

    /** Trending = top-rated with strong catalog volume signals (rating floor 6.5). */
    suspend fun trending(profileId: Long, n: Int = 15): List<Movie> = withContext(Dispatchers.Default) {
        content.movies(profileId).first().asSequence()
            .filter { it.rating >= 6.5 && it.poster.isNotBlank() }
            .sortedByDescending { it.rating + it.addedAt / 1_000_000.0 }
            .take(n)
            .toList()
    }

    /** Latest Releases — freshest additions to the catalogue, refreshed daily by ContentRefreshWorker. */
    suspend fun latestReleases(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        content.movies(profileId).first().asSequence()
            .filter { it.poster.isNotBlank() }
            .sortedByDescending { it.addedAt }
            .take(n)
            .toList()
    }

    /** Coming Soon — movies whose release year is the current year or later, sorted by year desc.
     *  Xtream/Eagle-style panels don't expose a dedicated "coming-soon" endpoint, so we surface
     *  the newest year-labelled titles as upcoming/premiere content. */
    suspend fun comingSoon(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        content.movies(profileId).first().asSequence()
            .filter { it.poster.isNotBlank() && it.year >= currentYear }
            .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt })
            .take(n)
            .toList()
    }

    // ---- Mood / vibe rails --------------------------------------------------
    // Genre metadata is often patchy in IPTV feeds, so mood filters cast a wide
    // keyword net rather than expecting exact tag matches.  Each rail scores
    // by matching genre keywords + a minimum rating floor + poster availability
    // so the home dashboard doesn't render empty tiles.

    private fun moodRail(
        movies: List<Movie>,
        keywords: List<String>,
        minRating: Double,
        n: Int,
    ): List<Movie> = movies.asSequence()
        .filter { it.poster.isNotBlank() && it.rating >= minRating }
        .filter { m ->
            val g = m.genre.lowercase()
            keywords.any { it in g }
        }
        .sortedByDescending { it.rating }
        .take(n)
        .toList()

    /** "Gritty & Tension-Filled" — crime/thriller/noir/drama with a strong rating. */
    suspend fun moodGritty(profileId: Long, n: Int = 12): List<Movie> = withContext(Dispatchers.Default) {
        moodRail(
            content.movies(profileId).first(),
            listOf("crime", "thriller", "noir", "mystery", "drama"),
            minRating = 6.8, n = n,
        )
    }

    /** "Late Night Background Watch" — comfort viewing: comedy / feel-good with lower rating floor. */
    suspend fun moodLateNight(profileId: Long, n: Int = 12): List<Movie> = withContext(Dispatchers.Default) {
        moodRail(
            content.movies(profileId).first(),
            listOf("comedy", "sitcom", "family", "romance", "animation"),
            minRating = 5.5, n = n,
        )
    }

    /** "Fast-Paced Thrillers" — action + high stakes with strong ratings. */
    suspend fun moodFastPaced(profileId: Long, n: Int = 12): List<Movie> = withContext(Dispatchers.Default) {
        moodRail(
            content.movies(profileId).first(),
            listOf("action", "adventure", "thriller", "war", "crime"),
            minRating = 6.5, n = n,
        )
    }

    /** "Mind-Bending Plots" — sci-fi / mystery with well-received storytelling. */
    suspend fun moodMindBending(profileId: Long, n: Int = 12): List<Movie> = withContext(Dispatchers.Default) {
        moodRail(
            content.movies(profileId).first(),
            listOf("sci-fi", "science", "mystery", "thriller", "fantasy"),
            minRating = 7.0, n = n,
        )
    }

    /** "Feel-Good Warm-Fuzzy" — animation / family / romance with a decent floor. */
    suspend fun moodFeelGood(profileId: Long, n: Int = 12): List<Movie> = withContext(Dispatchers.Default) {
        moodRail(
            content.movies(profileId).first(),
            listOf("animation", "family", "romance", "music", "biography"),
            minRating = 6.0, n = n,
        )
    }
}
