package tv.enktel.app.data.repo

import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Series
import tv.enktel.app.data.metadata.RatingRank

/**
 * A film or series library, arranged for the browse screens — off the UI
 * thread, and without redoing work that has not changed.
 *
 * ## What it replaces
 *
 * Movies and Series each did this inline, inside `remember { … }`, which runs
 * during composition, which runs on the UI thread:
 *
 * ```
 * raw.filter { … m.name.lowercase().contains(needle) ||
 *                m.cast.lowercase().contains(needle) || … }
 *    .sortedBy { it.name.lowercase() }
 * ```
 *
 * `raw` is the whole table. An IPTV line's film library is routinely tens of
 * thousands of titles, and that expression allocates three lowercased strings
 * per title per keystroke, then calls `lowercase()` again inside the sort's
 * comparator — twice per comparison, about a million and a half times for
 * 60,000 titles. Measured on a development machine: 117 ms to sort A–Z and
 * 34 ms to build the genre chips. A Fire TV Stick is ten to twenty times
 * slower than that, so opening Movies on A–Z froze the screen for over a
 * second, and it froze again for every filter chip and every typed letter.
 *
 * ## What it does instead
 *
 * Two steps, because they change at different rates.
 *
 * [index] runs once when the library arrives. It lowercases each name once,
 * joins the searchable text into one string, works out the rating score and
 * counts the genres. None of that depends on what the viewer has selected.
 *
 * [arrange] runs when a filter, the sort or the search changes. With the keys
 * already built it is comparisons and substring checks over values that are
 * sitting there, and the callers run both steps on a background dispatcher —
 * so even the part that is left never costs the UI a frame.
 *
 * The ordering is exactly what the inline code produced, title for title;
 * `VodShelfTest` holds a copy of the old expression and checks the two agree
 * across every sort and filter.
 */
object VodShelf {

    /** One title, with everything a sort or a search needs already worked out. */
    class Row<T> internal constructor(
        val item: T,
        internal val nameKey: String,
        internal val haystack: String,
        internal val genre: String,
        internal val year: Int,
        internal val addedAt: Long,
        internal val score: Double,
    )

    /** A library, indexed. [genres] are its commonest, most frequent first. */
    class Index<T> internal constructor(
        internal val rows: List<Row<T>>,
        val genres: List<String>,
        internal val series: Boolean,
    ) {
        val size: Int get() = rows.size
    }

    /** How many genre chips the filter bar offers. */
    private const val GENRE_CHIPS = 18

    /** Cannot occur in typed text, so a search cannot match across two fields. */
    private const val FIELD_BREAK = '\u0000'

    fun movies(items: List<Movie>): Index<Movie> = Index(
        rows = items.map { m ->
            Row(
                item = m,
                nameKey = m.name.lowercase(),
                // Films are found by who is in them and who made them, too.
                haystack = buildString(m.name.length + m.cast.length + m.director.length + 2) {
                    append(m.name.lowercase()).append(FIELD_BREAK)
                    append(m.cast.lowercase()).append(FIELD_BREAK)
                    append(m.director.lowercase())
                },
                genre = m.genre,
                year = m.year,
                addedAt = m.addedAt,
                score = RatingRank.score(m.imdbRating, m.imdbVotes, m.rating),
            )
        },
        genres = topGenres(items.map { it.genre }),
        series = false,
    )

    fun series(items: List<Series>): Index<Series> = Index(
        rows = items.map { s ->
            val key = s.name.lowercase()
            Row(
                item = s,
                nameKey = key,
                haystack = key,
                genre = s.genre,
                year = s.year,
                addedAt = 0L,
                score = RatingRank.score(s.imdbRating, s.imdbVotes, s.rating),
            )
        },
        genres = topGenres(items.map { it.genre }),
        series = true,
    )

    private fun topGenres(genres: List<String>): List<String> =
        genres.flatMap { ContentRepository.splitGenres(it) }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(GENRE_CHIPS).map { it.key }

    /**
     * The titles to show, in the order to show them.
     *
     * [sort] is the stored setting: `name`, `rating`, `added` or `year`.
     * Anything else is A–Z, which is what the screens have always fallen
     * back to.
     */
    fun <T> arrange(
        index: Index<T>,
        sort: String,
        genre: String? = null,
        decade: Int? = null,
        query: String = "",
    ): List<T> {
        val needle = query.trim().lowercase()
        val kept = ArrayList<Row<T>>(index.rows.size)
        for (row in index.rows) {
            if (genre != null && !row.genre.contains(genre, ignoreCase = true)) continue
            if (!VodFilters.matchesYear(row.year, decade)) continue
            if (needle.isNotEmpty() && !row.haystack.contains(needle)) continue
            kept += row
        }
        // Every sort here is stable, so titles that tie keep the order the
        // library arrived in — which is the panel's own, and the only order
        // there is for a shelf of films it gave no rating or year.
        val sorted: List<Row<T>> = when {
            sort == "rating" -> kept.sortedByDescending { it.score }
            // Sorting on the panel's own number ordered a shelf of zeroes on
            // any lineup that does not publish ratings; see RatingRank.
            index.series && (sort == "year" || sort == "added") -> kept.sortedByDescending { it.year }
            sort == "added" -> kept.sortedByDescending { it.addedAt }
            // Year alone left every title the panel gave no year for in one
            // undifferentiated block; ingest time breaks the tie.
            sort == "year" -> VodFilters.newest(kept, { it.year }, { it.addedAt })
            else -> kept.sortedBy { it.nameKey }
        }
        return sorted.map { it.item }
    }
}
