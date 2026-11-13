package tv.enktel.app.dvr

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import tv.enktel.app.EnktelApp

/**
 * Where a recording's alarm lands.
 *
 * Deliberately almost nothing. The few seconds after an exact alarm are the
 * window in which Android lets this app start its foreground service from the
 * background, so the service is asked for at once and everything that needs
 * the database — is this recording still wanted? was it cancelled? — is left
 * to the service, which has to check anyway because it can be started twice.
 */
class RecordingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            RecordingAlarms.ACTION_START -> {
                val id = intent.getLongExtra(RecordingService.EXTRA_ID, -1)
                // A refusal here is not the end of it: the WorkManager job for
                // the same recording is still there and keeps trying for as
                // long as the programme is on.
                if (id > 0) RecordingService.start(context, id)
            }

            // The viewer has just allowed exact alarms. Android cancelled every
            // alarm this app had when the permission was last withdrawn, and
            // does not put them back.
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                val app = context.applicationContext as? EnktelApp ?: return
                val pending = goAsync()
                app.graph.appScope.launch {
                    try {
                        RecordingAlarms.rearmAll(app, app.graph.db.recordingDao())
                    } catch (_: Throwable) {
                        // Best effort: the next app start does the same thing.
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
