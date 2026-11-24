package tv.enktel.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Multi-connection byte-range fetcher for large progressive VOD files.
 *
 * ### Why
 *
 * Media3 reads a progressive file over one HTTP connection. Many Xtream panels
 * and their CDNs cap bandwidth *per connection* (5–10 Mbit/s is common), so a
 * 40 Mbit/s 4K MP4 stalls on a 500 Mbit/s line. Download managers and Kodi's
 * `curl` input work around the same cap by splitting the file into byte
 * ranges and fetching several at once.
 *
 * ### How
 *
 * The first request asks for one chunk (`Range: bytes=pos-pos+chunk-1`). If the
 * server answers `206` with a total length in `Content-Range` and the file is
 * large enough to be worth it, the source switches to parallel mode: up to
 * [connections] chunk requests are kept in flight on a small thread pool, and
 * [read] drains them strictly in order, so the extractor still sees one
 * contiguous byte stream. Each chunk is held in memory only until it has been
 * read, so the peak cost is `connections × chunkBytes`.
 *
 * Anything else — a `200` that ignored the range, no total length, a small
 * file (playlists, HLS segments, subtitles) — is streamed straight from the
 * first response exactly as a plain single-connection source would, so the
 * worst case is the behaviour the app had before.
 *
 * Built on the app's shared [OkHttpClient], so connection pooling, HTTP/2,
 * the VLC user agent, TLS fallbacks and the gateway-failover interceptor all
 * apply to every chunk.
 */
@UnstableApi
class ParallelRangeDataSource private constructor(
    private val client: OkHttpClient,
    private val userAgent: () -> String,
    private val connections: Int,
    private val chunkBytes: Int,
    private val minParallelBytes: Long,
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val client: OkHttpClient,
        private val userAgent: () -> String,
        private val connections: Int = DEFAULT_CONNECTIONS,
        private val chunkBytes: Int = DEFAULT_CHUNK_BYTES,
        private val minParallelBytes: Long = DEFAULT_MIN_PARALLEL_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ParallelRangeDataSource(client, userAgent, connections.coerceIn(1, 8), chunkBytes, minParallelBytes)
    }

    private var dataSpec: DataSpec? = null
    private var uri: Uri? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    // Single-connection mode.
    private var single: Response? = null
    private var singleStream: InputStream? = null

    // Parallel mode.
    private var pool: ExecutorService? = null
    private val inFlight = ArrayDeque<Future<ByteArray>>()
    private val calls = java.util.concurrent.ConcurrentLinkedQueue<Call>()
    private var current: ByteArray? = null
    private var currentOffset = 0
    private var nextChunkStart = 0L
    private var endExclusive = 0L

    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.uri = dataSpec.uri
        transferInitializing(dataSpec)

        val start = dataSpec.position
        val firstEnd = start + chunkBytes - 1
        val requestedEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) start + dataSpec.length - 1 else Long.MAX_VALUE
        val first = execute(dataSpec, start, minOf(firstEnd, requestedEnd))
        responseHeaders = first.headers.toMultimap()

        val total = if (first.code == 206) parseTotal(first.header("Content-Range")) else -1L
        if (first.code == 206 && total > 0 && total - start >= minParallelBytes) {
            // Parallel mode. The first response is chunk 0.
            endExclusive = if (requestedEnd == Long.MAX_VALUE) total else minOf(total, requestedEnd + 1)
            bytesRemaining = endExclusive - start
            current = first.body.use { it.bytes() }
            currentOffset = 0
            nextChunkStart = start + (current?.size ?: 0)
            pool = Executors.newFixedThreadPool(connections) { r ->
                Thread(r, "enktel-range").apply { isDaemon = true }
            }
            fillPipeline(dataSpec)
        } else {
            // Single-connection mode: whatever the server gave us, streamed.
            single = first
            val body = first.body
            val stream = body.byteStream()
            if (first.code == 200 && start > 0) {
                // The server ignored the range. Skip to where we were asked
                // to start rather than handing the extractor the wrong bytes.
                var toSkip = start
                while (toSkip > 0) {
                    val skipped = stream.skip(toSkip)
                    if (skipped <= 0) throw HttpDataSource.HttpDataSourceException(
                        dataSpec, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                        HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                    )
                    toSkip -= skipped
                }
            }
            singleStream = stream
            val len = body.contentLength()
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                first.code == 206 && total > 0 -> total - start
                len >= 0 -> len - (if (first.code == 200) start else 0)
                else -> C.LENGTH_UNSET.toLong()
            }
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun fillPipeline(spec: DataSpec) {
        val p = pool ?: return
        while (inFlight.size < connections && nextChunkStart < endExclusive) {
            val s = nextChunkStart
            val e = minOf(nextChunkStart + chunkBytes, endExclusive) - 1
            nextChunkStart = e + 1
            inFlight.addLast(p.submit(Callable { fetchChunk(spec, s, e) }))
        }
    }

    private fun fetchChunk(spec: DataSpec, start: Long, end: Long): ByteArray {
        var attempt = 0
        while (true) {
            try {
                execute(spec, start, end).use { r ->
                    if (r.code != 206) throw IOException("HTTP ${r.code} for range $start-$end")
                    val bytes = r.body.bytes()
                    if (bytes.size.toLong() != end - start + 1) throw IOException("short range read")
                    return bytes
                }
            } catch (e: IOException) {
                if (++attempt >= 3) throw e
                Thread.sleep(250L * attempt)
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        singleStream?.let { s ->
            val want = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
            val n = s.read(buffer, offset, want)
            if (n == -1) return C.RESULT_END_OF_INPUT
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
            bytesTransferred(n)
            return n
        }
        var chunk = current
        if (chunk == null || currentOffset >= chunk.size) {
            val next = inFlight.pollFirst() ?: return C.RESULT_END_OF_INPUT
            chunk = try {
                next.get(READ_TIMEOUT_S, TimeUnit.SECONDS)
            } catch (e: Exception) {
                val cause = (e.cause as? IOException) ?: IOException(e)
                throw HttpDataSource.HttpDataSourceException(
                    cause, dataSpec!!, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSource.HttpDataSourceException.TYPE_READ,
                )
            }
            current = chunk
            currentOffset = 0
            dataSpec?.let { fillPipeline(it) }
        }
        val n = minOf(length, chunk!!.size - currentOffset)
        System.arraycopy(chunk, currentOffset, buffer, offset, n)
        currentOffset += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun close() {
        calls.forEach { it.cancel() }
        calls.clear()
        inFlight.forEach { it.cancel(true) }
        inFlight.clear()
        pool?.shutdownNow()
        pool = null
        current = null
        runCatching { singleStream?.close() }
        runCatching { single?.close() }
        singleStream = null
        single = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun execute(spec: DataSpec, start: Long, end: Long): Response {
        val range = if (end == Long.MAX_VALUE) "bytes=$start-" else "bytes=$start-$end"
        val req = Request.Builder()
            .url(spec.uri.toString())
            .header("User-Agent", userAgent())
            .header("Connection", "keep-alive")
            .header("Range", range)
            .apply { spec.httpRequestHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        val call = client.newCall(req)
        calls.add(call)
        val resp = try {
            call.execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e, spec, HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        } finally {
            calls.remove(call)
        }
        if (resp.code !in 200..299) {
            val headers = resp.headers.toMultimap()
            val code = resp.code
            val msg = resp.message
            resp.close()
            throw HttpDataSource.InvalidResponseCodeException(code, msg, null, headers, spec, ByteArray(0))
        }
        return resp
    }

    companion object {
        const val DEFAULT_CONNECTIONS = 4
        const val DEFAULT_CHUNK_BYTES = 4 * 1024 * 1024
        /** Below this, parallelism is overhead: playlists, segments, subtitles. */
        const val DEFAULT_MIN_PARALLEL_BYTES = 32L * 1024 * 1024
        private const val READ_TIMEOUT_S = 30L

        /** `bytes 0-1023/146515` → 146515; `*` or malformed → -1. */
        internal fun parseTotal(contentRange: String?): Long =
            contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull() ?: -1L
    }
}
