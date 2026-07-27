package tv.enktel.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import kotlin.math.sqrt
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.data.str

/**
 * Custom network diagnostic engine — tests connectivity directly against
 * the user's own IPTV server rather than a generic third-party speed-test
 * service, since that's the connection that actually matters for
 * buffering complaints.
 *
 * Android apps can't send raw ICMP echo requests without root, so "ping"
 * here is measured as raw TCP connect time to the server's host:port —
 * the same latency component that actually gates every HTTP request the
 * player makes, and a closer proxy for "will this stream start fast" than
 * ICMP would be anyway (ICMP is frequently deprioritised or blocked by
 * routers and CDNs, producing misleadingly bad numbers).
 */
object SpeedTestEngine {

    /** Result of a HEAD/GET probe against a single stream URL — used to
     *  identify what the panel actually serves for a live channel or a VOD
     *  asset (HLS playlist vs. raw MPEG-TS vs. progressive MP4, etc.). */
    data class StreamProbe(
        val url: String,
        val ok: Boolean,
        val httpCode: Int,
        val contentType: String,
        /** "HLS" | "MPEG-TS" | "MP4" | "MKV" | "DASH" | "UNKNOWN" */
        val container: String,
        /** Best-effort codec label from the container's response headers or
         *  a short byte-sniff of the body. Empty when we can't tell without
         *  actually decoding. */
        val codecHint: String,
        val serverHeader: String,
        val transcoderHint: String,
        val error: String? = null,
    )

    /** Xtream `get_server_info` + `user_info` snapshot; blank fields on
     *  M3U profiles or when the panel refuses the request. */
    data class ServerInfo(
        val url: String = "",
        val protocol: String = "",
        val port: String = "",
        val httpsPort: String = "",
        val serverSoftware: String = "",
        val timezone: String = "",
        val timeNow: String = "",
        val activeConnections: Int = 0,
        val maxConnections: Int = 0,
        val trial: Boolean = false,
        val expiresAt: Long = 0,
        val transcoderProcess: String = "",
    ) {
        fun isEmpty(): Boolean = url.isBlank() && serverSoftware.isBlank() && timezone.isBlank()
    }

    /** Single URL shape → HTTP status. Reported for each of the 6 candidate
     *  shapes StreamUrlResolver would try in order for a live channel. */
    data class ShapeResult(val url: String, val code: Int, val ms: Long, val error: String? = null) {
        val ok: Boolean get() = code in 200..299
    }

    data class UrlShapeSimulation(
        val liveShapes: List<ShapeResult> = emptyList(),
        val bestLive: ShapeResult? = null,
    )

    /** Sockets held open in parallel probe. Reports the panel's per-IP cap. */
    data class ConnectionCap(
        val attempted: Int,
        val succeeded: Int,
        val rejectedAt: Int, // 0 = didn't hit a cap
        val error: String? = null,
    )

    /** Redirect follow-up chain, TLS handshake + HTTP version detected on
     *  the actual panel host — useful for CDN-fronted / Cloudflare setups. */
    data class ProtocolInfo(
        val httpVersion: String = "",         // "http/1.1" | "h2" | ""
        val tlsVersion: String = "",           // "TLSv1.3" | "TLSv1.2" | ""
        val handshakeMs: Long = 0,
        val redirectChain: List<String> = emptyList(),
    )

    data class Result(
        val host: String,
        val resolvedIp: String?,
        val dnsLookupMs: Long,
        val pingMs: Int,
        val jitterMs: Int,
        val packetLossPct: Int,
        val downloadMbps: Double,
        val connectionType: NetworkClass.Kind,
        val recommendation: String,
        val bufferProjection: String,
        val urlShapes: UrlShapeSimulation = UrlShapeSimulation(),
        val connectionCap: ConnectionCap = ConnectionCap(0, 0, 0),
        val protocol: ProtocolInfo = ProtocolInfo(),
        val server: ServerInfo = ServerInfo(),
        val liveProbe: StreamProbe? = null,
        val vodProbe: StreamProbe? = null,
        val suggestions: List<String> = emptyList(),
        val error: String? = null,
    ) {
        /** Plain-text report suitable for "copy to clipboard → paste to support". */
        fun toReport(): String = buildString {
            appendLine("EnkTel IPTV — Network Diagnostic Report")
            appendLine("Server: $host")
            appendLine("Resolved IP: ${resolvedIp ?: "unresolved"}")
            appendLine("DNS lookup: ${dnsLookupMs} ms")
            appendLine("Ping (TCP connect): ${pingMs} ms")
            appendLine("Jitter: ${jitterMs} ms")
            appendLine("Packet loss (probe failures): $packetLossPct%")
            appendLine("Download throughput: %.2f Mbps".format(downloadMbps))
            appendLine("Connection type: $connectionType")
            if (!server.isEmpty()) {
                appendLine("---")
                appendLine("Panel URL: ${server.url}")
                appendLine("Protocol: ${server.protocol}  Port: ${server.port} (https ${server.httpsPort})")
                appendLine("Server software: ${server.serverSoftware}")
                appendLine("Transcoder process: ${server.transcoderProcess.ifBlank { "unknown" }}")
                appendLine("Timezone: ${server.timezone}   Panel time: ${server.timeNow}")
                appendLine("Connections: ${server.activeConnections} / ${server.maxConnections}${if (server.trial) " (trial)" else ""}")
            }
            liveProbe?.let {
                appendLine("---")
                appendLine("Live probe: ${it.url}")
                appendLine("  HTTP ${it.httpCode}   Content-Type: ${it.contentType}")
                appendLine("  Container: ${it.container}   Codec: ${it.codecHint.ifBlank { "n/a" }}")
                appendLine("  Server header: ${it.serverHeader.ifBlank { "n/a" }}")
                appendLine("  Transcoder: ${it.transcoderHint.ifBlank { "direct/unknown" }}")
                it.error?.let { e -> appendLine("  Error: $e") }
            }
            vodProbe?.let {
                appendLine("---")
                appendLine("VOD probe: ${it.url}")
                appendLine("  HTTP ${it.httpCode}   Content-Type: ${it.contentType}")
                appendLine("  Container: ${it.container}   Codec: ${it.codecHint.ifBlank { "n/a" }}")
                it.error?.let { e -> appendLine("  Error: $e") }
            }
            if (protocol.httpVersion.isNotBlank() || protocol.tlsVersion.isNotBlank()) {
                appendLine("---")
                appendLine("HTTP version: ${protocol.httpVersion.ifBlank { "unknown" }}")
                appendLine("TLS version: ${protocol.tlsVersion.ifBlank { "unknown" }}")
                if (protocol.handshakeMs > 0) appendLine("Handshake: ${protocol.handshakeMs} ms")
                if (protocol.redirectChain.isNotEmpty()) {
                    appendLine("Redirect chain:")
                    protocol.redirectChain.forEach { appendLine("  $it") }
                }
            }
            if (urlShapes.liveShapes.isNotEmpty()) {
                appendLine("---")
                appendLine("URL shape simulation:")
                urlShapes.liveShapes.forEach { s ->
                    val short = s.url.substringAfterLast('/')
                    val err = s.error?.let { " (${it.take(60)})" }.orEmpty()
                    appendLine("  ${if (s.code == 0) "err" else s.code}  ${s.ms}ms  $short$err")
                }
                urlShapes.bestLive?.let { appendLine("Best live shape: ${it.url}") }
            }
            if (connectionCap.attempted > 0) {
                appendLine("---")
                appendLine("Connection cap: ${connectionCap.succeeded}/${connectionCap.attempted} succeeded" +
                    if (connectionCap.rejectedAt > 0) " · panel rejected at slot ${connectionCap.rejectedAt}" else "")
            }
            if (suggestions.isNotEmpty()) {
                appendLine("---")
                appendLine("Suggestions:")
                suggestions.forEach { appendLine(" • $it") }
            }
            appendLine("---")
            appendLine("Recommended format: $recommendation")
            appendLine("Buffer health: $bufferProjection")
            error?.let { appendLine("Note: $it") }
        }
    }

    /**
     * Full connection diagnostic against a profile's server.
     *   • DNS + TCP-connect latency, jitter, loss on the panel host.
     *   • Real download throughput measured against a real stream URL when
     *     the caller supplies one (falls back to the panel root).
     *   • Xtream `get_server_info` + `user_info` for panel process, timezone,
     *     active/max connections, and the trial flag.
     *   • HEAD/short-GET probe on a live URL and a VOD URL to detect what
     *     the panel actually serves (HLS playlist / MPEG-TS / MP4 / DASH),
     *     the Server header, and whether a transcoder is in the path.
     *   • Suggestion list built from the observed data — surfaced next to
     *     the metrics so the user knows what to try.
     */
    suspend fun run(
        http: OkHttpClient,
        serverUrl: String,
        streamSampleUrl: String? = null,
        vodSampleUrl: String? = null,
        profile: Profile? = null,
        xtream: tv.enktel.app.data.xtream.XtreamClient? = null,
        onProgress: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val url = try { URL(serverUrl) } catch (e: Exception) {
            return@withContext Result(
                host = serverUrl, resolvedIp = null, dnsLookupMs = 0, pingMs = 0, jitterMs = 0,
                packetLossPct = 100, downloadMbps = 0.0, connectionType = NetworkClass.kind.value,
                recommendation = "Unable to test — invalid server URL", bufferProjection = "unknown",
                suggestions = listOf("Fix the server URL in Settings → Profiles."),
                error = e.message,
            )
        }
        val host = url.host
        val port = if (url.port > 0) url.port else if (url.protocol == "https") 443 else 80

        onProgress("Resolving DNS…")
        var resolvedIp: String? = null
        val dnsMs = measureMs {
            resolvedIp = try { InetAddress.getByName(host).hostAddress } catch (_: Exception) { null }
        }

        onProgress("Measuring latency…")
        val samples = mutableListOf<Long>()
        var failures = 0
        repeat(6) {
            val ms = try {
                measureMs {
                    Socket().use { s -> s.connect(java.net.InetSocketAddress(host, port), 4_000) }
                }
            } catch (_: Exception) { failures++; null }
            if (ms != null) samples += ms
        }
        val pingMs = if (samples.isNotEmpty()) samples.average().toInt() else -1
        val jitterMs = if (samples.size >= 2) stddev(samples).toInt() else 0
        val packetLossPct = ((failures.toDouble() / 6) * 100).toInt()

        // Xtream server-info: transcoder process, timezone, connection cap.
        // Only meaningful for Xtream profiles; m3u profiles skip it cleanly.
        val server: ServerInfo = if (profile != null && profile.kind == "xtream" && xtream != null) {
            onProgress("Reading Xtream server info…")
            fetchServerInfo(xtream, profile)
        } else ServerInfo()

        // Codec / container probes — the whole point of the "detected Xtream
        // API codec / m3u codec / transcoder" section.
        val liveProbe: StreamProbe? = streamSampleUrl?.let {
            onProgress("Probing live stream…")
            probeStream(http, it)
        }
        val vodProbe: StreamProbe? = vodSampleUrl?.let {
            onProgress("Probing VOD stream…")
            probeStream(http, it)
        }

        // Detect HTTP/TLS version + handshake time + redirect chain against
        // the panel host — surfaces CDN fronting (Cloudflare), HTTP/2 support,
        // and TLS-1.3 negotiation without needing an OS-level tool.
        onProgress("Probing HTTP + TLS…")
        val protocolInfo = probeProtocol(http, serverUrl)

        // URL-shape playback simulator — for Xtream profiles we try each of
        // the six candidate URL shapes StreamUrlResolver would walk and
        // report which one the panel actually answers. Removes the guesswork
        // when a channel won't play but the server ping is fine.
        onProgress("Simulating URL shapes…")
        val shapes = if (profile != null && profile.kind == "xtream")
            simulateUrlShapes(http, profile)
        else UrlShapeSimulation()

        // Concurrent-connection cap detector — opens N parallel range GETs
        // against a stream URL and reports how many the panel accepts before
        // rejecting. Tells the user their real per-IP connection allotment.
        onProgress("Detecting connection cap…")
        val cap = if (streamSampleUrl != null)
            detectConnectionCap(http, streamSampleUrl)
        else ConnectionCap(0, 0, 0)

        onProgress("Measuring download throughput…")
        val throughput = try {
            measureThroughputMbps(http, streamSampleUrl ?: serverUrl)
        } catch (_: Exception) { 0.0 }

        val kind = NetworkClass.kind.value
        val recommendation = recommend(throughput, pingMs, packetLossPct)
        val bufferProjection = projectBufferHealth(throughput, jitterMs, packetLossPct)
        val suggestions = buildSuggestions(
            pingMs = pingMs,
            jitterMs = jitterMs,
            lossPct = packetLossPct,
            mbps = throughput,
            server = server,
            live = liveProbe,
            vod = vodProbe,
        )

        Result(
            host = host,
            resolvedIp = resolvedIp,
            dnsLookupMs = dnsMs,
            pingMs = pingMs,
            jitterMs = jitterMs,
            packetLossPct = packetLossPct,
            downloadMbps = throughput,
            connectionType = kind,
            recommendation = recommendation,
            bufferProjection = bufferProjection,
            server = server,
            liveProbe = liveProbe,
            vodProbe = vodProbe,
            urlShapes = shapes,
            connectionCap = cap,
            protocol = protocolInfo,
            suggestions = suggestions,
            error = if (pingMs < 0) "Could not establish a TCP connection to $host:$port" else null,
        )
    }

    private fun probeProtocol(http: OkHttpClient, serverUrl: String): ProtocolInfo {
        val req = Request.Builder().url(serverUrl).get().build()
        val redirects = mutableListOf<String>()
        return try {
            val t0 = System.nanoTime()
            http.newCall(req).execute().use { r ->
                val ms = (System.nanoTime() - t0) / 1_000_000
                // Walk the priorResponse chain — that's OkHttp's linked list
                // of intermediate responses before the final landing.
                var p: okhttp3.Response? = r.priorResponse
                while (p != null) {
                    redirects += "${p.code} → ${p.header("Location") ?: p.request.url}"
                    p = p.priorResponse
                }
                ProtocolInfo(
                    httpVersion = r.protocol.toString(),
                    tlsVersion = r.handshake?.tlsVersion?.javaName.orEmpty(),
                    handshakeMs = ms,
                    redirectChain = redirects.reversed(),
                )
            }
        } catch (_: Throwable) {
            ProtocolInfo()
        }
    }

    private fun simulateUrlShapes(http: OkHttpClient, p: Profile): UrlShapeSimulation {
        // Build the same 6 shapes StreamUrlResolver would try for a live
        // channel, without needing a real Channel — the shapes only depend
        // on profile server/username/password + a placeholder stream id (1).
        val base = p.server.trimEnd('/')
        val u = p.username
        val pw = p.password
        val id = 1L
        val shapes = listOf(
            "$base/live/$u/$pw/$id.m3u8",
            "$base/live/$u/$pw/$id.ts",
            "$base/live/$u/$pw/$id",
            "$base/$u/$pw/$id.m3u8",
            "$base/$u/$pw/$id.ts",
            "$base/$u/$pw/$id",
        ).distinct()
        val results = shapes.map { url ->
            val t0 = System.nanoTime()
            try {
                val req = Request.Builder().url(url).header("Range", "bytes=0-1").get().build()
                http.newCall(req).execute().use { r ->
                    ShapeResult(url = url, code = r.code, ms = (System.nanoTime() - t0) / 1_000_000)
                }
            } catch (e: Throwable) {
                ShapeResult(url = url, code = 0, ms = (System.nanoTime() - t0) / 1_000_000, error = e.message)
            }
        }
        val best = results.firstOrNull { it.ok }
        return UrlShapeSimulation(liveShapes = results, bestLive = best)
    }

    private suspend fun detectConnectionCap(http: OkHttpClient, sampleUrl: String): ConnectionCap =
        kotlinx.coroutines.coroutineScope {
            val target = 8
            // kotlinx.coroutines.async replaces CompletableFuture.supplyAsync
            // here — supplyAsync requires API 24 but the app supports 21.
            // Dispatchers.IO gives the same "run these concurrently on
            // background threads" behaviour without the minSdk bump.
            val jobs = (1..target).map {
                async(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val req = Request.Builder().url(sampleUrl)
                            .header("Range", "bytes=0-65535")
                            .get()
                            .build()
                        http.newCall(req).execute().use { it.code }
                    } catch (_: Throwable) { -1 }
                }
            }
            val outcomes = try {
                kotlinx.coroutines.withTimeoutOrNull(20_000) { jobs.awaitAll() }
                    ?: emptyList()
            } catch (_: Throwable) { emptyList() }
            val succeeded = outcomes.count { it == 200 || it == 206 }
            // Panels commonly answer 403 / 429 / 503 once the per-IP cap is hit.
            val rejectedAt = outcomes.indexOfFirst { it == 403 || it == 429 || it == 503 }
                .let { if (it < 0) 0 else it + 1 }
            ConnectionCap(attempted = target, succeeded = succeeded, rejectedAt = rejectedAt)
        }

    private suspend fun fetchServerInfo(
        xtream: tv.enktel.app.data.xtream.XtreamClient,
        p: Profile,
    ): ServerInfo = try {
        val json = xtream.login(p)
        val si = json.get("server_info")
        val ui = json.get("user_info")
        ServerInfo(
            url = si.str("url").orEmpty(),
            protocol = si.str("server_protocol").orEmpty(),
            port = si.str("port").orEmpty(),
            httpsPort = si.str("https_port").orEmpty(),
            serverSoftware = si.str("server_name").orEmpty().ifBlank {
                si.str("time_zone")?.let { "Xtream Codes / XUI" } ?: ""
            },
            timezone = si.str("timezone").orEmpty().ifBlank { si.str("time_zone").orEmpty() },
            timeNow = si.str("time_now").orEmpty(),
            transcoderProcess = si.str("process").orEmpty().ifBlank { detectTranscoderFromServerName(si.str("server_name")) },
            activeConnections = ui.int("active_cons") ?: 0,
            maxConnections = ui.int("max_connections") ?: 0,
            trial = (ui.int("is_trial") ?: 0) == 1,
            expiresAt = (ui.long("exp_date") ?: 0L) * 1000L,
        )
    } catch (_: Throwable) {
        ServerInfo()
    }

    private fun detectTranscoderFromServerName(name: String?): String {
        val n = (name ?: "").lowercase()
        return when {
            "xui" in n -> "xui.one panel (direct MPEG-TS / HLS proxy)"
            "xtream" in n -> "Xtream Codes panel (direct MPEG-TS / HLS proxy)"
            "nginx" in n && "rtmp" in n -> "nginx-rtmp (relayed)"
            "ffmpeg" in n -> "FFmpeg transcoder in path"
            n.isBlank() -> ""
            else -> n
        }
    }

    private fun probeStream(http: OkHttpClient, url: String): StreamProbe {
        // A GET-with-Range=0-1 keeps servers that hate bare HEADs happy and
        // gives us the Content-Type without paying for a full download.
        // Panels reply 206 Partial Content on ranged GETs — that's a healthy
        // response, not a failure. Some flavors of nginx also drop directly
        // to 200 with the whole body; both are fine here.
        val req = Request.Builder().url(url)
            .header("Range", "bytes=0-1")
            .get()
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                val ct = r.header("Content-Type").orEmpty()
                val srv = r.header("Server").orEmpty()
                val xtc = r.header("X-Transcoder").orEmpty()
                val via = r.header("Via").orEmpty()
                // Prefer Content-Type over URL extension when they disagree
                // (a `.mp4` URL that actually serves `video/x-matroska` is
                // common on transcoded panels; the wire container is the
                // one ExoPlayer will see, so it wins).
                val container = containerFromContentType(ct, url)
                val codecHint = codecHintFromContentType(ct)
                val transcoder = when {
                    xtc.isNotBlank() -> xtc
                    via.isNotBlank() -> "Relayed via $via"
                    srv.contains("ffmpeg", true) -> "FFmpeg transcoder in path"
                    srv.contains("nginx-rtmp", true) -> "nginx-rtmp relay"
                    srv.contains("xui", true) || srv.contains("xtream", true) -> "Direct panel (no re-encode)"
                    else -> ""
                }
                // Treat 200 and 206 (Partial Content — the expected response
                // to our Range header) as success. r.isSuccessful already
                // covers 2xx, but be explicit so future readers don't wonder.
                val ok = r.code == 200 || r.code == 206 || r.isSuccessful
                StreamProbe(
                    url = url, ok = ok, httpCode = r.code,
                    contentType = ct, container = container, codecHint = codecHint,
                    serverHeader = srv, transcoderHint = transcoder,
                )
            }
        } catch (e: Exception) {
            StreamProbe(
                url = url, ok = false, httpCode = 0, contentType = "",
                container = "UNKNOWN", codecHint = "",
                serverHeader = "", transcoderHint = "", error = e.message,
            )
        }
    }

    private fun containerFromContentType(ct: String, url: String): String {
        val c = ct.lowercase()
        val pathExt = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when {
            "mpegurl" in c || "m3u" in c || pathExt in setOf("m3u8", "m3u") -> "HLS"
            "mp2t" in c || pathExt == "ts" -> "MPEG-TS"
            "video/mp4" in c || pathExt == "mp4" || pathExt == "m4v" -> "MP4"
            "matroska" in c || "video/x-matroska" in c || pathExt == "mkv" -> "MKV"
            "dash" in c || pathExt == "mpd" -> "DASH"
            "video/webm" in c || pathExt == "webm" -> "WebM"
            "video/x-flv" in c || pathExt == "flv" -> "FLV"
            c.isBlank() -> "UNKNOWN"
            else -> c.substringAfter('/').substringBefore(';').uppercase()
        }
    }

    private fun codecHintFromContentType(ct: String): String {
        // Content-Type sometimes ships a codecs="..." parameter (rare on live
        // TS but common on modern HLS). Surface it verbatim when present.
        val idx = ct.indexOf("codecs=", ignoreCase = true)
        if (idx < 0) return ""
        return ct.substring(idx + "codecs=".length).trim().trim('"', ';', ' ')
    }

    private fun buildSuggestions(
        pingMs: Int,
        jitterMs: Int,
        lossPct: Int,
        mbps: Double,
        server: ServerInfo,
        live: StreamProbe?,
        vod: StreamProbe?,
    ): List<String> {
        val out = mutableListOf<String>()
        if (pingMs < 0) out += "TCP connection failed — check the panel URL / port, and that the profile isn't expired."
        if (lossPct >= 20) out += "Packet loss ≥ 20%: try a wired connection or a different network path (VPN off, or on)."
        if (jitterMs >= 150) out += "High jitter — switch buffer profile to Large under Settings → Playback."
        if (mbps in 0.1..4.9) out += "Bandwidth < 5 Mbps — SD only. Consider a lower-bitrate 480p variant."
        // Per-HTTP-code guidance, applied to both probes. Live and VOD failures
        // often have different root causes (auth vs. stream ID vs. transcoder),
        // so the message calls out which one failed.
        listOfNotNull(live?.let { "Live" to it }, vod?.let { "VOD" to it }).forEach { (label, p) ->
            when (p.httpCode) {
                401 -> out += "$label probe returned 401 Unauthorized — credentials are wrong or the profile has expired. Re-enter the profile in Settings → Profiles."
                403 -> out += "$label probe returned 403 Forbidden — most often a connection cap on the reseller, a geo/IP block, or a Cloudflare bot rule. Try again in a minute, disable/enable VPN, and check the app is sending a media UA (v1.18.3+)."
                404 -> out += "$label probe returned 404 — the stream ID isn't served under this URL shape. The player will walk the fallback chain; if this keeps happening on every channel, resync the profile (Settings → Refresh catalogue)."
                407 -> out += "$label probe returned 407 Proxy Auth Required — a WAF or middlebox is challenging the request. The v1.18.3+ VLC User-Agent should bypass this; if you still see 407, contact the reseller."
                408, 504 -> out += "$label probe returned ${p.httpCode} — the panel/proxy timed out. Try Settings → Player buffer → Max stability, or move closer to your router."
                429 -> out += "$label probe returned 429 — the panel is rate-limiting you. Wait a minute, and avoid rapid channel-zapping."
                in 500..599 -> out += "$label probe returned ${p.httpCode} — panel-side issue; try again shortly or contact the reseller."
                0 -> if (p.error != null) out += "$label probe never got a response — ${p.error}. If this includes 'timeout', widen Player buffer to Max stability."
            }
        }
        if (live?.container == "HLS" && jitterMs >= 100) out += "Live is HLS with high jitter — Balanced buffer profile is a better fit than Low."
        if (live?.container == "MPEG-TS") out += "Live is raw MPEG-TS. If it stutters, try the HLS URL shape (Settings → Stream format → HLS)."
        // Container-mismatch heads-up (an .mp4 URL that actually serves MKV).
        vod?.let { p ->
            val urlExt = p.url.substringBefore('?').substringAfterLast('.', "").lowercase()
            val ctIsMkv = p.contentType.contains("matroska", true) || p.container == "MKV"
            if (urlExt == "mp4" && ctIsMkv) {
                out += "VOD URL is .mp4 but the panel actually serves MKV — v1.18.3+ handles this transparently. If playback still fails, force MP4 fallback via Settings → Stream format."
            }
        }
        if (server.maxConnections > 0 && server.activeConnections >= server.maxConnections) {
            out += "You've hit the panel's ${server.maxConnections}-connection cap. Close other devices or ask for a plan bump."
        }
        if (server.trial) out += "Panel reports this as a TRIAL account — bandwidth may be throttled."
        if (vod?.container == "MP4" && (live?.container == "MPEG-TS")) {
            out += "Panel serves VOD as MP4 (progressive) and live as MPEG-TS — normal for Xtream."
        }
        if (out.isEmpty()) out += "Everything looks healthy. If a specific stream buffers, use ▶ Retry with alternate source in the player."
        return out
    }

    private inline fun measureMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun stddev(samples: List<Long>): Double {
        val mean = samples.average()
        val variance = samples.sumOf { (it - mean) * (it - mean) } / samples.size
        return sqrt(variance)
    }

    /** Streams up to ~5 seconds (or 24 MB, whichever first) and computes Mbps.
     *  Larger window + bigger sample buffer than the old 3 s/8 MB one so
     *  slow-start Cloudflare / IPTV-Editor proxy paths get past the ramp
     *  before we take the measurement, and so 206 Partial Content responses
     *  (the norm for range-fetched panels) count as a healthy read. */
    private fun measureThroughputMbps(http: OkHttpClient, url: String): Double {
        val req = Request.Builder().url(url)
            .header("Range", "bytes=0-25165824") // 24 MB
            .build()
        var bytes = 0L
        val start = System.nanoTime()
        val deadlineNs = start + 5_000_000_000L
        val maxBytes = 24L * 1024 * 1024
        http.newCall(req).execute().use { resp ->
            // 206 (ranged) is the expected response; 200 also fine when the
            // panel ignores our Range. Any other code means the URL isn't a
            // media asset and the throughput number would be junk anyway.
            if (!(resp.code == 200 || resp.code == 206)) return 0.0
            val source = resp.body?.source() ?: return 0.0
            val buf = okio.Buffer()
            while (System.nanoTime() < deadlineNs && bytes < maxBytes) {
                val read = try { source.read(buf, 262_144) } catch (_: Exception) { -1L }
                if (read == -1L) break
                bytes += read
                buf.clear()
            }
        }
        val elapsedS = (System.nanoTime() - start) / 1_000_000_000.0
        if (elapsedS <= 0 || bytes <= 0) return 0.0
        val bits = bytes * 8.0
        return (bits / elapsedS) / 1_000_000.0
    }

    private fun recommend(mbps: Double, pingMs: Int, lossPct: Int): String = when {
        pingMs < 0 || lossPct >= 50 -> "Connection too unstable to recommend a format — check your network or VPN."
        mbps >= 25 -> "Your network comfortably supports 4K HLS / direct MPEG-TS without buffering."
        mbps >= 12 -> "Your network supports 1080p HLS reliably; 4K may occasionally buffer."
        mbps >= 5 -> "Your network supports 720p reliably. Consider Balanced or Low buffer profile for 1080p."
        mbps >= 2 -> "Your network is best suited to SD (480p) streams; higher bitrates will buffer."
        mbps > 0 -> "Very limited bandwidth detected — expect frequent buffering even at low bitrates."
        else -> "Unable to measure throughput — check server address and network connection."
    }

    private fun projectBufferHealth(mbps: Double, jitterMs: Int, lossPct: Int): String = when {
        lossPct >= 20 -> "At risk — packet loss this high will cause stalls regardless of buffer size."
        jitterMs >= 150 -> "Unstable — high jitter means the Large buffer profile is recommended."
        mbps >= 15 && jitterMs < 60 -> "Healthy — Balanced or even Low buffer profile should stay smooth."
        mbps >= 5 -> "Adequate — Balanced buffer profile recommended for headroom."
        else -> "Marginal — Large buffer profile recommended to absorb throughput dips."
    }
}
