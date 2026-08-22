package tv.enktel.app.data.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.arr
import tv.enktel.app.data.bool
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.str

/**
 * Minimal TMDB HTTPS client — only the two endpoints we need
 * (`/3/movie/{id}` and `/3/tv/{id}`), both with `append_to_response=keywords,credits`
 * so a single request returns cast + crew + tags. Unauthenticated: users
 * supply their own free-tier v4 read-only bearer token or v3 API key via
 * Settings → Metadata (see [TmdbEnrichmentWorker]).
 *
 * Response mapping is intentionally forgiving — TMDB has been known to
 * change nullability of individual fields; we prefer to return partial
 * data rather than fail the whole enrichment.
 */
class TmdbClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    serviceBase: String = SERVICE_BASE,
) {
    /**
     * No personal key set, so talk to our own proxy, which holds one.
     *
     * Enrichment used to require every viewer to register a TMDB key and paste
     * it into Settings, which meant it was off for essentially every install.
     * Compiling a key into the APK is not the alternative — an APK is a zip,
     * and a published key gets throttled or revoked for everybody at once.
     * A viewer who does set their own key talks to TMDB directly and never
     * touches the proxy.
     */
    private val viaService = apiKey.isBlank()

    private val apiBase = if (viaService) serviceBase.trimEnd('/') else BASE

    /** A v4 read token goes in a header; a v3 key goes in the query string. */
    private val useBearer = !viaService && apiKey.length > 60

    /** One place that knows how this build authenticates, if at all. */
    private fun get(url: String): Request = Request.Builder()
        .url(if (viaService || useBearer) url else "$url&api_key=$apiKey")
        .apply { if (useBearer) header("Authorization", "Bearer $apiKey") }
        .build()

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"

        /** Our keyed proxy. Same origin as the playlist and guide. */
        const val SERVICE_BASE = "https://enktel.tv/api/tmdb"
        private const val LANG = "en-US"
        private val LenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        /** `tt` followed by at least seven digits — IMDb's own id shape. */
        private val IMDB_ID = Regex("^tt[0-9]{7,}$")

        fun looksLikeImdbId(value: String): Boolean = IMDB_ID.matches(value.trim())

        /**
         * Pull IMDb's id out of a TMDB payload, from wherever it put it.
         *
         * A movie carries `imdb_id` at the top level; a series carries it only
         * under `external_ids`, which is why the request appends that. Reading
         * one and not the other would have given films IMDb links and left
         * every series without one.
         *
         * TMDB also answers with JSON `null` or an empty string for a title it
         * has no IMDb mapping for, so a value is taken only if it looks like an
         * IMDb id. Anything else is a link to a 404.
         */
        fun imdbIdOf(json: JsonObject): String {
            val candidates = listOf(json.str("imdb_id"), json.get("external_ids").str("imdb_id"))
            return candidates.filterNotNull().firstOrNull { looksLikeImdbId(it) }.orEmpty()
        }
    }

    data class Enrichment(
        val tmdbId: Long,
        /** IMDb's `tt…` id, or blank when TMDB knows no mapping. */
        val imdbId: String = "",
        val overview: String,
        val genres: List<String>,
        val studios: List<String>,
        val cast: List<String>,
        val directors: List<String>,
        val keywords: List<String>,
        val releaseYear: Int,
        val runtimeMinutes: Int,
        val backdropPath: String,
        val posterPath: String,
    )

    suspend fun movie(tmdbId: Long): Enrichment? = fetch("movie", tmdbId, isSeries = false)
    suspend fun series(tmdbId: Long): Enrichment? = fetch("tv", tmdbId, isSeries = true)

    /**
     * Resolves a TMDB id from a title, for catalogues whose panel does not
     * publish one.
     *
     * This is not an optional nicety. Enrichment previously read `tmdb_id`
     * straight off the Xtream `get_vod_info` payload and skipped the row when
     * it was absent — and most panels never populate that field, so for a
     * typical line enrichment resolved nothing at all no matter how often the
     * user re-synced. Searching by title is the only way the feature works for
     * those catalogues, which is to say: for most of them.
     *
     * [year] narrows the search when the catalogue knows it. TMDB ranks by
     * popularity, so the first hit for a bare title is usually right, but a
     * year turns "The Thing" from a coin-flip into an exact answer.
     */
    suspend fun search(title: String, year: Int, isSeries: Boolean): Long? =
        withContext(Dispatchers.IO) {
            val cleaned = TitleSanitizer.clean(title).trim()
            if (cleaned.isBlank()) return@withContext null

            val kind = if (isSeries) "tv" else "movie"
            // TMDB dates the two media types differently.
            val yearParam = when {
                year <= 0 -> ""
                isSeries -> "&first_air_date_year=$year"
                else -> "&year=$year"
            }
            val url = "$apiBase/search/$kind?language=$LANG&include_adult=false" +
                "&query=${java.net.URLEncoder.encode(cleaned, "UTF-8")}$yearParam"
            val req = get(url)
            val json: JsonElement = try {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    LenientJson.parseToJsonElement(r.body.string().ifBlank { "null" })
                }
            } catch (_: Throwable) {
                return@withContext null
            }
            val first = (json as? JsonObject)?.get("results").arr()?.firstOrNull()
                ?: return@withContext null
            first.int("id")?.toLong()?.takeIf { it > 0 }
        }

    /**
     * YouTube video id of the best trailer for [tmdbId], or null when TMDB has
     * none. Powers the hover auto-trailer on poster grids.
     *
     * Preference order is "what a viewer would call *the* trailer": an official
     * trailer first, then any trailer, then a teaser, then any YouTube clip at
     * all. English is tried first and then the unfiltered list, because a lot of
     * non-US catalogue titles only carry a trailer in their original language.
     */
    suspend fun trailerKey(tmdbId: Long, isSeries: Boolean): String? =
        trailerKeys(tmdbId, isSeries, limit = 1).firstOrNull()

    /**
     * The same ranking, but every candidate rather than only the winner.
     *
     * A YouTube video that its owner has switched embedding off for will never
     * play inside the app, no matter how correctly the player is configured —
     * the IFrame API answers error 101/150 and that is the end of it. Studios
     * do this to individual uploads fairly often, so the difference between
     * "trailers are broken for this film" and "trailers work" is frequently
     * just having a second id to try. Returning the list is what lets the
     * player fall through to the teaser instead of giving up.
     */
    suspend fun trailerKeys(
        tmdbId: Long,
        isSeries: Boolean,
        limit: Int = 4,
    ): List<String> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext emptyList()
        val kind = if (isSeries) "tv" else "movie"
        // English first, then unfiltered: a lot of non-US catalogue titles only
        // carry a trailer in their original language, and a localised list that
        // came back empty should not mask one that would not have.
        val ordered = rankTrailers(videos(kind, tmdbId, LANG)) +
            rankTrailers(videos(kind, tmdbId, null))
        ordered.distinct().take(limit)
    }

    private fun videos(kind: String, tmdbId: Long, language: String?): List<JsonElement> {
        val params = buildList {
            if (language != null) add("language=$language")
            if (!useBearer && !viaService) add("api_key=$apiKey")
        }
        val base = "$apiBase/$kind/$tmdbId/videos" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        val req = Request.Builder()
            .url(base)
            .apply { if (useBearer) header("Authorization", "Bearer $apiKey") }
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                val json = LenientJson.parseToJsonElement(r.body.string().ifBlank { "null" })
                (json as? JsonObject)?.get("results").arr().orEmpty()
            }
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * Every YouTube id in [results], best first.
     *
     * The order is "what a viewer would call *the* trailer": an official
     * trailer, then any trailer, then a teaser, then a clip, then whatever is
     * left. Previously this picked one and discarded the rest, which is why a
     * single un-embeddable upload meant no trailer at all.
     */
    private fun rankTrailers(results: List<JsonElement>): List<String> {
        if (results.isEmpty()) return emptyList()
        val youtube = results.filter { it.str("site").equals("YouTube", ignoreCase = true) }
        if (youtube.isEmpty()) return emptyList()
        fun rank(v: JsonElement): Int {
            val type = v.str("type").orEmpty().lowercase()
            val official = v.bool("official")
            return when {
                type == "trailer" && official -> 0
                type == "trailer" -> 1
                type == "teaser" -> 2
                type == "clip" -> 3
                else -> 4
            }
        }
        return youtube
            .sortedBy(::rank)
            .mapNotNull { it.str("key")?.takeIf(String::isNotBlank) }
    }

    /**
     * Returns the first YouTube video key from TMDB `/movie/{id}/videos` (or
     * `/tv/{id}/videos`), prioritising Trailer > Teaser > Clip. That's the
     * YouTube video id — the caller drops it into a `YouTubePlayerView` or
     * a `youtube.com/embed/{key}` iframe for the auto-trailer overlay.
     * Returns null when no video is available or the key isn't set.
     */
    suspend fun trailerYoutubeKey(kind: String, tmdbId: Long): String? = withContext(Dispatchers.IO) {
        // See [fetch]: a blank key means "use the proxy", not "give up".
        if (tmdbId <= 0) return@withContext null
        val urlBase = "$apiBase/$kind/$tmdbId/videos?language=$LANG"
        val req = get(urlBase)
        val json: JsonElement = try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                LenientJson.parseToJsonElement(r.body.string().ifBlank { "null" })
            }
        } catch (_: Throwable) { return@withContext null }
        if (json !is JsonObject) return@withContext null
        val results = json.get("results").arr() ?: return@withContext null
        // Prefer Trailer, then Teaser, then Clip. YouTube-hosted only.
        val ordered = results.sortedBy { v ->
            when (v.str("type").orEmpty().lowercase()) {
                "trailer" -> 0
                "teaser" -> 1
                "clip" -> 2
                else -> 3
            }
        }
        ordered.firstOrNull {
            v -> v.str("site").equals("YouTube", true)
        }?.str("key")
    }

    private suspend fun fetch(kind: String, tmdbId: Long, isSeries: Boolean): Enrichment? = withContext(Dispatchers.IO) {
        // Not `apiKey.isBlank()`. Blank is the *default*, and it is what
        // selects the server-side proxy up in [viaService] — so guarding on it
        // here made the proxy dead code: every install without a personal TMDB
        // key enriched nothing, which is every install the proxy was built
        // for. The only value that makes this call impossible is a missing id.
        if (tmdbId <= 0) return@withContext null
        // Accept either a v3 numeric key OR a v4 bearer token. We detect
        // token-style keys by their length (v4 tokens are ~200 chars,
        // v3 keys are 32 hex chars) and send the appropriate auth.
        // external_ids carries imdb_id for a series. A movie reports it at the
        // top level and a series never does, and appending it costs no extra
        // request — `append_to_response` is on the proxy's forwarded-parameter
        // list, so this works through the proxy as well as against TMDB direct.
        val urlBase =
            "$apiBase/$kind/$tmdbId?language=$LANG&append_to_response=keywords,credits,external_ids"
        val req = get(urlBase)
        val json: JsonElement = try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                LenientJson.parseToJsonElement(r.body.string().ifBlank { "null" })
            }
        } catch (_: Throwable) {
            return@withContext null
        }
        if (json !is JsonObject) return@withContext null

        val genres = json.get("genres").arr().orEmpty()
            .mapNotNull { it.str("name") }
        val studios = json.get("production_companies").arr().orEmpty()
            .mapNotNull { it.str("name") }
        val creditsCast = json.get("credits").get("cast").arr().orEmpty()
        val cast = creditsCast.take(6).mapNotNull { it.str("name") }
        val crew = json.get("credits").get("crew").arr().orEmpty()
        val directors = crew.filter {
            val job = it.str("job").orEmpty().lowercase()
            job == "director" || (isSeries && (job == "creator" || job == "executive producer"))
        }.mapNotNull { it.str("name") }.distinct()
        val keywords = when {
            isSeries -> json.get("keywords").get("results").arr()
            else -> json.get("keywords").get("keywords").arr()
        }.orEmpty().mapNotNull { it.str("name") }

        // TMDB ships different date fields depending on movie vs. tv.
        val dateField = if (isSeries) "first_air_date" else "release_date"
        val year = json.str(dateField).orEmpty()
            .take(4).toIntOrNull() ?: 0

        val runtime = when {
            isSeries -> json.get("episode_run_time").arr()?.firstOrNull()?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            } ?: 0
            else -> json.int("runtime") ?: 0
        }

        Enrichment(
            tmdbId = tmdbId,
            imdbId = imdbIdOf(json),
            overview = json.str("overview").orEmpty(),
            genres = genres,
            studios = studios,
            cast = cast,
            directors = directors,
            keywords = keywords,
            releaseYear = year,
            runtimeMinutes = runtime,
            backdropPath = json.str("backdrop_path").orEmpty(),
            posterPath = json.str("poster_path").orEmpty(),
        )
    }
}
