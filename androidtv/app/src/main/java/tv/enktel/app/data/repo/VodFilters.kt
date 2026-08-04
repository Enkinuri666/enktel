package tv.enktel.app.data.repo

/**
 * The Movies / Series year filter, as a pure function.
 *
 * It used to live inline in the Compose grid, which is why it went wrong and
 * stayed wrong: the chip label and the predicate that backed it were written in
 * two different places, so "2026+" could be rendered by one expression and
 * evaluated by another that meant something else entirely. Both now derive from
 * [YEAR_CHIPS] and [label], and the whole thing is testable without an Android
 * device.
 */
object VodFilters {

    /**
     * Chips at or above this year are open-ended. Below it they select a decade.
     *
     * The bug this constant exists to prevent: every chip was evaluated as
     * `year in v..(v + 9)`, so the chip drawn as "2026+" actually matched
     * 2026-2035 and the one drawn as "2025+" matched 2025-2034 — overlapping
     * ten-year windows, neither meaning "or later" as labelled.
     */
    const val OPEN_ENDED_FROM = 2025

    /** Sentinel for the "Older" chip: anything before 1990. */
    const val OLDER = -1

    /** The year chips, in display order. `null` ("Any") is added by the UI. */
    val YEAR_CHIPS: List<Int> = listOf(2026, 2025, 2020, 2010, 2000, 1990, OLDER)

    /** Chip caption. Shares its rule with [matchesYear] so the two cannot drift. */
    fun label(chip: Int): String = when {
        chip == OLDER -> "Older"
        chip >= OPEN_ENDED_FROM -> "$chip+"
        else -> "${chip}s"
    }

    /**
     * Does a title with this release [year] belong under [chip]?
     *
     * A [year] of 0 means the panel supplied none and none could be recovered
     * from the filename. Those titles match no chip — which is correct, but it
     * is also why the grid looked broken: on a catalogue where most rows have no
     * year, every chip appears to hide almost everything. [unknownYearCount]
     * exists so the UI can say that out loud instead of leaving the user to
     * guess.
     */
    fun matchesYear(year: Int, chip: Int?): Boolean = when {
        chip == null -> true
        year <= 0 -> false
        chip == OLDER -> year < 1990
        chip >= OPEN_ENDED_FROM -> year >= chip
        else -> year in chip..(chip + 9)
    }

    /** Titles the year chips can never match, so the UI can explain itself. */
    fun unknownYearCount(years: List<Int>): Int = years.count { it <= 0 }

    /**
     * "Newest" ordering.
     *
     * Sorting on year alone left everything the panel gave no year for in one
     * undifferentiated block at the bottom, in whatever order the query
     * happened to return. Ingest time breaks that tie, so the newest arrivals
     * still lead within the unknown-year group.
     */
    fun <T> newest(items: List<T>, year: (T) -> Int, addedAt: (T) -> Long): List<T> =
        items.sortedWith(compareByDescending(year).thenByDescending(addedAt))
}
