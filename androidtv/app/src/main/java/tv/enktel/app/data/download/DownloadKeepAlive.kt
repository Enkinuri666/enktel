package tv.enktel.app.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import tv.enktel.app.R

/**
 * Keeps the process alive while the parallel downloader is working.
 *
 * ## The bug this exists for
 *
 * There are two download engines. The system one is the platform
 * `DownloadManager`, which the OS runs on the app's behalf and which carries
 * on regardless. The parallel one is ours — four-way ranged GETs, resumable,
 * and the only one that can write into a folder the viewer picked — and it
 * runs as coroutines inside the app process with nothing holding that process
 * up. Leave the app and Android is entitled to freeze or kill it, so the
 * download stops. It reads as "downloads fail when you switch apps", which is
 * exactly what it is.
 *
 * The interaction that makes it common: **picking a download folder forces the
 * parallel engine**, because the system one cannot write to a SAF tree. So the
 * viewers most likely to hit this are the ones who followed the advice to put
 * downloads somewhere they can reach.
 *
 * This service does no downloading. It exists only so that, while work is in
 * flight, the process is one Android does not reclaim — which is the whole of
 * what was missing.
 */
class DownloadKeepAlive : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Downloading…"
        startForeground(NOTIF_ID, notification(text))
        // NOT_STICKY: a restart with no downloads to keep alive would be a
        // notification for nothing. The hub starts this again when it has work.
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            @Suppress("DEPRECATION")
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, DownloadHub.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("EnkTel downloads")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val NOTIF_ID = 4711
        private const val EXTRA_TEXT = "text"
        private const val ACTION_STOP = "tv.enktel.app.DOWNLOAD_KEEPALIVE_STOP"

        /**
         * Best-effort by design.
         *
         * From Android 12 an app cannot start a foreground service from the
         * background at all, so this throws exactly when the process was about
         * to be reclaimed anyway. Swallowing it keeps the download attempt
         * alive on the devices where it can work rather than turning a
         * platform restriction into a crash.
         */
        fun start(ctx: Context, text: String) {
            val i = Intent(ctx, DownloadKeepAlive::class.java).putExtra(EXTRA_TEXT, text)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, DownloadKeepAlive::class.java).setAction(ACTION_STOP)
            runCatching { ctx.startService(i) }
        }
    }
}
