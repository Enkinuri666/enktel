package tv.enktel.app

import android.content.Intent
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Turns an incoming `VIEW` intent into a route this app can navigate to.
 *
 * ### Why this exists
 *
 * Fire OS launcher integration is not a push API the app calls. You submit a
 * catalog feed of your VOD to Amazon, and each entry in that feed carries a
 * URI; Fire OS fires it at the app when the user selects a row on the launcher
 * home screen, or holds the Alexa button and says "play The Batman on EnkTel".
 * Google's `TvProvider` recommendations channel — the usual Android TV answer —
 * does not work on Fire TV at all, so this hand-off *is* the integration.
 *
 * The app had no `VIEW` filter and no URI parsing, so there was nothing for any
 * of that to land on: a catalog entry would have resolved and then done
 * nothing, which is the same silent-failure shape as the trailer button and the
 * catch-up URL.
 *
 * ### Shapes
 *
 * ```
 * enktel://play/movie/<streamId>
 * enktel://play/series/<seriesId>
 * enktel://play/channel/<streamId>
 * enktel://play/search?q=<terms>
 * https://enktel.tv/play/movie/<streamId>      (same, from a web link)
 * ```
 *
 * Ids are the *panel's* stream ids rather than our composite row keys, because
 * the catalog feed is generated from panel data and cannot know a profile id.
 * Resolution to a row key happens at navigation time against the active
 * profile.
 */
object DeepLink {

    sealed interface Target {
        data class Movie(val streamId: Long) : Target
        data class Series(val seriesId: Long) : Target
        data class Channel(val streamId: Long) : Target
        data class Search(val query: String) : Target
    }

    /** Reads [intent], or null when it carries no link we recognise. */
    fun from(intent: Intent?): Target? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        return parse(intent.dataString)
    }

    /**
     * The URI half, as a plain string.
     *
     * Deliberately not taking an `android.net.Uri`: that class is stubbed in
     * JVM unit tests and returns null for everything, so a Uri-based parser
     * could only be tested by pulling Robolectric into a project that has so
     * far needed none. The parsing here is simple enough that java.net.URI
     * covers it, which keeps the whole thing testable for free.
     */
    fun parse(raw: String?): Target? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        // enktel://play/movie/42  → authority "play", segments [movie, 42]
        // https://enktel.tv/play/movie/42 → authority "enktel.tv", segments [play, movie, 42]
        val parts = when {
            uri.scheme.equals("enktel", true) && uri.host.equals("play", true) -> segments
            uri.scheme.equals("https", true) && uri.host.equals("enktel.tv", true) &&
                segments.firstOrNull().equals("play", true) -> segments.drop(1)
            else -> return null
        }
        val kind = parts.getOrNull(0)?.lowercase() ?: return null
        if (kind == "search") {
            val q = uri.rawQuery.orEmpty()
                .split('&')
                .firstOrNull { it.startsWith("q=") }
                ?.removePrefix("q=")
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.trim()
                .orEmpty()
            return if (q.isBlank()) null else Target.Search(q)
        }
        // A non-numeric id is a malformed link, not a zero. Returning null lets
        // the caller open the app normally instead of navigating to whatever
        // happens to live at stream 0.
        val id = parts.getOrNull(1)?.toLongOrNull() ?: return null
        if (id <= 0) return null
        return when (kind) {
            "movie", "vod" -> Target.Movie(id)
            "series", "show", "tv" -> Target.Series(id)
            "channel", "live" -> Target.Channel(id)
            else -> null
        }
    }

    /**
     * The URI to publish for [target] in the Amazon catalog feed.
     *
     * Kept beside the parser on purpose: a feed generator and a parser that
     * disagree about the shape is a class of bug that only shows up in
     * production, on someone else's television.
     */
    fun uriFor(target: Target): String = when (target) {
        is Target.Movie -> "enktel://play/movie/${target.streamId}"
        is Target.Series -> "enktel://play/series/${target.seriesId}"
        is Target.Channel -> "enktel://play/channel/${target.streamId}"
        // URLEncoder is form encoding, which spells a space "+". A URI path
        // query accepts it, but %20 is what every other producer emits and what
        // a human reading the feed expects.
        is Target.Search ->
            "enktel://play/search?q=" + URLEncoder.encode(target.query, "UTF-8").replace("+", "%20")
    }
}
