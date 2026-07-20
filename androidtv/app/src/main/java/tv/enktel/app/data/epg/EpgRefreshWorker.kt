package tv.enktel.app.data.epg

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tv.enktel.app.EnktelApp
import java.util.concurrent.TimeUnit

/** Keeps the guide fresh in the background so now/next and the grid never go stale. */
class EpgRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as EnktelApp
        val profile = app.graph.playlists.activeProfile() ?: return Result.success()
        return try {
            app.graph.epg.refresh(profile)
            app.graph.settings.setEpgLastSync(System.currentTimeMillis())
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context, everyHours: Long = 12) {
            val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(everyHours, TimeUnit.HOURS)
                .setInitialDelay(everyHours, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("epg_refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
