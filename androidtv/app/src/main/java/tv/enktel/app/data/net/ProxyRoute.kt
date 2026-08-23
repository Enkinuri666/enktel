package tv.enktel.app.data.net

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * An exit in a country this project cannot deploy into.
 *
 * Some channels are served only to one country and published nowhere else.
 * HRT 1, 2 and 3 are the case that forced this: across the whole of iptv-org
 * they exist on exactly two hosts, a Bosnian carrier and Hrvatski Telekom's
 * CDN, and both serve Croatian subscribers only. No alternate source exists to
 * fail over to, and no serverless region exists in Croatia to relay through.
 * The only thing left is to make the request *from* there.
 *
 * The app could not, because the client pinned `Proxy.NO_PROXY`. That was
 * deliberate and still is — an OS- or JVM-level proxy applied to every call
 * without credentials produces a 407 loop, and inheriting one silently is
 * worse than refusing all of them. This keeps that default and adds one
 * exception: a proxy the viewer configured on purpose.
 *
 * ### Only where it is needed
 *
 * A proxy that carried everything would send 2,446 American channels through
 * Croatia to no benefit, and slow all of them down. So the selector routes a
 * host through the proxy only after that host has refused this device — the
 * interceptor marks it, and the retry goes out through the exit. Everything
 * else stays direct, which is what direct is for.
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

    /** Hosts that refused this device, and are therefore worth the detour. */
    private val viaProxy = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    fun configure(c: Config) {
        config = c
        // A changed exit invalidates what was learned through the old one: a
        // host that only failed because the previous proxy was refused should
        // get another direct attempt rather than inherit the verdict.
        viaProxy.clear()
    }

    fun current(): Config = config

    /** Is there an exit configured at all? */
    fun available(): Boolean = config.usable

    /**
     * Send this host through the proxy from now on.
     *
     * Returns false when there is nothing to route through, or when the host
     * is already being routed — which is how the caller knows a retry would
     * repeat itself rather than try something new.
     */
    fun routeThroughProxy(host: String): Boolean {
        if (!available()) return false
        val key = host.lowercase()
        return viaProxy.add(key)
    }

    fun isRouted(host: String): Boolean = viaProxy.contains(host.lowercase())

    /**
     * Send this host direct again.
     *
     * Called when the detour did not help. Without it a host that the proxy
     * cannot reach either would keep paying for the extra hop on every request,
     * forever, to arrive at the same refusal more slowly.
     */
    fun stopRouting(host: String) {
        viaProxy.remove(host.lowercase())
    }

    /** Forget everything, for a profile switch or a settings change. */
    fun reset() = viaProxy.clear()

    private fun proxyFor(host: String?): Proxy {
        val c = config
        if (host == null || !c.usable || !viaProxy.contains(host.lowercase())) return Proxy.NO_PROXY
        val type = if (c.socks) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress.createUnresolved(c.host, c.port))
    }

    /**
     * The selector the client installs in place of a fixed `Proxy.NO_PROXY`.
     *
     * Consulted per request rather than per client, which is the whole reason
     * this can be a runtime setting at all: the OkHttp client is built once, at
     * startup, and a `.proxy()` on the builder could never change afterwards.
     */
    fun selector(): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(proxyFor(uri?.host))

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {
            // A proxy that cannot be reached is worse than none: every request
            // to that host would keep failing at the same hop. Drop back to
            // direct and let the ordinary failover paths have their turn.
            uri?.host?.let { viaProxy.remove(it.lowercase()) }
        }
    }

    /** Answers a 407 from the configured proxy, when it wants credentials. */
    fun authenticator(): Authenticator = Authenticator { _: Route?, response: Response ->
        val c = config
        if (c.username.isBlank() && c.password.isBlank()) return@Authenticator null
        // Already tried and rejected: answering again produces a loop, which is
        // the exact failure the pinned NO_PROXY was there to prevent.
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
