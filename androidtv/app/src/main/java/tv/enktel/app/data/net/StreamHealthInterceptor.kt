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

        // A host the exit has already served goes out through it from the
        // start. A stream fetches a segment every few seconds; making each one
        // collect a refusal before retrying is twice the requests and a stall
        // per segment. If the exit has since died, fall through to the direct
        // attempt and the ordinary recovery below.
        if (ProxyRoute.isKnownGood(host)) {
            proxied(original)?.let { return it }
            ProxyRoute.noteFailed(host)
        }

        val resp: Response = try {
            chain.proceed(original)
        } catch (io: IOException) {
            StreamHealth.recordTimeout(host, io.message ?: "IO error")
            // Timeout / connection-refused / socket reset: try backup once.
            return failoverOrRethrow(chain, original, io) ?: throw io
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        if (resp.code in BLOCKED) {
            StreamHealth.recordBlocked(host)
            resp.close()
            return tryBackup(chain, original)
                ?: tryRelay(chain, original)
                ?: tryProxy(original)
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
        val alt = tryBackup(chain, original)
            ?: tryRelay(chain, original)
            ?: tryProxy(original)
            ?: return null
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
        val candidates = RelayUrls.fallbackChain(original.url.toString(), base)
        if (candidates.isEmpty()) return null

        for (relayed in candidates) {
            val rebuilt = original.newBuilder().url(relayed).build()
            val r = try { chain.proceed(rebuilt) } catch (_: IOException) { continue }
            if (r.isSuccessful) {
                val where = RelayUrls.regionOf(relayed)
                notify("Playing via $where — ${original.url.host} refused this location")
                relayed.toHttpUrlOrNull()?.host?.let { StreamHealth.setActiveGateway(it) }
                return r
            }
            // A 403 from the relay is the upstream refusing that country too,
            // which is the whole reason there is more than one to try.
            r.close()
        }
        return null
    }

    /**
     * Go out through the viewer's own exit.
     *
     * Last, deliberately. The relay is ours, free and quick, and it answers the
     * overwhelming majority of blocks — 83.7% of the lineup is American and the
     * US endpoint settles those. This is for what it cannot reach: a channel
     * published only inside a country nobody rents serverless capacity in.
     * Croatia is the case that forced it — HRT exists on two hosts, both
     * regional, with no third source anywhere in iptv-org.
     *
     * Nothing about the request changes; it leaves from somewhere else. See
     * [proxied] for why that cannot be done by re-proceeding on this chain.
     */
    private fun tryProxy(original: Request): Response? {
        val host = original.url.host
        if (!ProxyRoute.shouldTry(host)) return null

        val r = proxied(original)
        if (r != null) {
            ProxyRoute.noteWorked(host)
            notify("Playing via your proxy — $host refused this location")
            StreamHealth.setActiveGateway(ProxyRoute.current().host)
            return r
        }
        // It did not help, so stop paying for the extra hop on this host.
        ProxyRoute.noteFailed(host)
        return null
    }

    /**
     * Issue a request through the viewer's exit.
     *
     * Deliberately **not** `chain.proceed`. Proceeding re-enters the same
     * client, and a 403 leaves a healthy connection in its pool that the retry
     * then reuses — so the request goes out direct however the proxy is
     * configured. [ProxyRoute] hands back a client whose proxy is pinned and
     * whose connection pool is its own, which is the only way the detour is
     * actually taken.
     */
    private fun proxied(request: Request): Response? {
        val client = ProxyRoute.clientOrNull() ?: return null
        return try {
            val r = client.newCall(request.newBuilder().build()).execute()
            if (r.isSuccessful) r else { r.close(); null }
        } catch (_: IOException) {
            null
        }
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
        "$host refused this location, and every relay country too. This " +
            "channel is served only inside one country — Settings → Network " +
            "takes a proxy or a relay endpoint there."

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

    private companion object {
        /**
         * Refusals aimed at where the request came from.
         *
         * 403 is the usual IPTV signal. 451 says the same thing explicitly —
         * some CDNs answer a licence border with it rather than a bare
         * forbidden — and it is worth the same recovery. Nothing else is
         * included: 401 is about credentials and 404 is usually a stream that
         * has genuinely moved, and treating either as a block would send every
         * ordinary mistake through the relay and the viewer's proxy.
         */
        private val BLOCKED = setOf(403, 451)
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
