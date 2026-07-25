package tv.enktel.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import kotlin.math.sqrt

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
            appendLine("Recommended format: $recommendation")
            appendLine("Buffer health: $bufferProjection")
            error?.let { appendLine("Note: $it") }
        }
    }

    /**
     * Runs the full diagnostic against [serverUrl] (the active profile's
     * Xtream/M3U server).  [streamSampleUrl], if provided (e.g. the
     * currently-tuned live channel), is used for the throughput test
     * instead of the bare panel API so the Mbps number reflects real
     * streaming conditions rather than a tiny JSON response.
     */
    suspend fun run(
        http: OkHttpClient,
        serverUrl: String,
        streamSampleUrl: String? = null,
        onProgress: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val url = try { URL(serverUrl) } catch (e: Exception) {
            return@withContext Result(
                host = serverUrl, resolvedIp = null, dnsLookupMs = 0, pingMs = 0, jitterMs = 0,
                packetLossPct = 100, downloadMbps = 0.0, connectionType = NetworkClass.kind.value,
                recommendation = "Unable to test — invalid server URL", bufferProjection = "unknown",
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

        onProgress("Measuring download throughput…")
        val throughput = try {
            measureThroughputMbps(http, streamSampleUrl ?: serverUrl)
        } catch (e: Exception) { 0.0 }

        val kind = NetworkClass.kind.value
        val recommendation = recommend(throughput, pingMs, packetLossPct)
        val bufferProjection = projectBufferHealth(throughput, jitterMs, packetLossPct)

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
            error = if (pingMs < 0) "Could not establish a TCP connection to $host:$port" else null,
        )
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

    /** Streams up to ~3 seconds (or 8 MB, whichever first) and computes Mbps. */
    private fun measureThroughputMbps(http: OkHttpClient, url: String): Double {
        val req = Request.Builder().url(url).build()
        var bytes = 0L
        val start = System.nanoTime()
        val deadlineNs = start + 3_000_000_000L
        val maxBytes = 8L * 1024 * 1024
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return 0.0
            val source = resp.body?.source() ?: return 0.0
            val buf = okio.Buffer()
            while (System.nanoTime() < deadlineNs && bytes < maxBytes) {
                val read = try { source.read(buf, 65_536) } catch (_: Exception) { -1L }
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
