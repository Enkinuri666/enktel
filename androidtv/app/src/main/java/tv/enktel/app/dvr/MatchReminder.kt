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
    const val LEAD_MIN = 5L

    /**
     * The identity of a reminder: one channel, one kick-off.
     *
     * Shared with the screens rather than each of them rebuilding the string,
     * because the bell is now a toggle — it has to be able to ask whether a
     * reminder already exists, and a second spelling of the key would answer
     * "no" every time and the viewer would get two notifications.
     */
    fun keyFor(channelKey: String, startMs: Long): String = "$channelKey:$startMs"

    suspend fun schedule(
        context: Context,
        channelKey: String,
        channelName: String,
        title: String,
        startMs: Long,
        endMs: Long,
    ) {
        val app = context.applicationContext as EnktelApp
        val key = keyFor(channelKey, startMs)
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
            .addTag(tagFor(key))
            .build()
        // The database row is REPLACEd, but WorkManager would happily hold two
        // requests for the same match — so asking twice meant being told
        // twice. Replacing the pending request keeps one alert per fixture
        // however many times the bell is pressed.
        WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(key))
        WorkManager.getInstance(context).enqueue(work)
    }

    private fun tagFor(key: String) = "reminder:$key"

    suspend fun cancel(context: Context, key: String) {
        val app = context.applicationContext as EnktelApp
        app.graph.db.sportsDao().cancelReminder(key)
        WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(key))
    }
}

class MatchReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as EnktelApp
        val key = inputData.getString("key") ?: return Result.failure()
        val sports = app.graph.db.sportsDao()
        // Read the row, don't just check it exists. The stored reminder has
        // always held the fixture and the channel, and this notification said
        // "Sports starting soon / Tap to open the channel" — which channel,
        // and which sport, being the only two things the viewer wanted to
        // know. It was decoding the key for the channel id and throwing the
        // rest away rather than spending one query on the row itself.
        val row = sports.reminder(key) ?: return Result.success()
        val channelKey = row.channelKey

        val notif = NotificationCompat.Builder(applicationContext, EnktelApp.DVR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(row.title.ifBlank { "Sport starting soon" })
            .setContentText(
                buildString {
                    append("Starts in ")
                    append(MatchReminderScheduler.LEAD_MIN.toInt())
                    append(" min")
                    if (row.channelName.isNotBlank()) append(" on ").append(row.channelName)
                },
            )
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
