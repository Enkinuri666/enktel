package tv.enktel.app.data.net

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * An exit in a country this project cannot deploy into.
 *
 * Some channels are served only to one country and published nowhere else, and
 * no serverless region exists there to relay through. The only thing left is to
 * make the request *from* there, through an exit the viewer supplies.
 *
 * ### Why this is a separate client and not a ProxySelector
 *
 * It was a `ProxySelector` on the shared client, and that silently did nothing.
 *
 * A geo-block arrives as a 403, which is a perfectly healthy HTTP exchange —
 * the connection completes and goes back into OkHttp's pool. Marking the host
 * and re-issuing the request does not change the request's [okhttp3.Address]:
 * the selector instance, the (absent) pinned proxy and everything else compare
 * equal, so the pooled **direct** connection is still eligible and gets reused.
 * A `ProxySelector` is consulted only when a new connection has to be opened,
 * which on this path never happens. The retry went out direct, met the same
 * 403, and the proxy looked inert no matter what the viewer configured.
 *
 * A client of its own cannot have that problem. Its proxy is pinned rather than
 * selected, and its connection pool is its own, so a request issued through it
 * is on a connection that goes through the exit by construction.
 *
 * ### Only where it is needed
 *
 * Carrying everything through one viewer's proxy would send thousands of
 * American channels through Croatia for no benefit and slow all of them down.
 * So the exit is used only for hosts that have already refused this device, and
 * a host that the exit does not help is remembered so it stops paying for the
 * extra hop.
 */
object ProxyRoute {

    /** `host:port`, optionally with credentials. Blank means no proxy at all. */
    data class Config(
        val host: String = "",
        val port: Int = 0,
        /** SOCKS is what most personal endpoints and ssh -D speak. */
        val socks: Boolean = false,
        val username: String = "",
        val password: String = "",
    ) {
        val usable: Boolean get() = host.isNotBlank() && port in 1..65535
    }

    @Volatile
    private var config: Config = Config()

    /** The client every proxied request is issued on, rebuilt when config does. */
    @Volatile
    private var client: OkHttpClient? = null

    /** What to derive the proxied client from. See [attach]. */
    @Volatile
    private var base: OkHttpClient? = null

    /** Hosts the exit has actually served. */
    private val worked = concurrentSet()

    /** Hosts the exit was tried on and did not help. */
    private val failed = concurrentSet()

    private fun concurrentSet() = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /**
     * The client to model the proxied one on — same timeouts, TLS fallbacks and
     * agent, so a proxied request differs from a direct one in nothing but its
     * route.
     *
     * Must be a client **without** [StreamHealthInterceptor]: the interceptor
     * is what issues proxied requests, and giving the proxied client one too
     * would have every failure recover into itself.
     */
    fun attach(base: OkHttpClient) {
        this.base = base
        client = null
    }

    fun configure(c: Config) {
        config = c
        client = null
        // A changed exit invalidates what was learned through the old one: a
        // host that only failed because the previous proxy was refused deserves
        // another attempt rather than inheriting the verdict.
        worked.clear()
        failed.clear()
    }

    fun current(): Config = config

    /** Is there an exit configured at all? */
    fun available(): Boolean = config.usable

    /**
     * The client that goes out through the exit, or null when there is none.
     *
     * Built once per configuration. The connection pool is explicitly its own —
     * `newBuilder` would otherwise share the base client's, which is exactly
     * the sharing that made the selector approach fail.
     */
    fun clientOrNull(): OkHttpClient? {
        val c = config
        if (!c.usable) return null
        client?.let { return it }
        val from = base ?: return null

        val type = if (c.socks) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val built = from.newBuilder()
            .proxy(Proxy(type, InetSocketAddress.createUnresolved(c.host, c.port)))
            .proxyAuthenticator(authenticator())
            .connectionPool(ConnectionPool())
            .apply { interceptors().removeAll { it is StreamHealthInterceptor } }
            .build()
        client = built
        return built
    }

    /**
     * Is this host worth sending through the exit?
     *
     * False when nothing is configured, and when the exit has already been
     * tried on this host without helping — a stream fetches a segment every few
     * seconds, and repeating a detour that does not work doubles the requests
     * for every one of them.
     */
    fun shouldTry(host: String): Boolean =
        available() && !failed.contains(host.lowercase())

    /**
     * Has the exit already served this host?
     *
     * The caller uses this to go out through the proxy from the start. Without
     * it every segment of a working proxied stream pays a refusal first, which
     * is both slower and twice the requests.
     */
    fun isKnownGood(host: String): Boolean =
        available() && worked.contains(host.lowercase())

    fun noteWorked(host: String) {
        val key = host.lowercase()
        failed.remove(key)
        worked.add(key)
    }

    fun noteFailed(host: String) {
        val key = host.lowercase()
        worked.remove(key)
        failed.add(key)
    }

    /** Forget what was learned, for a profile switch or a settings change. */
    fun reset() {
        worked.clear()
        failed.clear()
    }

    /** Answers a 407 from the configured proxy, when it wants credentials. */
    fun authenticator(): Authenticator = Authenticator { _: Route?, response: Response ->
        val c = config
        if (c.username.isBlank() && c.password.isBlank()) return@Authenticator null
        // Already tried and rejected: answering again produces a loop, which is
        // the exact failure a blanket inherited proxy would cause.
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        buildRequest(response.request, Credentials.basic(c.username, c.password))
    }

    private fun buildRequest(request: Request, credential: String): Request =
        request.newBuilder().header("Proxy-Authorization", credential).build()

    /**
     * Parse `host:port`, `http://host:port` or `socks5://host:port`.
     *
     * Typed by a person into a TV remote's on-screen keyboard, so it accepts
     * the shapes someone would actually enter rather than one canonical form.
     */
    fun parse(raw: String, username: String = "", password: String = ""): Config {
        var s = raw.trim()
        if (s.isEmpty()) return Config()
        var socks = false
        for (prefix in listOf("socks5://", "socks4://", "socks://")) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                socks = true
                s = s.substring(prefix.length)
            }
        }
        for (prefix in listOf("http://", "https://")) {
            if (s.startsWith(prefix, ignoreCase = true)) s = s.substring(prefix.length)
        }
        s = s.substringBefore('/').trim()
        val host = s.substringBeforeLast(':', s).trim()
        val port = s.substringAfterLast(':', "").trim().toIntOrNull() ?: 0
        if (host.isBlank() || port !in 1..65535) return Config()
        return Config(host = host, port = port, socks = socks, username = username, password = password)
    }
}
