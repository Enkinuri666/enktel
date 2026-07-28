package tv.enktel.app.data.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tv.enktel.app.data.prefs.SettingsStore
import java.io.IOException

/**
 * Imperative one-shot Discord webhook poster for the manual "🎧 Share to
 * Discord" button on the player screens. Sibling to [DiscordWebhookPublisher]
 * which auto-publishes state changes; this one is user-triggered and skips
 * the debounce because the user pressed a button and expects the message to
 * land immediately.
 *
 * Sends a Rich Presence-style embed announcing "Now streaming <title> in
 * <voice channel name>" so friends see the invite in the channel chat.
 */
class DiscordAnnouncer(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
) {

    sealed class Kind {
        data class Vod(val title: String, val year: Int, val poster: String, val genre: String) : Kind()
        data class Live(val channelName: String, val logo: String, val programTitle: String) : Kind()
        data class Sport(val eventTitle: String, val league: String, val channelName: String, val isLive: Boolean) : Kind()
    }

    fun share(scope: CoroutineScope, kind: Kind) {
        scope.launch(Dispatchers.IO) {
            val url = try { settings.discordWebhook.first() } catch (_: Throwable) { "" }
            if (url.isBlank()) return@launch
            val voice = try { settings.discordVoiceChannel.first() } catch (_: Throwable) { "Richard's Hangout" }
            val body = renderPayload(kind, voice)
            post(url, body)
        }
    }

    private fun renderPayload(kind: Kind, voice: String): String {
        val (title, description, color, thumb) = when (kind) {
            is Kind.Vod -> {
                val yearBit = if (kind.year > 0) " (${kind.year})" else ""
                val genreBit = if (kind.genre.isNotBlank()) "\n${escape(kind.genre)}" else ""
                Quad(
                    "🎬 Now streaming in $voice",
                    "**${escape(kind.title)}**${escape(yearBit)}$genreBit\n\n_Join the voice channel to watch along._",
                    VOD_COLOR,
                    kind.poster,
                )
            }
            is Kind.Live -> {
                val progBit = kind.programTitle.takeIf { it.isNotBlank() }?.let { "\n${escape(it)}" }.orEmpty()
                Quad(
                    "📺 Now streaming Live TV in $voice",
                    "**${escape(kind.channelName)}**$progBit\n\n_Join the voice channel to watch along._",
                    LIVE_COLOR,
                    kind.logo,
                )
            }
            is Kind.Sport -> {
                val prefix = if (kind.isLive) "🔴 LIVE · " else ""
                val onChan = if (kind.channelName.isNotBlank()) "\non ${escape(kind.channelName)}" else ""
                val league = if (kind.league.isNotBlank()) "\n${escape(kind.league)}" else ""
                Quad(
                    "⚽ Now streaming Sports in $voice",
                    "$prefix**${escape(kind.eventTitle)}**$league$onChan\n\n_Join the voice channel to watch along._",
                    SPORT_COLOR,
                    "",
                )
            }
        }
        val thumbJson = if (thumb.isNotBlank()) ""","thumbnail":{"url":"${escape(thumb)}"}""" else ""
        return """
        {
          "content": "@here — come watch!",
          "allowed_mentions": { "parse": [] },
          "embeds": [{
            "title": "${escape(title)}",
            "description": "${escape(description)}",
            "color": $color,
            "footer": { "text": "EnkTel IPTV · shared via webhook" }
            $thumbJson
          }]
        }
        """.trimIndent()
    }

    private fun post(url: String, body: String) {
        try {
            http.newCall(
                Request.Builder().url(url).post(body.toRequestBody(JSON)).build(),
            ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { /* swallow — never interrupt playback */ }
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        } catch (_: Throwable) { /* swallow */ }
    }

    private data class Quad(val title: String, val desc: String, val color: Int, val thumb: String)

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val LIVE_COLOR = 0xEF4444
        private const val VOD_COLOR = 0x34D399
        private const val SPORT_COLOR = 0x3B9DFF
    }
}
