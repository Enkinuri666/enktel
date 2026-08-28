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

    suspend fun call(p: Profile, action: String?, extra: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val url = StringBuilder(p.server.trimEnd('/'))
                .append("/player_api.php?username=").append(p.username)
                .append("&password=").append(p.password)
            if (action != null) url.append("&action=").append(action)
            extra.forEach { (k, v) -> url.append('&').append(k).append('=').append(v) }
            http.newCall(Request.Builder().url(url.toString()).build()).execute().use { resp ->
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
