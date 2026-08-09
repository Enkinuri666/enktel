package tv.enktel.app.data.net

import androidx.annotation.VisibleForTesting
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
/** "in 27 days" / "expired 3 days ago" / "expires today", from a Unix ms instant. */
internal fun expiryLabel(atMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val days = ((atMs - nowMs) / 86_400_000L).toInt()
    return when {
        atMs <= nowMs -> {
            val ago = ((nowMs - atMs) / 86_400_000L).toInt()
            if (ago <= 0) "expired today" else "expired $ago day${if (ago == 1) "" else "s"} ago"
        }
        days <= 0 -> "expires today"
        days == 1 -> "1 day left"
        else -> "$days days left"
    }
}

object SpeedTestEngine {

    /**
     * Length of the sustained throughput sample, in seconds.
     *
     * Long enough for TCP to leave slow-start on a fast link — five seconds
     * systematically under-reported anything above ~100 Mbps — and short
     * enough that the user is not left staring at a progress bar.
     */
    const val WINDOW_SEC = 17L

    /** A VOD asset — a plain file, sent as fast as the line allows. The only
     *  source that measures the connection rather than the content. */
    const val SOURCE_VOD = "vod"

    /** A live channel — paced at its own bitrate by the broadcaster, so the
     *  result is a floor on the line speed, never a measurement of it. */
    const val SOURCE_LIVE = "live"

    /** The panel API itself. A few kilobytes of JSON: far too little to
     *  measure anything, and reported as such rather than as a number. */
    const val SOURCE_API = "api"

    /** Nothing was measured. */
    const val SOURCE_NONE = "none"

    /** True when [source] cannot support a meaningful throughput claim. */
    fun sourceIsCapped(source: String): Boolean = source == SOURCE_LIVE || source == SOURCE_API

    /** How a given source should be described to a human, in one clause. */
    fun sourceLabel(source: String): String = when (source) {
        SOURCE_VOD -> "a VOD file (measures the connection)"
        SOURCE_LIVE -> "a live channel (paced at the channel's own bitrate)"
        SOURCE_API -> "the panel API (too small to measure)"
        else -> "nothing"
    }

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

    /**
     * What the app has actually downloaded from the panel and cached locally.
     *
     * "There's nothing in the guide", "Catch-Up is empty" and "half my
     * channels are missing" are all reported as playback faults and none of
     * them are — they are sync results. Counting what is in the database turns
     * each into a number the user and the reseller can both look at.
     */
    data class Catalogue(
        val channels: Int = 0,
        val movies: Int = 0,
        val series: Int = 0,
        val catchupChannels: Int = 0,
        val catchupDays: Int = 0,
        val epgProgrammes: Int = 0,
        /** Distinct channels the loaded guide covers. */
        val epgChannels: Int = 0,
        /** Furthest programme end time in the guide, Unix ms. */
        val epgHorizonMs: Long = 0,
        /** When the profile last completed a catalogue sync, Unix ms. */
        val lastSyncMs: Long = 0,
    ) {
        fun isEmpty(): Boolean = channels == 0 && movies == 0 && series == 0 && epgProgrammes == 0

        /** Guide depth in whole days ahead of now; 0 when the guide is stale or absent. */
        val epgDaysAhead: Int
            get() = if (epgHorizonMs <= 0) 0
            else ((epgHorizonMs - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)

        /** Share of channels the guide covers, 0–100. */
        val epgCoveragePct: Int
            get() = if (channels <= 0) 0 else (epgChannels * 100 / channels).coerceIn(0, 100)
    }

    data class Result(
        val host: String,
        val resolvedIp: String?,
        val dnsLookupMs: Long,
        val pingMs: Int,
        val jitterMs: Int,
        val packetLossPct: Int,
        val downloadMbps: Double,
        /**
         * What the throughput figure was actually measured against.
         *
         * A speed test is only a speed test when the far end sends as fast as
         * the line allows. A VOD file does; a live channel does not — it is
         * paced at its own bitrate, so measuring one reports the channel and
         * calls it the connection. The engine prefers VOD and falls back, and
         * that fallback used to be silent: a 6 Mbps channel on a 200 Mbps line
         * produced "6 Mbps", which reads as a broadband fault rather than as a
         * measurement of the wrong thing.
         *
         * One of [SOURCE_VOD], [SOURCE_LIVE], [SOURCE_API], [SOURCE_NONE].
         */
        val speedSource: String = SpeedTestEngine.SOURCE_NONE,
        val connectionType: NetworkClass.Kind,
        val recommendation: String,
        val bufferProjection: String,
        val urlShapes: UrlShapeSimulation = UrlShapeSimulation(),
        val connectionCap: ConnectionCap = ConnectionCap(0, 0, 0),
        val protocol: ProtocolInfo = ProtocolInfo(),
        val server: ServerInfo = ServerInfo(),
        val liveProbe: StreamProbe? = null,
        val vodProbe: StreamProbe? = null,
        /** What this box can decode and display. See [DeviceProbe]. */
        val device: DeviceProbe.Info = DeviceProbe.Info(),
        /** What the app has cached from the panel. See [Catalogue]. */
        val catalogue: Catalogue = Catalogue(),
        val suggestions: List<String> = emptyList(),
        val error: String? = null,
        /**
         * When this run finished, Unix ms.
         *
         * Without it a result is undated, and a screen showing readings from
         * twenty minutes ago looks exactly like one showing readings from
         * twenty seconds ago — which is how stale numbers get pasted into a
         * bug report as though they described the problem.
         */
        val measuredAtMs: Long = System.currentTimeMillis(),
    ) {
        /** Plain-text report suitable for "copy to clipboard → paste to support". */
        fun toReport(): String = buildString {
            appendLine("EnkTel IPTV — Network Diagnostic Report")
            appendLine("Measured: " + tv.enktel.app.data.TimeFormat.format("yyyy-MM-dd HH:mm:ss", measuredAtMs))
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("App: ${tv.enktel.app.BuildConfig.VERSION_NAME} (${tv.enktel.app.BuildConfig.FLAVOR})")
            appendLine("Server: $host")
            appendLine("Resolved IP: ${resolvedIp ?: "unresolved"}")
            appendLine("DNS lookup: ${dnsLookupMs} ms")
            appendLine("Ping (TCP connect): ${pingMs} ms")
            appendLine("Jitter: ${jitterMs} ms")
            appendLine("Packet loss (probe failures): $packetLossPct%")
            appendLine("Download throughput: %.2f Mbps".format(downloadMbps))
            appendLine("  measured against: " + SpeedTestEngine.sourceLabel(speedSource))
            appendLine("Connection type: $connectionType")
            if (device.linkDownKbps > 0) {
                appendLine("Link estimate (OS): %.1f Mbps".format(device.linkDownKbps / 1000.0))
            }
            if (!device.isEmpty()) {
                appendLine("---")
                appendLine("Display: ${device.displayLabel}")
                appendLine("HDR: ${device.hdrTypes.joinToString(", ").ifBlank { "SDR only" }}")
                appendLine("Video decoders:")
                listOf("H.264", "HEVC", "AV1", "VP9").forEach { label ->
                    val d = device.decoder(label)
                    appendLine(
                        "  $label: " + when {
                            d == null -> "not supported"
                            else -> (if (d.hardware) "hardware" else "software") +
                                d.resolutionLabel.let { if (it.isBlank()) "" else " up to $it" }
                        },
                    )
                }
                if (device.totalRamMb > 0) {
                    appendLine("Memory: ${device.availRamMb} MB free of ${device.totalRamMb} MB")
                }
                if (device.totalStorageMb > 0) {
                    appendLine("Storage: ${device.freeStorageMb} MB free of ${device.totalStorageMb} MB")
                }
                if (device.abi.isNotBlank()) appendLine("ABI: ${device.abi}")
            }
            if (!catalogue.isEmpty()) {
                appendLine("---")
                appendLine("Catalogue: ${catalogue.channels} channels · ${catalogue.movies} movies · ${catalogue.series} series")
                if (catalogue.lastSyncMs > 0) {
                    appendLine("Last sync: " + tv.enktel.app.data.TimeFormat.format("yyyy-MM-dd HH:mm", catalogue.lastSyncMs))
                }
                appendLine(
                    "Guide: ${catalogue.epgProgrammes} programmes over ${catalogue.epgChannels} channels " +
                        "(${catalogue.epgCoveragePct}% coverage, ${catalogue.epgDaysAhead}d ahead)",
                )
                appendLine(
                    "Catch-Up: ${catalogue.catchupChannels} channels" +
                        if (catalogue.catchupDays > 0) " · up to ${catalogue.catchupDays} days back" else "",
                )
            }
            if (!server.isEmpty()) {
                appendLine("---")
                appendLine("Panel URL: ${server.url}")
                appendLine("Protocol: ${server.protocol}  Port: ${server.port} (https ${server.httpsPort})")
                appendLine("Server software: ${server.serverSoftware}")
                appendLine("Transcoder process: ${server.transcoderProcess.ifBlank { "unknown" }}")
                appendLine("Timezone: ${server.timezone}   Panel time: ${server.timeNow}")
                appendLine("Connections: ${server.activeConnections} / ${server.maxConnections}${if (server.trial) " (trial)" else ""}")
                // The panel has always told us this and the report has never
                // shown it, so an expired line reads as a network fault right
                // up until someone thinks to check the account.
                if (server.expiresAt > 0) {
                    appendLine(
                        "Subscription expires: " +
                            tv.enktel.app.data.TimeFormat.format("yyyy-MM-dd HH:mm", server.expiresAt) +
                            " (${expiryLabel(server.expiresAt)})",
                    )
                }
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
        /** Decoder / display / storage snapshot. The caller takes it because
         *  this engine has no Context and no reason to acquire one. */
        device: DeviceProbe.Info = DeviceProbe.Info(),
        /** Locally cached catalogue counts, read from the database by the caller. */
        catalogue: Catalogue = Catalogue(),
        onProgress: (String) -> Unit = {},
        /** Live readings during the throughput phase, for the on-screen meter. */
        onSpeedSample: (SpeedSample) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val url = try { URL(serverUrl) } catch (e: Exception) {
            return@withContext Result(
                host = serverUrl, resolvedIp = null, dnsLookupMs = 0, pingMs = 0, jitterMs = 0,
                packetLossPct = 100, downloadMbps = 0.0, connectionType = NetworkClass.kind.value,
                recommendation = "Unable to test — invalid server URL", bufferProjection = "unknown",
                device = device, catalogue = catalogue,
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
            // Pass the line's stated cap so the probe cannot exceed it. The
            // server-info call above has already run, so on an Xtream profile
            // this is known by now.
            detectConnectionCap(http, streamSampleUrl, server.maxConnections)
        else ConnectionCap(0, 0, 0)

        // VOD first. A live channel is served at its own bitrate, so measuring
        // against one reports the channel, not the connection. VOD is a plain
        // file the panel sends as fast as the line allows, which is the thing
        // a speed test is actually asking about.
        //
        // The fallback chain stays, because a number with a caveat beats no
        // number — but which link was taken is now carried out with the
        // result. Silently measuring a live channel and printing the answer as
        // "download throughput" is how a healthy 200 Mbps line gets reported
        // as 6 Mbps, and the user goes to argue with their ISP about a figure
        // that was only ever the channel's bitrate.
        val speedUrl = vodSampleUrl ?: streamSampleUrl ?: serverUrl
        val speedSource = when {
            vodSampleUrl != null -> SOURCE_VOD
            streamSampleUrl != null -> SOURCE_LIVE
            else -> SOURCE_API
        }
        onProgress(
            when (speedSource) {
                SOURCE_VOD -> "Measuring download throughput…"
                SOURCE_LIVE -> "Measuring against a live channel (no VOD available)…"
                else -> "No stream to measure against…"
            },
        )
        val throughput = try {
            measureThroughputMbps(http, speedUrl, onSpeedSample)
        } catch (_: Exception) { 0.0 }

        val kind = NetworkClass.kind.value
        val recommendation = recommend(throughput, pingMs, packetLossPct, speedSource)
        val bufferProjection = projectBufferHealth(throughput, jitterMs, packetLossPct)
        val suggestions = buildSuggestions(
            pingMs = pingMs,
            jitterMs = jitterMs,
            lossPct = packetLossPct,
            mbps = throughput,
            speedSource = speedSource,
            server = server,
            live = liveProbe,
            vod = vodProbe,
            device = device,
            catalogue = catalogue,
        )

        Result(
            host = host,
            resolvedIp = resolvedIp,
            dnsLookupMs = dnsMs,
            pingMs = pingMs,
            jitterMs = jitterMs,
            packetLossPct = packetLossPct,
            downloadMbps = throughput,
            speedSource = if (throughput > 0) speedSource else SOURCE_NONE,
            connectionType = kind,
            recommendation = recommendation,
            bufferProjection = bufferProjection,
            server = server,
            liveProbe = liveProbe,
            vodProbe = vodProbe,
            device = device,
            catalogue = catalogue,
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

    private suspend fun detectConnectionCap(
        http: OkHttpClient,
        sampleUrl: String,
        /** The line's own cap from user_info, or 0 when the panel didn't say. */
        reportedMax: Int = 0,
    ): ConnectionCap =
        kotlinx.coroutines.coroutineScope {
            // Never open more sockets than the line is allowed.
            //
            // On a 1-connection line, firing eight parallel GETs is not a
            // measurement — it is seven guaranteed rejections, and on panels
            // that count failed auth attempts it can get the line temporarily
            // blocked by the very test meant to diagnose it. Probe one past the
            // stated cap so the boundary is still confirmed, and no further.
            val target = if (reportedMax > 0) (reportedMax + 1).coerceAtMost(8) else 8
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
            // A ranged GET is not universally accepted. Panels that reject it
            // answer 407, and OkHttp turns a 407 received without a configured
            // proxy into a thrown ProtocolException rather than a response — so
            // the probe reported "HTTP 0 / UNKNOWN" for a live URL that Panel
            // Doctor, which issues a plain GET, reads perfectly well. Two
            // sections of the same report disagreeing about the same URL.
            //
            // Retry without the Range header before giving up, and if that
            // fails too, say 407 rather than 0.
            if (isProxyAuthArtifact(e)) retryPlainGet(http, url) else StreamProbe(
                url = url, ok = false, httpCode = 0, contentType = "",
                container = "UNKNOWN", codecHint = "",
                serverHeader = "", transcoderHint = "", error = e.message,
            )
        }
    }

    /**
     * OkHttp raises this instead of returning the response, so the real status
     * never reaches the caller. Matched on the message because the library
     * throws a plain ProtocolException with no dedicated type.
     */
    private fun isProxyAuthArtifact(e: Exception): Boolean =
        e.message?.contains("HTTP_PROXY_AUTH", ignoreCase = true) == true ||
            e.message?.contains("407") == true

    /** Second attempt with no Range header — what Panel Doctor does, and what works. */
    private fun retryPlainGet(http: OkHttpClient, url: String): StreamProbe = try {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            val ct = r.header("Content-Type").orEmpty()
            StreamProbe(
                url = url, ok = r.isSuccessful, httpCode = r.code,
                contentType = ct,
                container = containerFromContentType(ct, url),
                codecHint = codecHintFromContentType(ct),
                serverHeader = r.header("Server").orEmpty(),
                transcoderHint = r.header("X-Transcoder").orEmpty(),
                error = if (r.isSuccessful) null
                else "Ranged GET was refused (407); a plain GET returned ${r.code}",
            )
        }
    } catch (e2: Exception) {
        StreamProbe(
            url = url, ok = false, httpCode = 407, contentType = "",
            container = "UNKNOWN", codecHint = "", serverHeader = "", transcoderHint = "",
            error = "Panel answered 407 Proxy Authentication Required to a ranged GET " +
                "and did not answer a plain GET either. Usually a WAF or bot rule rather " +
                "than a real proxy — check Settings → Custom User-Agent.",
        )
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

    @VisibleForTesting
    internal fun buildSuggestions(
        pingMs: Int,
        jitterMs: Int,
        lossPct: Int,
        mbps: Double,
        speedSource: String = SOURCE_VOD,
        server: ServerInfo,
        live: StreamProbe?,
        vod: StreamProbe?,
        device: DeviceProbe.Info = DeviceProbe.Info(),
        catalogue: Catalogue = Catalogue(),
    ): List<String> {
        val out = mutableListOf<String>()
        if (pingMs < 0) out += "TCP connection failed — check the panel URL / port, and that the profile isn't expired."
        if (lossPct >= 20) out += "Packet loss ≥ 20%: try a wired connection or a different network path (VPN off, or on)."
        if (jitterMs >= 150) out += "High jitter — switch buffer profile to Large under Settings → Playback."
        // Bandwidth advice, but only where the number means what it says. A
        // live channel is paced by the broadcaster, so "< 5 Mbps" off a live
        // sample is a description of the channel and telling the user to drop
        // to 480p over it is advice about a problem they may not have.
        if (sourceIsCapped(speedSource)) {
            out += when (speedSource) {
                SOURCE_LIVE ->
                    "Throughput was measured against a live channel because this profile has no " +
                        "VOD available. A live stream is sent at its own bitrate, so %.1f Mbps is a floor on your connection, not a measurement of it — your line is at least this fast and probably much faster.".format(mbps)
                else ->
                    "No stream was available to measure against, so the throughput figure is not meaningful. " +
                        "Sync the catalogue (Settings → Refresh catalogue) and run this again."
            }
        } else if (mbps in 0.1..4.9) {
            out += "Bandwidth < 5 Mbps — SD only. Consider a lower-bitrate 480p variant."
        }
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
        // Expiry. The panel has always reported it; nothing ever looked.
        if (server.expiresAt > 0) {
            val daysLeft = (server.expiresAt - System.currentTimeMillis()) / 86_400_000L
            when {
                daysLeft < 0 -> out += "This subscription EXPIRED ${expiryLabel(server.expiresAt)}. Nothing will play until it is renewed — that is the whole fault, whatever else this report says."
                daysLeft <= 3 -> out += "Subscription ${expiryLabel(server.expiresAt)}. Renew before it lapses or playback stops without warning."
            }
        }

        // --- Device-side limits. A connection report that never mentions the
        // box blames the network for decode problems the network never caused.
        if (!device.isEmpty()) {
            val hevc = device.decoder("HEVC")
            val avc = device.decoder("H.264")
            if (hevc == null) {
                out += "This device advertises no HEVC decoder at all. HEVC channels and 4K VOD will not play here regardless of bandwidth — use the H.264 variant where the panel offers one."
            } else if (!hevc.hardware) {
                out += "HEVC decodes in software on this device. Expect judder and heat on anything above 1080p; a stick with hardware HEVC (or the H.264 variant) is the fix, not a bigger buffer."
            } else if (!hevc.supports4k && mbps >= 25) {
                out += "Your line is fast enough for 4K but this device's HEVC decoder tops out at ${hevc.resolutionLabel}. 4K streams will fail or fall back — pick the 1080p variant."
            }
            if (avc != null && !avc.hardware) {
                out += "Even H.264 is decoding in software here. This box is below the app's comfortable minimum; expect dropped frames on most channels."
            }
            if (device.displayWidth in 1..1920 && mbps >= 25) {
                out += "Your display is ${device.displayLabel.substringBefore("  ")}, so 4K streams are downscaled on the way in. Choosing the 1080p variant costs nothing visually and saves bandwidth."
            }
            if (device.hdrTypes.isEmpty() && device.displayWidth >= 3800) {
                out += "4K display reporting no HDR support — HDR channels will play tone-mapped to SDR. Usually an HDMI input or cable limitation rather than the TV."
            }
            if (device.totalRamMb in 1..1200) {
                out += "Under 1.2 GB of RAM. Close other apps before watching; low-memory boxes drop the player first when the system needs room."
            }
            if (device.freeStorageMb in 1..500) {
                out += "Only ${device.freeStorageMb} MB of storage free. Downloads and recordings will fail, and the guide cache can be evicted mid-session."
            }
            // The OS link estimate against the measured figure — the two
            // disagreeing localises the bottleneck without another test.
            val linkMbps = device.linkDownKbps / 1000.0
            if (linkMbps > 0 && mbps > 0) {
                if (linkMbps >= 25 && mbps < linkMbps * 0.3) {
                    out += "Your link reports %.0f Mbps but the panel delivered %.1f Mbps. The bottleneck is between the panel and you, not your Wi-Fi — usually reseller-side shaping or a congested route.".format(linkMbps, mbps)
                } else if (linkMbps < 12) {
                    out += "The OS rates this link at only %.0f Mbps. Move closer to the router, switch to the 5 GHz band, or use Ethernet before blaming the panel.".format(linkMbps)
                }
            }
        }

        // --- Catalogue-side gaps. These arrive as playback complaints and are
        // not playback problems.
        if (!catalogue.isEmpty()) {
            if (catalogue.epgProgrammes == 0 && catalogue.channels > 0) {
                out += "The guide is empty — no EPG data has been loaded for this profile. Settings → Refresh guide, and check the profile's EPG URL if it stays empty."
            } else if (catalogue.channels > 0 && catalogue.epgCoveragePct < 50) {
                out += "The guide covers only ${catalogue.epgCoveragePct}% of your ${catalogue.channels} channels (${catalogue.epgChannels} of them). The gaps are channels whose EPG id the panel does not publish — nothing the app can fix locally."
            }
            if (catalogue.epgProgrammes > 0 && catalogue.epgDaysAhead == 0) {
                out += "The loaded guide does not extend past today. Refresh it — a stale guide is why Catch-Up and reminders look broken."
            }
            if (catalogue.catchupChannels == 0 && catalogue.channels > 0) {
                out += "No channel on this line advertises a catch-up archive, so the Catch-Up screen will stay empty. It is a package feature — ask your reseller whether your plan includes it."
            }
            if (catalogue.lastSyncMs > 0) {
                val ageDays = (System.currentTimeMillis() - catalogue.lastSyncMs) / 86_400_000L
                if (ageDays >= 7) {
                    out += "The catalogue was last synced $ageDays days ago. New channels and VOD added since then are missing locally — Settings → Refresh catalogue."
                }
            }
        }
        if (vod?.container == "MP4" && (live?.container == "MPEG-TS")) {
            out += "Panel serves VOD as MP4 (progressive) and live as MPEG-TS — normal for Xtream."
        }
        // Never claim health while a headline measurement is missing.
        //
        // The report could previously show "Unable to measure throughput" in
        // the recommendation and "Everything looks healthy" in the suggestions
        // at the same time, which tells the user nothing except that the tool
        // does not know what it found.
        if (mbps <= 0.0) {
            out += "Throughput could not be measured, so the buffer advice above is a guess. " +
                "This usually means the panel refused the sample download or closed it early — " +
                "the other probes in this report succeeded, so it is not general connectivity."
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

    /** One live reading during the throughput test, for the on-screen meter. */
    data class SpeedSample(
        val elapsedSec: Double,
        val instantMbps: Double,
        val averageMbps: Double,
        val bytes: Long,
    )

    /**
     * Measures real download throughput over a sustained window.
     *
     * Three things were wrong with the previous version, and the first is the
     * one that made it report nothing:
     *
     *  1. **It measured a live stream.** Panels serve live channels at roughly
     *     the channel's own bitrate — that is the entire point of a live feed —
     *     so the figure was the stream's bitrate, not the connection's
     *     capacity. On a panel that rate-limits hard, or one that closed the
     *     socket early, the sample came back too short to divide by and the
     *     function returned 0.0, surfacing as "unable to measure throughput"
     *     even though every other probe in the report succeeded.
     *
     *  2. **It ran straight after the connection-cap detector**, which opens
     *     eight parallel connections to the same URL. Those sockets sit in
     *     OkHttp's pool afterwards, so on a line whose cap is small the
     *     throughput request was competing against the test's own probes.
     *
     *  3. **Five seconds is too short.** TCP needs several seconds to leave
     *     slow-start on a high-bandwidth link, so short samples systematically
     *     under-report fast connections.
     *
     * Now: prefers a VOD asset (served as fast as the line allows), runs for
     * [WINDOW_SEC] seconds after the first byte, and reports [onSample] about
     * five times a second so the UI can show the speed as it settles rather
     * than a single number at the end.
     */
    private fun measureThroughputMbps(
        http: OkHttpClient,
        url: String,
        onSample: (SpeedSample) -> Unit = {},
    ): Double {
        val req = Request.Builder().url(url)
            // 256 MB ceiling: large enough that a gigabit line cannot exhaust
            // it inside the window and start measuring an idle socket.
            .header("Range", "bytes=0-268435456")
            .header("Accept-Encoding", "identity")
            .build()

        var bytes = 0L
        var firstByteNs = 0L
        var lastNs = 0L
        var lastEmitNs = 0L
        var lastEmitBytes = 0L
        val callStart = System.nanoTime()
        val hardDeadlineNs = callStart + (WINDOW_SEC + 12L) * 1_000_000_000L
        val windowNs = WINDOW_SEC * 1_000_000_000L

        try {
            http.newCall(req).execute().use { resp ->
                if (!(resp.code == 200 || resp.code == 206)) return 0.0
                val source = resp.body.source()
                val buf = okio.Buffer()
                while (true) {
                    val now = System.nanoTime()
                    if (now > hardDeadlineNs) break
                    if (firstByteNs != 0L && now - firstByteNs > windowNs) break
                    val read = try { source.read(buf, 262_144) } catch (_: Exception) { -1L }
                    if (read == -1L) break
                    if (firstByteNs == 0L) {
                        firstByteNs = System.nanoTime()
                        lastEmitNs = firstByteNs
                    }
                    bytes += read
                    lastNs = System.nanoTime()
                    buf.clear()

                    // ~5 readings a second: fast enough to look live, slow
                    // enough that the instant figure is not pure jitter.
                    if (lastNs - lastEmitNs >= 200_000_000L) {
                        val instantSec = (lastNs - lastEmitNs) / 1_000_000_000.0
                        val totalSec = (lastNs - firstByteNs) / 1_000_000_000.0
                        onSample(
                            SpeedSample(
                                elapsedSec = totalSec,
                                instantMbps = ((bytes - lastEmitBytes) * 8.0 / instantSec) / 1_000_000.0,
                                averageMbps = if (totalSec > 0) (bytes * 8.0 / totalSec) / 1_000_000.0 else 0.0,
                                bytes = bytes,
                            ),
                        )
                        lastEmitNs = lastNs
                        lastEmitBytes = bytes
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through — whatever was transferred before the failure is
            // still a usable measurement if it ran long enough.
        }

        if (firstByteNs == 0L || bytes <= 0) return 0.0
        val elapsedS = (lastNs - firstByteNs) / 1_000_000_000.0
        if (elapsedS < 0.5) return 0.0
        return (bytes * 8.0 / elapsedS) / 1_000_000.0
    }

    @VisibleForTesting
    internal fun recommend(
        mbps: Double,
        pingMs: Int,
        lossPct: Int,
        speedSource: String = SOURCE_VOD,
    ): String = when {
        pingMs < 0 || lossPct >= 50 -> "Connection too unstable to recommend a format — check your network or VPN."
        // Format advice needs a number that describes the line. Off a live
        // channel it describes the channel, and recommending 480p because a
        // 6 Mbps broadcast was measured at 6 Mbps is advice about nothing.
        sourceIsCapped(speedSource) ->
            "Throughput could not be measured against a VOD file, so no format recommendation is offered — " +
                "the figure shown is a floor, not your line speed. Latency, loss and the URL probes above are still accurate."
        mbps >= 25 -> "Your network comfortably supports 4K HLS / direct MPEG-TS without buffering."
        mbps >= 12 -> "Your network supports 1080p HLS reliably; 4K may occasionally buffer."
        mbps >= 5 -> "Your network supports 720p reliably. Consider Balanced or Low buffer profile for 1080p."
        mbps >= 2 -> "Your network is best suited to SD (480p) streams; higher bitrates will buffer."
        mbps > 0 -> "Very limited bandwidth detected — expect frequent buffering even at low bitrates."
        // Only blame connectivity when connectivity actually failed. When the
        // TCP probes and URL shapes all succeeded, telling the user to "check
        // the server address" sends them after a problem that is not there.
        pingMs >= 0 -> "Throughput sample did not complete — the panel refused or cut the download. " +
            "Connectivity itself is fine (latency and URL probes succeeded)."
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
