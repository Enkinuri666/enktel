package tv.enktel.app.data.net

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import android.view.Display
import java.io.File

/**
 * What the *box* can do, as opposed to what the connection can do.
 *
 * Connection Diagnostics answered one half of "why does this stutter" and left
 * the other half to guesswork. A Fire TV Stick Lite with no hardware HEVC path
 * will judder on a 4K HEVC channel down a gigabit line, and a 1080p panel
 * downscales a 4K stream the user is paying bandwidth for — neither shows up
 * anywhere in a latency-and-throughput report, so both arrive at support as
 * "the app is broken on my TV".
 *
 * Everything here is read from the platform, costs no network, and needs no
 * permission the app does not already hold.
 */
object DeviceProbe {

    /** One decoder the device advertises for a codec we care about. */
    data class Decoder(
        /** "H.264", "HEVC", "AV1", "VP9" */
        val label: String,
        val mime: String,
        val hardware: Boolean,
        val maxWidth: Int,
        val maxHeight: Int,
        /** Frames per second at [maxWidth]×[maxHeight], 0 when the device won't say. */
        val maxFps: Int,
    ) {
        /** "3840×2160 @60" — or blank when the codec reports no video limits. */
        val resolutionLabel: String
            get() = if (maxWidth <= 0 || maxHeight <= 0) "" else
                "${maxWidth}×$maxHeight" + if (maxFps > 0) " @${maxFps}" else ""

        val supports4k: Boolean get() = maxWidth >= 3800 && maxHeight >= 2100
        val supports1080p: Boolean get() = maxWidth >= 1900 && maxHeight >= 1060
    }

    data class Info(
        val decoders: List<Decoder> = emptyList(),
        /** Physical panel size in pixels — the mode the display is actually in. */
        val displayWidth: Int = 0,
        val displayHeight: Int = 0,
        val refreshHz: Double = 0.0,
        /** "HDR10", "HLG", "Dolby Vision", "HDR10+" — empty on an SDR display. */
        val hdrTypes: List<String> = emptyList(),
        val freeStorageMb: Long = 0,
        val totalStorageMb: Long = 0,
        val totalRamMb: Long = 0,
        val availRamMb: Long = 0,
        /**
         * The OS's own estimate of the active link's downstream capacity, kbps.
         *
         * Worth reporting next to the measured figure because the two disagree
         * in an informative way: a link that reports 300 Mbps while the panel
         * delivers 6 puts the bottleneck beyond the user's Wi-Fi, and a link
         * reporting 12 Mbps explains a slow measurement without anyone
         * suspecting the reseller.
         */
        val linkDownKbps: Int = 0,
        val abi: String = "",
    ) {
        fun isEmpty(): Boolean = decoders.isEmpty() && displayWidth == 0 && totalRamMb == 0L

        fun decoder(label: String): Decoder? =
            decoders.filter { it.label == label }.maxByOrNull {
                // Prefer the hardware entry, then the largest frame it accepts.
                (if (it.hardware) 1L shl 40 else 0L) + it.maxWidth.toLong() * it.maxHeight
            }

        val displayLabel: String
            get() = if (displayWidth <= 0) "unknown" else
                "${displayWidth}×$displayHeight" +
                    (if (refreshHz > 0) " @%.0f Hz".format(refreshHz) else "") +
                    when {
                        displayWidth >= 3800 -> "  (4K)"
                        displayWidth >= 1900 -> "  (1080p)"
                        displayWidth >= 1260 -> "  (720p)"
                        else -> ""
                    }
    }

    /** Codecs an IPTV panel actually serves. Anything else is noise in a report. */
    private val WANTED = linkedMapOf(
        "video/avc" to "H.264",
        "video/hevc" to "HEVC",
        "video/av01" to "AV1",
        "video/x-vnd.on2.vp9" to "VP9",
        "video/mp4v-es" to "MPEG-4",
    )

    fun snapshot(ctx: Context): Info {
        val mode = displayMode(ctx)
        val (freeMb, totalMb) = storage(ctx)
        val (totalRam, availRam) = ram(ctx)
        return Info(
            decoders = decoders(),
            displayWidth = mode?.first ?: 0,
            displayHeight = mode?.second ?: 0,
            refreshHz = mode?.third ?: 0.0,
            hdrTypes = hdrTypes(ctx),
            freeStorageMb = freeMb,
            totalStorageMb = totalMb,
            totalRamMb = totalRam,
            availRamMb = availRam,
            linkDownKbps = linkDownKbps(ctx),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        )
    }

    private fun decoders(): List<Decoder> = try {
        val out = mutableListOf<Decoder>()
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.forEach { info ->
            if (info.isEncoder) return@forEach
            info.supportedTypes.forEach types@{ type ->
                val label = WANTED[type.lowercase()] ?: return@types
                val caps = try { info.getCapabilitiesForType(type) } catch (_: Throwable) { null }
                val video = caps?.videoCapabilities
                val w = video?.supportedWidths?.upper ?: 0
                val h = video?.supportedHeights?.upper ?: 0
                val fps = try {
                    if (video != null && w > 0 && h > 0)
                        video.getSupportedFrameRatesFor(w, h).upper.toInt()
                    else 0
                } catch (_: Throwable) { 0 }
                out += Decoder(
                    label = label,
                    mime = type,
                    hardware = isHardware(info),
                    maxWidth = w,
                    maxHeight = h,
                    maxFps = fps,
                )
            }
        }
        out
    } catch (_: Throwable) { emptyList() }

    /**
     * `isHardwareAccelerated()` only exists from API 29. Below that the name
     * prefix is the only signal available, and it is a reliable one: the
     * platform's own software decoders are the `OMX.google.` and `c2.android.`
     * families, and everything else on a shipping device is vendor silicon.
     */
    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { info.isHardwareAccelerated } catch (_: Throwable) { false }
        } else {
            val n = info.name.lowercase()
            !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains(".sw.")
        }

    private fun display(ctx: Context): Display? = try {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.getDisplay(Display.DEFAULT_DISPLAY)
    } catch (_: Throwable) { null }

    /** width, height, refresh — the mode the panel is *in*, not its largest. */
    private fun displayMode(ctx: Context): Triple<Int, Int, Double>? = try {
        display(ctx)?.mode?.let {
            Triple(it.physicalWidth, it.physicalHeight, it.refreshRate.toDouble())
        }
    } catch (_: Throwable) { null }

    private fun hdrTypes(ctx: Context): List<String> = try {
        val d = display(ctx)
        val raw: IntArray = when {
            d == null -> IntArray(0)
            // API 34 moved this onto the mode: a display can support different
            // HDR formats in different modes, which the display-wide list could
            // not express.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                d.mode.supportedHdrTypes
            // Deprecated at 34, but the only answer from 24 to 33 — and there
            // is no answer at all below 24, which the app still supports.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> legacyHdrTypes(d)
            else -> IntArray(0)
        }
        raw.map {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                4 -> "HDR10+" // HDR_TYPE_HDR10_PLUS, API 29
                else -> "type $it"
            }
        }
    } catch (_: Throwable) { emptyList() }

    @Suppress("DEPRECATION")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private fun legacyHdrTypes(d: Display): IntArray =
        d.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)

    /** free, total — in MB, on the volume downloads and recordings land on. */
    private fun storage(ctx: Context): Pair<Long, Long> = try {
        val dir: File = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val stat = StatFs(dir.absolutePath)
        val block = stat.blockSizeLong
        (stat.availableBlocksLong * block / (1024 * 1024)) to
            (stat.blockCountLong * block / (1024 * 1024))
    } catch (_: Throwable) { 0L to 0L }

    /** total, available — in MB. */
    private fun ram(ctx: Context): Pair<Long, Long> = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)) to (info.availMem / (1024 * 1024))
    } catch (_: Throwable) { 0L to 0L }

    private fun linkDownKbps(ctx: Context): Int = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        cm.getNetworkCapabilities(net)?.linkDownstreamBandwidthKbps ?: 0
    } catch (_: Throwable) { 0 }
}
