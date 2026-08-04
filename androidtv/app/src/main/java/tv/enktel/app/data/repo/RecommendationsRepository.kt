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
    /**
     * Latest *releases* — newest by release year.
     *
     * This sorted by `addedAt`, which is when the title appeared in the
     * playlist, not when the film came out. That made it a duplicate of
     * "Recently Added" and of "New This Week" — three rails running the same
     * query under three names, which is why Home showed the same handful of
     * titles repeatedly and never actually surfaced new releases.
     *
     * Year is the only release signal the catalogue carries. Ties break on
     * addedAt so that within a year the freshest arrivals lead.
     */
    suspend fun latestReleases(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        content.movies(profileId).first().asSequence()
            .filter { it.poster.isNotBlank() && it.year > 0 }
            .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt })
            .take(n)
            .toList()
    }

    /**
     * Coming Soon — titles whose release year is still in the future.
     *
     * Was `year >= currentYear`, which in any month after January matches every
     * film released earlier the same year. That is precisely the reported bug:
     * a rail promising "coming soon" full of things released months ago.
     *
     * The catalogue carries a release *year* and no release date, so a title
     * dated this year cannot be distinguished from one released last week.
     * Strictly-future years is the only claim the data actually supports.
     *
     * This rail is therefore usually empty — panels rarely list next year's
     * films — and Home already hides it when it is. An empty honest rail beats
     * a full dishonest one.
     */
    suspend fun comingSoon(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        content.movies(profileId).first().asSequence()
            .filter { it.poster.isNotBlank() && it.year > currentYear }
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

    // ---- General-interest rails -------------------------------------------
    // Built from fields every catalogue carries — rating, genre, year — rather
    // than from enriched keyword tags, so they populate on a fresh sync
    // instead of waiting for the enrichment worker.

    /**
     * Top Rated — the catalogue's best-reviewed titles.
     *
     * Distinct from [trending], which floors at 6.5 and then orders by recency:
     * this orders by rating outright, so it answers "what is genuinely good"
     * rather than "what is good and new".
     */
    suspend fun topRated(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        content.movies(profileId).first().asSequence()
            .filter { it.poster.isNotBlank() && it.rating > 0 }
            .sortedWith(compareByDescending<Movie> { it.rating }.thenByDescending { it.year })
            .take(n)
            .toList()
    }

    /**
     * Documentaries — by genre, not by subject keyword.
     *
     * Replaces the keyword-matched "Deep Dive Documentaries" rail, which only
     * surfaced documentaries about one specific topic and so was empty on most
     * catalogues.
     */
    suspend fun documentaries(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        content.movies(profileId).first().asSequence()
            .filter { it.poster.isNotBlank() && it.genre.contains("documentar", ignoreCase = true) }
            .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt })
            .take(n)
            .toList()
    }

    /**
     * Newest series by release year.
     *
     * Home was almost entirely films — every rail but "Favorite Channels" drew
     * from the movie table — so a catalogue's series content was invisible
     * unless the user opened the Series tab deliberately.
     *
     * Ordered by year rather than by arrival: unlike Movie, the Series entity
     * carries no `addedAt`, because the Xtream series listing does not publish
     * an `added` field. Year is the only recency signal available, so the rail
     * is named for what it can actually show.
     */
    suspend fun newSeries(profileId: Long, n: Int = 20): List<tv.enktel.app.data.db.Series> =
        withContext(Dispatchers.Default) {
            content.series(profileId).first().asSequence()
                .filter { it.poster.isNotBlank() && it.year > 0 }
                .sortedWith(
                    compareByDescending<tv.enktel.app.data.db.Series> { it.year }
                        .thenByDescending { it.rating },
                )
                .take(n)
                .toList()
        }

    /** Aggregated home-page rail payload. */
    data class HomeRails(
        /** What the most recent sync actually introduced. */
        val justAdded: List<Movie>,
        val latestReleases: List<Movie>,
        val topPicks: List<Movie>,
        val trending: List<Movie>,
        val newThisWeek: List<Movie>,
        val becauseYouWatched: List<Movie>,
        val moodFastPaced: List<Movie>,
        val moodGritty: List<Movie>,
        val moodMindBending: List<Movie>,
        val moodLateNight: List<Movie>,
        val moodFeelGood: List<Movie>,
        val topRated: List<Movie>,
        val documentaries: List<Movie>,
        val newSeries: List<tv.enktel.app.data.db.Series>,
    )

    suspend fun homeRails(profileId: Long): HomeRails = withContext(Dispatchers.Default) {
        val allMovies = content.movies(profileId).first()
        val withPoster = allMovies.filter { it.poster.isNotBlank() }
        val history = content.continueWatching(profileId, 30).first()
        val seedIds = history.map { it.refId }.toHashSet()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val week = TimeUnit.DAYS.toSeconds(14)
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000

        // Just Added: what *this sync* introduced, newest first.
        //
        // This is the rail that answers "show me what turned up in the last
        // refresh". It runs off firstSeenAt, which is set by diffing one sync
        // against the previous one (see FreshCatalogue) rather than off the
        // panel's `added` field, which M3U lines do not carry and Xtream lines
        // report as the provider's ingest date.
        val justAdded = withPoster
            .filter { FreshCatalogue.isNew(it.firstSeenAt, nowMs) }
            .sortedWith(compareByDescending<Movie> { it.firstSeenAt }.thenByDescending { it.year })
            .take(24)

        // Latest Releases: newest by *release year*.
        //
        // Kept identical to the standalone latestReleases() helper on purpose.
        // This used to carry its own inline copy sorted by addedAt, which made
        // it a third name for the same recently-added query and is why Home
        // showed the same handful of titles across several rails.
        val latest = withPoster
            .filter { it.year > 0 }
            .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt })
            .take(24)

        val used = HashSet<String>().apply {
            addAll(justAdded.map { it.key })
            addAll(latest.map { it.key })
        }

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

        // Coming Soon is deliberately absent here. It used to be built from
        // this same catalogue filtered to `year >= currentYear`, which cannot
        // work: anything in the catalogue is already playable, so the rail
        // advertised films the user could watch immediately, and a catalogue row
        // carries only a year so there was nothing to count down to. It now
        // comes from ComingSoonRepository, which reads a feed of genuinely
        // unreleased titles with real dates.

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

        val topRatedRail = pick(topRated(profileId, 40), 20)
        val docsRail = pick(documentaries(profileId, 40), 20)
        // Series are not deduped against the movie pool — different table, no
        // overlap possible — so they are taken directly.
        val seriesRail = newSeries(profileId, 20)

        HomeRails(
            justAdded = justAdded,
            latestReleases = latest,
            topPicks = topPicks,
            trending = trending,
            newThisWeek = newThis,
            becauseYouWatched = because,
            moodFastPaced = moodFast,
            moodGritty = moodGrit,
            moodMindBending = moodMind,
            moodLateNight = moodLate,
            moodFeelGood = moodGood,
            topRated = topRatedRail,
            documentaries = docsRail,
            newSeries = seriesRail,
        )
    }
}
