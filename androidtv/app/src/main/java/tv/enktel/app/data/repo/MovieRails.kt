package tv.enktel.app.data.repo

import tv.enktel.app.data.db.Movie

/**
 * The rules behind the home rails, as pure functions over a catalogue.
 *
 * ## Why these are not methods on [RecommendationsRepository]
 *
 * They used to be, and every rule ended up written twice: once in the
 * standalone `trending()` / `newThisWeek()` helper, and again inline in
 * `homeRails()`, which loads the catalogue once and computes everything in a
 * single pass rather than paying for thirteen full table reads.
 *
 * Two copies of a rule drift, and these had. "Trending" ordered by
 * `rating + addedAt / 1_000_000` in the helper and by `rating desc, year desc`
 * inline — so the voice assistant's "what's trending" answered with a
 * different list from the Trending rail on Home, under the same name. The mood
 * rails matched on `genre` alone in the helper and on `genre + tags` inline,
 * so one saw TMDB keywords and the other did not.
 *
 * This is the third time that shape of bug has surfaced in this codebase
 * (Coming Soon and Latest Releases were the first two). One rule, one place,
 * and it is a pure function so it can actually be tested — there is no device
 * in this build environment, so anything requiring Room or a Flow is not.
 */
object MovieRails {

    /**
     * Final tie-break for every rail.
     *
     * Rating and year both tie constantly on a real catalogue — hundreds of
     * titles share a round rating, and a decade only has ten years in it — and
     * a comparator that leaves those ties unresolved returns whatever order it
     * was handed. That is stable only for as long as the query feeding it is,
     * so a rail can silently reshuffle when an unrelated query changes. The
     * row key is unique and immutable, so ending on it makes every rail
     * deterministic for a given catalogue, whatever order it arrives in.
     */
    private val byKey = compareBy<Movie> { it.key }

    /** Titles worth putting on a rail at all: they need art to render. */
    fun renderable(all: List<Movie>): List<Movie> = all.filter { it.poster.isNotBlank() }

    /** Rating floor for [trending] — "good", not merely "rated". */
    const val TRENDING_MIN_RATING = 6.5

    /** Rating floor for [topPicks] — the curated cut. */
    const val TOP_PICKS_MIN_RATING = 7.0

    /** How recent an arrival counts as "new this week". */
    const val NEW_WINDOW_DAYS = 14L

    /**
     * Trending — well-rated first, newest as the tie-break.
     *
     * A catalogue commonly has hundreds of titles sharing a round rating, so
     * without a deterministic tie-break the rail reshuffles between reads and
     * looks broken.
     */
    fun trending(renderable: List<Movie>): List<Movie> = renderable
        .filter { it.rating >= TRENDING_MIN_RATING }
        .sortedWith(compareByDescending<Movie> { it.rating }.thenByDescending { it.year }.then(byKey))

    /**
     * Top Picks — TMDB-enriched and highly rated, regardless of recency.
     *
     * `enrichedAt > 0` matters: an un-enriched row's rating comes from the
     * panel and is frequently a placeholder, so including those would fill a
     * "best of" rail with whatever the provider happened to type.
     */
    fun topPicks(renderable: List<Movie>): List<Movie> = renderable
        .filter { it.enrichedAt > 0 && it.rating >= TOP_PICKS_MIN_RATING }
        .sortedWith(compareByDescending<Movie> { it.rating }.thenByDescending { it.year }.then(byKey))

    /**
     * New This Week — by the panel's own `added` stamp, which is in *seconds*.
     *
     * Distinct from the Just Added rail, which uses `firstSeenAt` and means
     * "new to this line". This one means "the provider says it is new", and on
     * an M3U line, where no `added` field exists at all, it is empty.
     */
    fun newThisWeek(renderable: List<Movie>, nowSec: Long): List<Movie> {
        val cutoff = nowSec - NEW_WINDOW_DAYS * 24 * 60 * 60
        return renderable
            .filter { it.addedAt > cutoff }
            .sortedWith(compareByDescending<Movie> { it.addedAt }.then(byKey))
    }

    /**
     * Latest Releases — newest by *release year*, not by arrival.
     *
     * Sorting this by `addedAt` is what made it a third name for the
     * recently-added query and put the same handful of titles in three rails
     * at once. Year is the only release signal a catalogue row carries; ties
     * break on arrival so the freshest within a year leads.
     */
    fun latestReleases(renderable: List<Movie>): List<Movie> = renderable
        .filter { it.year > 0 }
        .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt }.then(byKey))

    /**
     * Coming Soon — release year strictly in the future.
     *
     * Usually empty, and correctly so: everything in a catalogue is already
     * playable. `year >= currentYear` was the old rule and it matched every
     * film released earlier the same year, which is how a "coming soon" rail
     * filled up with things you could watch immediately. The genuine
     * upcoming-titles feed lives in [EnktelFeed].
     */
    fun comingSoon(renderable: List<Movie>, currentYear: Int): List<Movie> = renderable
        .filter { it.year > currentYear }
        .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt }.then(byKey))

    /** Top Rated — purely by rating. "Genuinely good", ignoring recency. */
    fun topRated(renderable: List<Movie>): List<Movie> = renderable
        .filter { it.rating > 0 }
        .sortedWith(compareByDescending<Movie> { it.rating }.thenByDescending { it.year }.then(byKey))

    /**
     * Documentaries, by genre rather than by subject keyword.
     *
     * The keyword-matched version only ever surfaced documentaries about one
     * specific topic, so it was empty on most catalogues.
     */
    fun documentaries(renderable: List<Movie>): List<Movie> = renderable
        .filter { it.genre.contains("documentar", ignoreCase = true) }
        .sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt }.then(byKey))

    /**
     * A mood rail: keyword match over genre *and* TMDB tags, above a floor.
     *
     * Searching tags as well as genre is the point — IPTV genre metadata is
     * patchy and often a single word, so a genre-only match leaves most mood
     * rails empty on a real catalogue.
     */
    fun mood(renderable: List<Movie>, keywords: List<String>, minRating: Double): List<Movie> =
        renderable
            .filter { it.rating >= minRating }
            .filter { m ->
                val hay = (m.genre + " " + m.tags).lowercase()
                keywords.any { it in hay }
            }
            .sortedWith(compareByDescending<Movie> { it.rating }.thenByDescending { it.year }.then(byKey))

    // The mood keyword sets, named so both the rail and any future caller use
    // the same definition of what "gritty" means.
    val MOOD_FAST_PACED = listOf("action", "adventure", "thriller", "war", "crime")
    val MOOD_GRITTY = listOf("crime", "thriller", "noir", "mystery", "drama")
    val MOOD_MIND_BENDING = listOf("sci-fi", "science", "mystery", "thriller", "fantasy")
    val MOOD_LATE_NIGHT = listOf("comedy", "sitcom", "family", "romance", "animation")
    val MOOD_FEEL_GOOD = listOf("animation", "family", "romance", "music", "biography")
}
