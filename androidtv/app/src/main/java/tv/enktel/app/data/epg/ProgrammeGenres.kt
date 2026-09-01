package tv.enktel.app.data.epg

/**
 * Turns a programme's XMLTV `<category>` elements into something worth showing.
 *
 * XMLTV lets a programme carry any number of categories and says nothing about
 * what they contain, so what arrives is not a tidy genre list. Real guides
 * carry the same idea three times in three spellings, bare DVB numeric codes,
 * and occasionally a whole synopsis that belongs in `<desc>`. Printing that
 * verbatim under a programme title is worse than printing nothing.
 *
 * Stored comma-separated, matching how [tv.enktel.app.data.db.Movie.genre]
 * already holds the same kind of value.
 */
object ProgrammeGenres {

    /**
     * How many are kept.
     *
     * A programme tagged with nine categories has not told the viewer more
     * than one tagged with two — it has filled the line the title needs. Three
     * is what fits beside a programme title without wrapping.
     */
    const val MAX = 3

    private val WHITESPACE = Regex("\\s+")
    private val HEX_CODE = Regex("0x[0-9a-fA-F]+")

    /** Longer than this and it is a description in the wrong element. */
    private const val MAX_LENGTH = 28

    /**
     * Normalise raw category text into a comma-separated genre string.
     *
     * Returns "" when nothing survives, which every caller already treats as
     * "no genre" — the same contract as a missing `<desc>`.
     */
    fun normalise(raw: List<String>): String {
        val out = LinkedHashMap<String, String>()   // lowercase key -> display form
        for (item in raw) {
            // Collapse internal whitespace before anything else. Trimming the
            // ends is not enough: a category carrying an embedded newline is
            // rendered on one line beside the title, and the break would split
            // the row rather than the text.
            val t = item.replace(WHITESPACE, " ").trim().trim(',', ';', '/', '|')
            if (t.isEmpty() || t.length > MAX_LENGTH) continue
            // A bare number is a DVB genre code, and guides emit it in hex as
            // often as in decimal. Both are meaningful to a decoder and
            // meaningless on screen, so both are dropped rather than shown.
            // The hex form has a letter in it, so the all-digits test alone
            // lets "0x20" through.
            if (t.all { it.isDigit() } || HEX_CODE.matches(t)) continue
            // Something with no letter at all is punctuation or a separator
            // that survived a bad export.
            if (t.none { it.isLetter() }) continue
            val key = t.lowercase()
            if (key !in out) out[key] = display(t)
            if (out.size >= MAX) break
        }
        return out.values.joinToString(", ")
    }

    /**
     * Fix the casing only where the source supplied none.
     *
     * Guides shout ("DRAMA") and mumble ("drama") in roughly equal measure, and
     * both look wrong next to a properly cased title. But a value that is
     * already mixed case was cased deliberately — "iPlayer Exclusive",
     * "BBC News" — and re-casing it would be the change that makes things
     * worse, so those are left exactly as they came.
     */
    private fun display(s: String): String {
        val letters = s.filter { it.isLetter() }
        val uniform = letters.isEmpty() ||
            letters.all { it.isUpperCase() } ||
            letters.all { it.isLowerCase() }
        if (!uniform) return s
        // Capitalise each word, including after a hyphen or slash, so "sci-fi"
        // becomes "Sci-Fi" rather than "Sci-fi".
        val sb = StringBuilder(s.length)
        var atStart = true
        for (c in s) {
            sb.append(if (atStart) c.uppercaseChar() else c.lowercaseChar())
            atStart = !c.isLetterOrDigit()
        }
        return sb.toString()
    }
}
