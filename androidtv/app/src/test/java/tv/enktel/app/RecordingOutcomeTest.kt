package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.db.Recording
import tv.enktel.app.dvr.RecordingOutcome
import tv.enktel.app.dvr.RecordingOutcome.Refused

/**
 * What the Recordings list says about a recording once it is over.
 *
 * Three things were wrong, and each was the list telling the viewer something
 * untrue about a file on their own disk:
 *
 *  - A recording stopped by hand kept the size from its last progress tick —
 *    up to 8 MB short, and "64 KB" for anything under 8 MB — and an end time
 *    of zero, because the code that wrote the real figures ran in a coroutine
 *    that had just been cancelled and so could not touch the database.
 *  - A scheduled recording that Android refused to start stayed "Scheduled"
 *    for ever.
 *  - Progress was written by reading the row and writing the whole thing
 *    back, which dereferenced null when the row had been deleted meanwhile.
 *
 * The service is the part that cannot be unit-tested, so the decisions were
 * moved out of it and are pinned here.
 */
class RecordingOutcomeTest {

    private val now = 1_800_000_000_000L
    private fun row(endMs: Long = 0L) = Recording(
        id = 7, profileId = 1, title = "Dnevnik", channelName = "RTS 1",
        streamUrl = "http://example.invalid/1.ts", filePath = "/r/Dnevnik_7.ts",
        status = "RECORDING", startMs = now - 600_000, endMs = endMs, sizeBytes = 8L shl 20,
    )

    private val mb = 1L shl 20

    // ---- a recording that ended ----

    @Test
    fun `a stopped recording is done, at its real size, ending now`() {
        val out = RecordingOutcome.settle(row(), bytesOnDisk = 13 * mb + 4321, failed = false, nowMs = now)
        assertEquals("DONE", out.status)
        assertEquals("the size on disk, not the last progress tick", 13 * mb + 4321, out.sizeBytes)
        assertEquals("'record now' stores 0 for the end; a finished one has an end", now, out.endMs)
    }

    @Test
    fun `stopping after a few seconds is still done, not failed`() {
        // They pressed Stop. However little there is, it is what they asked for.
        val out = RecordingOutcome.settle(row(), bytesOnDisk = 300_000, failed = false, nowMs = now)
        assertEquals("DONE", out.status)
        assertEquals(300_000L, out.sizeBytes)
    }

    @Test
    fun `a stream that dropped late leaves a recording worth keeping`() {
        val out = RecordingOutcome.settle(row(), bytesOnDisk = 900 * mb, failed = true, nowMs = now)
        assertEquals("DONE", out.status)
        assertEquals(now, out.endMs)
    }

    @Test
    fun `a stream that never got going is a failure`() {
        val scheduledEnd = now + 3_600_000
        val out = RecordingOutcome.settle(row(endMs = scheduledEnd), bytesOnDisk = 2 * mb, failed = true, nowMs = now)
        assertEquals("FAILED", out.status)
        assertEquals("still reports what is on disk, so Delete can say what it frees", 2 * mb, out.sizeBytes)
        assertEquals("a failure keeps the end it was scheduled for", scheduledEnd, out.endMs)
    }

    @Test
    fun `the keep threshold is exclusive, as it always was`() {
        // The start-up clean-up in EnktelApp uses `bytes > 5 MB` for rows left
        // behind by a dead process. Two rules for the same question would
        // have the same file listed as Done or Failed depending on how the
        // recording happened to end.
        assertEquals("FAILED", RecordingOutcome.settle(row(), RecordingOutcome.KEEP_BYTES, true, now).status)
        assertEquals("DONE", RecordingOutcome.settle(row(), RecordingOutcome.KEEP_BYTES + 1, true, now).status)
        assertEquals(5L shl 20, RecordingOutcome.KEEP_BYTES)
    }

    @Test
    fun `settling changes nothing but status, size and end`() {
        val before = row()
        val out = RecordingOutcome.settle(before, 20 * mb, failed = false, nowMs = now)
        assertEquals(before.copy(status = out.status, sizeBytes = out.sizeBytes, endMs = out.endMs), out)
    }

    // ---- progress ----

    @Test
    fun `progress is written once per step, not once per read near a boundary`() {
        // The old test was `written % 8 MB < 64 KB`. Feed it a stream arriving
        // in 1400-byte pieces and count how often each rule fires over 32 MB.
        val piece = 1400L
        var written = 0L
        var reported = 0L
        var newRule = 0
        var oldRule = 0
        while (written < 32 * mb) {
            written += piece
            if (written % (8L shl 20) < 64 * 1024) oldRule++
            if (RecordingOutcome.progressDue(written, reported)) { reported = written; newRule++ }
        }
        assertEquals("one write per 8 MB", 4, newRule)
        assertTrue("the old rule fired $oldRule times for the same stream", oldRule > 150)
    }

    @Test
    fun `nothing is due before the first step`() {
        assertFalse(RecordingOutcome.progressDue(written = 64 * 1024, lastReported = 0))
        assertFalse(RecordingOutcome.progressDue(RecordingOutcome.PROGRESS_STEP - 1, 0))
        assertTrue(RecordingOutcome.progressDue(RecordingOutcome.PROGRESS_STEP, 0))
    }

    // ---- the countdown on a scheduled row ----

    @Test
    fun `the wait is said the way a person would say it`() {
        assertEquals("Starting…", RecordingOutcome.startsIn(0))
        assertEquals("Starting…", RecordingOutcome.startsIn(-5))
        assertEquals("Starts in 1 min", RecordingOutcome.startsIn(1))
        assertEquals("Starts in 89 min", RecordingOutcome.startsIn(89))
        assertEquals("Starts in 2 h", RecordingOutcome.startsIn(134))
        // Tomorrow evening's news used to be "Starts in 1559 min".
        assertEquals("Starts in 25 h", RecordingOutcome.startsIn(1559))
        assertEquals("Starts in 2 days", RecordingOutcome.startsIn(48 * 60))
        assertEquals("Starts in 6 days", RecordingOutcome.startsIn(6 * 24 * 60 + 300))
    }

    // ---- a start Android refused ----

    @Test
    fun `a refused start is retried while the programme is still on`() {
        assertEquals(Refused.RETRY, RecordingOutcome.afterRefusedStart(endMs = now + 60_000, nowMs = now))
    }

    @Test
    fun `a refused start is given up once the programme is over`() {
        assertEquals(Refused.GIVE_UP, RecordingOutcome.afterRefusedStart(endMs = now, nowMs = now))
        assertEquals(Refused.GIVE_UP, RecordingOutcome.afterRefusedStart(endMs = now - 1, nowMs = now))
    }

    @Test
    fun `an open-ended recording has no moment to give up at`() {
        assertEquals(Refused.RETRY, RecordingOutcome.afterRefusedStart(endMs = 0, nowMs = now))
    }
}
