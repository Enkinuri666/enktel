package tv.enktel.app.data.repo

/**
 * Turning "Sky Sports Main Event" into a line in *this* playlist.
 *
 * ### The gap this closes
 *
 * The Sports Hub knows the fixture — from TheSportsDB, independent of anyone's
 * EPG — and the Match Centre knows which broadcasters carry it. Neither was any
 * help finding it, because between the published name of a broadcaster and the
 * name of a channel on an IPTV line there is a layer of decoration:
 *
 *     Sky Sports Main Event
 *     UK: SKY SPORTS MAIN EVENT FHD
 *     |UK| Sky Sp. Main Event HD ᴴᴰ
 *     EN - Sky Sports Main Event [1080p]
 *
 * All four are the same channel. A plain substring test matches none of them
 * against the first, which is why the answer to "what channel is this on" was
 * either nothing or the user scrolling fifteen thousand rows by hand.
 *
 * This is deliberately *not* built on the EPG. The whole point of the request
 * was that the guide data is unreliable — channel names are not, because the
 * provider has to make them legible to a human picking from a list.
 *
 * ### How it decides
 *
 * Both sides are reduced to a bag of meaningful words and compared. Every word
 * of the broadcaster must be present for a match to count at all, so "Sky
 * Sports Cricket" never answers a request for "Sky Sports Football"; the score
 * then rewards a channel that adds nothing of its own, because "Sky Sports Main
 * Event" beats "Sky Sports Main Event Extra" when the question was the former.
 */
object BroadcastMatcher {

    /** Below this, a match is a coincidence rather than an answer. */
    const val MIN_SCORE = 60

    /**
     * Country and group prefixes providers put in front of everything.
     *
     * Matched as a whole leading token — a bare `UK` or `US` mid-name is part
     * of the channel ("BBC News UK"), not decoration.
     */
    private val PREFIX = Regex(
        // Either bracketed — |UK|, [HR], (US) — or followed by a separator:
        // "UK:", "EN -", "DE |". A bare code with neither is left alone,
        // because "BBC News UK" ends in one and it belongs to the name.
        """^\s*(?:[\[|(]\s*[a-z]{2,4}\s*[\]|)]|[a-z]{2,4}\s*[:\-|])\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Quality, codec and packaging noise. Every one of these appears on real
     * lines and none of it distinguishes one channel from another.
     */
    private val NOISE = setOf(
        "hd", "sd", "fhd", "uhd", "qhd", "4k", "8k", "1080p", "1080", "720p", "720",
        "2160p", "hevc", "h264", "h265", "x264", "x265", "raw", "backup", "alt",
        "feed", "vip", "plus1", "channel", "tv", "the", "and",
    )

    /** Superscript and full-width letters some providers decorate names with. */
    private val FANCY = mapOf(
        'ᴴ' to 'h', 'ᴰ' to 'd', 'ᶠ' to 'f', 'ᵁ' to 'u', 'ᴷ' to 'k', 'ᴬ' to 'a',
    )

    /**
     * The meaningful words of a name, lowercased and stripped of decoration.
     *
     * Public because the Sports Hub uses it to decide whether two broadcasters
     * are really the same one before showing both.
     */
    fun tokens(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val fancied = buildString(raw.length) { raw.forEach { append(FANCY[it] ?: it) } }
        val stripped = PREFIX.replace(fancied, "")
        return stripped
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() && it !in NOISE }
    }

    /**
     * 0–100: how well [channelName] answers a request for [broadcaster].
     *
     * Zero unless every word of the broadcaster appears in the channel — a
     * partial name is a different channel, not a worse match for this one.
     */
    fun score(broadcaster: String, channelName: String): Int {
        val want = tokens(broadcaster)
        val have = tokens(channelName)
        if (want.isEmpty() || have.isEmpty()) return 0
        if (!have.containsAll(want)) return 0
        // Everything asked for is present. What is left is how much else the
        // channel carries: an exact match scores 100, and each surplus word
        // costs, because it usually marks a different feed of the same brand —
        // "extra", "2", "west", "espanol".
        val surplus = have.size - want.size
        return (100 - surplus * 12).coerceAtLeast(MIN_SCORE)
    }

    /** One channel that carries a broadcaster, with how sure we are. */
    data class Hit<T>(val channel: T, val score: Int)

    /**
     * The lines in this playlist that carry [broadcaster], best first.
     *
     * [name] reads the channel's display name; the caller keeps its own type,
     * so this stays free of the database layer and testable on plain strings.
     */
    fun <T> find(
        broadcaster: String,
        channels: List<T>,
        limit: Int = 4,
        name: (T) -> String,
    ): List<Hit<T>> =
        channels.asSequence()
            .map { Hit(it, score(broadcaster, name(it))) }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()

    /**
     * The same, across every broadcaster carrying a fixture.
     *
     * Deduplicated by channel, because a fixture on both "Sky Sports Main
     * Event" and "Sky Sports Football" should not offer the same line twice
     * when the playlist happens to match both.
     */
    fun <T> findAny(
        broadcasters: List<String>,
        channels: List<T>,
        limit: Int = 4,
        key: (T) -> String,
        name: (T) -> String,
    ): List<Hit<T>> =
        broadcasters
            .flatMap { find(it, channels, limit, name) }
            .sortedByDescending { it.score }
            .distinctBy { key(it.channel) }
            .take(limit)
}
