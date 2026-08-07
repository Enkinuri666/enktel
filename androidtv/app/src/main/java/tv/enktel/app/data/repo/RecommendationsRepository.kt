package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.enktel.app.data.db.Movie

/**
 * Simple on-device recommender: uses the user's watch history + favorites to score movies
 * by shared genre/decade. Zero external calls — everything stays local.
 */
class RecommendationsRepository(private val content: ContentRepository) {

    /**
     * The catalogue, minus anything that cannot be rendered on a rail.
     *
     * Every single-rail helper loads this the same way, so it lives here
     * rather than being spelled out eight times. [homeRails] deliberately does
     * *not* use it — it loads once and computes every rail from that one read,
     * which is the whole reason it exists.
     */
    private suspend fun pool(profileId: Long): List<Movie> =
        MovieRails.renderable(content.movies(profileId).first())

    /** Top N movies sharing genres with recently watched items, minus the ones already watched. */
    suspend fun becauseYouWatched(profileId: Long, n: Int = 15): List<Movie> = withContext(Dispatchers.Default) {
        val history = content.watchHistory(profileId, 30).first()
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

    /** Movies the panel marked as added recently — see [MovieRails.newThisWeek]. */
    suspend fun newThisWeek(profileId: Long, n: Int = 15): List<Movie> = withContext(Dispatchers.Default) {
        MovieRails.newThisWeek(pool(profileId), System.currentTimeMillis() / 1000).take(n)
    }

    /**
     * Trending — see [MovieRails.trending].
     *
     * This used to order by `rating + addedAt / 1_000_000` while the Trending
     * rail on Home ordered by rating then year, so the voice assistant's
     * "what's trending" answered with a different list from the rail under
     * that name. One rule now, in MovieRails.
     */
    suspend fun trending(profileId: Long, n: Int = 15): List<Movie> = withContext(Dispatchers.Default) {
        MovieRails.trending(pool(profileId)).take(n)
    }

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
        MovieRails.latestReleases(pool(profileId)).take(n)
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
        MovieRails.comingSoon(pool(profileId), currentYear).take(n)
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
        MovieRails.topRated(pool(profileId)).take(n)
    }

    /**
     * Documentaries — by genre, not by subject keyword.
     *
     * Replaces the keyword-matched "Deep Dive Documentaries" rail, which only
     * surfaced documentaries about one specific topic and so was empty on most
     * catalogues.
     */
    suspend fun documentaries(profileId: Long, n: Int = 20): List<Movie> = withContext(Dispatchers.Default) {
        MovieRails.documentaries(pool(profileId)).take(n)
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
        val withPoster = MovieRails.renderable(allMovies)
        val history = content.watchHistory(profileId, 30).first()
        val seedIds = history.map { it.refId }.toHashSet()
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

        val latest = MovieRails.latestReleases(withPoster).take(24)

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
        val trending = pick(MovieRails.trending(withPoster), 18)

        // Top Picks: TMDB-enriched titles ranked by rating regardless of
        // recency — the "curated by the algorithm" cut. Lets us guarantee
        // a rail full of quality when a fresh catalogue hasn't landed
        // many high-rated items in the last two weeks.
        val topPicks = pick(MovieRails.topPicks(withPoster), 18)

        val newThis = pick(MovieRails.newThisWeek(withPoster, nowSec), 18)

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

        fun mood(keywords: List<String>, minRating: Double) =
            pick(MovieRails.mood(withPoster, keywords, minRating), 14)

        val moodFast = mood(MovieRails.MOOD_FAST_PACED, 6.5)
        val moodGrit = mood(MovieRails.MOOD_GRITTY, 6.8)
        val moodMind = mood(MovieRails.MOOD_MIND_BENDING, 7.0)
        val moodLate = mood(MovieRails.MOOD_LATE_NIGHT, 5.5)
        val moodGood = mood(MovieRails.MOOD_FEEL_GOOD, 6.0)

        // Computed from the catalogue already in hand, not by calling the
        // single-rail helpers — those each re-read the whole movie table, and
        // avoiding a dozen such reads is the entire point of homeRails.
        val topRatedRail = pick(MovieRails.topRated(withPoster), 20)
        val docsRail = pick(MovieRails.documentaries(withPoster), 20)
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
