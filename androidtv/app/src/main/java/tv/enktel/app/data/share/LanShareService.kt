package tv.enktel.app.data.share

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
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

    /**
     * Kept for as long as sharing runs, and no longer.
     *
     * Wi-Fi filters out packets that are not addressed to this device, which
     * includes the subnet broadcast the PC client uses to find us. This lock
     * turns that filtering off; holding it costs battery, which is why it
     * belongs to the service that already stops when the viewer stops sharing
     * rather than to the app.
     */
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        acquireMulticastLock()
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

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        // Best effort throughout: a device without Wi-Fi hardware, or one that
        // refuses the lock, still shares perfectly well over its address — the
        // viewer just has to type it rather than being found.
        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("enktel-share")?.apply {
                setReferenceCounted(false)
                acquire()
                multicastLock = this
            }
        }
    }

    private fun shutDown() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
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

    /**
     * The queue mirror handed to the server, alive only while sharing is.
     *
     * Created per run and closed with it: it holds a database subscription,
     * and one left running after the viewer stopped sharing is a coroutine
     * collecting rows for a server nobody can reach.
     */
    private var remote: DownloadRemote? = null

    /** Null when nothing is being shared. */
    val current: StateFlow<LanShareServer.Started?> = _current.asStateFlow()

    /**
     * Start sharing [files]. Returns an error for the screen, or null on success.
     *
     * [hub] is optional so a caller with nothing to control — a test, or a
     * future screen that only wants to hand over one file — can leave the
     * remote-control routes switched off rather than passing a stub.
     */
    fun start(
        ctx: Context,
        ip: String,
        files: List<LanShareServer.Shared>,
        hub: DownloadHub? = null,
    ): String? {
        if (files.isEmpty()) return "Nothing finished downloading yet, so there is nothing to send."
        val mirror = hub?.let { DownloadRemote(it) }
        val started = server.start(
            ip = ip,
            shared = files,
            remote = mirror,
            deviceName = deviceName(),
            appVersion = tv.enktel.app.BuildConfig.VERSION_NAME,
        )
        if (started == null) {
            mirror?.close()
            return "Could not open the sharing port. Another app may be using it."
        }
        remote = mirror
        _current.value = started
        val i = Intent(ctx, LanShareService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }.onFailure {
            // No foreground service means no protection from being killed, and
            // a server that dies mid-transfer without saying so is worse than
            // one that never started.
            serverStopped()
            return "Could not keep sharing running in the background."
        }
        return null
    }

    /**
     * What the PC client shows in its device list.
     *
     * `Build.MODEL` alone is "SM-G991B" on half the phones in the house, so
     * the manufacturer goes in front unless the model already says it.
     */
    private fun deviceName(): String {
        val model = Build.MODEL.orEmpty().trim()
        val make = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        return when {
            model.isBlank() -> make.ifBlank { "EnkTel device" }
            make.isBlank() || model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
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
        remote?.close()
        remote = null
        _current.value = null
    }
}
