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

    /**
     * Relay endpoint. Same origin as the playlist and guide.
     *
     * enktel.tv, not watch.enktel.tv. `/api/stream` is a route in *this*
     * repository and deploys with this site; watch.enktel.tv is a separate
     * property — the browser player — and serves no such route. The comment
     * above was already right about which origin this belongs to; the constant
     * disagreed with it, so every relayed request went to a host that answers
     * a 404 page, and the Direct/Relay switch in Settings has never done
     * anything but break playback. This is the same mistake that pointed the
     * playlist and the guide at the wrong host.
     */
    const val DEFAULT_BASE = "https://enktel.tv/api/stream"

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

    /**
     * The relay URL to retry a failed request with, or null when relaying it
     * would be wrong.
     *
     * Separate from [wrap] because the two answer different questions. [wrap]
     * is "route this deliberately"; this is "that failed, is the relay worth a
     * try". It refuses three cases, each of which is a real request this app
     * makes:
     *
     *  - a request already aimed at the relay's own host. That covers the
     *    guide, the playlist and the TMDB and OMDb proxies, which all live on
     *    that origin: a 403 from one of those is the service saying no, and
     *    asking it again through itself would not change the answer. It also
     *    stops the relay nesting inside itself on a rewritten HLS manifest.
     *  - anything that is not http(s) — a `file://` playlist the viewer
     *    imported has no upstream to fetch.
     *  - a URL [wrap] declines to change, so a caller never re-issues an
     *    identical request and calls it a retry.
     *
     * java.net.URI rather than OkHttp's HttpUrl so this is a plain function
     * that a unit test can call without a request or a chain.
     */
    fun fallbackFor(url: String, base: String = DEFAULT_BASE): String? {
        val endpoint = base.trim().trimEnd('/')
        if (endpoint.isEmpty()) return null

        val relayHost = hostOf(endpoint) ?: return null
        val targetHost = hostOf(url) ?: return null
        if (targetHost.equals(relayHost, ignoreCase = true)) return null

        val wrapped = wrap(url, endpoint, enabled = true)
        return wrapped.takeIf { it != url.trim() && it != url }
    }

    /**
     * Country-pinned relay endpoints, in the order worth trying.
     *
     * A geo-block is satisfied by *one* country, not by "somewhere other than
     * yours" — a British broadcaster refuses an American address as firmly as
     * an Australian one. So a blocked stream is retried from each country this
     * project can ask from, most likely first.
     *
     * US leads because the lineup is 83.7% American (2,446 of 2,923). GB is
     * second at 307. Two extra requests is the worst case, and only ever on a
     * request that has already failed.
     *
     * There is no Croatian entry, and that is not an oversight: no serverless
     * region exists in Croatia, so nothing here can present a Croatian address.
     * The twelve HR channels sit on `.hr` and `.ba` hosts, and one that
     * geo-locks stays locked whichever of these is asked. That case wants a
     * Croatian exit — Settings → Backup gateways takes one — or a source that
     * does not block, which is a different piece of work.
     */
    fun regionBases(base: String = DEFAULT_BASE): List<String> {
        val endpoint = base.trim().trimEnd('/')
        if (endpoint.isEmpty()) return emptyList()
        return listOf("$endpoint/us", "$endpoint/gb")
    }

    /**
     * Every relay URL worth trying for a request that just failed, in order.
     *
     * Empty when relaying this URL would be wrong — see [fallbackFor], which
     * decides that once for the whole chain.
     */
    fun fallbackChain(url: String, base: String = DEFAULT_BASE): List<String> {
        if (fallbackFor(url, base) == null) return emptyList()
        return regionBases(base).mapNotNull { fallbackFor(url, it) }
    }

    /** A human name for whichever endpoint answered, for the on-screen note. */
    fun regionOf(relayUrl: String): String = when {
        relayUrl.contains("/api/stream/us?", true) -> "the US relay"
        relayUrl.contains("/api/stream/gb?", true) -> "the UK relay"
        else -> "the relay"
    }

    private fun hostOf(url: String): String? = try {
        java.net.URI(url.trim()).host
    } catch (_: Throwable) {
        null
    }

    /** [wrap] over an ordered candidate list, preserving its order. */
    fun wrapAll(urls: List<String>, base: String = DEFAULT_BASE, enabled: Boolean = true): List<String> {
        if (!enabled) return urls
        return urls.map { wrap(it, base, enabled) }
    }
}
