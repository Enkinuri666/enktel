package tv.enktel.app.data.metadata

/**
 * Thematic keyword set powering the "The Phenomenon", "Deep Dive
 * Documentaries", and "Latest Exopolitics" home rails. Kept as a plain
 * const list rather than a resource so downstream code (the SQL LIKE
 * builders in ContentRepository, the tags-column normalization in the
 * enrichment worker) can iterate it without touching the Android layer.
 *
 * When a title, genre, or TMDB keyword contains any of these, it's
 * flagged for the themed rails.
 */
object UfoKeywords {

    /** Broad umbrella — "The Phenomenon" rail. */
    val phenomenon: List<String> = listOf(
        "ufo", "uap", "aliens", "alien contact", "extraterrestrial", "ancient aliens",
        "close encounters", "paranormal", "disclosure", "cover-up", "conspiracy",
        "unexplained", "supernatural", "the phenomenon", "roswell", "area 51",
    )

    /** Narrower — anything a serious exopolitics viewer would want on the
     *  "Latest Exopolitics" rail (excludes generic sci-fi drama). */
    val exopolitics: List<String> = listOf(
        "exopolitics", "disclosure", "cover-up", "government cover", "declassified",
        "whistleblower", "uap task force", "ufo hearing", "congressional hearing",
        "grusch", "elizondo", "aatip", "programs", "black project", "arcane science",
    )

    /** All keywords, lowercased + deduplicated — used by the tag scanner. */
    val all: List<String> = (phenomenon + exopolitics).map { it.lowercase() }.distinct()

    /** Returns the subset of [tokens] that overlap [all] — used by the
     *  worker to compute the `tags` column value from a TMDB keywords list
     *  or an already-clean title. */
    fun matched(tokens: Collection<String>): List<String> {
        val lc = tokens.map { it.lowercase() }
        return all.filter { needle ->
            lc.any { hay -> hay == needle || hay.contains(needle) }
        }
    }
}
