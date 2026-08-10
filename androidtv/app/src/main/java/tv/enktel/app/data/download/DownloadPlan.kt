package tv.enktel.app.data.download

import kotlin.math.max
import kotlin.math.min

/**
 * How a file is cut up for a parallel download.
 *
 * Split out of [ParallelDownloader] because this is arithmetic, it decides how
 * fast a download finishes, and it was wrong in a way nothing could catch — no
 * exception, no error, just a transfer that started at full speed and got
 * slower the longer it ran.
 *
 * ### The tail
 *
 * Workers pull chunks from a shared queue, so while there are chunks left
 * everybody is busy and the download runs at full width. The end is the
 * problem: once the last chunks are handed out, workers finish and retire one
 * by one, and the transfer winds down through three quarters, then half, then
 * a single connection carrying the remainder on its own. The download does not
 * slow down all at once — it decays, which is exactly how it was described.
 *
 * How long that decay lasts is set by one number: the size of the last chunks.
 *
 * The previous plan capped the *count* at eight chunks per worker and then
 * derived the size from it, which meant the size grew without limit:
 *
 * | File   | Chunks | Size of each | Worst tail on one connection |
 * |:-------|-------:|-------------:|-----------------------------:|
 * | 512 MB |     32 |        16 MB |                        16 MB |
 * | 4 GB   |     32 |       128 MB |                       128 MB |
 * | 10 GB  |     32 |       320 MB |                       320 MB |
 *
 * The 16 MB target the constant claimed to aim for was therefore honoured only
 * below 512 MB — which is smaller than any film anyone downloads. On a 4 GB
 * film the last connection was alone with 128 MB, minutes of it, at a quarter
 * of the speed the same line had managed all the way up to that point.
 *
 * ### What this does instead
 *
 * Chunks are cut large at the front and small at the back. The body moves in
 * big pieces, which keeps the count — and therefore the resume record written
 * to the database as the download runs — bounded. The final stretch is cut
 * into small pieces, so however unevenly the workers finish, the one left
 * holding the last chunk is holding a few megabytes rather than a few hundred.
 *
 * Same 4 GB film: a body of ~38 MB chunks and a tail of 24 × 4 MB, so the
 * worst case at the end is one connection with 4 MB left — about a second,
 * instead of minutes.
 */
object DownloadPlan {

    /** Preferred size of a body chunk. Big enough that per-request overhead is
     *  irrelevant, small enough that a mid-transfer reset costs little. */
    const val TARGET_CHUNK_BYTES = 16L * 1024 * 1024

    /** Size of a chunk in the closing stretch. This is the number that decides
     *  how long the tail lasts, because it is what the last worker still
     *  standing has left to do. */
    const val TAIL_CHUNK_BYTES = 4L * 1024 * 1024

    /** Small chunks per worker at the end. Six gives the queue enough left to
     *  re-balance across a fourfold spread in worker speed without making the
     *  whole plan long. */
    const val TAIL_CHUNKS_PER_STREAM = 6

    /**
     * Ceiling on chunks in one plan.
     *
     * The plan is serialised into the resume record on every progress write, so
     * its length is a cost paid continuously for the whole download, not once.
     * At roughly 35 characters a chunk this is about 4.5 KB, which is cheap to
     * build and cheap to store.
     */
    const val MAX_CHUNKS = 128

    /** One piece of work: an inclusive byte range. */
    data class Chunk(val start: Long, val end: Long) {
        val length: Long get() = end - start + 1
    }

    /**
     * Cut [total] bytes into work for [streams] parallel connections.
     *
     * Empty when there is nothing to fetch. Otherwise always at least
     * [streams] chunks, so no worker is idle from the very beginning.
     */
    fun segments(total: Long, streams: Int): List<Chunk> {
        if (total <= 0) return emptyList()
        val n = max(streams, 1)

        // Reserve the closing stretch, but never more of the file than there
        // is: a small file is all tail, which is the right answer for it.
        val wantedTail = n.toLong() * TAIL_CHUNKS_PER_STREAM
        val tailCount = min(wantedTail, total / TAIL_CHUNK_BYTES).toInt().coerceAtLeast(0)
        val tailSpan = tailCount * TAIL_CHUNK_BYTES
        val bodySpan = total - tailSpan

        val bounds = ArrayList<Chunk>(MAX_CHUNKS)
        if (bodySpan > 0) {
            // Body chunks are TARGET_CHUNK_BYTES until that would need more
            // chunks than the plan is allowed, at which point they grow. A
            // bigger body chunk costs nothing at the end — the tail is what the
            // last worker is left with.
            val budget = max(MAX_CHUNKS - tailCount, 1)
            val wanted = ceilDiv(bodySpan, TARGET_CHUNK_BYTES)
            val count = wanted.coerceIn(1L, budget.toLong()).toInt()
            val size = ceilDiv(bodySpan, count.toLong())
            var at = 0L
            while (at < bodySpan) {
                val end = min(at + size, bodySpan) - 1
                bounds += Chunk(at, end)
                at = end + 1
            }
        }
        var at = bodySpan.coerceAtLeast(0)
        while (at < total) {
            val end = min(at + TAIL_CHUNK_BYTES, total) - 1
            bounds += Chunk(at, end)
            at = end + 1
        }

        // A file too small to have been divided this way still has to keep
        // every worker busy, or the parallelism it was granted is fiction.
        if (bounds.size < n) return even(total, n)
        return bounds
    }

    private fun even(total: Long, n: Int): List<Chunk> {
        val size = max(ceilDiv(total, n.toLong()), 1L)
        val out = ArrayList<Chunk>(n)
        var at = 0L
        while (at < total) {
            val end = min(at + size, total) - 1
            out += Chunk(at, end)
            at = end + 1
        }
        return out
    }

    private fun ceilDiv(a: Long, b: Long): Long = if (b <= 0) a else (a + b - 1) / b
}
