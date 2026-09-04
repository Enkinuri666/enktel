package tv.enktel.app.data.share

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.enktel.app.R
import tv.enktel.app.data.download.DownloadHub

/**
 * Owns the [LanShareServer] for as long as it is sharing.
 *
 * The server used to be held by the Downloads screen and stopped when that
 * screen went away — which is fine for a screen and useless for a transfer.
 * A film is several gigabytes over house Wi-Fi: the viewer starts it, then
 * goes to look at something else, and the copy has to keep running. Anything
 * that only works while you stare at it is not a feature.
 *
 * It is still not left running forever. It stops when the viewer stops it, and
 * the notification it must show while foreground is the reminder that
 * something is listening — which is the right trade for a server on a home
 * network: visible for as long as it exists.
 */
class LanShareService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        val started = LanShareController.current.value
        startForeground(
            NOTIF_ID,
            notification(
                started?.let { "Sharing at ${it.url} · PIN ${it.pin}" }
                    ?: "Sharing downloads on your network",
            ),
        )
        // NOT_STICKY: a restart would revive a server with no shares and a
        // stale PIN, which is worse than not coming back.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        LanShareController.serverStopped()
        if (Build.VERSION.SDK_INT >= 24) {
            @Suppress("DEPRECATION")
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, DownloadHub.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("EnkTel · sending to PC")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val NOTIF_ID = 4712
        const val ACTION_STOP = "tv.enktel.app.LAN_SHARE_STOP"
    }
}

/**
 * The one live server, and what the screen needs to describe it.
 *
 * A singleton because there is one network interface and one port: two servers
 * would fight over both. Holding it here rather than in a composable is what
 * lets it outlive the screen that started it.
 */
object LanShareController {

    private val server = LanShareServer()
    private val _current = MutableStateFlow<LanShareServer.Started?>(null)

    /** Null when nothing is being shared. */
    val current: StateFlow<LanShareServer.Started?> = _current.asStateFlow()

    /** Start sharing [files]. Returns an error for the screen, or null on success. */
    fun start(ctx: Context, ip: String, files: List<LanShareServer.Shared>): String? {
        if (files.isEmpty()) return "Nothing finished downloading yet, so there is nothing to send."
        val started = server.start(ip, files)
            ?: return "Could not open the sharing port. Another app may be using it."
        _current.value = started
        val i = Intent(ctx, LanShareService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }.onFailure {
            // No foreground service means no protection from being killed, and
            // a server that dies mid-transfer without saying so is worse than
            // one that never started.
            server.stop()
            _current.value = null
            return "Could not keep sharing running in the background."
        }
        return null
    }

    fun stop(ctx: Context) {
        runCatching {
            ctx.startService(Intent(ctx, LanShareService::class.java).setAction(LanShareService.ACTION_STOP))
        }
        // Stopped here as well as in the service: the intent may not arrive if
        // the service is already gone, and the socket must close either way.
        serverStopped()
    }

    /** Called by the service as it goes down. */
    internal fun serverStopped() {
        server.stop()
        _current.value = null
    }
}
