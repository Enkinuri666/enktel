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
) {
    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        private const val LANG = "en-US"
        private val LenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    data class Enrichment(
        val tmdbId: Long,
        val overview: String,
        val genres: List<String>,
        val studios: List<String>,
        val cast: List<String>,
        val directors: List<String>,
        val keywords: List<String>,
        val releaseYear: Int,
        val runtimeMinutes: Int,
        val backdropPath: String,
    )

    suspend fun movie(tmdbId: Long): Enrichment? = fetch("movie", tmdbId, isSeries = false)
    suspend fun series(tmdbId: Long): Enrichment? = fetch("tv", tmdbId, isSeries = true)

    private suspend fun fetch(kind: String, tmdbId: Long, isSeries: Boolean): Enrichment? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || tmdbId <= 0) return@withContext null
        // Accept either a v3 numeric key OR a v4 bearer token. We detect
        // token-style keys by their length (v4 tokens are ~200 chars,
        // v3 keys are 32 hex chars) and send the appropriate auth.
        val useBearer = apiKey.length > 60
        val urlBase = "$BASE/$kind/$tmdbId?language=$LANG&append_to_response=keywords,credits"
        val req = Request.Builder()
            .url(if (useBearer) urlBase else "$urlBase&api_key=$apiKey")
            .apply { if (useBearer) header("Authorization", "Bearer $apiKey") }
            .build()
        val json: JsonElement = try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                LenientJson.parseToJsonElement(r.body?.string() ?: "null")
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
            overview = json.str("overview").orEmpty(),
            genres = genres,
            studios = studios,
            cast = cast,
            directors = directors,
            keywords = keywords,
            releaseYear = year,
            runtimeMinutes = runtime,
            backdropPath = json.str("backdrop_path").orEmpty(),
        )
    }
}
