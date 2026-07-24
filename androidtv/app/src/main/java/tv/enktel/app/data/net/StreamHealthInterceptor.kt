package tv.enktel.app.data.net

import android.os.SystemClock
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that (a) feeds [StreamHealth] as requests complete and
 * (b) auto-fails a request over to a configured backup gateway when the
 * primary throws 403 (classic IPTV geoblock signal) or times out.
 *
 * Backup gateway list is supplied by [gateways] — a lambda so callers can
 * back it with a Flow-backed pref without wiring DataStore through here.
 * The list is `host[:port]` entries; only the request's host is swapped,
 * so the rest of the URL (scheme, path, query) is preserved intact.
 *
 * "Transparent to the user" is the goal: a 403 that would have been a hard
 * playback error becomes at worst a one-second glitch as the interceptor
 * re-issues against the backup.  The chain records both the primary
 * failure (for the toast) and the backup success (for the health chip)
 * so the user knows what just happened.
 */
class StreamHealthInterceptor(
    private val gateways: () -> List<String>,
    private val notify: (String) -> Unit = {},
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val start = SystemClock.elapsedRealtime()
        val host = original.url.host
        val resp: Response = try {
            chain.proceed(original)
        } catch (io: IOException) {
            StreamHealth.recordTimeout(host, io.message ?: "IO error")
            // Timeout / connection-refused / socket reset: try backup once.
            return failoverOrRethrow(chain, original, io) ?: throw io
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        if (resp.code == 403) {
            StreamHealth.recordBlocked(host)
            resp.close()
            val alt = tryBackup(chain, original) ?: throw IOException("403 Forbidden — no backup gateway configured")
            if (alt.isSuccessful) {
                notify("Switched to backup gateway ${alt.request.url.host}")
                StreamHealth.setActiveGateway(alt.request.url.host)
            }
            return alt
        }
        if (resp.isSuccessful) {
            StreamHealth.recordSuccess(elapsed)
        }
        return resp
    }

    private fun failoverOrRethrow(
        chain: Interceptor.Chain,
        original: Request,
        why: IOException,
    ): Response? {
        val alt = tryBackup(chain, original) ?: return null
        if (alt.isSuccessful) {
            notify("Backup gateway recovered from: ${why.message}")
            StreamHealth.setActiveGateway(alt.request.url.host)
        }
        return alt
    }

    private fun tryBackup(chain: Interceptor.Chain, original: Request): Response? {
        val list = try { gateways() } catch (_: Throwable) { emptyList() }
        if (list.isEmpty()) return null
        val currentHost = original.url.host
        for (raw in list) {
            val (h, p) = parseHostPort(raw)
            if (h.equals(currentHost, ignoreCase = true)) continue
            val newUrl = original.url.newBuilder().host(h).apply {
                if (p != null) port(p)
            }.build()
            val rebuilt = original.newBuilder().url(newUrl).build()
            val r = try { chain.proceed(rebuilt) } catch (_: IOException) { continue }
            if (r.isSuccessful) return r
            r.close()
        }
        return null
    }

    private fun parseHostPort(raw: String): Pair<String, Int?> {
        val trimmed = raw.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/')
        val idx = trimmed.lastIndexOf(':')
        return if (idx > 0 && idx < trimmed.length - 1) {
            trimmed.substring(0, idx) to trimmed.substring(idx + 1).toIntOrNull()
        } else {
            trimmed to null
        }
    }
}
