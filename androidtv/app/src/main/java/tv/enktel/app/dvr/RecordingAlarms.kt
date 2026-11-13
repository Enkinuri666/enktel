package tv.enktel.app.dvr

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import tv.enktel.app.data.db.RecordingDao

/**
 * Starts a scheduled recording at its time, whether or not the app is open.
 *
 * ### Why a scheduled recording needs an alarm
 *
 * Recording runs in a foreground service, and from Android 12 an app that is
 * not on screen may not start one. A scheduled recording is, by its nature,
 * the one that begins while the viewer is doing something else — so on any
 * recent device the WorkManager job that was the whole of the scheduler woke
 * up on time, asked for the service, and was refused.
 *
 * Android leaves a short list of doors open, and one of them is made for this:
 * a broadcast from an **exact alarm** may start a foreground service, because
 * an exact alarm is the platform's own definition of "the user asked for this
 * to happen at this time".
 *
 * An exact alarm also fires while the device is dozing, which the job never
 * promised. A phone left on a table overnight delays deferrable work until its
 * next maintenance window; a recording that starts forty minutes into the
 * match is not the recording that was asked for.
 *
 * ### The permission
 *
 * `SCHEDULE_EXACT_ALARM`. Before Android 12 there is nothing to ask. On 12 and
 * 13 it is granted at install. From 14 it is **off until the viewer turns it
 * on**, under Special app access → Alarms & reminders, and nothing but the
 * viewer can turn it on — there is no dialog to show. [settingsIntent] is the
 * way to that screen; the Recordings list offers it when it matters.
 *
 * ### What happens without it
 *
 * Exactly what happened before this existed: the WorkManager job is still
 * enqueued for every scheduled recording, and it starts the recording if the
 * app is open at the time and keeps trying while the programme is on if it is
 * not. The alarm is an addition, not a replacement, so a device that will not
 * grant it loses nothing.
 */
object RecordingAlarms {

    const val ACTION_START = "tv.enktel.app.dvr.ALARM_START"

    /**
     * Whether this version of Android has a permission for exact alarms at
     * all. Before 12 (API 31) any app could set one.
     */
    fun needsPermission(sdkInt: Int): Boolean = sdkInt >= 31

    /**
     * Whether this device will let the app set an exact alarm right now.
     *
     * Asked every time rather than remembered: the viewer can change it in
     * Settings while the app is in the background, and Android kills the app
     * when they take it away.
     */
    fun exactAllowed(context: Context): Boolean {
        // The literal comparison is for lint, which recognises a version check
        // only when it can see one; needsPermission is the same rule, kept as
        // a function so it can be tested without an emulated Android 11.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !needsPermission(Build.VERSION.SDK_INT)) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * Whether a start time is worth an alarm at all.
     *
     * One that has already passed is not: that is "record this programme",
     * pressed while it is on, and the job enqueued alongside starts it at once
     * with the app on screen. An alarm in the past fires immediately too, and
     * the two would race to start the same recording.
     */
    fun worthArming(atMs: Long, nowMs: Long): Boolean = atMs > nowMs

    /**
     * Set the alarm that starts [recordingId] at [atMs].
     *
     * @return whether an exact alarm now stands for it. False is not a
     * failure — see "What happens without it" above.
     */
    fun arm(
        context: Context,
        recordingId: Long,
        atMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!worthArming(atMs, nowMs) || !exactAllowed(context)) return false
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context, recordingId))
            true
        } catch (_: SecurityException) {
            // Allowed when asked a moment ago and not now: the permission was
            // withdrawn in between. Rare, and not worth a crash to report.
            false
        }
    }

    fun cancel(context: Context, recordingId: Long) {
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, recordingId))
        }
    }

    /**
     * Set the alarm for every recording still waiting for its time.
     *
     * Alarms do not outlive a reboot, and they are all cancelled when the
     * permission is withdrawn and not restored when it is given back. So this
     * runs whenever the app starts, when the Recordings screen is opened, and
     * when Android reports the permission changed. Setting an alarm that
     * already stands replaces it, so running this too often costs nothing.
     *
     * @return how many alarms now stand.
     */
    suspend fun rearmAll(
        context: Context,
        dao: RecordingDao,
        nowMs: Long = System.currentTimeMillis(),
    ): Int = dao.scheduled().count { arm(context, it.id, it.startMs, nowMs) }

    /**
     * The screen where the viewer allows exact alarms for this app, or null on
     * a version of Android that has no such permission to give.
     *
     * Not every device that has the permission has the screen — a television
     * build can ship without it — so whoever launches this has to be ready for
     * nothing to answer.
     */
    fun settingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !needsPermission(Build.VERSION.SDK_INT)) {
            null
        } else {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * One PendingIntent per recording.
     *
     * Told apart by the data URI, not by request code: two intents that
     * differ only in their extras are the *same* intent to the alarm manager,
     * so without it the second recording scheduled would replace the first.
     */
    private fun pending(context: Context, recordingId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RecordingAlarmReceiver::class.java)
                .setAction(ACTION_START)
                .setData("enktel-recording://start/$recordingId".toUri())
                .putExtra(RecordingService.EXTRA_ID, recordingId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
