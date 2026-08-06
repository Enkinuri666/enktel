package tv.enktel.app.data.repo

/**
 * Turns what a user typed into something FTS4 `MATCH` will accept.
 *
 * This is not cosmetic. `MATCH` takes a query *language*, not a string: `"`,
 * `*`, `-`, `^`, `(`, `)`, `NEAR`, `OR` and `AND` all mean something, and a
 * malformed expression makes SQLite raise rather than return no rows. A user
 * typing an apostrophe into the search box — "The Handmaid's Tale" — would take
 * the screen down with it.
 *
 * So: reduce to alphanumeric tokens, drop anything empty, and give the final
 * token a `*` so results appear while the user is still typing it. Everything
 * is ANDed, which is what a viewer means by "bat man" — both words, in any
 * order, which the old LIKE query could not express at all.
 */
object FtsQuery {

    /** Null when [raw] has nothing searchable in it. */
    fun toMatch(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val tokens = raw
            .lowercase()
            // Apostrophes vanish rather than splitting the word: "handmaid's"
            // should tokenise as one term, not "handmaid" plus a stray "s".
            .replace("'", "")
            .replace("’", "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        // A single character prefix-matches most of the catalogue and is not
        // worth the query; let the LIKE path handle it.
        if (tokens.size == 1 && tokens[0].length < 2) return null
        return tokens.mapIndexed { i, t ->
            if (i == tokens.lastIndex) "$t*" else t
        }.joinToString(" ")
    }
}
