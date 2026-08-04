package tv.enktel.app.data.repo

import tv.enktel.app.data.db.Channel

/**
 * Category, search and visibility rules for live channels.
 *
 * Extracted rather than inlined into a screen for the same reason [VodFilters]
 * was: the guide, the channel browser and search all have to agree about what
 * "SEVEN MATE" matches and which channels a category contains, and three
 * copies of that logic drift. It is also the only way to test it — there is no
 * device in this build environment, so anything living inside a `@Composable`
 * is untestable by construction.
 *
 * Everything here is pure: no Room, no Flow, no Android.
 */
object ChannelFilters {

    /** Sentinel category id meaning "no category filter". */
    const val ALL = "__all__"

    /**
     * Quality/encoding suffixes providers bolt onto channel names.
     *
     * These are noise for matching — a user typing "seven mate" is not
     * declaring a preference between `SEVEN MATE HD` and `SEVEN MATE FHD`,
     * and if we match on the raw string they get neither.
     */
    private val QUALITY_TAGS = setOf(
        "hd", "sd", "fhd", "uhd", "4k", "8k", "hq", "lq", "hevc", "h265", "h264",
        "raw", "backup", "alt", "vip", "plus", "1080", "1080p", "720", "720p", "576",
    )

    /**
     * Leading group markers: `AU|`, `AU:`, `[AU]`, `AU -`, `(AU)`.
     *
     * Kept out of the *name* tokens but folded back in as searchable text, so
     * "au sport" still works while "seven" is not buried behind a prefix.
     */
    private val PREFIX_RE = Regex("""^\s*[\[(]?([A-Za-z]{2,6})[\])]?\s*[|:\-–]\s*""")

    private val SPLIT_RE = Regex("""[^a-z0-9]+""")

    /** Lower-cased, prefix-stripped, quality-stripped tokens for [text]. */
    fun tokens(text: String): List<String> {
        val stripped = PREFIX_RE.replace(text.lowercase(), "")
        return stripped.split(SPLIT_RE)
            .filter { it.isNotBlank() && it !in QUALITY_TAGS }
    }

    /** The group marker a name carries, if any — `"AU| Seven"` → `"au"`. */
    fun groupPrefix(name: String): String =
        PREFIX_RE.find(name.lowercase())?.groupValues?.getOrNull(1).orEmpty()

    /**
     * Does [channel] match a free-text [query]?
     *
     * Every token of the query has to prefix-match some token of the channel's
     * searchable text, so word order does not matter and partial words work:
     * "mate sev" finds "AU| SEVEN MATE HD", and so does "sev mate".
     *
     * The channel number is searchable too — on a set-top box, typing the
     * number is how you find a channel, and `name LIKE '%7%'` matched every
     * channel with a 7 anywhere in its title instead.
     */
    fun matches(channel: Channel, query: String): Boolean {
        val q = tokens(query)
        if (q.isEmpty()) return true
        val hay = tokens(channel.name) +
            tokens(channel.categoryName) +
            listOfNotNull(groupPrefix(channel.name).takeIf { it.isNotBlank() }) +
            listOf(channel.num.toString())
        return q.all { needle -> hay.any { it.startsWith(needle) } }
    }

    /**
     * The full channel pipeline, in the order a user reasons about it.
     *
     * [hidden] wins over everything: a channel the user hid should not
     * reappear because it happens to be a favourite or to match a search.
     */
    fun apply(
        channels: List<Channel>,
        categoryId: String = ALL,
        query: String = "",
        favourites: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        favouritesOnly: Boolean = false,
        radioOnly: Boolean? = null,
    ): List<Channel> = channels.asSequence()
        .filter { it.key !in hidden }
        .filter { categoryId == ALL || it.categoryId == categoryId }
        .filter { !favouritesOnly || it.key in favourites }
        .filter { radioOnly == null || it.isRadio == radioOnly }
        .filter { matches(it, query) }
        .toList()

    /**
     * How many visible channels each category holds, plus [ALL].
     *
     * Surfacing the count next to every category name is what makes an empty
     * filter self-explanatory — the failure mode all through this app has been
     * filters that return nothing while looking like they worked.
     */
    fun categoryCounts(
        channels: List<Channel>,
        hidden: Set<String> = emptySet(),
    ): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        var total = 0
        channels.forEach { ch ->
            if (ch.key in hidden) return@forEach
            total++
            out[ch.categoryId] = (out[ch.categoryId] ?: 0) + 1
        }
        out[ALL] = total
        return out
    }

    /**
     * Categories present in [channels], ordered by the channels' own sort
     * order rather than alphabetically.
     *
     * Providers order their categories deliberately (news, sport, movies, then
     * the long tail); re-sorting that alphabetically buries the categories
     * people actually open.
     */
    fun categoriesOf(channels: List<Channel>): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        channels.sortedWith(compareBy({ it.sortIdx }, { it.num })).forEach { ch ->
            if (ch.categoryId.isNotBlank() && ch.categoryId !in seen) {
                seen[ch.categoryId] = ch.categoryName.ifBlank { ch.categoryId }
            }
        }
        return seen.entries.map { it.key to it.value }
    }
}
