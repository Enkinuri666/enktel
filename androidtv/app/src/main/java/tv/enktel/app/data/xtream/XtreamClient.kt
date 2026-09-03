package tv.enktel.app.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.db.Profile
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Thin client for the Xtream Codes `player_api.php` panel API. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class XtreamClient(private val http: OkHttpClient) {

    private fun urlFor(p: Profile, action: String?, extra: Map<String, String>): String {
        val url = StringBuilder(p.server.trimEnd('/'))
            .append("/player_api.php?username=").append(p.username)
            .append("&password=").append(p.password)
        if (action != null) url.append("&action=").append(action)
        extra.forEach { (k, v) -> url.append('&').append(k).append('=').append(v) }
        return url.toString()
    }

    suspend fun call(p: Profile, action: String?, extra: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val url = urlFor(p, action, extra)
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Panel returned HTTP ${resp.code}")
                // Parsed straight off the socket rather than through
                // `body.string()`.
                //
                // `string()` materialises the whole response as a Java String
                // — UTF-16, so two bytes per character — and the parser then
                // builds its tree on top of that while the String is still
                // referenced. get_vod_streams on a large line is tens of
                // megabytes of JSON, so the peak was the payload twice over
                // plus the tree, and a phone with a 256 MB heap ran out during
                // "Sync now". Decoding from the stream never holds the text at
                // all.
                val source = resp.body.source()
                // An empty body is a panel answering 200 with nothing, which
                // happens. Checked before decoding because the decoder treats
                // end-of-input as malformed JSON, and that would report a
                // panel quirk as a parse failure.
                if (source.exhausted()) {
                    JsonNull
                } else {
                    LenientJson.decodeFromStream<JsonElement>(source.inputStream())
                }
            }
        }

    /**
     * Read a list endpoint one entry at a time, mapping each as it arrives.
     *
     * [call] decodes off the socket, so the response text is never held whole
     * — but the *tree* still is, and the tree is the expensive part. Measured
     * against this app's own parser, a `JsonElement` tree costs about **7.3
     * times** the raw JSON: 29 MB of `get_vod_streams` becomes 214 MB of
     * objects. A six-figure line runs 70 MB or more, which projects past half
     * a gigabyte, and a phone gets a 256 MB heap. No amount of care further
     * down helps once that tree exists.
     *
     * So the tree is never built. The top-level array is decoded as a
     * sequence, each entry is turned into its row by [map] and then dropped,
     * and what survives is the rows — which are a fraction of the size,
     * because a `Movie` keeps ten fields and the panel sends thirty.
     *
     * [map] runs while the response is still open, which is what makes the
     * laziness real; returning null from it skips an entry.
     */
    suspend fun <T> mapArray(
        p: Profile,
        action: String,
        extra: Map<String, String> = emptyMap(),
        map: (JsonElement, Int) -> T?,
    ): List<T> = withContext(Dispatchers.IO) {
        val url = urlFor(p, action, extra)
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Panel returned HTTP ${resp.code}")
            PanelArray.mapEntries(resp.body.byteStream(), action, map)
        }
    }

    suspend fun login(p: Profile): JsonElement = call(p, null)
    suspend fun liveCategories(p: Profile) = call(p, "get_live_categories")
    suspend fun liveStreams(p: Profile) = call(p, "get_live_streams")
    suspend fun vodCategories(p: Profile) = call(p, "get_vod_categories")
    suspend fun vodStreams(p: Profile) = call(p, "get_vod_streams")
    suspend fun seriesCategories(p: Profile) = call(p, "get_series_categories")
    suspend fun seriesList(p: Profile) = call(p, "get_series")
    suspend fun seriesInfo(p: Profile, seriesId: Long) = call(p, "get_series_info", mapOf("series_id" to "$seriesId"))
    suspend fun vodInfo(p: Profile, vodId: Long) = call(p, "get_vod_info", mapOf("vod_id" to "$vodId"))
    suspend fun shortEpg(p: Profile, streamId: Long, limit: Int = 4) =
        call(p, "get_short_epg", mapOf("stream_id" to "$streamId", "limit" to "$limit"))

    companion object {
        fun liveUrl(p: Profile, streamId: Long, hls: Boolean): String {
            val base = p.server.trimEnd('/')
            return if (hls) "$base/live/${p.username}/${p.password}/$streamId.m3u8"
            else "$base/live/${p.username}/${p.password}/$streamId.ts"
        }

        fun vodUrl(p: Profile, streamId: Long, ext: String): String =
            "${p.server.trimEnd('/')}/movie/${p.username}/${p.password}/$streamId.${ext.ifBlank { "mp4" }}"

        fun episodeUrl(p: Profile, episodeId: Long, ext: String): String =
            "${p.server.trimEnd('/')}/series/${p.username}/${p.password}/$episodeId.${ext.ifBlank { "mp4" }}"

        /** Catch-up / timeshift stream for an archived programme. */
        fun timeshiftUrl(p: Profile, streamId: Long, startMs: Long, durationMinutes: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            val start = fmt.format(Date(startMs))
            return "${p.server.trimEnd('/')}/timeshift/${p.username}/${p.password}/$durationMinutes/$start/$streamId.ts"
        }

        fun xmltvUrl(p: Profile): String =
            "${p.server.trimEnd('/')}/xmltv.php?username=${p.username}&password=${p.password}"
    }
}
