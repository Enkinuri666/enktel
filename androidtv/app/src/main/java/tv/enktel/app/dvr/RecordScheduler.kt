package tv.enktel.app.dvr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tv.enktel.app.data.db.Recording
import tv.enktel.app.EnktelApp
import java.util.concurrent.TimeUnit

object RecordScheduler {

    /** Start recording right now; endMs of 0 = record until manually stopped. */
    suspend fun recordNow(context: Context, profileId: Long, title: String, channelName: String, streamUrl: String, durationMinutes: Long = 0): Long {
        val app = context.applicationContext as EnktelApp
        val now = System.currentTimeMillis()
        val id = app.graph.db.recordingDao().insert(
            Recording(
                profileId = profileId, title = title, channelName = channelName,
                streamUrl = streamUrl, status = "RECORDING",
                startMs = now,
                endMs = if (durationMinutes > 0) now + TimeUnit.MINUTES.toMillis(durationMinutes) else 0,
            )
        )
        RecordingService.start(context, id)
        return id
    }

    /** Schedule a future recording (from the TV guide). */
    suspend fun schedule(context: Context, profileId: Long, title: String, channelName: String, streamUrl: String, startMs: Long, endMs: Long): Long {
        val app = context.applicationContext as EnktelApp
        val id = app.graph.db.recordingDao().insert(
            Recording(
                profileId = profileId, title = title, channelName = channelName,
                streamUrl = streamUrl, status = "SCHEDULED", startMs = startMs, endMs = endMs,
            )
        )
        val delay = (startMs - System.currentTimeMillis()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<StartRecordingWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(RecordingService.EXTRA_ID, id).build())
            .build()
        WorkManager.getInstance(context).enqueue(work)
        return id
    }

    suspend fun cancel(context: Context, recordingId: Long) {
        val app = context.applicationContext as EnktelApp
        val dao = app.graph.db.recordingDao()
        val rec = dao.byId(recordingId) ?: return
        if (rec.status == "RECORDING") RecordingService.stop(context, recordingId)
        else dao.setStatus(recordingId, "CANCELLED")
    }
}

class StartRecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(RecordingService.EXTRA_ID, -1)
        if (id <= 0) return Result.failure()
        val app = applicationContext as EnktelApp
        val rec = app.graph.db.recordingDao().byId(id) ?: return Result.failure()
        if (rec.status != "SCHEDULED") return Result.success()
        RecordingService.start(applicationContext, id)
        return Result.success()
    }
}
