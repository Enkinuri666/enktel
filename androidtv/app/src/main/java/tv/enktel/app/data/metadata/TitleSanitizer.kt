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
 */
object TitleSanitizer {

    // Common junk to strip. Order matters — extension first so the tokens
    // stripped later don't consume valid text.
    private val patterns: List<Regex> = listOf(
        // File extensions at the end: ".mp4" / ".mkv" / ".ts" / etc.
        Regex("\\.(mp4|mkv|avi|mov|webm|ts|m4v|flv|wmv|mpg|mpeg)\\b", RegexOption.IGNORE_CASE),
        // Resolution / quality tags — the ones that appear inside brackets
        // and the bare-word variants. HDR variants + dolby/atmos flags too.
        Regex("[\\[(]\\s*(4k|uhd|hd|fhd|sd|1080p?|720p?|480p?|2160p?|hdr(10\\+?)?|dv|dolby(-vision)?|atmos|remux|bluray|webrip|webdl|hdrip|dvdrip|xvid|x264|x265|hevc|h\\.?264|h\\.?265|10bit|8bit|aac|ac3|dts)\\s*[\\])]", RegexOption.IGNORE_CASE),
        Regex("\\b(4k|uhd|fhd|1080p|720p|480p|2160p|hdr10\\+?|dv|hdrip|webrip|webdl|dvdrip|xvid|hevc|remux|bluray)\\b", RegexOption.IGNORE_CASE),
        // Multi-language tags (`MULTI`, `MULTiSUB`), episode-title
        // separators (`| Episode 3 |`), and repeated symbol runs.
        Regex("\\b(multi|multisub|multiaudio|dual|dubbed|subbed)\\b", RegexOption.IGNORE_CASE),
        // Stray brackets with only whitespace inside.
        Regex("[\\[(]\\s*[\\])]"),
        // Country-code prefixes on live channels: "US: ", "UK - ", "PT| ".
        // Only strip when it's 2 uppercase letters + a separator at the
        // very start (won't gobble legitimate titles like "US Marshals").
        Regex("^\\s*[A-Z]{2}\\s*[:|\\-]\\s+"),
        // Standalone quality/format symbols scattered through the title.
        Regex("[▶◉●○★☆]"),
    )

    // After the above passes, collapse runs of whitespace / stray punctuation.
    private val whitespaceRuns = Regex("\\s{2,}")
    private val leadingTrailingPunct = Regex("^[\\s\\-|:.,;/]+|[\\s\\-|:.,;/]+\$")

    /** Returns [raw] with cosmetic junk removed. Preserves year suffix `(2019)`
     *  when it survives the pattern pass — that's semantically useful. */
    fun clean(raw: String): String {
        if (raw.isBlank()) return raw
        var s = raw
        patterns.forEach { s = it.replace(s, " ") }
        s = whitespaceRuns.replace(s, " ")
        s = leadingTrailingPunct.replace(s, "")
        // If we accidentally stripped everything, fall back to original.
        return s.trim().ifBlank { raw.trim() }
    }

    /** Tokenise into normalised search keywords — lowercased, split on
     *  common separators, blank/very-short tokens dropped. Used by
     *  UfoKeywordScanner + the search index. */
    fun keywords(raw: String): List<String> =
        raw.lowercase()
            .split(Regex("[\\s,|/:;\\-\\[\\]()]+"))
            .filter { it.length >= 2 }
            .distinct()
}
