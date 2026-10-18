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
     *
     * Two shapes, because a bracketed marker is already delimited by its own
     * brackets: `[US] HBO HD` carries no `|` or `:` and still obviously has a
     * prefix, while a bare `BBC One HD` must not lose its first word to the
     * same rule. So brackets stand alone and bare letters need a separator.
     */
    private val PREFIX_RE =
        Regex("""^\s*(?:[\[(]([A-Za-z]{2,6})[\])]|([A-Za-z]{2,6})\s*[|:\-–])\s*(?:[|:\-–]\s*)?""")

    private val SPLIT_RE = Regex("""[^a-z0-9]+""")

    /** Lower-cased, prefix-stripped, quality-stripped tokens for [text]. */
    fun tokens(text: String): List<String> {
        val stripped = PREFIX_RE.replace(text.lowercase(), "")
        return stripped.split(SPLIT_RE)
            .filter { it.isNotBlank() && it !in QUALITY_TAGS }
    }

    /** The group marker a name carries, if any — `"AU| Seven"` → `"au"`. */
    fun groupPrefix(name: String): String =
        PREFIX_RE.find(name.lowercase())
            ?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            .orEmpty()

    /**
     * What to print on a channel card.
     *
     * Providers put a country or group marker on the front of every name —
     * `AU | Fox Cricket HD`, `UK: Sky Sports Main Event`. In a list it reads
     * as a column of identical prefixes; on a three-across phone grid, where
     * a cell is about 120 dp, it is five characters of the width spent on
     * something every neighbouring card also says, and the actual name is
     * what gets the ellipsis: `AU | Fox Cricke…`, `AU | Nickelode…`.
     *
     * Dropped for display only. The raw name is still what
     * [matches] searches, so typing "au" finds them exactly as before, and
     * the marker is not information the card loses — the category chips above
     * it are the same grouping, and the category is on the card's second line
     * whenever there is no programme to show instead.
     *
     * The same [PREFIX_RE] the matcher uses, so the two cannot disagree about
     * what a prefix is. Falls back to the original whenever stripping would
     * leave too little to identify: a channel genuinely called `UK: 4` should
     * keep its prefix rather than become `4`.
     */
    fun displayName(name: String): String {
        val stripped = PREFIX_RE.replace(name, "").trim()
        return if (stripped.length >= MIN_DISPLAY_NAME) stripped else name.trim()
    }

    /** Below this, a stripped name is too short to be worth the ambiguity. */
    private const val MIN_DISPLAY_NAME = 3

    /**
     * The two letters on the plate when a channel has no logo.
     *
     * Half a provider's line ships without one, and the plate was drawing
     * `name.take(2)` off the raw name — which, after the country prefix, made
     * fifteen consecutive Australian channels all read "AU". The name itself
     * is printed directly underneath, so the plate repeating the one thing
     * every neighbour also says is the least useful thing it could show.
     *
     * Initials of the first two words instead: `Fox Cricket HD` → "FC",
     * `Channel 7 HD` → "C7".
     */
    fun initials(name: String): String {
        val words = WORD_RE.findAll(displayName(name))
            .map { it.value }
            .filter { it.lowercase() !in QUALITY_TAGS }
            .take(2)
            .toList()
        val second = words.getOrNull(1)
        val letters = when {
            words.isEmpty() -> displayName(name).take(2)
            second == null -> words[0].take(2)
            // A channel whose second word is its number keeps the number:
            // "Channel 10 HD" is "C10", not "C1", which is a different
            // channel on every Australian line.
            second.all { it.isDigit() } ->
                if (second.length <= 2) words[0].take(1) + second else words[0].take(2)
            else -> words[0].take(1) + second.take(1)
        }
        return letters.uppercase()
    }

    private val WORD_RE = Regex("""[A-Za-z0-9]+""")

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
