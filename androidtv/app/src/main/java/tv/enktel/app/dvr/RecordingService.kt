package tv.enktel.app.dvr

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import tv.enktel.app.EnktelApp
import tv.enktel.app.R
import java.io.File

/**
 * Foreground DVR engine. Downloads the raw transport stream to app storage so
 * recordings survive reboots and play back offline through the internal player.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as EnktelApp
        when (intent?.action) {
            ACTION_STOP -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                jobs.remove(id)?.cancel()
                scope.launch { app.graph.db.recordingDao().byId(id)?.let { finish(it.id, "DONE") } }
                if (jobs.isEmpty()) stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id > 0 && !jobs.containsKey(id)) {
                    startForeground(NOTIF_ID, buildNotification("Recording…"))
                    jobs[id] = scope.launch { record(id) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun record(id: Long) {
        val app = application as EnktelApp
        val dao = app.graph.db.recordingDao()
        val rec = dao.byId(id) ?: return
        val dir = File(getExternalFilesDir(null) ?: filesDir, "recordings").apply { mkdirs() }
        val safeName = rec.title.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60)
        val file = File(dir, "${safeName}_${rec.id}.ts")
        dao.update(rec.copy(status = "RECORDING", filePath = file.absolutePath))
        updateNotification("REC · ${rec.title}")

        var written = 0L
        try {
            val call = app.graph.http.newCall(Request.Builder().url(rec.streamUrl).build())
            call.execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                resp.body!!.byteStream().use { input ->
                    file.outputStream().buffered(256 * 1024).use { out ->
                        val buf = ByteArray(64 * 1024)
                        val stopAt = rec.endMs
                        while (scope.isActive && currentCoroutineActive()) {
                            if (stopAt > 0 && System.currentTimeMillis() >= stopAt) break
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            if (written % (8L shl 20) < 64 * 1024) {
                                dao.update(dao.byId(id)!!.copy(sizeBytes = written))
                            }
                        }
                    }
                }
            }
            dao.update(dao.byId(id)!!.copy(status = "DONE", sizeBytes = file.length(), endMs = System.currentTimeMillis()))
        } catch (e: Exception) {
            val status = if (written > 5L shl 20) "DONE" else "FAILED"
            dao.byId(id)?.let { dao.update(it.copy(status = status, sizeBytes = file.length())) }
        } finally {
            jobs.remove(id)
            if (jobs.isEmpty()) stopSelfSafely()
        }
    }

    private suspend fun currentCoroutineActive(): Boolean =
        kotlin.coroutines.coroutineContext[Job]?.isActive != false

    private suspend fun finish(id: Long, status: String) {
        (application as EnktelApp).graph.db.recordingDao().setStatus(id, status)
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, EnktelApp.DVR_CHANNEL)
            .setSmallIcon(R.drawable.tv_banner)
            .setContentTitle("EnkTel DVR")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        try { nm.notify(NOTIF_ID, buildNotification(text)) } catch (_: SecurityException) {}
    }

    companion object {
        const val ACTION_START = "tv.enktel.app.dvr.START"
        const val ACTION_STOP = "tv.enktel.app.dvr.STOP"
        const val EXTRA_ID = "recording_id"
        private const val NOTIF_ID = 4207

        fun start(context: Context, recordingId: Long) {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_ID, recordingId)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context, recordingId: Long) {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_STOP).putExtra(EXTRA_ID, recordingId)
            context.startService(i)
        }
    }
}
