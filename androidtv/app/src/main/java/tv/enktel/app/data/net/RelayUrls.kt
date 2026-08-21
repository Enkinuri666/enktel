package tv.enktel.app.data.net

import java.net.URLEncoder

/**
 * Route a stream through the server-side relay instead of opening it directly.
 *
 * Direct is the default and is what you want: fewest hops, lowest latency,
 * nothing in the middle. Relay exists for when the path between *this device*
 * and *that host* is the thing that is broken — a network that blocks the
 * host, an origin that refuses this address. The stream then arrives from our
 * own origin over a path we control.
 *
 * The URL is passed through untouched, credentials and all. The relay adds no
 * access of its own: a stream the line is not entitled to is refused exactly
 * as it would be direct. This changes where the request comes from, nothing
 * else.
 */
object RelayUrls {

    /** Relay endpoint. Same origin as the playlist and guide. */
    const val DEFAULT_BASE = "https://watch.enktel.tv/api/stream"

    /**
     * Wrap one URL for the relay, or return it unchanged.
     *
     * Returns the input untouched when relay is off, when there is no base to
     * relay through, when the URL is not http(s) — a local file has no upstream
     * to fetch — or when it has already been wrapped. That last case matters
     * because a rewritten HLS manifest comes back with its segments already
     * pointing here, and wrapping those a second time would nest the relay
     * inside itself.
     */
    fun wrap(url: String, base: String = DEFAULT_BASE, enabled: Boolean = true): String {
        if (!enabled) return url
        val target = url.trim()
        if (target.isEmpty()) return url
        if (!target.startsWith("http://", true) && !target.startsWith("https://", true)) return url
        val endpoint = base.trim().trimEnd('/')
        if (endpoint.isEmpty()) return url
        if (target.startsWith("$endpoint?", true)) return url

        return "$endpoint?u=${URLEncoder.encode(target, "UTF-8")}"
    }

    /** [wrap] over an ordered candidate list, preserving its order. */
    fun wrapAll(urls: List<String>, base: String = DEFAULT_BASE, enabled: Boolean = true): List<String> {
        if (!enabled) return urls
        return urls.map { wrap(it, base, enabled) }
    }
}
