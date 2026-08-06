package tv.enktel.app.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import tv.enktel.app.data.metadata.TmdbClient
import tv.enktel.app.data.prefs.SettingsStore

/**
 * Trailer lookups for the Netflix-style hover auto-play.
 *
 * The metadata enrichment worker already stamps every movie/series row with its
 * TMDB id, so finding a trailer is one extra request against an id we hold —
 * no title matching, no guessing. This class exists purely to keep that request
 * from happening more than once per title: a user sweeping a remote across a
 * poster rail re-focuses the same twenty items constantly, and TMDB's free tier
 * is 40 requests per 10 seconds.
 *
 * Caching rules:
 *  - Results are memoised for the process lifetime, misses included — a title
 *    with no trailer on TMDB will still have none in thirty seconds, and
 *    remembering that is what stops the grid re-asking on every pass.
 *  - Concurrent requests for the same title share one in-flight lookup.
 *  - The cache is bounded; oldest entries are evicted first.
 */
class TrailerRepository(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
) {
    private companion object {
        const val MAX_ENTRIES = 400

        /** Server-side TMDB lookup, so trailers need no per-user setup. */
        const val PROXY = "https://enktel.tv/api/trailer"
    }

    /** key → YouTube video id, or null when TMDB has no trailer for it. */
    private val cache = LinkedHashMap<String, String?>(64, 0.75f, true)
    private val lock = Mutex()

    /**
     * Always true now.
     *
     * This used to be `tmdbApiKey.isNotBlank()`, and it was the reason trailers
     * "didn't work": the whole feature was gated behind the user going into
     * Settings and pasting a TMDB API key, which essentially nobody does. Every
     * trailer path then returned null and no-opped in silence, which reads as a
     * broken feature rather than an unconfigured one. Lookups now fall back to
     * enktel.tv's own server-side key, so a personal key is an optimisation
     * (fewer shared-quota hops), not a prerequisite.
     */
    suspend fun isAvailable(): Boolean = true

    /**
     * YouTube video id for [tmdbId], or null if there isn't one (or TMDB is
     * unreachable, or no API key is set). Never throws.
     */
    suspend fun trailerKey(tmdbId: Long, isSeries: Boolean): String? =
        trailerKey(tmdbId, "", isSeries)

    /**
     * As above, but resolves the TMDB id from [title] when the row has none.
     *
     * Hover trailers were inert for most libraries and this is why: the id is
     * written by the enrichment worker, the worker could only read it from the
     * panel, and most panels never publish one — so `tmdbId` stayed 0, the
     * guard below returned null immediately, and no trailer ever loaded. That
     * looked like "the toggle does nothing".
     *
     * Searching here rather than waiting for the worker also means the feature
     * works on the first hover of a fresh catalogue, instead of only after
     * enrichment has ground through thousands of rows at one request per
     * 250 ms.
     */
    suspend fun trailerKey(tmdbId: Long, title: String, isSeries: Boolean): String? {
        if (tmdbId <= 0 && title.isBlank()) return null
        // Cache on the title when there is no id, so a miss is remembered too
        // and a hovered poster does not re-search on every focus.
        val cacheKey = if (tmdbId > 0) {
            "${if (isSeries) "tv" else "movie"}:$tmdbId"
        } else {
            "${if (isSeries) "tv" else "movie"}:t:${title.lowercase()}"
        }
        lock.withLock { if (cache.containsKey(cacheKey)) return cache[cacheKey] }

        val apiKey = try { settings.tmdbApiKey.first() } catch (_: Throwable) { "" }
        val found = if (apiKey.isNotBlank()) {
            // A personal key talks to TMDB directly — one hop fewer, and the
            // user's own quota rather than the shared one.
            try {
                val client = TmdbClient(http, apiKey)
                val id = if (tmdbId > 0) tmdbId else client.search(title, 0, isSeries)
                if (id == null || id <= 0) null else client.trailerKey(id, isSeries)
            } catch (_: Throwable) { null }
        } else {
            proxyLookup(tmdbId, title, isSeries)
        }

        lock.withLock {
            cache[cacheKey] = found
            while (cache.size > MAX_ENTRIES) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return found
    }

    /**
     * enktel.tv's server-side lookup, used when the user has set no key of
     * their own — which is the overwhelmingly common case and the one the
     * feature was previously broken for.
     *
     * Returns null on anything other than a clean answer. The endpoint
     * deliberately distinguishes "no trailer exists" from "lookup is not
     * configured", but from the app's side both mean the same thing: don't
     * offer a trailer button.
     */
    private suspend fun proxyLookup(
        tmdbId: Long,
        title: String,
        isSeries: Boolean,
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val q = buildString {
                append(PROXY)
                append("?type=").append(if (isSeries) "tv" else "movie")
                if (tmdbId > 0) {
                    append("&tmdb=").append(tmdbId)
                } else {
                    append("&title=").append(java.net.URLEncoder.encode(title, "UTF-8"))
                }
            }
            val req = okhttp3.Request.Builder().url(q).get().build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                parseProxyKey(r.body.string())
            }
        } catch (_: Throwable) { null }
    }
}

/**
 * Pulls the YouTube id out of an `/api/trailer` response.
 *
 * `key` is null in the response whenever there is no trailer, so a null here
 * and a parse failure are the same outcome by design.
 */
private val proxyJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

internal fun parseProxyKey(body: String): String? = try {
    proxyJson.parseToJsonElement(body)
        .let { it as? kotlinx.serialization.json.JsonObject }
        ?.get("key")
        ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf { it.isNotBlank() }
} catch (_: Throwable) { null }
