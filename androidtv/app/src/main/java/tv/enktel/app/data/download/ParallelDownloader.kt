package tv.enktel.app.data.download

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/**
 * Custom OkHttp-based downloader tuned for maximum transfer rate.
 *
 * Strategy per download:
 *   1. HEAD (fallback to Range=0-0 GET) → learn Content-Length + Accept-Ranges.
 *   2. If ranges supported AND size ≥ [PARALLEL_MIN_BYTES]: split into
 *      [PARALLEL_STREAMS] concurrent ranged GETs, each writing directly into
 *      the correct offset of a pre-allocated file with RandomAccessFile.
 *   3. Otherwise: single-stream download.
 *
 * Targets both a plain File (app-scoped external storage, USB, legacy public
 * folder) and a SAF DocumentFile (user-picked folder via Storage Access
 * Framework). File-target uses RandomAccessFile.seek so parallel writers
 * don't collide; DocumentFile-target streams sequentially (SAF's
 * OutputStream API doesn't expose positional writes) and takes the
 * single-stream path even when parallelism would be legal.
 *
 * Progress is reported byte-by-byte through [onProgress] so the calling
 * DownloadHub can push updates into Room at the same cadence as the
 * platform DownloadManager did.
 */
class ParallelDownloader(
    private val app: Context,
    private val http: OkHttpClient,
) {

    companion object {
        // Number of concurrent range fetches per file. 4 hits a good balance:
        // enough to saturate most residential downlinks, few enough that
        // panel-side per-IP limits (usually 4-8 conns) don't reject us.
        private const val PARALLEL_STREAMS = 4
        // Don't bother with parallelism for tiny files — connection setup
        // overhead dominates below ~4 MB.
        private const val PARALLEL_MIN_BYTES = 4L * 1024 * 1024
        // 512 KB read buffer — large enough that syscall overhead per read
        // is trivial, small enough to keep progress updates responsive.
        private const val READ_BUFFER_BYTES = 512 * 1024
        // How often to flush progress to the caller (throttled to avoid
        // churning Room + UI on every buffered read).
        private const val PROGRESS_TICK_BYTES = 1L * 1024 * 1024 // 1 MB
    }

    data class Handle(val cancel: () -> Unit)

    interface Target {
        val displayPath: String
        suspend fun openWrite(sizeBytes: Long): Writer
        data class FileTarget(val file: File) : Target {
            override val displayPath: String get() = file.absolutePath
            override suspend fun openWrite(sizeBytes: Long): Writer {
                file.parentFile?.mkdirs()
                return FileWriter(file, sizeBytes)
            }
        }
        data class SafTarget(val ctx: Context, val doc: DocumentFile) : Target {
            override val displayPath: String get() = doc.uri.toString()
            override suspend fun openWrite(sizeBytes: Long): Writer {
                val os = ctx.contentResolver.openOutputStream(doc.uri, "w")
                    ?: throw java.io.IOException("Could not open SAF output stream")
                return StreamWriter(os)
            }
        }
    }

    interface Writer {
        /** Whether this writer supports positional writes; drives parallel vs. sequential path. */
        val supportsParallel: Boolean
        fun writeAt(offset: Long, buf: ByteArray, len: Int)
        fun writeSequential(buf: ByteArray, len: Int)
        fun close()
    }

    private class FileWriter(private val file: File, sizeBytes: Long) : Writer {
        private val raf = RandomAccessFile(file, "rw").apply { if (sizeBytes > 0) setLength(sizeBytes) }
        private val lock = Any()
        override val supportsParallel: Boolean = true
        override fun writeAt(offset: Long, buf: ByteArray, len: Int) {
            synchronized(lock) { raf.seek(offset); raf.write(buf, 0, len) }
        }
        override fun writeSequential(buf: ByteArray, len: Int) {
            synchronized(lock) { raf.write(buf, 0, len) }
        }
        override fun close() { try { raf.close() } catch (_: Throwable) {} }
    }

    private class StreamWriter(private val os: OutputStream) : Writer {
        override val supportsParallel: Boolean = false
        override fun writeAt(offset: Long, buf: ByteArray, len: Int) =
            writeSequential(buf, len) // SAF has no positional write; caller must not use parallel path.
        override fun writeSequential(buf: ByteArray, len: Int) { os.write(buf, 0, len) }
        override fun close() { try { os.close() } catch (_: Throwable) {} }
    }

    /**
     * Kicks off a download. Runs on Dispatchers.IO — call from a coroutine
     * scope you're happy to have hold the job. Returns a [Handle] so the
     * caller can cancel mid-flight (e.g. user taps ✕ on the downloads list).
     */
    fun start(
        url: String,
        target: Target,
        userAgent: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onDone: (path: String) -> Unit,
        onError: (message: String) -> Unit,
    ): Handle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch {
            try {
                val probe = probe(url, userAgent)
                val writer = target.openWrite(probe.contentLength)
                try {
                    val useParallel = writer.supportsParallel
                        && probe.acceptsRanges
                        && probe.contentLength >= PARALLEL_MIN_BYTES
                    if (useParallel) {
                        downloadParallel(url, userAgent, writer, probe.contentLength, onProgress)
                    } else {
                        downloadSingle(url, userAgent, writer, probe.contentLength, onProgress)
                    }
                    onDone(target.displayPath)
                } finally {
                    writer.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // caller cancelled — don't report as error
                throw e
            } catch (t: Throwable) {
                onError(t.message ?: "download failed")
            }
        }
        return Handle(cancel = { scope.cancel() })
    }

    private data class Probe(val contentLength: Long, val acceptsRanges: Boolean)

    private suspend fun probe(url: String, ua: String): Probe = withContext(Dispatchers.IO) {
        // Ranged GET of 0-0 works on servers that refuse bare HEADs (common
        // on Xtream). Response length header still reflects the whole file.
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        http.newCall(req).execute().use { r ->
            val contentRange = r.header("Content-Range").orEmpty()
            val acceptsRanges = r.code == 206 || r.header("Accept-Ranges")?.contains("bytes", true) == true
            val total = when {
                contentRange.contains('/') -> contentRange.substringAfterLast('/').trim().toLongOrNull() ?: -1L
                else -> r.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            Probe(contentLength = total.coerceAtLeast(0), acceptsRanges = acceptsRanges)
        }
    }

    private suspend fun downloadParallel(
        url: String, ua: String, writer: Writer, total: Long,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val chunkSize = max(total / PARALLEL_STREAMS, 1L)
        val progress = java.util.concurrent.atomic.AtomicLong(0L)
        val nextTick = java.util.concurrent.atomic.AtomicLong(PROGRESS_TICK_BYTES)
        val jobs: List<Deferred<Unit>> = (0 until PARALLEL_STREAMS).map { i ->
            val start = i * chunkSize
            val end = if (i == PARALLEL_STREAMS - 1) total - 1 else (start + chunkSize - 1)
            async {
                fetchRange(url, ua, start, end, writer) { bytes ->
                    val n = progress.addAndGet(bytes.toLong())
                    // Throttled progress tick so we don't hammer Room / UI.
                    if (n >= nextTick.get()) {
                        nextTick.set(n + PROGRESS_TICK_BYTES)
                        onProgress(n, total)
                    }
                }
            }
        }
        jobs.awaitAll()
        onProgress(total, total) // final 100 %
    }

    private fun fetchRange(
        url: String, ua: String, from: Long, to: Long,
        writer: Writer, onRead: (Int) -> Unit,
    ) {
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Range", "bytes=$from-$to")
            .get()
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} on range $from-$to")
            val input = r.body?.byteStream() ?: throw java.io.IOException("empty body")
            val buf = ByteArray(READ_BUFFER_BYTES)
            var offset = from
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                writer.writeAt(offset, buf, n)
                offset += n
                onRead(n)
            }
        }
    }

    private suspend fun downloadSingle(
        url: String, ua: String, writer: Writer, total: Long,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .get()
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val effectiveTotal = if (total > 0) total else (r.header("Content-Length")?.toLongOrNull() ?: 0L)
            val input = r.body?.byteStream() ?: throw java.io.IOException("empty body")
            val buf = ByteArray(READ_BUFFER_BYTES)
            var received = 0L
            var nextTick = PROGRESS_TICK_BYTES
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                writer.writeSequential(buf, n)
                received += n
                if (received >= nextTick) {
                    nextTick = received + PROGRESS_TICK_BYTES
                    onProgress(received, effectiveTotal)
                }
            }
            onProgress(received, effectiveTotal)
        }
    }
}
