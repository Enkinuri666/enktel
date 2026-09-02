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

    /** A file inside a torrent the account holds. */
    data class TorrentFile(
        val id: Int,
        val path: String,
        val bytes: Long,
        val selected: Boolean,
    ) {
        /** Just the filename, which is what a picker should show. */
        val name: String get() = path.trimStart('/').substringAfterLast('/')
    }

    /** A torrent in the account, with enough state to decide what to do next. */
    data class Torrent(
        val id: String,
        val filename: String,
        val hash: String,
        val bytes: Long,
        /** Real-Debrid's own word: magnet_conversion, waiting_files_selection,
         *  downloading, downloaded, error, virus, dead… */
        val status: String,
        val progress: Int,
        val files: List<TorrentFile>,
        /** Restricted links, present once the status is `downloaded`. */
        val links: List<String>,
    ) {
        val ready: Boolean get() = status == "downloaded"

        /**
         * Real-Debrid will not fetch anything until it is told which files it
         * wants, and a torrent sitting in this state looks stalled rather than
         * waiting.
         */
        val awaitingSelection: Boolean get() = status == "waiting_files_selection"
    }

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

    /**
     * Add a magnet the viewer supplied to their account.
     *
     * The link comes from them, exactly as a hoster link does. Adding it does
     * not start a download: Real-Debrid holds the torrent until it is told
     * which files to fetch, which is what [selectFiles] is for.
     *
     * @return the new torrent's id
     */
    suspend fun addMagnet(magnetUri: String): Result<String> {
        val body = FormBody.Builder().add("magnet", magnetUri.trim()).build()
        return call("$BASE/torrents/addMagnet", body) { it.str("id").orEmpty() }
    }

    /** Everything the account knows about one torrent. */
    suspend fun torrentInfo(id: String): Result<Torrent> =
        call("$BASE/torrents/info/$id") { o ->
            Torrent(
                id = o.str("id").orEmpty(),
                filename = o.str("filename").orEmpty(),
                hash = o.str("hash").orEmpty().lowercase(),
                bytes = o.long("bytes") ?: 0L,
                status = o.str("status").orEmpty(),
                progress = o.int("progress") ?: 0,
                files = o.get("files").arr().orEmpty().mapNotNull { f ->
                    (f as? JsonObject)?.let {
                        TorrentFile(
                            id = it.int("id") ?: return@let null,
                            path = it.str("path").orEmpty(),
                            bytes = it.long("bytes") ?: 0L,
                            selected = (it.int("selected") ?: 0) == 1,
                        )
                    }
                },
                links = o.get("links").arr().orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            )
        }

    /**
     * Choose which files to fetch. Pass no ids for everything.
     *
     * Selecting all is the right default for a film, and wrong for a season
     * pack where it would pull twenty episodes to watch one — so the caller
     * decides and the picker exists.
     */
    suspend fun selectFiles(id: String, fileIds: List<Int> = emptyList()): Result<Unit> {
        val body = FormBody.Builder()
            .add("files", if (fileIds.isEmpty()) "all" else fileIds.joinToString(","))
            .build()
        return requestUnit("$BASE/torrents/selectFiles/$id", body)
    }

    /**
     * Does the service already hold this torrent?
     *
     * Worth asking before adding one. A cached torrent plays immediately; an
     * uncached one has to be fetched first, which can take a while, and
     * knowing which is which beforehand is the difference between waiting on
     * purpose and wondering whether something is broken.
     *
     * A false here means "not cached", never "unusable" — the answer is
     * advisory and a failure to get one is reported as not-cached rather than
     * blocking the add.
     */
    suspend fun isCached(infoHash: String): Result<Boolean> =
        request("$BASE/torrents/instantAvailability/$infoHash", null) { el ->
            // The response is keyed by hash and the value is an object of
            // hosters, each holding a list of file sets. Anything non-empty
            // under the hash means at least one complete set is held.
            val byHash = el as? JsonObject ?: return@request false
            byHash.values.any { hoster ->
                (hoster as? JsonObject)?.values?.any { set ->
                    (set as? JsonArray)?.isNotEmpty() == true || set is JsonObject
                } == true
            }
        }

    /** Remove a torrent from the account. */
    suspend fun deleteTorrent(id: String): Result<Unit> =
        requestUnit("$BASE/torrents/delete/$id", null, delete = true)

    /** Remove an entry from the download history. */
    suspend fun deleteDownload(id: String): Result<Unit> =
        requestUnit("$BASE/downloads/delete/$id", null, delete = true)

    /** What is left on a hoster that limits this account. */
    data class HostQuota(
        val host: String,
        val left: Long,
        val limit: Long,
        /** Real-Debrid's own word: links, gigabytes or bytes. */
        val unit: String,
        val reset: String,
    )

    /**
     * Per-hoster allowances, for the hosters that have one.
     *
     * Most entries on a premium account are unlimited, and an unlimited
     * allowance is not information — so the ones with no limit are dropped
     * here rather than filling a row on screen with zeroes.
     */
    suspend fun traffic(): Result<List<HostQuota>> = request("$BASE/traffic", null) { el ->
        (el as? JsonObject)?.mapNotNull { (host, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            HostQuota(
                host = host,
                left = o.long("left") ?: 0L,
                limit = o.long("limit") ?: 0L,
                unit = o.str("type").orEmpty(),
                reset = o.str("reset").orEmpty(),
            )
        }?.filter { it.limit > 0 }?.sortedBy { it.host } ?: emptyList()
    }

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

    /**
     * For the endpoints that answer 204 with no body.
     *
     * The ordinary path parses the response, and an empty string is not JSON —
     * so selectFiles and the deletes would have failed on success, which is
     * the most confusing shape a bug can take.
     */
    private suspend fun requestUnit(
        url: String,
        body: okhttp3.RequestBody?,
        delete: Boolean = false,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "No Real-Debrid token set. Add one in Settings." }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .apply {
                    when {
                        delete -> delete()
                        body != null -> post(body)
                    }
                }
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val apiError = runCatching {
                        (Lenient.parseToJsonElement(resp.body.string()) as? JsonObject)?.str("error")
                    }.getOrNull()
                    throw IOException(RealDebrid.describeFailure(resp.code, apiError))
                }
            }
        }
    }

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
