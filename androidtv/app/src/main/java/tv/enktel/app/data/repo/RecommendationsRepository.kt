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
}
