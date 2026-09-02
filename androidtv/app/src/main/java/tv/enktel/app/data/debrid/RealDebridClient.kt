package tv.enktel.app.data.debrid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.arr
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.data.str
import java.io.IOException

/**
 * A client for the viewer's own Real-Debrid account.
 *
 * Real-Debrid is a service the viewer subscribes to. It takes a link they
 * already have and returns a direct, unthrottled one, and it holds whatever
 * they have added to their account. This talks to that account with their own
 * token and plays back what the service says is theirs. It does not look
 * anywhere for content to feed it — there is no search here, by design.
 *
 * Every call needs a token, so every method returns a [Result] rather than
 * throwing: a debrid account that has lapsed is a normal state for a viewer to
 * be in and should read as a message, not a crash.
 */
class RealDebridClient(
    private val http: OkHttpClient,
    private val token: String,
) {
    val configured: Boolean get() = token.isNotBlank()

    /** Who this token belongs to, and whether it can actually unrestrict. */
    data class Account(
        val username: String,
        val type: String,
        val expiration: String,
        val points: Int,
    )

    /** One playable item — a completed download or an unrestricted link. */
    data class Item(
        val id: String,
        val filename: String,
        val bytes: Long,
        /** The direct URL to hand the player. */
        val download: String,
        val streamable: Boolean,
    )

    suspend fun account(): Result<Account> = call("$BASE/user") { o ->
        Account(
            username = o.str("username").orEmpty(),
            type = o.str("type").orEmpty(),
            expiration = o.str("expiration").orEmpty(),
            points = o.int("points") ?: 0,
        )
    }

    /**
     * Turn a hoster link the viewer supplies into a direct one.
     *
     * The link comes from the viewer — pasted in, or already sitting in their
     * account. Nothing here discovers links.
     */
    suspend fun unrestrict(link: String, password: String = ""): Result<Item> {
        val body = FormBody.Builder()
            .add("link", link.trim())
            .apply { if (password.isNotBlank()) add("password", password) }
            .build()
        return call("$BASE/unrestrict/link", body) { it.toItem() }
    }

    /**
     * The viewer's own download history, newest first.
     *
     * Capped rather than paged: this is a list to pick from on a television,
     * and the API's own default is 100. Asking for thousands would spend the
     * rate limit to fill a screen nobody scrolls to the end of.
     */
    suspend fun downloads(limit: Int = 100): Result<List<Item>> =
        callList("$BASE/downloads?limit=${limit.coerceIn(1, 5000)}") { it.toItem() }

    /** Files the viewer has added to their account. */
    suspend fun torrents(limit: Int = 100): Result<List<Item>> =
        callList("$BASE/torrents?limit=${limit.coerceIn(1, 5000)}") { o ->
            Item(
                id = o.str("id").orEmpty(),
                filename = o.str("filename").orEmpty(),
                bytes = o.long("bytes") ?: 0L,
                // A torrent entry carries links to unrestrict rather than a
                // direct URL, so the first one is kept and resolved on demand.
                download = o.get("links").arr()
                    ?.firstOrNull()
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty(),
                streamable = o.str("status") == "downloaded",
            )
        }

    // ── plumbing ───────────────────────────────────────────────────────

    private fun JsonObject.toItem() = Item(
        id = str("id").orEmpty(),
        filename = str("filename").orEmpty(),
        bytes = long("filesize") ?: long("bytes") ?: 0L,
        download = str("download").orEmpty(),
        streamable = (int("streamable") ?: 0) == 1,
    )

    private suspend fun <T> call(
        url: String,
        body: okhttp3.RequestBody? = null,
        map: (JsonObject) -> T,
    ): Result<T> = request(url, body) { el ->
        (el as? JsonObject)?.let(map)
            ?: throw IOException("Real-Debrid returned something unexpected.")
    }

    private suspend fun <T> callList(url: String, map: (JsonObject) -> T): Result<List<T>> =
        request(url, null) { el ->
            (el as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(map) }
                ?: throw IOException("Real-Debrid returned something unexpected.")
        }

    private suspend fun <T> request(
        url: String,
        body: okhttp3.RequestBody?,
        map: (JsonElement) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "No Real-Debrid token set. Add one in Settings." }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .apply { if (body != null) post(body) }
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    // The service explains itself in the body; RealDebrid turns
                    // that into something a viewer can act on. Parsing is
                    // best-effort because an error body is the one place a
                    // service is least likely to be well-formed.
                    val apiError = runCatching {
                        (Lenient.parseToJsonElement(text) as? JsonObject)?.str("error")
                    }.getOrNull()
                    throw IOException(RealDebrid.describeFailure(resp.code, apiError))
                }
                map(Lenient.parseToJsonElement(text))
            }
        }
    }

    companion object {
        private const val BASE = RealDebrid.BASE
        private val Lenient = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
