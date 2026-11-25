package tv.enktel.app.data.hub

import tv.enktel.app.data.metadata.TitleSanitizer
import kotlin.math.abs

/**
 * Pure matching logic that decides whether a Live-TV programme and a VOD /
 * series row in the user's own catalogue are the same title.
 *
 * This is the on-device version of the "matching engine" every linear-to-VOD
 * bridge needs. There is no backend and no third-party aggregator in the loop:
 * the pool is whatever the line already carries, so a match is always something
 * the viewer can actually open, and a miss costs one skipped card rather than a
 * wrong recommendation.
 *
 * ### Why scoring and not a string equals
 *
 * EPG titles are messy — "Game of Thrones S03E05", "Game of Thrones (New)",
 * "GAME OF THRONES" — and a catalogue name is rarely spelled the same way. So
 * both sides are normalised ([TitleSanitizer.clean] strips episode numbers,
 * parentheticals, accents and case) and then compared on three axes, the same
 * ones a server-side matcher weighs:
 *
 *  - **Title** — token-set overlap (Jaccard) between the cleaned titles, which
 *    survives word-order and a dropped "The" far better than character
 *    distance. This carries the most weight.
 *  - **Year** — when both sides know it, an exact first-air / release year is
 *    strong evidence and separates a reboot from its original.
 *  - **Type** — a live *series* should match a series row, not a film of the
 *    same name; a type agreement is a small boost, a disagreement a penalty.
 *
 * A candidate under [ACCEPT_THRESHOLD] is treated as "no match", because a
 * confident miss is better than a plausible-looking wrong answer on a hub that
 * invites the viewer to "watch on demand".
 */
object LiveVodMatch {

    /** One thing the matcher can score a programme against. */
    data class Candidate(
        val key: String,
        val title: String,
        val year: Int,
        val isSeries: Boolean,
    )

    data class Scored(val candidate: Candidate, val score: Double)

    /** Below this, the best candidate is discarded as "not in the catalogue". */
    const val ACCEPT_THRESHOLD = 0.62

    private const val TITLE_WEIGHT = 0.78
    private const val YEAR_BONUS = 0.18
    private const val TYPE_BONUS = 0.10
    private const val TYPE_PENALTY = 0.22

    /** Significant tokens of a title — words of two letters or more, after the
     *  episode scaffolding EPG feeds carry is stripped.
     *
     *  [TitleSanitizer.clean] tidies accents and case but leaves "S03E05" and a
     *  "(New Episode)" tag in place, so those are removed here first — otherwise
     *  "Game of Thrones S03E05" shares only half its tokens with the clean
     *  catalogue name and never clears the threshold. */
    internal fun tokens(raw: String): Set<String> {
        val stripped = raw.lowercase()
            .replace(Regex("\([^)]*\)"), " ")        // (New Episode), (Live)
            .replace(Regex("\[[^\]]*\]"), " ")
            .replace(Regex("s\d{1,2}\s*e\d{1,3}"), " ") // s03e05 / s3 e5
            .replace(Regex("\bep(isode)?\s*\d+"), " ")  // episode 5 / ep 5
        return TitleSanitizer.clean(stripped)
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
    }

    /** Jaccard overlap of the two token sets, 0.0..1.0. */
    internal fun titleSimilarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        val union = (ta + tb).size.toDouble()
        return inter / union
    }

    /**
     * Confidence that [candidate] is the VOD equivalent of the live programme
     * [liveTitle] (airing in [liveYear], or 0 when unknown; [liveIsSeries]
     * from the EPG classification). 0.0..~1.0.
     */
    fun score(
        liveTitle: String,
        liveYear: Int,
        liveIsSeries: Boolean,
        candidate: Candidate,
    ): Double {
        val title = titleSimilarity(liveTitle, candidate.title)
        if (title == 0.0) return 0.0

        var s = title * TITLE_WEIGHT
        if (liveYear > 0 && candidate.year > 0) {
            if (abs(liveYear - candidate.year) == 0) s += YEAR_BONUS
            // A year that is wildly off on an otherwise good title match is a
            // reboot or a namesake; nudge it down rather than trusting the name.
            else if (abs(liveYear - candidate.year) > 4) s -= YEAR_BONUS / 2
        }
        s += if (liveIsSeries == candidate.isSeries) TYPE_BONUS else -TYPE_PENALTY
        return s.coerceIn(0.0, 1.0)
    }

    /**
     * The best candidate for the programme, or null when none clears
     * [ACCEPT_THRESHOLD]. Series are offered before films when the live entry
     * is itself a series, so an exact-named film cannot outrank the show.
     */
    fun best(
        liveTitle: String,
        liveYear: Int,
        liveIsSeries: Boolean,
        candidates: List<Candidate>,
    ): Scored? =
        candidates.asSequence()
            .map { Scored(it, score(liveTitle, liveYear, liveIsSeries, it)) }
            .filter { it.score >= ACCEPT_THRESHOLD }
            .maxWithOrNull(
                compareBy<Scored> { it.score }
                    .thenBy { it.candidate.isSeries == liveIsSeries },
            )
}
