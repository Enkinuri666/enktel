package tv.enktel.app.dvr

import tv.enktel.app.data.db.Recording

/**
 * What a recording's row should say once its download has ended, and what to
 * do when Android will not let one begin.
 *
 * Pulled out of the service because these are the decisions that were wrong
 * and none of them needs a service to test.
 */
object RecordingOutcome {

    /**
     * A failed recording with less than this on disk is a failure. With more,
     * it is a recording that ended early — a stream that dropped in the last
     * minute of a film is still a film somebody wants to watch.
     */
    const val KEEP_BYTES = 5L shl 20

    /** How much has to arrive between two progress writes. */
    const val PROGRESS_STEP = 8L shl 20

    /**
     * Whether the size on the row is due an update.
     *
     * This used to be `written % 8 MB < 64 KB`, which is true for every read
     * that happens to land just past a boundary — dozens in a row on a stream
     * that arrives in packet-sized pieces — and each one was a database read
     * and a write.
     */
    fun progressDue(written: Long, lastReported: Long): Boolean = written - lastReported >= PROGRESS_STEP

    /**
     * The row as it should read now the download is over.
     *
     * @param failed the download ended in an error, as opposed to reaching its
     * end time or being stopped by the viewer. A stop is not a failure however
     * little was recorded: they pressed Stop, and the list should say Done.
     */
    fun settle(row: Recording, bytesOnDisk: Long, failed: Boolean, nowMs: Long): Recording {
        val kept = !failed || bytesOnDisk > KEEP_BYTES
        return row.copy(
            status = if (kept) "DONE" else "FAILED",
            sizeBytes = bytesOnDisk,
            // The moment it actually ended. "Record now" stores 0 here, meaning
            // "until stopped", and a finished recording still saying 0 has no
            // length. A failure keeps the end it was scheduled for.
            endMs = if (kept) nowMs else row.endMs,
        )
    }

    enum class Refused { RETRY, GIVE_UP }

    /**
     * What a scheduled start should do when Android refuses the service.
     *
     * From Android 12 an app that is not on screen may not start a foreground
     * service, so a recording scheduled for while the TV is showing something
     * else is turned away. The row used to stay "Scheduled" for ever. Now the
     * start is retried for as long as the programme is still on — opening the
     * app during it records what is left — and marked failed once it is over,
     * which is at least true.
     */
    fun afterRefusedStart(endMs: Long, nowMs: Long): Refused =
        if (endMs > 0 && nowMs >= endMs) Refused.GIVE_UP else Refused.RETRY
}
