package tv.enktel.app.data.m3u

import java.util.Locale

/**
 * Put the viewer's own country at the top of a lineup.
 *
 * The free-to-air playlist is one list for everybody, and it is mostly not
 * yours: 2,446 of its 2,923 channels are American, against 158 Australian. A
 * viewer outside the US opens on a wall of channels that answer HTTP 403,
 * because a FAST channel's licence stops at its border, and has to scroll past
 * all of it to reach the ones that play.
 *
 * Reordering cannot make a geo-blocked stream work — nothing client-side can.
 * What it can do is put the channels that *do* work where the viewer lands.
 *
 * Group titles carry the country as a `CC - Genre` prefix, which is what
 * `build-lineup.mjs` emits. A group with no such prefix sorts after the
 * viewer's country and before everyone else's: an unlabelled channel is more
 * likely to be relevant than one labelled as somewhere else.
 */
object LocalFirst {

    /** `AU - Sports` → `AU`; anything else → "". */
    fun countryOf(group: String): String {
        val text = group.trim()
        if (text.length < 4) return ""
        if (text[2] != ' ' || text[3] != '-') return ""
        val cc = text.take(2)
        return if (cc.all { it in 'A'..'Z' }) cc else ""
    }

    /**
     * The device's country, upper-cased, or "" when the platform has none.
     *
     * `Locale.getDefault().country` rather than a SIM or network lookup: it
     * needs no permission, it is what the viewer chose, and it is right for a
     * TV box that has no radio at all.
     */
    fun deviceCountry(): String = runCatching {
        Locale.getDefault().country.uppercase(Locale.ROOT)
    }.getOrDefault("")

    /**
     * Rank for a group title: 0 for the viewer's country, 1 for unlabelled,
     * 2 for everybody else.
     *
     * Only ever used as a *stable* sort key, so channels keep the playlist's
     * own order within each band — the lineup is already sorted by country,
     * then genre, then name, and none of that is worth discarding.
     */
    fun rank(group: String, country: String): Int {
        if (country.isEmpty()) return 1
        val cc = countryOf(group)
        return when {
            cc == country -> 0
            cc.isEmpty() -> 1
            else -> 2
        }
    }

    /**
     * Stable-sort anything carrying a group title so local comes first.
     *
     * @param items the rows to reorder
     * @param groupOf the group title of a row
     * @param country ISO-3166 alpha-2; "" leaves the order untouched
     */
    fun <T> sort(items: List<T>, country: String, groupOf: (T) -> String): List<T> {
        if (country.isEmpty()) return items
        // sortedBy is stable, so equal ranks keep their existing order.
        return items.sortedBy { rank(groupOf(it), country) }
    }
}
