package tv.enktel.app.data.net

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.enktel.app.BuildConfig
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.long
import tv.enktel.app.data.str
import java.io.IOException

/**
 * Result of a successful trial signup — everything OnboardingScreen needs to
 * auto-log-in without a second form. [expiresAt] is a Unix-ms timestamp; if the
 * server didn't return one we fall back to now + 24 h so the settings banner
 * still shows something.
 */
data class TrialCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val expiresAt: Long,
)

/**
 * Talks to the Eagle 4K trial signup endpoint (BuildConfig.EAGLE_TRIAL_URL).
 *
 * Wire shape (POST, application/json):
 *   { device_id: "<android id>", duration_hours: 24 }
 * Expected response:
 *   { server_url, username, password, expires_at }   // expires_at may be
 *   { server, username, password, exp_date }         // Xtream-style seconds
 * Either shape is accepted. Trailing panels sometimes wrap the payload in
 * `data:{...}` — we unwrap that.
 */
class EagleTrialClient(private val http: OkHttpClient) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun createTrial(ctx: Context): Result<TrialCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            val url = BuildConfig.EAGLE_TRIAL_URL
            require(url.startsWith("http")) { "Trial endpoint not configured" }
            val body = buildJsonObject {
                put("device_id", deviceId(ctx))
                put("duration_hours", 24)
                put("client", "android-${BuildConfig.FLAVOR}")
                put("version", BuildConfig.VERSION_NAME)
            }.toString().toRequestBody(jsonType)
            val req = Request.Builder().url(url).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException(errorMessage(resp.code, text))
                }
                parseTrialResponse(text)
            }
        }
    }

    private fun parseTrialResponse(text: String): TrialCredentials {
        require(text.isNotBlank()) { "Empty response from trial endpoint" }
        val root = LenientJson.parseToJsonElement(text) as? JsonObject
            ?: throw IOException("Unrecognised trial response")
        val payload = (root["data"] as? JsonObject) ?: root
        val serverUrl = payload.str("server_url") ?: payload.str("server") ?: payload.str("panel")
            ?: throw IOException("Trial response missing server URL")
        val username = payload.str("username") ?: throw IOException("Trial response missing username")
        val password = payload.str("password") ?: throw IOException("Trial response missing password")
        val expiresAt = payload.long("expires_at")
            ?: payload.long("exp_date")?.times(1000L)
            ?: (System.currentTimeMillis() + 24 * 60 * 60_000L)
        return TrialCredentials(serverUrl.trim(), username.trim(), password.trim(), expiresAt)
    }

    private fun errorMessage(code: Int, body: String): String {
        val friendly = when (code) {
            404 -> "Trial signup is not available right now"
            409 -> "This device already used its free trial"
            429 -> "Too many trial requests — try again later"
            in 500..599 -> "Trial server is temporarily unavailable"
            else -> "Trial signup failed (HTTP $code)"
        }
        val serverMessage = runCatching {
            val obj = LenientJson.parseToJsonElement(body) as? JsonObject
            obj?.str("message") ?: obj?.str("error")
        }.getOrNull()
        return if (serverMessage.isNullOrBlank()) friendly else "$friendly · $serverMessage"
    }

    /**
     * A stable identifier for this device, used only to bound free trials to
     * one per device.
     *
     * `ANDROID_ID` is the right primitive for that: it is scoped per app
     * signing key and per user, resets on factory reset, and is not a hardware
     * serial — so it identifies an *install*, not a person, and cannot be
     * correlated with any other app's copy. Lint flags every use of it because
     * the API is widely abused for tracking; this is the sanctioned exception,
     * hence the suppression rather than a workaround.
     *
     * The fallback is memoised. It used to be
     * `"unknown-${System.currentTimeMillis()}"` evaluated per call, which
     * returned a *different* id every time — so on any device where
     * ANDROID_ID is unavailable the trial limit it exists to enforce did not
     * apply at all, and an ordinary network retry counted as a second device.
     * Memoising makes it stable for the life of the process, which is as long
     * as a single signup flow lasts.
     */
    @android.annotation.SuppressLint("HardwareIds")
    private fun deviceId(ctx: Context): String {
        val androidId = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (_: Throwable) { "" }
        if (androidId.isNotBlank()) return androidId
        return fallbackId ?: "unknown-${System.currentTimeMillis()}".also { fallbackId = it }
    }

    @Volatile private var fallbackId: String? = null
}
