package tv.enktel.app.dvr

import android.content.Context
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import tv.enktel.app.data.db.Recording
import tv.enktel.app.EnktelApp
import java.io.File
import java.util.concurrent.TimeUnit

object RecordScheduler {

    /**
     * Start recording right now; endMs of 0 = record until manually stopped.
     *
     * @return the recording's id, or 0 when Android would not start the
     * recorder — the row is marked failed, and the caller should say so rather
     * than announce a recording that is not happening.
     */
    suspend fun recordNow(
        context: Context,
        profileId: Long,
        title: String,
        channelName: String,
        streamUrl: String,
        durationMinutes: Long = 0,
        channelLogo: String = "",
    ): Long {
        val app = context.applicationContext as EnktelApp
        val now = System.currentTimeMillis()
        val id = app.graph.db.recordingDao().insert(
            Recording(
                profileId = profileId, title = title, channelName = channelName,
                streamUrl = streamUrl, status = "RECORDING",
                startMs = now,
                endMs = if (durationMinutes > 0) now + TimeUnit.MINUTES.toMillis(durationMinutes) else 0,
                channelLogo = channelLogo,
            )
        )
        if (!RecordingService.start(context, id)) {
            app.graph.db.recordingDao().setStatus(id, "FAILED")
            return 0L
        }
        return id
    }

    /** Schedule a future recording (from the TV guide). Applies the configured start/end padding. */
    suspend fun schedule(
        context: Context,
        profileId: Long,
        title: String,
        channelName: String,
        streamUrl: String,
        startMs: Long,
        endMs: Long,
        channelLogo: String = "",
    ): Long {
        val app = context.applicationContext as EnktelApp
        val prefix = app.graph.settings.recPrefixMin.first()
        val suffix = app.graph.settings.recSuffixMin.first()
        val paddedStart = startMs - TimeUnit.MINUTES.toMillis(prefix.toLong())
        val paddedEnd = endMs + TimeUnit.MINUTES.toMillis(suffix.toLong())
        val id = app.graph.db.recordingDao().insert(
            Recording(
                profileId = profileId, title = title, channelName = channelName,
                streamUrl = streamUrl, status = "SCHEDULED", startMs = paddedStart, endMs = paddedEnd,
                channelLogo = channelLogo,
            )
        )
        val delay = (paddedStart - System.currentTimeMillis()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<StartRecordingWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(RecordingService.EXTRA_ID, id).build())
            .build()
        WorkManager.getInstance(context).enqueue(work)
        return id
    }

    /** Bytes free on the volume backing the recordings directory. */
    fun freeStorageBytes(context: Context): Long {
        // StatFs throws on a path it cannot stat — a card pulled out, a volume
        // still mounting — and this is called while the Recordings screen is
        // being drawn, where an exception takes the screen with it.
        return runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings").apply { mkdirs() }
            StatFs(dir.path).availableBytes
        }.getOrDefault(0L)
    }

    suspend fun cancel(context: Context, recordingId: Long) {
        val app = context.applicationContext as EnktelApp
        val dao = app.graph.db.recordingDao()
        val rec = dao.byId(recordingId) ?: return
        if (rec.status == "RECORDING") {
            // If the request cannot even be delivered there is no service to
            // close the row, so close it here.
            if (!RecordingService.stop(context, recordingId)) dao.setStatus(recordingId, "DONE")
        } else {
            dao.setStatus(recordingId, "CANCELLED")
        }
    }
}

class StartRecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(RecordingService.EXTRA_ID, -1)
        if (id <= 0) return Result.failure()
        val app = applicationContext as EnktelApp
        val rec = app.graph.db.recordingDao().byId(id) ?: return Result.failure()
        if (rec.status != "SCHEDULED") return Result.success()
        if (RecordingService.start(applicationContext, id)) return Result.success()
        return when (RecordingOutcome.afterRefusedStart(rec.endMs, System.currentTimeMillis())) {
            RecordingOutcome.Refused.RETRY -> Result.retry()
            RecordingOutcome.Refused.GIVE_UP -> {
                app.graph.db.recordingDao().setStatus(id, "FAILED")
                Result.failure()
            }
        }
    }
}
