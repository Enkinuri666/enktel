package tv.enktel.app.data.net

import android.os.SystemClock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
 *
 * There are two recoveries, tried in that order. A backup gateway, which the
 * viewer configures and which swaps the host for a mirror of the same panel;
 * and failing that the relay, which needs no configuration and changes only
 * *where the request comes from*. The second is what answers a geo-block:
 * those are refusals aimed at the address, not the credentials, so the only
 * thing that changes the answer is asking from somewhere else.
 */
class StreamHealthInterceptor(
    private val gateways: () -> List<String>,
    private val notify: (String) -> Unit = {},
    /**
     * Where to relay through when a host refuses this device.
     *
     * A lambda so a test can point it somewhere inert, and so a future setting
     * can move it without touching this class.
     */
    private val relayBase: () -> String = { RelayUrls.DEFAULT_BASE },
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
            return tryBackup(chain, original)
                ?: tryRelay(chain, original)
                ?: throw IOException(blockedMessage(host))
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
        // The helpers announce whichever path worked; this only adds the
        // reason, which the 403 path does not have.
        val alt = tryBackup(chain, original) ?: tryRelay(chain, original) ?: return null
        notify("Recovered from: ${why.message}")
        return alt
    }

    /**
     * Last resort: fetch the same URL through our own origin.
     *
     * A 403 on a stream is almost always geographic — the host is answering
     * the *address the request came from*, not the credentials — so the one
     * thing that changes the answer is asking from somewhere else. The relay
     * is somewhere else. Nothing about the request changes but its origin: the
     * URL is passed through whole, credentials and all, and a stream the line
     * is not entitled to is refused exactly as it would be direct.
     *
     * This is deliberately not gated on the Direct/Relay setting. That switch
     * chooses the *normal* path, and its default is Direct because direct is
     * better whenever it works. This is the abnormal path — the request has
     * already failed — and a viewer should not have to know the switch exists
     * to watch a channel that a host will not serve to their country.
     *
     * Two things are never relayed. A request already aimed at the relay's own
     * host, which would nest the relay inside itself; and that same rule keeps
     * the metadata proxies out of it, since the guide, playlist, TMDB and OMDb
     * endpoints all live on that origin — a 403 from those means the service
     * said no, and asking it again from itself would not change its mind.
     */
    private fun tryRelay(chain: Interceptor.Chain, original: Request): Response? {
        val base = try { relayBase() } catch (_: Throwable) { "" }
        val relayed = RelayUrls.fallbackFor(original.url.toString(), base) ?: return null
        val relayHost = relayed.toHttpUrlOrNull()?.host ?: return null

        val rebuilt = original.newBuilder().url(relayed).build()
        val r = try { chain.proceed(rebuilt) } catch (_: IOException) { return null }
        if (r.isSuccessful) {
            notify("Playing via relay — ${original.url.host} refused this location")
            StreamHealth.setActiveGateway(relayHost)
            return r
        }
        r.close()
        return null
    }

    /**
     * What to say when nothing worked.
     *
     * "403 Forbidden" told a viewer nothing they could act on. A block that
     * survives the relay is one the relay's own location cannot satisfy
     * either, which is worth saying plainly rather than reporting as a generic
     * refusal.
     */
    private fun blockedMessage(host: String): String =
        "$host refused this location, and the relay's too. This channel is " +
            "geo-locked to a region neither can reach."

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
            if (r.isSuccessful) {
                notify("Switched to backup gateway $h")
                StreamHealth.setActiveGateway(h)
                return r
            }
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
