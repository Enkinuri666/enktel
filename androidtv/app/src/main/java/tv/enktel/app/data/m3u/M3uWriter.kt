package tv.enktel.app.data.m3u

import tv.enktel.app.data.db.Channel

/**
 * Render channels back out as an M3U playlist.
 *
 * The inverse of [M3uParser], and deliberately symmetric with it: every
 * attribute the parser reads is written back. A round trip that quietly drops
 * a field is how the radio flag went missing on the scraper side — parsed on
 * the way in, never written on the way out, so stations arrived downstream
 * looking like television. Anything added to one of these belongs in the other.
 */
object M3uWriter {

    /**
     * @param channels in the order they should appear
     * @param epgUrl written as `x-tvg-url` on the header when non-blank
     * @param urlOf resolves a channel's playable URL. An M3U profile carries
     *   one per row; an Xtream profile builds them from the line, which is why
     *   this is a caller's decision rather than a field read.
     */
    fun write(
        channels: List<Channel>,
        epgUrl: String = "",
        urlOf: (Channel) -> String = { it.url },
    ): String = buildString {
        append(if (epgUrl.isBlank()) "#EXTM3U" else "#EXTM3U x-tvg-url=\"${attr(epgUrl)}\"")
        append('\n')

        for (ch in channels) {
            val url = urlOf(ch)
            // A row with nowhere to point is not a channel any player can use,
            // and writing it produces a file that fails to parse cleanly.
            if (url.isBlank()) continue

            append("#EXTINF:-1")
            if (ch.epgId.isNotBlank()) append(" tvg-id=\"${attr(ch.epgId)}\"")
            if (ch.name.isNotBlank()) append(" tvg-name=\"${attr(ch.name)}\"")
            if (ch.logo.isNotBlank()) append(" tvg-logo=\"${attr(ch.logo)}\"")
            if (ch.num > 0) append(" tvg-chno=\"${ch.num}\"")
            if (ch.categoryName.isNotBlank()) append(" group-title=\"${attr(ch.categoryName)}\"")
            if (ch.isRadio) append(" radio=\"true\"")
            if (ch.archiveDays > 0) append(" catchup-days=\"${ch.archiveDays}\"")
            if (ch.catchupType.isNotBlank()) append(" catchup=\"${attr(ch.catchupType)}\"")
            if (ch.catchupSource.isNotBlank()) append(" catchup-source=\"${attr(ch.catchupSource)}\"")
            append(',')
            append(ch.name)
            append('\n')

            // Per-channel agent, in the form the parser reads back.
            if (ch.userAgent.isNotBlank()) {
                append("#EXTVLCOPT:http-user-agent=").append(ch.userAgent).append('\n')
            }

            // The DRM the channel came in with, in the same form. Without this
            // an export silently strips it, and re-importing the file yields a
            // channel that looks intact and cannot be decrypted.
            if (ch.drmScheme.isNotBlank() && ch.drmLicense.isNotBlank()) {
                append("#KODIPROP:inputstream.adaptive.license_type=")
                    .append(kodiScheme(ch.drmScheme)).append('\n')
                append("#KODIPROP:inputstream.adaptive.license_key=")
                    .append(ch.drmLicense).append('\n')
            }

            append(url).append('\n')
        }
    }

    /**
     * M3U attributes are double-quoted with no escape sequence defined, so a
     * quote inside a value cannot be represented — it ends the attribute and
     * corrupts every one after it. Substituting an apostrophe keeps the file
     * parseable, which matters more than the exact character.
     */
    private fun attr(value: String): String = value.replace('"', '\'').replace("\n", " ")

    /**
     * The system identifier for a scheme, which is what is written back.
     *
     * The parser accepts the short names too, but a file this app produces
     * should be readable by anything that reads Kodi properties, and the
     * qualified spelling is the one every such reader knows.
     */
    private fun kodiScheme(scheme: String): String = when (scheme) {
        tv.enktel.app.data.m3u.DrmInfo.WIDEVINE -> "com.widevine.alpha"
        tv.enktel.app.data.m3u.DrmInfo.PLAYREADY -> "com.microsoft.playready"
        else -> "org.w3.clearkey"
    }
}
