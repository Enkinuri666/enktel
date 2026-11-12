package tv.enktel.app.data

/**
 * This list, with any later item that repeats an earlier one's key removed.
 *
 * ### Why a list needs this before it is shown
 *
 * A lazy list identifies its items by key, and two items with one key is not a
 * glitch it tolerates: it throws `IllegalArgumentException: Key "…" was already
 * used`, and the screen the list is on goes with it. Rows from the database
 * cannot do that — their keys are primary keys. Rows from somewhere else can:
 *
 *  - Coming Soon keyed each film on its id, and a film whose id was missing
 *    from the feed got 0. Two of those and the screen could not be opened.
 *    A paged listing that shifts between two page requests repeats a film
 *    too, with a perfectly good id.
 *  - The highlights rail keyed on the video's address. One video covering two
 *    matches is one address twice.
 *  - The live-scores rail keyed on home, away and league, which a double
 *    header satisfies twice in an afternoon.
 *
 * None of it shows up until the day the feed says something unusual, and then
 * it shows up as a screen that closes the app.
 *
 * ### Why not `distinctBy`
 *
 * It is `distinctBy`, on the day it is needed. On every other day — nearly all
 * of them — nothing repeats, and this hands back the same list rather than a
 * copy: these are called while a screen is being drawn, some of them on lists
 * of several hundred rows, and the common case should cost one pass and no
 * second list. Returning the same instance also keeps `remember(list)` keys
 * downstream from seeing a change that is not one.
 */
fun <T> List<T>.uniqueBy(key: (T) -> Any?): List<T> {
    if (size < 2) return this
    val seen = HashSet<Any?>(size * 2)
    for (item in this) {
        if (!seen.add(key(item))) return distinctBy(key)
    }
    return this
}
