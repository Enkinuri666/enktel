package tv.enktel.app.data.metadata

/**
 * Where to send a viewer who wants IMDb's page for a title.
 *
 * Two URLs, tried in order, because the right destination depends on what the
 * device has installed:
 *
 *  - [appUri] is IMDb's own scheme. If their app is installed it opens
 *    straight on the title, which on a phone is what someone tapping this
 *    wants.
 *  - [webUrl] is the ordinary page, which every device can open.
 *
 * The caller tries the first and falls back to the second — an `imdb://`
 * intent with no handler throws `ActivityNotFoundException`, and on a TV box
 * there is usually no handler, so the fallback is the normal path rather than
 * the exceptional one.
 *
 * Both are built here rather than inline at the call site so the shapes are
 * testable without an Android runtime; a wrong path here is a 404 the viewer
 * sees and nothing in the build notices.
 */
object ImdbLinks {

    /** IMDb's app scheme. Three slashes is correct — the authority is empty. */
    fun appUri(imdbId: String): String? =
        normalised(imdbId)?.let { "imdb:///title/$it/" }

    /** The public page, for when the app is not installed. */
    fun webUrl(imdbId: String): String? =
        normalised(imdbId)?.let { "https://www.imdb.com/title/$it/" }

    /**
     * Both destinations for a title, most specific first, or empty when there
     * is no usable id — an empty list is the signal to not show the button at
     * all rather than to show one that goes nowhere.
     */
    fun targets(imdbId: String): List<String> =
        listOfNotNull(appUri(imdbId), webUrl(imdbId))

    private fun normalised(imdbId: String): String? =
        imdbId.trim().takeIf { TmdbClient.looksLikeImdbId(it) }
}
