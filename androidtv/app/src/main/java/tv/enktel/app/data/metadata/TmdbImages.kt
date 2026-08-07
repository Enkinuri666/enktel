package tv.enktel.app.data.metadata

/**
 * Turns the relative image paths TMDB returns into URLs.
 *
 * TMDB never sends a usable URL. Every image field in its payload is a bare
 * path like `/kqjL17yufvn9OVLyXYpvtyrFfak.jpg`, and the caller is expected to
 * prepend a base and pick a size. Storing the raw path in the database and
 * building the URL at the call site would put that knowledge — including which
 * size is right for a poster versus a hero image — in every screen that shows
 * one, so it lives here and the database stores finished URLs.
 *
 * ### Sizes are chosen for the weakest device
 *
 * The floor for this app is a Fire TV Stick Lite: 1 GB of RAM shared with the
 * system, decoding a 1080p stream. `original` backdrops from TMDB are commonly
 * 3840 px wide, which decodes to roughly 59 MB of bitmap for something that
 * will be drawn 1920 px wide at most and blurred behind a rail. w1280 covers a
 * 1080p panel and decodes to about a sixteenth of that.
 */
object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"

    /** Portrait box art. w500 is ~2× the largest poster this app draws. */
    private const val POSTER_SIZE = "w500"

    /** Landscape hero art, used behind details pages and the ambient glow. */
    private const val BACKDROP_SIZE = "w1280"

    fun poster(path: String?): String = url(path, POSTER_SIZE)

    fun backdrop(path: String?): String = url(path, BACKDROP_SIZE)

    /**
     * Empty for anything that is not a TMDB image path.
     *
     * The empty string rather than null because these are written straight into
     * non-null database columns, where "" already means "we have no image" and
     * every consumer checks `isNotBlank()`. Returning a half-built URL for a
     * blank path — the failure mode if this just concatenated — would produce a
     * row that looks like it has art and 404s when something tries to load it.
     */
    private fun url(path: String?, size: String): String {
        val p = path?.trim().orEmpty()
        // TMDB paths are always absolute. Anything else is a value we did not
        // get from TMDB — most likely a URL a panel supplied, which must not be
        // prefixed, or junk.
        if (!p.startsWith("/") || p.length < 2) return ""
        return BASE + size + p
    }
}
