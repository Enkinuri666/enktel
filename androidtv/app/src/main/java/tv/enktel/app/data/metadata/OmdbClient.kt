package tv.enktel.app.data.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.str

/**
 * IMDb ratings, which TMDB does not carry.
 *
 * A TMDB payload gives IMDb's *id* (`tt0137523`) and TMDB's *own* rating
 * (`vote_average`). The two ratings are computed from different audiences and
 * routinely disagree by half a point or more, so showing TMDB's number under
 * an IMDb label would be wrong. IMDb publishes no free API of its own; OMDb is
 * the long-standing third-party mirror that does, keyed by exactly the id TMDB
 * already handed us.
 *
 * The key lives on our server, not in the APK — same reasoning as
 * [TmdbClient]: an APK is a zip, and a key inside one is a published key that
 * gets throttled for everybody at once. Devices ask `enktel.tv/api/omdb`,
 * which holds the key and caches hard at the edge, so the same few thousand
 * titles are fetched from OMDb once rather than once per install.
 */
class OmdbClient(
    private val http: OkHttpClient,
    private val serviceBase: String = SERVICE_BASE,
) {

    /**
     * What IMDb says about one title.
     *
     * Separate from the rating so the UI can decide: a 9.4 from 40 votes is
     * not the same claim as a 9.4 from 400,000, and only the vote count tells
     * them apart.
     */
    data class Rating(val imdbId: String, val rating: Double, val votes: Int)

    /**
     * Look up one title. Null when the id is unusable, the service has no key
     * configured, or OMDb has no rating for it.
     */
    suspend fun rating(imdbId: String): Rating? = withContext(Dispatchers.IO) {
        if (!TmdbClient.looksLikeImdbId(imdbId)) return@withContext null
        val url = "${serviceBase.trimEnd('/')}?i=${imdbId.trim()}"
        val body = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body.string()
            }
        } catch (_: Throwable) {
            return@withContext null
        }
        parseRating(body)
    }

    companion object {
        /** Our keyed proxy. Same origin as the playlist, guide and TMDB proxy. */
        const val SERVICE_BASE = "https://enktel.tv/api/omdb"

        private val Lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Parse an OMDb response.
         *
         * Everything in an OMDb payload is a **string**, including the numbers:
         * `"imdbRating":"8.4"` and `"imdbVotes":"2,357,891"` — with the commas.
         * Reading either as a JSON number gets null, and reading the votes with
         * `toInt()` gets null too, so both are unpicked explicitly here rather
         * than left to a deserialiser to get quietly wrong.
         *
         * OMDb reports failure in the body with `{"Response":"False"}` and
         * HTTP 200, so a successful request is not a successful lookup. It also
         * writes the literal string `"N/A"` into any field it has no value for,
         * which parses as a number nowhere and must not become a 0.0 rating.
         */
        fun parseRating(body: String): Rating? {
            val json = runCatching { Lenient.parseToJsonElement(body.ifBlank { "null" }) }
                .getOrNull() as? JsonObject ?: return null

            if (json.str("Response").equals("False", ignoreCase = true)) return null

            val id = json.str("imdbID").orEmpty().trim()
            if (!TmdbClient.looksLikeImdbId(id)) return null

            val rating = json.numberish("imdbRating")?.toDoubleOrNull() ?: return null
            // A rating outside IMDb's own 1–10 scale is a parse that went
            // wrong, not a title nobody liked.
            if (rating <= 0.0 || rating > 10.0) return null

            val votes = json.numberish("imdbVotes")
                ?.replace(",", "")
                ?.toIntOrNull()
                ?: 0

            return Rating(imdbId = id, rating = rating, votes = votes)
        }

        /** A field's text, unless OMDb filled it with its "no value" marker. */
        private fun JsonElement?.numberish(key: String): String? =
            str(key)?.trim()?.takeUnless { it.isEmpty() || it.equals("N/A", ignoreCase = true) }
    }
}
