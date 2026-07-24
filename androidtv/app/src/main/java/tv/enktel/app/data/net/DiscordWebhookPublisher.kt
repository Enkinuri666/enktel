package tv.enktel.app.data.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Pushes [PresenceTracker.state] into a user-configured Discord webhook URL
 * as a compact rich-embed message.  This is the practical mobile substitute
 * for full Discord Rich Presence (which requires local IPC to the desktop
 * Discord client and can't work from Android).
 *
 * Behaviour:
 *   - Reads [webhookUrl] from settings; publisher is a no-op when the
 *     setting is blank.
 *   - Debounces: fires at most once every [minIntervalMs] so a scrubbing
 *     user doesn't spam the webhook.  Discord itself rate-limits at
 *     ~5 req/min per webhook.
 *   - Renders as a Discord embed rather than a plain content line so the
 *     card in the channel looks like Rich Presence (title + description +
 *     poster/logo thumbnail + colour bar tied to content type).
 *   - Silently drops failures — sharing what you're watching should never
 *     interrupt the actual watching.
 */
class DiscordWebhookPublisher(
    private val http: OkHttpClient,
    private val webhookUrls: Flow<String>,
    private val minIntervalMs: Long = 15_000L,
) {

    private var lastPostAt: Long = 0
    private var lastPayload: String? = null

    fun startIn(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            PresenceTracker.state.collect { s ->
                val url = try { webhookUrls.first() } catch (_: Throwable) { "" }
                if (url.isBlank()) return@collect
                val payload = renderPayload(s) ?: return@collect
                val now = System.currentTimeMillis()
                if (now - lastPostAt < minIntervalMs && payload == lastPayload) return@collect
                lastPostAt = now
                lastPayload = payload
                post(url, payload)
            }
        }
    }

    private fun renderPayload(s: PresenceTracker.State): String? {
        val (title, description, color, thumb) = when (s) {
            is PresenceTracker.State.Idle -> return null
            is PresenceTracker.State.Live -> {
                val extra = s.programTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                Quad(
                    "📺 Watching Live TV",
                    "${escape(s.channelName)}${escape(extra)}",
                    LIVE_COLOR,
                    s.channelLogo,
                )
            }
            is PresenceTracker.State.Vod -> {
                val yearBit = if (s.year > 0) " (${s.year})" else ""
                val pctBit = if (s.durationMs > 0) {
                    val pct = ((s.positionMs.toDouble() / s.durationMs) * 100)
                        .toInt().coerceIn(0, 100)
                    val bars = "█".repeat(pct / 10) + "░".repeat(10 - pct / 10)
                    "\n[$bars] $pct%"
                } else ""
                val genreBit = if (s.genre.isNotBlank()) "\n${escape(s.genre)}" else ""
                Quad(
                    "🎬 Watching",
                    "**${escape(s.title)}**${escape(yearBit)}$genreBit$pctBit",
                    VOD_COLOR,
                    s.poster,
                )
            }
            is PresenceTracker.State.Sport -> {
                val liveBit = if (s.isLive) "🔴 LIVE · " else ""
                val leagueBit = if (s.league.isNotBlank()) "\n${escape(s.league)}" else ""
                val chanBit = if (s.channelName.isNotBlank()) "\non ${escape(s.channelName)}" else ""
                Quad(
                    "⚽ Watching Sports",
                    "$liveBit**${escape(s.eventTitle)}**$leagueBit$chanBit",
                    SPORT_COLOR,
                    "",
                )
            }
        }
        // Discord webhook body — no username override so the webhook renders
        // with whatever name+avatar the user configured on the Discord side.
        val thumbJson = if (thumb.isNotBlank()) ""","thumbnail":{"url":"${escape(thumb)}"}""" else ""
        return """
        {
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
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .build()
        try {
            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { /* swallow */ }
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
        private const val LIVE_COLOR = 0xEF4444   // EnktelLive (red)
        private const val VOD_COLOR = 0x34D399    // EnktelOk (emerald)
        private const val SPORT_COLOR = 0x3B9DFF  // EnktelBlue
    }
}
