package tv.enktel.app.data.debrid

/**
 * Finding one thing in a Real-Debrid account.
 *
 * The account lists files, and files from a debrid service are named the way
 * release files are named: `The.Batman.2022.2160p.WEB-DL.x265-GROUP.mkv`.
 * Typing "the batman" into a plain `contains` finds nothing, because the
 * spaces the viewer typed are dots in the name — so the obvious
 * implementation of a search box is one that appears to work and never
 * matches anything.
 *
 * So both sides are reduced to words and every word has to appear somewhere.
 * That also makes the order irrelevant, which matters because nobody
 * remembers whether the year came before the resolution.
 */
object DebridSearch {

    /**
     * Everything that separates words in a release name.
     *
     * Dots and underscores are the common ones; brackets and dashes turn up
     * around tags and group names. Apostrophes are deliberately *not* here:
     * splitting "Ocean's" into two words would let it match a search for
     * "ocean s", which is not what anyone typed.
     */
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}']+")

    /** Words, lowercased, with the empties dropped. */
    fun tokens(s: String): List<String> =
        s.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }

    /**
     * Does [filename] match everything the viewer typed?
     *
     * Every query word must appear as a prefix of some word in the name.
     * Prefix rather than equality so that a half-typed word narrows the list
     * as it is typed, which is the whole value of a search box on a remote
     * where typing is slow.
     *
     * An empty query matches everything: a blank box is not a filter.
     */
    fun matches(filename: String, query: String): Boolean {
        val wanted = tokens(query)
        if (wanted.isEmpty()) return true
        val have = tokens(filename)
        if (have.isEmpty()) return false
        return wanted.all { w -> have.any { it.startsWith(w) } }
    }

    /** [items] filtered by [query], keeping the order they came in. */
    fun <T> filter(items: List<T>, query: String, name: (T) -> String): List<T> {
        if (tokens(query).isEmpty()) return items
        return items.filter { matches(name(it), query) }
    }
}
