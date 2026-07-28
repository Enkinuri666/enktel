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

    // ---- v1.20.0 themed rails (UFO / UAP / Exopolitics) --------------------
    // Backed by the DB `tags` column which the MetadataEnrichmentWorker
    // populates from TMDB keywords. Falls back to matching `name` + `genre`
    // so users see hits even before enrichment runs.

    /** "The Phenomenon" — mixed movies + series matching the broad UFO/UAP
     *  keyword umbrella (see [tv.enktel.app.data.metadata.UfoKeywords]). */
    suspend fun phenomenonMovies(profileId: Long, n: Int = 30): List<Movie> =
        content.moviesMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.phenomenon, n)

    suspend fun phenomenonSeries(profileId: Long, n: Int = 30): List<tv.enktel.app.data.db.Series> =
        content.seriesMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.phenomenon, n)

    /** "Deep Dive Documentaries" — documentary genre + phenomenon keywords. */
    suspend fun deepDiveDocs(profileId: Long, n: Int = 30): List<Movie> =
        content.moviesDocsMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.phenomenon, n)

    /** "Latest Exopolitics" — narrower exopolitics keyword set, sorted by
     *  release year descending so the freshest content leads. */
    suspend fun latestExopolitics(profileId: Long, n: Int = 30): List<Movie> =
        content.moviesMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.exopolitics, n)

    // ---- v1.25.0 consolidated home rails -----------------------------------
    // Everything above is a per-rail query that reads the full movie list
    // and applies its own filter/sort. When mood/theme keywords overlap
    // (`crime` hits "Gritty", "Fast-Paced" *and* "Trending"), the same
    // half-dozen top-rated titles ended up dominating every rail on the
    // home page. [homeRails] computes the whole set in one pass with
    // cross-rail dedup so each themed strip has a distinct sample:
    //
    //   Continue Watching / Watchlist / Recordings / Favs are user-owned
    //     rails and stay untouched — they should always contain their
    //     entries even if those items also match a mood.
    //   Latest Releases is picked first so newest content is guaranteed
    //     to appear even when it also fits a mood.
    //   Trending → New This Week → Because You Watched follow, each
    //     drawing from the pool minus everything already handed out.
    //   Mood + themed rails come last, taking the top matches remaining.
    //
    // Result: no more "Blade Runner 2049 in every single rail" feel; each
    // strip surfaces a genuinely different corner of the catalogue.

    /** Aggregated home-page rail payload. */
    data class HomeRails(
        val latestReleases: List<Movie>,
        val comingSoon: List<Movie>,
        val topPicks: List<Movie>,
        val trending: List<Movie>,
        val newThisWeek: List<Movie>,
        val becauseYouWatched: List<Movie>,
        val moodFastPaced: List<Movie>,
        val moodGritty: List<Movie>,
        val moodMindBending: List<Movie>,
        val moodLateNight: List<Movie>,
        val moodFeelGood: List<Movie>,
        val phenomenon: List<Movie>,
        val deepDiveDocs: List<Movie>,
        val latestExopolitics: List<Movie>,
    )

    suspend fun homeRails(profileId: Long): HomeRails = withContext(Dispatchers.Default) {
        val allMovies = content.movies(profileId).first()
        val withPoster = allMovies.filter { it.poster.isNotBlank() }
        val history = content.continueWatching(profileId, 30).first()
        val seedIds = history.map { it.refId }.toHashSet()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val week = TimeUnit.DAYS.toSeconds(14)
        val nowSec = System.currentTimeMillis() / 1000

        // Latest Releases: freshest additions with a poster. Always at least
        // 6 items when the catalogue has content, even if `addedAt` is zero
        // (fall back to year desc).
        val latest = withPoster
            .sortedWith(compareByDescending<Movie> { it.addedAt }.thenByDescending { it.year })
            .take(24)

        val used = HashSet<String>().apply { addAll(latest.map { it.key }) }

        fun pick(list: List<Movie>, limit: Int): List<Movie> {
            val out = ArrayList<Movie>(limit)
            for (m in list) {
                if (m.key in used) continue
                out.add(m)
                used.add(m.key)
                if (out.size >= limit) break
            }
            return out
        }

        // Coming Soon — release year is current year or later. Even the
        // newest of these is likely already in `latest`; dedup keeps that
        // rail meaningful when the catalogue has genuine forward-dated stock.
        val comingSoon = pick(
            withPoster.asSequence()
                .filter { it.year >= currentYear }
                .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt })
                .toList(),
            20,
        )

        // Trending: top-rated. TMDB enrichment fills `rating` from the
        // popular-and-well-reviewed slice; when a lot of titles share the
        // same rating we tie-break on year desc.
        val trending = pick(
            withPoster.asSequence()
                .filter { it.rating >= 6.5 }
                .sortedWith(compareByDescending<Movie> { it.rating }.thenByDescending { it.year })
                .toList(),
            18,
        )

        // Top Picks: TMDB-enriched titles ranked by rating regardless of
        // recency — the "curated by the algorithm" cut. Lets us guarantee
        // a rail full of quality when a fresh catalogue hasn't landed
        // many high-rated items in the last two weeks.
        val topPicks = pick(
            withPoster.asSequence()
                .filter { it.enrichedAt > 0 && it.rating >= 7.0 }
                .sortedByDescending { it.rating }
                .toList(),
            18,
        )

        // New This Week: added in the last 14 days.
        val newThis = pick(
            withPoster.asSequence()
                .filter { it.addedAt > nowSec - week }
                .sortedByDescending { it.addedAt }
                .toList(),
            18,
        )

        // Because You Watched: keep the existing scoring but exclude
        // anything we've already surfaced.
        val because = if (history.isNotEmpty()) {
            val seed = withPoster.filter { it.streamId in seedIds }
            val seedGenres = seed.flatMap { ContentRepository.splitGenres(it.genre) }
                .groupingBy { it.lowercase() }.eachCount()
            val scored = withPoster.asSequence()
                .filter { it.streamId !in seedIds && it.genre.isNotBlank() }
                .map { m ->
                    val overlap = ContentRepository.splitGenres(m.genre).sumOf {
                        seedGenres[it.lowercase()] ?: 0
                    }
                    m to (overlap * 3 + (m.rating / 2).toInt())
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
                .toList()
            pick(scored, 15)
        } else emptyList()

        fun moodPool(keywords: List<String>, minRating: Double): List<Movie> =
            withPoster.asSequence()
                .filter { it.rating >= minRating }
                .filter { m ->
                    val hay = (m.genre + " " + m.tags).lowercase()
                    keywords.any { it in hay }
                }
                .sortedByDescending { it.rating }
                .toList()

        val moodFast = pick(moodPool(listOf("action", "adventure", "thriller", "war", "crime"), 6.5), 14)
        val moodGrit = pick(moodPool(listOf("crime", "thriller", "noir", "mystery", "drama"), 6.8), 14)
        val moodMind = pick(moodPool(listOf("sci-fi", "science", "mystery", "thriller", "fantasy"), 7.0), 14)
        val moodLate = pick(moodPool(listOf("comedy", "sitcom", "family", "romance", "animation"), 5.5), 14)
        val moodGood = pick(moodPool(listOf("animation", "family", "romance", "music", "biography"), 6.0), 14)

        val phen = pick(
            content.moviesMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.phenomenon, 60),
            30,
        )
        val docs = pick(
            content.moviesDocsMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.phenomenon, 60),
            30,
        )
        val exo = pick(
            content.moviesMatchingKeywords(profileId, tv.enktel.app.data.metadata.UfoKeywords.exopolitics, 60),
            30,
        )

        HomeRails(
            latestReleases = latest,
            comingSoon = comingSoon,
            topPicks = topPicks,
            trending = trending,
            newThisWeek = newThis,
            becauseYouWatched = because,
            moodFastPaced = moodFast,
            moodGritty = moodGrit,
            moodMindBending = moodMind,
            moodLateNight = moodLate,
            moodFeelGood = moodGood,
            phenomenon = phen,
            deepDiveDocs = docs,
            latestExopolitics = exo,
        )
    }
}
