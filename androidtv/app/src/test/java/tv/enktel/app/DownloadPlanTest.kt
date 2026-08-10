package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.download.DownloadPlan

/**
 * "The download starts off strong and slowly begins to lose momentum."
 *
 * Workers pull chunks from a shared queue, so the transfer runs at full width
 * until the queue empties — and then decays, because workers finish at
 * different times and retire one by one until a single connection is carrying
 * whatever is left of the last chunk on its own.
 *
 * The size of that last chunk is the whole story, and it used to grow without
 * limit: the plan capped the chunk *count* at eight per worker and derived the
 * size from it, so a 4 GB film got 128 MB chunks and a 10 GB one got 320 MB —
 * against a stated target of 16 MB that only ever held below 512 MB.
 *
 * These pin the property that matters: however big the file, the last worker
 * standing is left with a few megabytes.
 */
class DownloadPlanTest {

    private val MB = 1024L * 1024
    private val GB = 1024L * MB

    private fun plan(total: Long, streams: Int = 4) = DownloadPlan.segments(total, streams)

    // ── the fault ──────────────────────────────────────────────────────

    @Test
    fun `the last chunk of a big file is small`() {
        // The old plan produced 128 MB here — minutes of one connection alone.
        for (size in listOf(1 * GB, 4 * GB, 10 * GB, 40 * GB)) {
            val last = plan(size).last()
            assertTrue(
                "a $size byte file ends on a ${last.length} byte chunk",
                last.length <= DownloadPlan.TAIL_CHUNK_BYTES,
            )
        }
    }

    @Test
    fun `the closing stretch is all small chunks, not just the final one`() {
        // One small chunk at the end would not help: four workers finish at
        // four different times, so there has to be enough small work left for
        // the queue to even them out.
        val tail = plan(4 * GB).takeLast(DownloadPlan.TAIL_CHUNKS_PER_STREAM * 4)
        assertTrue(
            "tail sizes: ${tail.map { it.length }}",
            tail.all { it.length <= DownloadPlan.TAIL_CHUNK_BYTES },
        )
    }

    // ── the constraint that made the fault ─────────────────────────────

    @Test
    fun `the plan stays short enough to serialise on every write`() {
        // The plan is joined into the resume record and stored as the download
        // runs, so its length is a cost paid continuously.
        for (size in listOf(100 * MB, 4 * GB, 40 * GB, 200 * GB)) {
            assertTrue(
                "a $size byte file planned into ${plan(size).size} chunks",
                plan(size).size <= DownloadPlan.MAX_CHUNKS,
            )
        }
    }

    // ── it still has to be a correct plan ──────────────────────────────

    @Test
    fun `the chunks cover the file exactly once`() {
        for (size in listOf(9 * MB, 100 * MB, 1 * GB, 4 * GB, 37 * GB, 12345678901L)) {
            val chunks = plan(size)
            assertEquals("$size starts at 0", 0L, chunks.first().start)
            assertEquals("$size ends at the last byte", size - 1, chunks.last().end)
            chunks.zipWithNext { a, b ->
                assertEquals("$size has a gap or overlap at ${a.end}", a.end + 1, b.start)
            }
            assertEquals("$size total", size, chunks.sumOf { it.length })
            assertTrue("$size has an empty chunk", chunks.all { it.length > 0 })
        }
    }

    @Test
    fun `every worker has something to do from the start`() {
        for (streams in 1..8) {
            for (size in listOf(9 * MB, 50 * MB, 4 * GB)) {
                assertTrue(
                    "$size across $streams streams gave ${plan(size, streams).size} chunks",
                    plan(size, streams).size >= streams,
                )
            }
        }
    }

    @Test
    fun `nothing to fetch plans nothing`() {
        assertTrue(DownloadPlan.segments(0, 4).isEmpty())
        assertTrue(DownloadPlan.segments(-1, 4).isEmpty())
    }

    @Test
    fun `a file smaller than one tail chunk is still planned`() {
        val chunks = DownloadPlan.segments(1024, 4)
        assertEquals(1024L, chunks.sumOf { it.length })
        assertEquals(0L, chunks.first().start)
        assertEquals(1023L, chunks.last().end)
    }

    @Test
    fun `body chunks are near the target rather than enormous`() {
        // The number the old plan claimed to aim for and did not honour above
        // half a gigabyte. Allowed to grow on a very large file, because the
        // ceiling on the plan length has to win somewhere — but the tail is
        // what protects the end of the download, not this.
        val body = plan(4 * GB).dropLast(DownloadPlan.TAIL_CHUNKS_PER_STREAM * 4)
        val biggest = body.maxOf { it.length }
        assertTrue(
            "biggest body chunk was $biggest",
            biggest <= 4 * DownloadPlan.TARGET_CHUNK_BYTES,
        )
    }
}
