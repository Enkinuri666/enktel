package tv.enktel.app.data.download

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Custom OkHttp-based downloader tuned for throughput *and* survivability on
 * IPTV panels, which routinely reset long-lived connections mid-transfer.
 *
 * Strategy per download:
 *   1. Ranged probe (`Range: bytes=0-0`) → learn Content-Length + Accept-Ranges.
 *   2. If ranges are supported AND size ≥ [PARALLEL_MIN_BYTES]: split into up
 *      to [MAX_STREAMS] concurrent ranged GETs, each writing straight into its
 *      own offset of a pre-allocated file.
 *   3. Otherwise: single-stream download.
 *
 * ### Why this file was rewritten (v1.34.0)
 *
 * The first cut worked on a good day and fell over on a bad one. Three
 * concrete defects, all reported as "downloads are slow" or
 * "connection stopped by software side":
 *
 *  - **No retries.** A single `SocketException: Software caused connection
 *    abort` — which an overloaded panel throws routinely, often minutes into
 *    a transfer — failed the whole download and threw away every byte. Each
 *    range worker now retries with exponential backoff, restarting at the
 *    exact byte it stopped on rather than at the start of its segment.
 *  - **Serialised writes.** Every 512 KB block took a global lock and did
 *    `RandomAccessFile.seek()` + `write()`, so the four "parallel" streams
 *    took turns and thrashed the disk head. Writes now go through
 *    [FileChannel.write] with an explicit position, which is safe for
 *    concurrent use and needs no lock at all.
 *  - **Shared HTTP client.** Downloads ran on the app-wide OkHttp client,
 *    whose dispatcher allows only 5 concurrent requests per host. Four range
 *    workers plus ordinary catalogue/EPG traffic meant range GETs sat queued
 *    behind other calls. Downloads now get a dedicated dispatcher and
 *    connection pool (sharing the parent's socket factory and interceptors).
 *
 * Repeated aborts also shrink concurrency on the fly ([shrinkConcurrency]),
 * because a panel that keeps hanging up is usually enforcing a per-IP
 * connection cap — backing off to two streams, then one, gets the file down
 * where hammering it with four never would.
 *
 * Targets both a plain File (app-scoped external storage, USB, legacy public
 * folder) and a SAF DocumentFile (user-picked folder). File targets support
 * positional writes and therefore full parallelism + mid-file resume; SAF
 * targets stream sequentially (the SAF OutputStream API has no positional
 * write) and resume by appending.
 */
class ParallelDownloader(
    private val app: Context,
    baseHttp: OkHttpClient,
) {

    companion object {
        /** Upper bound on concurrent range fetches per file. Four saturates
         *  most residential downlinks while staying under the 4-8 per-IP
         *  connection caps typical of Xtream panels. */
        private const val MAX_STREAMS = 4
        /** Below this, connection setup costs more than parallelism saves. */
        private const val PARALLEL_MIN_BYTES = 8L * 1024 * 1024
        /** 256 KB read buffer: syscall overhead is already negligible here and
         *  smaller blocks keep progress (and pause latency) responsive. */
        private const val READ_BUFFER_BYTES = 256 * 1024
        /** Flush progress to the caller at most this often, by bytes… */
        private const val PROGRESS_TICK_BYTES = 1L * 1024 * 1024
        /** …and at most this often, by wall clock. */
        private const val PROGRESS_TICK_MS = 900L
        /** Attempts per range worker before the download is declared failed. */
        private const val MAX_SEGMENT_ATTEMPTS = 6
        /** Consecutive aborts that trigger dropping one concurrent stream. */
        private const val ABORTS_BEFORE_SHRINK = 3
    }

    /**
     * Dedicated client for download traffic. Derived from the app-wide client
     * so it inherits the UA + stream-health interceptors and proxy policy, but
     * with its own dispatcher and pool so range workers never queue behind
     * catalogue, EPG or artwork requests.
     */
    private val http: OkHttpClient = baseHttp.newBuilder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 8
            }
        )
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        // Deliberately shorter than the app-wide 180 s: a stalled range worker
        // should be recycled fast and retried from its last byte, not left
        // hanging for three minutes while the other workers finish.
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Controls for an in-flight download.
     *  - [cancel] tears everything down and abandons the partial file.
     *  - [pause] stops the workers *cleanly*, flushes the byte offsets each one
     *    reached, and fires `onPaused` so the caller can persist them.
     */
    data class Handle(val cancel: () -> Unit, val pause: () -> Unit)

    /** One contiguous byte range and how far into it we've got. */
    private class Segment(val start: Long, val end: Long, @Volatile var done: Long) {
        /** Absolute offset of the next byte to fetch. */
        val cursor: Long get() = start + done
        val length: Long get() = end - start + 1
        val complete: Boolean get() = done >= length
    }

    interface Target {
        val displayPath: String
        /**
         * @param sizeBytes total file size (0 when unknown)
         * @param appendFrom for sequential (SAF) targets, the byte offset to
         *   continue writing at. Positional targets ignore it.
         * @return null when the target can't honour [appendFrom] (caller then
         *   restarts the download from zero).
         */
        suspend fun openWrite(sizeBytes: Long, appendFrom: Long): Writer?
        /** Bytes already on disk, for validating a resume. */
        fun existingLength(): Long

        data class FileTarget(val file: File) : Target {
            override val displayPath: String get() = file.absolutePath
            override fun existingLength(): Long = if (file.exists()) file.length() else 0L
            override suspend fun openWrite(sizeBytes: Long, appendFrom: Long): Writer {
                file.parentFile?.mkdirs()
                return FileWriter(file, sizeBytes)
            }
        }

        data class SafTarget(val ctx: Context, val doc: DocumentFile) : Target {
            override val displayPath: String get() = doc.uri.toString()
            override fun existingLength(): Long = try { doc.length() } catch (_: Throwable) { 0L }
            override suspend fun openWrite(sizeBytes: Long, appendFrom: Long): Writer? {
                // "wa" = write-append. Most DocumentsProviders support it; the
                // ones that don't throw, and returning null tells the caller to
                // fall back to a clean restart from byte 0.
                val appending = appendFrom > 0
                val os = try {
                    ctx.contentResolver.openOutputStream(doc.uri, if (appending) "wa" else "w")
                } catch (_: Throwable) { null }
                if (os == null) {
                    if (appending) return null
                    throw java.io.IOException("Could not open SAF output stream")
                }
                return StreamWriter(os)
            }
        }
    }

    interface Writer {
        /** Whether positional writes are supported (drives parallel vs. sequential). */
        val supportsParallel: Boolean
        fun writeAt(offset: Long, buf: ByteArray, len: Int)
        fun writeSequential(buf: ByteArray, len: Int)
        fun close()
    }

    /**
     * Positional writer backed by a [FileChannel]. `write(ByteBuffer, position)`
     * does not mutate the channel's shared file position, so concurrent range
     * workers can write different offsets simultaneously without a lock — the
     * single biggest throughput win over the previous seek-under-lock writer.
     */
    private class FileWriter(file: File, sizeBytes: Long) : Writer {
        private val raf = RandomAccessFile(file, "rw").apply {
            // Pre-allocate so the filesystem can lay the file out contiguously
            // and later writes never have to extend it.
            if (sizeBytes > 0 && length() != sizeBytes) setLength(sizeBytes)
        }
        private val channel: FileChannel = raf.channel
        private var seqPos = 0L
        override val supportsParallel: Boolean = true
        override fun writeAt(offset: Long, buf: ByteArray, len: Int) {
            val bb = ByteBuffer.wrap(buf, 0, len)
            var pos = offset
            while (bb.hasRemaining()) pos += channel.write(bb, pos)
        }
        override fun writeSequential(buf: ByteArray, len: Int) {
            writeAt(seqPos, buf, len)
            seqPos += len
        }
        override fun close() {
            try { channel.force(false) } catch (_: Throwable) {}
            try { raf.close() } catch (_: Throwable) {}
        }
    }

    private class StreamWriter(private val os: OutputStream) : Writer {
        override val supportsParallel: Boolean = false
        override fun writeAt(offset: Long, buf: ByteArray, len: Int) = writeSequential(buf, len)
        override fun writeSequential(buf: ByteArray, len: Int) { os.write(buf, 0, len) }
        override fun close() {
            try { os.flush() } catch (_: Throwable) {}
            try { os.close() } catch (_: Throwable) {}
        }
    }

    // ---- resume-state codec ------------------------------------------------
    // Format: "<total>|<start>-<end>-<done>;<start>-<end>-<done>;…"
    // Deliberately a flat string rather than JSON so it drops straight into a
    // Room TEXT column with no type converter.

    private fun encodeState(total: Long, segments: List<Segment>): String =
        buildString {
            append(total).append('|')
            segments.forEachIndexed { i, s ->
                if (i > 0) append(';')
                append(s.start).append('-').append(s.end).append('-').append(s.done)
            }
        }

    private fun decodeState(encoded: String, expectedTotal: Long): List<Segment>? {
        if (encoded.isBlank()) return null
        return try {
            val (totalPart, segPart) = encoded.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val total = totalPart.toLongOrNull() ?: return null
            // A different size means the remote file changed under us — the
            // saved offsets are meaningless, so start over.
            if (expectedTotal > 0 && total != expectedTotal) return null
            val segs = segPart.split(';').filter { it.isNotBlank() }.map { chunk ->
                val parts = chunk.split('-')
                if (parts.size != 3) return null
                Segment(
                    start = parts[0].toLongOrNull() ?: return null,
                    end = parts[1].toLongOrNull() ?: return null,
                    done = parts[2].toLongOrNull() ?: return null,
                )
            }
            segs.ifEmpty { null }
        } catch (_: Throwable) { null }
    }

    /**
     * Kicks off (or resumes) a download.
     *
     * @param resumeState blob previously handed to `onProgress` / `onPaused`.
     *   Blank starts fresh. Silently ignored if it no longer matches the file
     *   on the server or on disk.
     */
    fun start(
        url: String,
        target: Target,
        userAgent: String,
        resumeState: String = "",
        onProgress: (downloaded: Long, total: Long, state: String) -> Unit,
        onDone: (path: String) -> Unit,
        onError: (message: String) -> Unit,
        onPaused: (downloaded: Long, total: Long, state: String) -> Unit = { _, _, _ -> },
    ): Handle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pauseFlag = PauseFlag()

        scope.launch {
            var writer: Writer? = null
            var segments: List<Segment> = emptyList()
            var total = 0L
            try {
                val probe = probe(url, userAgent)
                total = probe.contentLength

                // Decide whether the saved offsets are still trustworthy: the
                // server must report the same size, and the bytes must still be
                // on disk. Either check failing just costs us a restart.
                val saved = decodeState(resumeState, total)
                val onDisk = target.existingLength()
                val resumable = saved != null &&
                    total > 0 &&
                    probe.acceptsRanges &&
                    onDisk >= (if (target is Target.FileTarget) total else saved.sumOf { it.done })

                segments = when {
                    resumable -> saved!!
                    total > 0 && probe.acceptsRanges && total >= PARALLEL_MIN_BYTES ->
                        planSegments(total, MAX_STREAMS)
                    else -> listOf(Segment(0, max(total - 1, 0), 0))
                }

                val appendFrom = if (resumable && target is Target.SafTarget) segments.sumOf { it.done } else 0L
                val sink = target.openWrite(total, appendFrom)
                    ?: run {
                        // SAF provider refused append — restart cleanly.
                        segments = listOf(Segment(0, max(total - 1, 0), 0))
                        target.openWrite(total, 0)
                    }
                    ?: throw java.io.IOException("Could not open the download target for writing")
                writer = sink

                val useParallel = sink.supportsParallel &&
                    probe.acceptsRanges &&
                    total >= PARALLEL_MIN_BYTES &&
                    segments.size > 1

                val ticker = ProgressTicker(total, segments, onProgress) { t, s -> encodeState(t, s) }

                if (useParallel) {
                    runParallel(url, userAgent, sink, segments, ticker, pauseFlag)
                } else {
                    runSingle(url, userAgent, sink, segments.first(), total, ticker, pauseFlag)
                }

                if (pauseFlag.paused) {
                    onPaused(segments.sumOf { it.done }, total, encodeState(total, segments))
                } else {
                    ticker.flush()
                    onProgress(total.coerceAtLeast(segments.sumOf { it.done }), total, "")
                    onDone(target.displayPath)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (pauseFlag.paused) {
                    // Racing pause vs. an in-flight socket error: treat it as a
                    // pause, since the bytes are safe and the user asked to stop.
                    onPaused(segments.sumOf { it.done }, total, encodeState(total, segments))
                } else {
                    onError(friendlyError(t))
                }
            } finally {
                writer?.close()
            }
        }

        return Handle(
            cancel = { scope.cancel() },
            pause = { pauseFlag.paused = true },
        )
    }

    /** Shared, mutable "stop cleanly" flag readable from every range worker. */
    private class PauseFlag { @Volatile var paused = false }

    /**
     * Throttled progress publisher. Range workers call [add] on every read;
     * the caller only hears about it once per [PROGRESS_TICK_BYTES] or
     * [PROGRESS_TICK_MS], whichever comes first, so Room and the UI aren't
     * churned hundreds of times a second.
     */
    private class ProgressTicker(
        private val total: Long,
        private val segments: List<Segment>,
        private val onProgress: (Long, Long, String) -> Unit,
        private val encode: (Long, List<Segment>) -> String,
    ) {
        private val downloaded = AtomicLong(segments.sumOf { it.done })
        private val nextTickBytes = AtomicLong(downloaded.get() + PROGRESS_TICK_BYTES)
        @Volatile private var lastTickMs = 0L

        fun add(bytes: Int) {
            val now = downloaded.addAndGet(bytes.toLong())
            val nowMs = System.currentTimeMillis()
            if (now >= nextTickBytes.get() || nowMs - lastTickMs >= PROGRESS_TICK_MS) {
                nextTickBytes.set(now + PROGRESS_TICK_BYTES)
                lastTickMs = nowMs
                onProgress(now, total, encode(total, segments))
            }
        }

        fun flush() = onProgress(downloaded.get(), total, encode(total, segments))
    }

    private data class Probe(val contentLength: Long, val acceptsRanges: Boolean)

    private suspend fun probe(url: String, ua: String): Probe = withContext(Dispatchers.IO) {
        // A ranged GET of byte 0 works on servers that refuse a bare HEAD
        // (common on Xtream). Content-Range still reports the whole size.
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Range", "bytes=0-0")
            .header("Accept-Encoding", "identity")
            .get()
            .build()
        http.newCall(req).execute().use { r ->
            val contentRange = r.header("Content-Range").orEmpty()
            val acceptsRanges = r.code == 206 ||
                r.header("Accept-Ranges")?.contains("bytes", true) == true
            val total = when {
                contentRange.contains('/') ->
                    contentRange.substringAfterLast('/').trim().toLongOrNull() ?: -1L
                else -> r.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            // Drain the single probe byte so the connection returns to the pool
            // clean; abandoning an unread body forces a socket teardown, which
            // some panels log (and then punish) as a client-side abort.
            try { r.body?.bytes() } catch (_: Throwable) {}
            Probe(contentLength = total.coerceAtLeast(0), acceptsRanges = acceptsRanges)
        }
    }

    private fun planSegments(total: Long, streams: Int): List<Segment> {
        val n = streams.coerceAtLeast(1)
        val chunk = max(total / n, 1L)
        return (0 until n).map { i ->
            val start = i * chunk
            val end = if (i == n - 1) total - 1 else min(start + chunk - 1, total - 1)
            Segment(start, end, 0)
        }.filter { it.start <= it.end }
    }

    private suspend fun runParallel(
        url: String, ua: String, writer: Writer, segments: List<Segment>,
        ticker: ProgressTicker, pause: PauseFlag,
    ) = withContext(Dispatchers.IO) {
        // Every worker holds a permit while its socket is open. Starts fully
        // open (one permit per segment) and ratchets down via [Throttle] as
        // the server hangs up on us, so a panel enforcing a per-IP connection
        // cap sees fewer sockets instead of the same four being retried.
        val throttle = Throttle(segments.size, this)
        val jobs: List<Deferred<Unit>> = segments.map { seg ->
            async {
                fetchSegment(url, ua, seg, writer, positional = true, ticker, pause, throttle)
            }
        }
        jobs.awaitAll()
    }

    private suspend fun runSingle(
        url: String, ua: String, writer: Writer, seg: Segment,
        total: Long, ticker: ProgressTicker, pause: PauseFlag,
    ) = withContext(Dispatchers.IO) {
        fetchSegment(
            url, ua, seg, writer,
            positional = writer.supportsParallel,
            ticker = ticker, pause = pause,
            throttle = Throttle(1, this),
            // A whole-file worker with an unknown length can't send a Range
            // header at all — a bare GET is the only option.
            rangeless = total <= 0,
        )
    }

    /**
     * Adaptive concurrency limiter. Workers must hold a permit to have a socket
     * open; [retireOne] permanently removes one so repeated aborts converge on
     * however many parallel connections the server is actually willing to serve.
     */
    private class Throttle(initialPermits: Int, private val scope: CoroutineScope) {
        private val semaphore = kotlinx.coroutines.sync.Semaphore(max(initialPermits, 1))
        private val live = AtomicInteger(max(initialPermits, 1))
        private val aborts = AtomicInteger(0)

        suspend fun <T> withSlot(block: suspend () -> T): T {
            semaphore.acquire()
            try { return block() } finally { semaphore.release() }
        }

        /** Record an abort; shrinks the stream count once they start piling up. */
        fun noteAbort() {
            if (aborts.incrementAndGet() < ABORTS_BEFORE_SHRINK) return
            aborts.set(0)
            retireOne()
        }

        fun noteSuccess() = aborts.set(0)

        /**
         * Swallow one permit for the rest of the download — never dropping
         * below a single stream. Acquired on a detached coroutine so the
         * worker that noticed the aborts isn't itself blocked waiting for a
         * sibling's socket to finish.
         */
        private fun retireOne() {
            while (true) {
                val cur = live.get()
                if (cur <= 1) return
                if (live.compareAndSet(cur, cur - 1)) {
                    scope.launch { semaphore.acquire() } // never released
                    return
                }
            }
        }
    }

    /**
     * Fetches one byte range, retrying on transient network failure and always
     * restarting at [Segment.cursor] — the byte after the last one actually
     * written — so a mid-transfer reset costs the retry, not the segment.
     */
    private suspend fun fetchSegment(
        url: String,
        ua: String,
        seg: Segment,
        writer: Writer,
        positional: Boolean,
        ticker: ProgressTicker,
        pause: PauseFlag,
        throttle: Throttle,
        rangeless: Boolean = false,
    ) {
        // A rangeless stream can't be resumed: the server would replay from
        // byte 0 and we'd write those bytes over the wrong offsets. One shot
        // only — the caller reports the failure and the user can retry, which
        // starts a clean transfer.
        val maxAttempts = if (rangeless) 1 else MAX_SEGMENT_ATTEMPTS
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            if (pause.paused) return
            if (seg.complete && !rangeless) return
            try {
                throttle.withSlot { fetchOnce(url, ua, seg, writer, positional, ticker, pause, rangeless) }
                throttle.noteSuccess()
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (pause.paused) return
                lastError = t
                attempt++
                // A panel that keeps hanging up is usually enforcing a per-IP
                // connection cap; competing for it harder makes the download
                // slower, not faster.
                if (isAbort(t)) throttle.noteAbort() else if (!isRetryable(t)) throw t
                if (attempt >= maxAttempts) break
                // 1s, 2s, 4s, 8s, 16s — long enough for a rate-limited panel to
                // forgive us, short enough that a blip barely registers.
                delay(1000L shl min(attempt - 1, 4))
            }
        }
        throw lastError ?: java.io.IOException("range ${seg.start}-${seg.end} failed")
    }

    /** One attempt at draining a segment's remaining bytes. */
    private fun fetchOnce(
        url: String, ua: String, seg: Segment, writer: Writer,
        positional: Boolean, ticker: ProgressTicker, pause: PauseFlag,
        rangeless: Boolean,
    ) {
        val builder = Request.Builder().url(url)
            .header("User-Agent", ua)
            // Ranges and transfer encodings interact badly on some reseller
            // relays: asking for identity keeps byte offsets meaning what we
            // think they mean.
            .header("Accept-Encoding", "identity")
            .get()
        if (!rangeless) builder.header("Range", "bytes=${seg.cursor}-${seg.end}")
        val req = builder.build()

        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                throw java.io.IOException("HTTP ${r.code} on bytes ${seg.cursor}-${seg.end}")
            }
            // A server that ignores Range and replays from byte 0 would corrupt
            // the file if we wrote its bytes at our cursor. Detect it and, if
            // we're already part-way in, treat it as fatal for this segment.
            if (!rangeless && seg.cursor > 0 && r.code != 206) {
                throw java.io.IOException("Server ignored the resume request (HTTP ${r.code})")
            }
            val input = r.body?.byteStream() ?: throw java.io.IOException("empty response body")
            val buf = ByteArray(READ_BUFFER_BYTES)
            while (true) {
                if (pause.paused) return
                val n = input.read(buf)
                if (n == -1) break
                if (positional) writer.writeAt(seg.cursor, buf, n) else writer.writeSequential(buf, n)
                seg.done += n
                ticker.add(n)
            }
        }
    }

    /** The family of "the other end hung up" errors worth retrying. */
    private fun isAbort(t: Throwable): Boolean {
        val msg = (t.message ?: "").lowercase()
        return t is SocketException ||
            "connection reset" in msg ||
            "connection abort" in msg ||
            "broken pipe" in msg ||
            "unexpected end of stream" in msg ||
            "stream was reset" in msg
    }

    private fun isRetryable(t: Throwable): Boolean =
        isAbort(t) ||
            t is SocketTimeoutException ||
            t is InterruptedIOException ||
            t is java.io.IOException

    /**
     * Turns the JVM's networking vocabulary into something a customer can act
     * on. "Software caused connection abort" in particular reads like the app
     * broke, when it means the server (or a middlebox) closed the socket.
     */
    private fun friendlyError(t: Throwable): String {
        val raw = t.message ?: t::class.java.simpleName
        val msg = raw.lowercase()
        return when {
            "connection abort" in msg || "connection reset" in msg || "broken pipe" in msg ->
                "The server closed the connection — press ↻ to resume, or switch Settings → Downloads → Engine to System (single-stream)"
            "unexpected end of stream" in msg ->
                "The server ended the transfer early — retry to resume from where it stopped"
            t is SocketTimeoutException || "timeout" in msg ->
                "Timed out waiting for the server — the panel may be overloaded"
            "http 403" in msg -> "Access denied (403) — the panel rejected this download"
            "http 404" in msg -> "File not found (404) on the panel"
            "http 5" in msg -> "The panel returned a server error — try again shortly"
            "enospc" in msg || "no space" in msg -> "Not enough free storage on the device"
            else -> raw
        }
    }
}
