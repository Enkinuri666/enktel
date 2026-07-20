package tv.enktel.app.dvr

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tv.enktel.app.EnktelApp
import tv.enktel.app.MainActivity
import tv.enktel.app.R
import tv.enktel.app.data.db.MatchReminder
import java.util.concurrent.TimeUnit

object MatchReminderScheduler {
    /** Alert this many minutes before the match starts. */
    private const val LEAD_MIN = 5L

    suspend fun schedule(
        context: Context,
        channelKey: String,
        channelName: String,
        title: String,
        startMs: Long,
        endMs: Long,
    ) {
        val app = context.applicationContext as EnktelApp
        val key = "$channelKey:$startMs"
        app.graph.db.sportsDao().addReminder(
            MatchReminder(
                key = key, channelKey = channelKey, channelName = channelName,
                title = title, startMs = startMs, endMs = endMs,
            )
        )
        val fireAt = startMs - TimeUnit.MINUTES.toMillis(LEAD_MIN)
        val delay = (fireAt - System.currentTimeMillis()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<MatchReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("key", key).build())
            .addTag("reminder:$key")
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    suspend fun cancel(context: Context, key: String) {
        val app = context.applicationContext as EnktelApp
        app.graph.db.sportsDao().cancelReminder(key)
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder:$key")
    }
}

class MatchReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as EnktelApp
        val key = inputData.getString("key") ?: return Result.failure()
        val sports = app.graph.db.sportsDao()
        if (!sports.hasReminder(key)) return Result.success()

        // The stored reminder holds the exact match info; using key encoding again avoids
        // needing an extra "get" DAO method.
        val (channelKey, startStr) = key.split(':').let {
            it.dropLast(1).joinToString(":") to it.last()
        }
        val notif = NotificationCompat.Builder(applicationContext, EnktelApp.DVR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sports starting soon")
            .setContentText("Tap to open the channel")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext, key.hashCode(),
                    Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("channel_key", channelKey)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(key.hashCode(), notif) } catch (_: SecurityException) {}
        sports.cancelReminder(key)
        return Result.success()
    }
}
