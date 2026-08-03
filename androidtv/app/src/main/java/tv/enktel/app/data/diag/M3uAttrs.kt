package tv.enktel.app.data.diag

/**
 * `#EXTINF` attribute parsing, specifically the catch-up ones.
 *
 * You cannot test catch-up without first knowing how to *build* the request,
 * and that is entirely determined by these attributes: `catchup` names the
 * scheme, `catchup-source` gives the URL template, `catchup-days`/`timeshift`
 * give the window. Guessing instead of reading them is why catch-up works on
 * one line and 404s on another that looks identical.
 */
object M3uAttrs {

    data class Entry(
        val tvgId: String = "",
        val tvgName: String = "",
        val tvgLogo: String = "",
        val groupTitle: String = "",
        /** `catchup` / `catchup-type`, e.g. default | append | shift | flussonic. */
        val catchupType: String = "",
        /** `catchup-source` URL template, when the line supplies one. */
        val catchupSource: String = "",
        /** Archive window in days, from catchup-days or timeshift. */
        val catchupDays: Int = 0,
        val title: String = "",
    ) {
        val hasCatchup: Boolean get() = catchupDays > 0 || catchupType.isNotBlank() || catchupSource.isNotBlank()

        /** The scheme this entry implies, reusing the shared enum. */
        val scheme: CatchupScheme get() = when {
            // An explicit `catchup=` always wins. Template inspection is only a
            // fallback: a flussonic source legitimately contains ${'$'}{start}, so
            // matching on the template first would misread it as `append`.
            catchupType.equals("flussonic", true) -> CatchupScheme.FLUSSONIC
            catchupType.equals("append", true) -> CatchupScheme.APPEND
            catchupType.equals("shift", true) -> CatchupScheme.SHIFT
            catchupType.equals("xc", true) || catchupType.equals("xtream", true) ->
                CatchupScheme.XTREAM_TIMESHIFT
            catchupType.equals("default", true) -> CatchupScheme.DEFAULT
            // No declared type — infer from the template shape. Order matters
            // here too: flussonic's `archive-` marker is more specific than the
            // bare presence of a start placeholder.
            catchupSource.contains("archive", true) -> CatchupScheme.FLUSSONIC
            catchupSource.contains("timeshift", true) -> CatchupScheme.XTREAM_TIMESHIFT
            catchupSource.contains("${'$'}{start}") || catchupSource.contains("utc=") ->
                CatchupScheme.APPEND
            else -> CatchupScheme.UNKNOWN
        }
    }

    /** Parses one `#EXTINF:` line. Returns null when it isn't one. */
    fun parseExtInf(line: String): Entry? {
        if (!line.startsWith("#EXTINF:")) return null
        // Everything after the last unquoted comma is the display title.
        val title = line.substringAfterLast(',', "").trim()
        return Entry(
            tvgId = attr(line, "tvg-id"),
            tvgName = attr(line, "tvg-name"),
            tvgLogo = attr(line, "tvg-logo"),
            groupTitle = attr(line, "group-title"),
            // Providers spell this three ways; all mean the same thing.
            catchupType = attr(line, "catchup").ifBlank { attr(line, "catchup-type") },
            catchupSource = attr(line, "catchup-source"),
            catchupDays = (attr(line, "catchup-days").ifBlank { attr(line, "timeshift") })
                .toIntOrNull() ?: 0,
            title = title,
        )
    }

    /** `key="value"` — the only form the spec allows for EXTINF attributes. */
    internal fun attr(line: String, key: String): String {
        val needle = "$key=\""
        val i = line.indexOf(needle)
        if (i < 0) return ""
        return line.substring(i + needle.length).substringBefore('"')
    }
}
