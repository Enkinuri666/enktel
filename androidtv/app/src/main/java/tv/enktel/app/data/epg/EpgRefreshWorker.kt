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
        // Thermal guard: if the device is hot, skip this cycle entirely
        // rather than adding to the load.  WorkManager will fire us again
        // on the next scheduled tick when things have cooled down.
        if (tv.enktel.app.data.net.ThermalGuard.level.value.shouldSkipBackground) {
            return Result.success()
        }
        // Honour "Off": a viewer who turned automatic refresh off meant it,
        // and the background job is as automatic as the on-launch one.
        if (app.graph.settings.autoRefreshHoursNow() == tv.enktel.app.data.repo.AutoRefresh.OFF) {
            return Result.success()
        }
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
                // Needs the network, so declare it: without a constraint the job
                // fires on schedule even with no connection, the refresh throws,
                // and the cycle is spent on a guaranteed Result.retry(). Letting
                // WorkManager hold it until there is a connection is both
                // correct and cheaper. Not held to unmetered — a stale guide
                // breaks now/next, reminders and catch-up, so unlike downloads
                // and enrichment the guide is worth a little cellular data.
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("epg_refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
