package tv.enktel.app.data.net

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.player.PlaybackTelemetry
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Built-in connection and system monitoring.
 *
 * Buffering complaints are almost never "the app is broken" — they're a slow
 * link, a saturated Wi-Fi band, a box that has thermally throttled, or a device
 * that has run out of memory or storage. None of that is visible from inside a
 * player, so a support conversation turns into guesswork on both sides.
 *
 * This samples the things that actually predict a stall, once every
 * [SAMPLE_INTERVAL_MS], and keeps a rolling window of them:
 *
 *  - **Link** — transport (Ethernet / Wi-Fi / cellular) and the downstream
 *    bandwidth the OS reports for it.
 *  - **Request health** — rolling latency, timeouts and 403s observed by the
 *    app's own HTTP traffic via [StreamHealth], so the numbers describe the
 *    panel the user is actually on rather than a generic speed-test host.
 *  - **Playback** — bandwidth estimate, dropped frames and buffer depth from
 *    the live player ([PlaybackTelemetry]). Dropped frames rising while the
 *    buffer stays deep is a decode problem; the buffer draining while frames
 *    hold is a network problem. That distinction is the whole point.
 *  - **Device** — thermal state, memory pressure, free storage, battery.
 *
 * Everything is passive: no polling of the network, no extra requests, no
 * permissions beyond what the app already holds. [probeLatency] exists for the
 * one case where the user explicitly asks for a fresh reading.
 */
object SystemMonitor {

    /** How often a sample is taken while monitoring is running. */
    private const val SAMPLE_INTERVAL_MS = 2_000L
    /** Rolling window kept in memory — 150 samples ≈ 5 minutes. */
    private const val HISTORY = 150

    data class Sample(
        val atMs: Long = 0,
        // ---- link ----
        val transport: NetworkClass.Kind = NetworkClass.Kind.UNKNOWN,
        val hasInternet: Boolean = true,
        /** OS-reported downstream link bandwidth, kbps. 0 when unknown. */
        val linkDownKbps: Int = 0,
        val linkUpKbps: Int = 0,
        // ---- request health (from real app traffic) ----
        val latencyMs: Int = 0,
        val quality: StreamHealth.Quality = StreamHealth.Quality.UNKNOWN,
        val timeouts: Int = 0,
        val blocked403: Int = 0,
        val activeGateway: String? = null,
        // ---- playback ----
        val playbackKbps: Long = 0,
        val droppedFrames: Int = 0,
        val bufferAheadMs: Long = 0,
        val playbackFresh: Boolean = false,
        // ---- device ----
        val thermal: ThermalGuard.Level = ThermalGuard.Level.NONE,
        /** This process's CPU usage since the previous sample, percent of one
         *  core-second per wall-second (so >100 is possible on multi-core). */
        val appCpuPct: Int = 0,
        val usedMemMb: Long = 0,
        val totalMemMb: Long = 0,
        val lowMemory: Boolean = false,
        val freeStorageMb: Long = 0,
        val batteryPct: Int = -1,
        val charging: Boolean = false,
    ) {
        /** One-line verdict for the header chip. */
        val verdict: String
            get() = when {
                !hasInternet -> "Offline"
                quality == StreamHealth.Quality.BLOCKED -> "Blocked by panel"
                thermal >= ThermalGuard.Level.MODERATE -> "Device running hot"
                lowMemory -> "Low memory"
                quality == StreamHealth.Quality.POOR -> "Poor connection"
                playbackFresh && bufferAheadMs in 1..1_500 -> "Buffer running low"
                quality == StreamHealth.Quality.FAIR -> "Fair connection"
                quality == StreamHealth.Quality.GOOD -> "Healthy"
                else -> "Monitoring"
            }

        /** True when something on this sample warrants the user's attention. */
        val degraded: Boolean
            get() = !hasInternet ||
                quality == StreamHealth.Quality.POOR ||
                quality == StreamHealth.Quality.BLOCKED ||
                thermal >= ThermalGuard.Level.MODERATE ||
                lowMemory
    }

    private val _current = MutableStateFlow(Sample())
    val current: StateFlow<Sample> = _current.asStateFlow()

    private val _history = MutableStateFlow<List<Sample>>(emptyList())
    val history: StateFlow<List<Sample>> = _history.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var appContext: Context? = null

    // CPU accounting needs the previous reading to produce a rate.
    private var lastCpuTicks = 0L
    private var lastCpuAtMs = 0L

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /** Begin sampling. Idempotent — a second call while running does nothing. */
    fun start() {
        if (job?.isActive == true) return
        val ctx = appContext ?: return
        job = scope.launch {
            while (isActive) {
                val sample = sample(ctx)
                _current.value = sample
                _history.value = (_history.value + sample).takeLast(HISTORY)
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    /** Stop sampling. The last sample and history stay readable. */
    fun stop() {
        job?.cancel()
        job = null
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    private fun sample(ctx: Context): Sample {
        val health = StreamHealth.state.value
        val playback = PlaybackTelemetry.state.value
        val now = System.currentTimeMillis()
        val caps = networkCaps(ctx)
        val mem = memoryInfo(ctx)
        val battery = batteryInfo(ctx)

        return Sample(
            atMs = now,
            transport = NetworkClass.kind.value,
            hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true,
            linkDownKbps = caps?.linkDownstreamBandwidthKbps ?: 0,
            linkUpKbps = caps?.linkUpstreamBandwidthKbps ?: 0,
            latencyMs = health.meanLatencyMs,
            quality = health.quality,
            timeouts = health.timeouts,
            blocked403 = health.blocked403,
            activeGateway = health.activeGateway,
            playbackKbps = playback.bandwidthBps / 1000,
            droppedFrames = playback.droppedFrames,
            bufferAheadMs = playback.bufferAheadMs,
            playbackFresh = playback.isFresh(now),
            thermal = ThermalGuard.level.value,
            appCpuPct = appCpuPercent(now),
            usedMemMb = mem.first,
            totalMemMb = mem.second,
            lowMemory = mem.third,
            freeStorageMb = freeStorageMb(ctx),
            batteryPct = battery.first,
            charging = battery.second,
        )
    }

    private fun networkCaps(ctx: Context): NetworkCapabilities? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork)
        } catch (_: Throwable) { null }
    }

    /** @return used MB, total MB, lowMemory flag. */
    private fun memoryInfo(ctx: Context): Triple<Long, Long, Boolean> = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalMb = info.totalMem / (1024 * 1024)
        val availMb = info.availMem / (1024 * 1024)
        Triple((totalMb - availMb).coerceAtLeast(0), totalMb, info.lowMemory)
    } catch (_: Throwable) { Triple(0L, 0L, false) }

    /** @return battery percent (-1 unknown), charging. */
    private fun batteryInfo(ctx: Context): Pair<Int, Boolean> = try {
        // Sticky broadcast: passing a null receiver reads the current value
        // without registering anything, so there's nothing to unregister.
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            -1 to false
        } else {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            pct to charging
        }
    } catch (_: Throwable) { -1 to false }

    private fun freeStorageMb(ctx: Context): Long = try {
        val dir: File = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val stat = StatFs(dir.absolutePath)
        // The Long overloads landed in API 18, so no legacy path is needed.
        (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
    } catch (_: Throwable) { 0L }

    /**
     * This process's CPU usage, as a percentage of wall-clock time.
     *
     * `/proc/stat` (whole-device) has been unreadable by apps since Android 8,
     * but a process can still read its own `/proc/self/stat`. Fields 14 and 15
     * are utime and stime in clock ticks; the delta between samples over the
     * elapsed wall time is the app's own CPU share. That's the honest number to
     * show anyway — the app can only answer for itself.
     */
    private fun appCpuPercent(nowMs: Long): Int = try {
        val fields = File("/proc/self/stat").readText().split(' ')
        // Field indices are 1-based in proc(5); utime is field 14, stime 15.
        val utime = fields.getOrNull(13)?.toLongOrNull() ?: 0L
        val stime = fields.getOrNull(14)?.toLongOrNull() ?: 0L
        val ticks = utime + stime
        val elapsedMs = nowMs - lastCpuAtMs
        val pct = if (lastCpuAtMs > 0 && elapsedMs > 0) {
            // Linux reports 100 ticks/second on every Android device we target.
            val tickMs = (ticks - lastCpuTicks) * 10
            ((tickMs * 100) / elapsedMs).toInt().coerceIn(0, 800)
        } else 0
        lastCpuTicks = ticks
        lastCpuAtMs = nowMs
        pct
    } catch (_: Throwable) { 0 }

    /** Outcome of [probeLatency]. */
    data class Ping(
        /** Round-trip in ms, or -1 when nothing came back at all. */
        val ms: Int = -1,
        /** HTTP status the panel answered with. 0 when it never answered. */
        val httpCode: Int = 0,
        /** Which request finally got through — "HEAD" or "GET". */
        val via: String = "",
        /** Why it failed, when it did. */
        val error: String? = null,
    ) {
        val ok: Boolean get() = ms >= 0
    }

    /**
     * One active round-trip against [url], for the "test now" button.
     * Deliberately not on the sampling path — passive measurement is what
     * keeps the monitor free.
     *
     * This used to send a single HEAD and collapse *every* failure to -1,
     * which the UI rendered as "Panel did not respond" — even while the
     * card above it showed a working latency figure from real traffic. Two
     * reasons it lied:
     *
     *  - **HEAD is not universally served.** Plenty of Xtream panels and the
     *    WAFs in front of them answer HEAD with 405, or drop it, while
     *    answering GET on the same URL perfectly well.
     *  - **OkHttp throws on a 407 received without a configured proxy**
     *    (`ProtocolException`) rather than returning the response, so a panel
     *    behind a bot rule never reached the `it.code` line at all.
     *
     * A reply is a reply: any HTTP status proves the panel is reachable and
     * answering, so 403 and 404 are successful round trips here, not
     * failures. Only a transport error is silence, and now it says which.
     */
    suspend fun probeLatency(http: OkHttpClient, url: String): Ping {
        if (url.isBlank()) return Ping(error = "No panel address configured")
        val client = http.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        var firstError: String? = null
        for (method in listOf("HEAD", "GET")) {
            val started = System.nanoTime()
            try {
                val req = Request.Builder().url(url)
                    .apply { if (method == "HEAD") head() else get() }
                    .build()
                val code = client.newCall(req).execute().use { it.code }
                val ms = ((System.nanoTime() - started) / 1_000_000).toInt()
                // 405/501 mean "not this verb" — worth retrying as GET rather
                // than reporting a round trip the user cannot act on.
                if (method == "HEAD" && (code == 405 || code == 501)) {
                    firstError = firstError ?: "HEAD rejected (HTTP $code)"
                    continue
                }
                return Ping(ms = ms, httpCode = code, via = method)
            } catch (e: Throwable) {
                // See the 407 note above: OkHttp reports it as a thrown
                // ProtocolException, but the panel demonstrably answered.
                val msg = e.message.orEmpty()
                if (msg.contains("HTTP_PROXY_AUTH", true) || msg.contains("407")) {
                    val ms = ((System.nanoTime() - started) / 1_000_000).toInt()
                    return Ping(ms = ms, httpCode = 407, via = method, error = "Blocked by a bot rule")
                }
                firstError = firstError ?: (e.message ?: e.javaClass.simpleName)
            }
        }
        return Ping(error = firstError ?: "No response")
    }
}
