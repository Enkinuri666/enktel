package tv.enktel.app.data.metadata

/**
 * Normalises the messy, human-typed titles Xtream panels ship out of the
 * box — file extensions in the title (`Interstellar.mkv`), fake resolution
 * tags (`Movie 4K UHD`), redundant year brackets, junk symbols, country
 * prefixes on channels (`UK: BBC ONE HD`), and repeated whitespace.
 *
 * Applied at sync time so the database stores the clean version once, and
 * every downstream feature (search, home rails, watchlist) benefits without
 * needing to re-sanitize on every read.
 *
 * Intentionally minimal — the goal is "readable to humans", not "canonical".
 * We don't tokenise into semantic parts (title / year / edition / codec)
 * because that's the enrichment worker's job, and losing information here
 * would hurt search recall later.
 *
 * ### The rule this file is built around
 *
 * A wrong strip is much worse than a missed one. A title left slightly noisy
 * is ugly; a title with a real word removed is a different film. Everything
 * below that could plausibly appear in a real name is either excluded or
 * gated on surrounding evidence that we are looking at scene junk. Edition
 * markers — `Extended`, `Unrated`, `IMAX`, `Director's Cut` — are deliberately
 * *not* stripped: they are information the user wants, not noise.
 */
object TitleSanitizer {

    /**
     * Scene releases separate words with dots or underscores rather than
     * spaces: `The.Matrix.1999.1080p.BluRay.x264-GROUP`. Every pattern below
     * is written against word boundaries, and a dot is not one, so before this
     * existed the strip passes punched holes in the middle of the string and
     * left `The.Matrix.1999. . .x264-GROUP` — measurably worse than the input.
     *
     * Gated on the title having no spaces at all, because that is what makes a
     * dotted string a scene name rather than ordinary punctuation. `Mr. Robot`
     * and `Marvel's Agents of S.H.I.E.L.D.` both contain spaces and are left
     * alone.
     *
     * The two-segments-of-three rule is what saves initialisms. `W.A.R.` has
     * no spaces and two dots, but every segment is a single letter, so it is
     * not a word-separated title and is left as it is.
     */
    private fun despace(raw: String): Pair<String, Boolean> {
        if (raw.contains(' ')) return raw to false
        val sep = when {
            raw.count { it == '_' } >= 2 -> '_'
            raw.count { it == '.' } >= 2 -> '.'
            else -> return raw to false
        }
        val parts = raw.split(sep).filter { it.isNotBlank() }
        // Underscores never appear in a real display title, so they need no
        // further evidence. Dots do.
        if (sep == '.' && parts.count { it.length >= 3 } < 2) return raw to false
        return parts.joinToString(" ") to true
    }

    /**
     * The release group glued to the end of a scene name: `…x264-GROUP`.
     *
     * Only applied to strings [despace] recognised as scene names. Run
     * unconditionally it would eat the tail of every hyphenated title —
     * `Spider-Man`, `Ant-Man`, `X-Men` — and that is exactly the kind of wrong
     * strip this file exists to avoid.
     */
    private val sceneGroupSuffix = Regex("-[A-Za-z0-9]{2,}\\s*$")

    // Common junk to strip. Order matters — extension first so the tokens
    // stripped later don't consume valid text.
    private val patterns: List<Regex> = listOf(
        // File extensions at the end: ".mp4" / ".mkv" / ".ts" / etc.
        Regex("\\.(mp4|mkv|avi|mov|webm|ts|m4v|flv|wmv|mpg|mpeg)\\b", RegexOption.IGNORE_CASE),
        // Resolution / quality tags — the ones that appear inside brackets
        // and the bare-word variants. HDR variants + dolby/atmos flags too.
        Regex("[\\[(]\\s*(4k|uhd|hd|fhd|sd|1080p?|720p?|480p?|2160p?|hdr(10\\+?)?|dv|dolby(-vision)?|atmos|remux|bluray|webrip|webdl|hdrip|dvdrip|xvid|x264|x265|hevc|h\\.?264|h\\.?265|10bit|8bit|aac|ac3|dts)\\s*[\\])]", RegexOption.IGNORE_CASE),
        // Bare-word forms of the same tags.
        //
        // `hd` and `sd` are deliberately absent: "BBC ONE HD" and "BBC ONE"
        // are two different channels on most lines, and collapsing them makes
        // the guide ambiguous. So are the edition markers — Extended, Unrated,
        // IMAX, Remastered — which the user wants to see.
        Regex(
            "\\b(4k|uhd|fhd|1080p|720p|480p|2160p|hdr10\\+?|dv|hdrip|webrip|web-?dl|" +
                "brrip|bdrip|dvdrip|dvdscr|hdts|hdcam|camrip|telesync|xvid|hevc|remux|bluray|" +
                "x\\.?26[45]|h\\.?26[45]|10bit|8bit|aac|ac-?3|e-?ac-?3|ddp|dts(-hd)?|truehd)\\b",
            RegexOption.IGNORE_CASE,
        ),
        // Multi-language tags and episode-title separators.
        //
        // Bare `dual` used to be here and had to go: `Dual` is a 2022 film,
        // and "Dual 2022" was being reduced to "2022". It only counts as junk
        // when it is qualifying something.
        Regex("\\b(multi|multisub|multiaudio|dual[\\s\\-]?audio|dubbed|subbed)\\b", RegexOption.IGNORE_CASE),
        // Stray brackets with only whitespace inside.
        Regex("[\\[(]\\s*[\\])]"),
        // Country-code prefixes on live channels: "US: ", "UK - ", "PT| ".
        // Only strip when it's 2 uppercase letters + a separator at the
        // very start (won't gobble legitimate titles like "US Marshals").
        Regex("^\\s*[A-Z]{2}\\s*[:|\\-]\\s+"),
        // The same idea for the longer prefixes panels use, which the
        // two-letter rule above never matched — `USA: `, `ARA - `, `EXYU| `.
        //
        // An explicit list rather than `[A-Z]{3,4}`, because that would strip
        // the front off any title whose first word happens to be short and
        // capitalised: "MTV: Hits" and "HBO: Originals" are channel names, not
        // country prefixes, and the pattern cannot tell them apart.
        Regex(
            "^\\s*(ARA|BUL|CRO|CZE|DEU|ENG|ESP|EXYU|FRA|GER|GRE|HUN|IND|ITA|LAT|LATINO|" +
                "NED|NOR|POL|POR|ROM|RUS|SCA|SPA|SRB|SWE|TUR|UAE|USA|VIP|YUG)\\s*[:|\\-]\\s+",
        ),
        // The bracketed form of the same thing: "[EN] Top Gun", "(AR) فيلم".
        Regex("^\\s*[\\[(][A-Z]{2,4}[\\])]\\s*"),
        // Standalone quality/format symbols scattered through the title.
        Regex("[▶◉●○★☆]"),
    )

    // After the above passes, collapse runs of whitespace / stray punctuation.
    private val whitespaceRuns = Regex("\\s{2,}")
    private val leadingTrailingPunct = Regex("^[\\s\\-|:.,;/]+|[\\s\\-|:,;/]+\$|\\s+\\.\\s*\$")

    /** Returns [raw] with cosmetic junk removed. Preserves year suffix `(2019)`
     *  when it survives the pattern pass — that's semantically useful. */
    fun clean(raw: String): String {
        if (raw.isBlank()) return raw
        val (despaced, wasScene) = despace(raw.trim())
        var s = despaced
        if (wasScene) s = sceneGroupSuffix.replace(s, "")
        patterns.forEach { s = it.replace(s, " ") }
        s = whitespaceRuns.replace(s, " ")
        s = leadingTrailingPunct.replace(s, "")
        // If we accidentally stripped everything, fall back to original.
        return s.trim().ifBlank { raw.trim() }
    }

    /**
     * A trailing broadcast stamp: an optional `HH:mm` followed by a date, at
     * the very end of the string. Matches `09:00 28-07-2026`, `28/07/2026`,
     * `2026-07-28`.
     *
     * A date is *required* even though the time is the uglier half. Plenty of
     * legitimate programmes end in a time — "Sky News At 10:00", "News at Ten"
     * — and stripping a bare trailing time would quietly rename them. The junk
     * we're targeting always carries the date, so requiring it is what keeps
     * this safe.
     */
    private val trailingBroadcastStamp = Regex(
        """[\s\-|·,]*(\d{1,2}:\d{2}(:\d{2})?)?[\s\-|·,]*""" +
            """(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{4}[-/.]\d{1,2}[-/.]\d{1,2})[\s\-|·,]*$""",
    )

    /**
     * [clean], plus the broadcast stamps EPG feeds bolt onto programme titles.
     *
     * Xtream panels and some XMLTV sources ship titles like
     * `ARENA ESPORT HD 09:00 28-07-2026` — the channel's own scheduling data
     * duplicated into the programme name. It's noise everywhere it's shown:
     * the Sports Hub, the guide, the player's now-playing bar. The start time
     * is already a structured field on the row, so the copy in the title is
     * pure redundancy.
     *
     * Separate from [clean] because it's only correct for programmes. Applying
     * the date strip to a VOD title would mangle anything legitimately ending
     * in a date.
     */
    fun cleanProgramme(raw: String): String {
        if (raw.isBlank()) return raw
        val stripped = trailingBroadcastStamp.replace(clean(raw), "")
        return stripped.trim().ifBlank { clean(raw) }
    }

    /** Tokenise into normalised search keywords — lowercased, split on
     *  common separators, blank/very-short tokens dropped. Used by
     *  UfoKeywordScanner + the search index. */
    fun keywords(raw: String): List<String> =
        raw.lowercase()
            .split(Regex("[\\s,|/:;\\-\\[\\]()._]+"))
            .filter { it.length >= 2 }
            .distinct()
}
