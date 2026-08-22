package tv.enktel.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Other hosts carrying the same channel, published beside the lineup.
 *
 * The relay answers a geo-block by asking from another country, and there are
 * only the countries it can run in — a Croatian broadcaster is served from
 * neither Washington nor London. It also does nothing at all for a host that
 * has simply gone down. Playing the channel from a *different host* answers
 * both, and answers them wherever the viewer happens to be.
 *
 * The index is a flat `{ "<tvg-id>": ["url", …] }` built by
 * scripts/build-alternates.mjs from the lineup's own duplicate rows and
 * iptv-org's stream index. 1,041 of 2,303 channels have at least one, in about
 * 139 KB.
 *
 * Everything here is best-effort. A sync must not fail because this file is
 * missing, malformed or slow: the app is perfectly usable with no alternates,
 * which is what it did until now.
 */
object AlternateSources {

    /**
     * Where the index lives, given the playlist URL it accompanies.
     *
     * Derived rather than configured, so a deployment that moves the playlist
     * does not have to remember to move a second setting — and so a build
     * pointed at someone else's playlist looks for the index next to *that*
     * one, finds nothing, and carries on.
     */
    fun indexUrlFor(playlistUrl: String): String? {
        val url = playlistUrl.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        val base = url.substringBeforeLast('/', "")
        if (base.isEmpty()) return null
        return "$base/enktel-alternates.json"
    }

    /**
     * Fetch and parse the index. Empty on any failure, by design.
     */
    fun fetch(http: OkHttpClient, playlistUrl: String): Map<String, List<String>> {
        val url = indexUrlFor(playlistUrl) ?: return emptyMap()
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return emptyMap()
                parse(r.body.string())
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    /**
     * Parse the index.
     *
     * Lenient about shape and strict about contents: an entry that is not a
     * list of http(s) strings is dropped rather than trusted. This file is
     * fetched over the network and its URLs are handed to a media player, so
     * "whatever was in the JSON" is not good enough.
     */
    fun parse(body: String): Map<String, List<String>> {
        val root = runCatching { Lenient.parseToJsonElement(body.ifBlank { "{}" }) }
            .getOrNull() as? JsonObject ?: return emptyMap()

        val out = LinkedHashMap<String, List<String>>()
        for ((id, value) in root) {
            if (id.isBlank()) continue
            val urls = (value as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
                ?.map { it.trim() }
                ?.filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
                ?.distinct()
                .orEmpty()
            if (urls.isNotEmpty()) out[id] = urls
        }
        return out
    }

    /** How [tv.enktel.app.data.db.Channel.altUrls] stores a list. */
    fun encode(urls: List<String>): String = urls.joinToString("\n")

    /** The inverse of [encode], tolerant of blank and of stray whitespace. */
    fun decode(stored: String): List<String> =
        stored.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private val Lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
