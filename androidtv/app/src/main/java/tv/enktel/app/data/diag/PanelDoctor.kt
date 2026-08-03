package tv.enktel.app.data.diag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.db.Profile

/**
 * Runs the panel diagnostics.
 *
 * Split from [SettingsAdvisor] on purpose: this layer only measures, and every
 * failure it meets becomes a recorded fact rather than an exception thrown at
 * the UI. A diagnostic tool that dies when the thing it is diagnosing is
 * broken would be useless precisely when it is needed.
 *
 * Network cost is the design constraint. A full pass reads a few kilobytes per
 * stream — never a whole file — and results are cached by [DiagnosticsCache]
 * so re-opening the panel does not re-probe the line.
 */
object PanelDoctor {

    /** Bytes pulled from the head of a stream. Enough for EBML + magic bytes. */
    private const val HEAD_BYTES = 8 * 1024
    private const val REQUEST_TIMEOUT_MS = 12_000L

    suspend fun run(
        http: OkHttpClient,
        profile: Profile,
        xtream: tv.enktel.app.data.xtream.XtreamClient?,
        liveUrl: String?,
        vodUrl: String?,
        catchupUrl: String?,
        channelsWithArchive: Int,
        settings: PlaybackSettings,
        onProgress: (String) -> Unit = {},
    ): PanelReport = withContext(Dispatchers.IO) {
        val client = http.newBuilder()
            .callTimeout(REQUEST_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        onProgress("Reading line structure…")
        val structure = probeStructure(profile, xtream)

        onProgress("Inspecting live stream…")
        val live = liveUrl?.let { inspect(client, it) }

        onProgress("Inspecting VOD container…")
        val vod = vodUrl?.let { inspect(client, it) }

        onProgress("Testing catch-up…")
        val catchup = probeCatchup(client, catchupUrl, channelsWithArchive)

        val changes = SettingsAdvisor.advise(settings, live, vod, catchup)
        val notes = SettingsAdvisor.notes(live, vod)

        PanelReport(
            profileId = profile.id,
            ranAtMs = System.currentTimeMillis(),
            structure = structure,
            live = live,
            vod = vod,
            catchup = catchup,
            settingsAtRun = settings,
            changes = changes,
            notes = notes,
        )
    }

    /**
     * Asks the panel what it thinks it serves, via the Xtream API.
     *
     * `container_extension` on get_vod_streams is the panel's own declaration
     * of each title's container, which is worth having next to the bytes we
     * actually pull — a library that says "mkv" while serving MP4 (or the
     * reverse) explains a whole class of playback failure.
     */
    private suspend fun probeStructure(
        profile: Profile,
        xtream: tv.enktel.app.data.xtream.XtreamClient?,
    ): LineStructure {
        if (xtream == null || profile.kind != "xtream") return LineStructure()
        return try {
            val live = runCatching { xtream.liveStreams(profile) }.getOrNull()
            val vod = runCatching { xtream.vodStreams(profile) }.getOrNull()
            val containers = mutableMapOf<String, Int>()
            var archive = 0
            (vod as? List<*>)?.forEach { row ->
                val m = row as? Map<*, *> ?: return@forEach
                val ext = (m["container_extension"] as? String).orEmpty().lowercase()
                if (ext.isNotBlank()) containers[ext] = (containers[ext] ?: 0) + 1
            }
            (live as? List<*>)?.forEach { row ->
                val m = row as? Map<*, *> ?: return@forEach
                val days = (m["tv_archive_duration"] ?: m["tv_archive"])?.toString()
                    ?.toIntOrNull() ?: 0
                if (days > 0) archive++
            }
            LineStructure(
                queried = true,
                liveCount = (live as? List<*>)?.size ?: 0,
                vodCount = (vod as? List<*>)?.size ?: 0,
                vodContainers = containers,
                archiveCount = archive,
            )
        } catch (e: Exception) {
            LineStructure(queried = true, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Reads the head of [url] and its range behaviour.
     *
     * One request does double duty: `Range: bytes=0-N` both fetches the magic
     * bytes and tells us whether the server honours ranges at all.
     */
    internal fun inspect(http: OkHttpClient, url: String): ContainerFacts {
        // HEAD first: cheap, and `Accept-Ranges` tells us what the server
        // claims before we find out what it actually does. Kept advisory —
        // panels that reject HEAD entirely are common, and one that omits
        // Accept-Ranges may still honour a ranged GET perfectly well.
        var headSupported = false
        var headAcceptsRanges = false
        try {
            http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", tv.enktel.app.DEFAULT_UA)
                    .head()
                    .build(),
            ).execute().use { r ->
                headSupported = r.code in 200..299
                headAcceptsRanges = r.header("Accept-Ranges")
                    .orEmpty().contains("bytes", ignoreCase = true)
            }
        } catch (_: Exception) {
            // Left false — the ranged GET below is the real test.
        }

        val head = try {
            http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", tv.enktel.app.DEFAULT_UA)
                    .header("Range", "bytes=0-${HEAD_BYTES - 1}")
                    .header("Accept-Encoding", "identity")
                    .get()
                    .build(),
            ).execute()
        } catch (e: Exception) {
            return ContainerFacts(url = url, error = e.message ?: e.javaClass.simpleName)
        }

        val bytes: ByteArray
        val code: Int
        val contentType: String
        val contentRange: String
        val contentLength: Long
        var chunked = false
        var keepAlive = false
        head.use { r ->
            code = r.code
            contentType = r.header("Content-Type").orEmpty()
            contentRange = r.header("Content-Range").orEmpty()
            contentLength = r.header("Content-Length")?.toLongOrNull() ?: -1L
            chunked = r.header("Transfer-Encoding").orEmpty().contains("chunked", true)
            keepAlive = !r.header("Connection").orEmpty().equals("close", true)
            bytes = try {
                r.body?.byteStream()?.use { s -> s.readNBytesCompat(HEAD_BYTES) } ?: ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }

        val total = contentRange.substringAfterLast('/', "").trim().toLongOrNull()
            ?: contentLength.takeIf { it > 0 } ?: 0L
        val partial = code == 206

        val detected = detectContainer(bytes, contentType)
        val mkv = if (detected == "MATROSKA" || detected == "WEBM") {
            runCatching { Ebml.parseHead(bytes) }.getOrNull()
        } else null

        // Only worth a second request when the first proved ranges work and we
        // know how big the file is.
        val midOk = if (partial && total > HEAD_BYTES * 2L) {
            probeMidFileRange(http, url, total)
        } else false

        val urlExt = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        val mismatch = when (urlExt) {
            "mp4", "m4v" -> detected == "MATROSKA" || detected == "WEBM"
            "mkv" -> detected == "MP4"
            else -> false
        }

        val expectedMime = expectedMimeFor(detected)
        val mimeOk = expectedMime.isEmpty() || contentType.isBlank() ||
            expectedMime.split('|').any { contentType.contains(it, ignoreCase = true) }

        return ContainerFacts(
            url = url,
            declaredContentType = contentType,
            detected = detected,
            mismatch = mismatch,
            mimeCorrect = mimeOk,
            mimeExpected = if (mimeOk) "" else expectedMime.replace('|', '/'),
            chunked = chunked,
            keepAlive = keepAlive,
            matroska = mkv,
            range = RangeSupport(
                tested = true,
                headSupported = headSupported,
                headAcceptsRanges = headAcceptsRanges,
                partialContent = partial,
                totalBytes = total,
                midFileSeekOk = midOk,
                httpCode = code,
            ),
            error = if (code !in 200..299) "HTTP $code" else null,
        )
    }

    /**
     * Asks for a range in the middle of the file and checks the server put us
     * there. Servers that answer 206 for `bytes=0-` but ignore a non-zero
     * offset are common enough to be worth catching — they present as "seek
     * jumps back to the start", which looks exactly like a player bug.
     */
    private fun probeMidFileRange(http: OkHttpClient, url: String, total: Long): Boolean {
        val start = total / 2
        return try {
            http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", tv.enktel.app.DEFAULT_UA)
                    .header("Range", "bytes=$start-${start + 1023}")
                    .header("Accept-Encoding", "identity")
                    .get()
                    .build(),
            ).execute().use { r ->
                val cr = r.header("Content-Range").orEmpty()
                try { r.body?.bytes() } catch (_: Exception) {}
                r.code == 206 && parseRangeStart(cr) == start
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The Content-Type a correctly configured server sends for [detected].
     * Pipe-separated where more than one spelling is legitimate.
     */
    internal fun expectedMimeFor(detected: String): String = when (detected) {
        "HLS" -> "mpegurl"
        "MPEG-TS" -> "mp2t"
        "MATROSKA" -> "matroska"
        "WEBM" -> "webm"
        "MP4" -> "mp4"
        else -> ""
    }

    /**
     * Which catch-up scheme a URL template implies.
     *
     * Panels advertise these inconsistently, and picking the wrong one is why
     * catch-up "works" on one line and 404s on another that looks identical.
     */
    internal fun detectCatchupScheme(template: String?, xtreamStyleUrl: String?): CatchupScheme = when {
        template.isNullOrBlank() && !xtreamStyleUrl.isNullOrBlank() -> CatchupScheme.XTREAM_TIMESHIFT
        template.isNullOrBlank() -> CatchupScheme.UNKNOWN
        template.contains("\${'$'}{start}") || template.contains("utc=") ||
            template.contains("start=") -> CatchupScheme.APPEND
        template.contains("timeshift", true) -> CatchupScheme.XTREAM_TIMESHIFT
        template.contains("shift", true) -> CatchupScheme.SHIFT
        template.contains("archive", true) || template.contains("flussonic", true) ->
            CatchupScheme.FLUSSONIC
        else -> CatchupScheme.DEFAULT
    }

    /** `bytes 12345-13000/99999` → 12345. Null when unparseable. */
    internal fun parseRangeStart(contentRange: String): Long? =
        contentRange.substringAfter("bytes", "")
            .trim()
            .substringBefore('-')
            .trim()
            .toLongOrNull()

    /** Container from magic bytes, falling back to the declared type. */
    internal fun detectContainer(bytes: ByteArray, contentType: String): String {
        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            // EBML header — Matroska and WebM share it; DocType separates them.
            if (b0 == 0x1A && b1 == 0x45 && b2 == 0xDF && b3 == 0xA3) {
                val doc = runCatching { Ebml.parseHead(bytes).docType }.getOrNull().orEmpty()
                return if (doc.equals("webm", true)) "WEBM" else "MATROSKA"
            }
            // MPEG-TS: 0x47 sync byte every 188 bytes.
            if (b0 == 0x47 &&
                (bytes.size <= 188 || (bytes[188].toInt() and 0xFF) == 0x47)
            ) return "MPEG-TS"
            // ISO-BMFF: 'ftyp' at offset 4.
            if (bytes.size >= 8 &&
                bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
            ) return "MP4"
            // HLS playlists are text.
            val headText = String(bytes, 0, minOf(bytes.size, 64), Charsets.US_ASCII)
            if (headText.startsWith("#EXTM3U")) return "HLS"
        }
        return when {
            contentType.contains("mpegurl", true) -> "HLS"
            contentType.contains("matroska", true) -> "MATROSKA"
            contentType.contains("webm", true) -> "WEBM"
            contentType.contains("mp4", true) -> "MP4"
            contentType.contains("mp2t", true) -> "MPEG-TS"
            else -> "UNKNOWN"
        }
    }

    private fun probeCatchup(
        http: OkHttpClient,
        url: String?,
        channelsWithArchive: Int,
    ): CatchupFacts {
        if (url.isNullOrBlank()) {
            return CatchupFacts(
                tested = false,
                channelsWithArchive = channelsWithArchive,
                error = if (channelsWithArchive == 0) {
                    "No channel in the catalogue advertises an archive window."
                } else null,
            )
        }
        return try {
            http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", tv.enktel.app.DEFAULT_UA)
                    .header("Range", "bytes=0-2047")
                    .get()
                    .build(),
            ).execute().use { r ->
                try { r.body?.bytes() } catch (_: Exception) {}
                CatchupFacts(
                    tested = true,
                    scheme = detectCatchupScheme(null, url),
                    available = r.code in 200..299,
                    httpCode = r.code,
                    channelsWithArchive = channelsWithArchive,
                    sampleUrl = url,
                )
            }
        } catch (e: Exception) {
            CatchupFacts(
                tested = true,
                available = false,
                channelsWithArchive = channelsWithArchive,
                sampleUrl = url,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    /** `readNBytes` is API 33; this app supports 23. */
    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(max, 8192))
        val chunk = ByteArray(4096)
        var total = 0
        while (total < max) {
            val n = read(chunk, 0, minOf(chunk.size, max - total))
            if (n <= 0) break
            out.write(chunk, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
