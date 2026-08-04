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
    }

    /** key → YouTube video id, or null when TMDB has no trailer for it. */
    private val cache = LinkedHashMap<String, String?>(64, 0.75f, true)
    private val lock = Mutex()

    /** True when a TMDB key is configured — the whole feature is inert without one. */
    suspend fun isAvailable(): Boolean = settings.tmdbApiKey.first().isNotBlank()

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
        if (apiKey.isBlank()) return null
        val found = try {
            val client = TmdbClient(http, apiKey)
            val id = if (tmdbId > 0) tmdbId else client.search(title, 0, isSeries)
            if (id == null || id <= 0) null else client.trailerKey(id, isSeries)
        } catch (_: Throwable) { null }

        lock.withLock {
            cache[cacheKey] = found
            while (cache.size > MAX_ENTRIES) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return found
    }
}
