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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // ConcurrentHashMap, not HashMap: onStartCommand touches this on the main
    // thread while record()'s finally block removes from it on Dispatchers.IO.
    // Two threads mutating a plain HashMap can corrupt its internal table —
    // classically an infinite loop on resize, which on a foreground service
    // means a pinned CPU core and a recording that never ends.
    private val jobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

    /**
     * The open connection behind each recording, so that Stop can close it.
     *
     * Cancelling the coroutine is not enough on its own. The copy loop spends
     * its life inside `InputStream.read`, which is an ordinary blocking call
     * and knows nothing about coroutines, so a cancelled recording carried on
     * until the next packet arrived — and on a stream that had stalled, until
     * the socket timed out. Closing the call makes `read` return at once.
     */
    private val calls = java.util.concurrent.ConcurrentHashMap<Long, okhttp3.Call>()

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Without this the recording coroutine outlives the service.
     *
     * The scope was never cancelled anywhere, and `record()`'s copy loop only
     * checks `scope.isActive` — which stays true forever if nothing cancels it.
     * So a destroyed service left a coroutine holding an open OkHttp call,
     * still writing to disk and still updating the database, with no UI and no
     * way to stop it. On a metered connection that's invisible data burn, and
     * it holds one of the line's connections open — which on a 1- or
     * 2-connection Xtream line is enough to break live playback.
     */
    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Android's notice that this service has used its foreground allowance —
     * six hours of `dataSync` in any twenty-four, from Android 15. Whatever is
     * recording is closed off as it stands and kept; ignoring the call gets the
     * whole app killed a few seconds later, recording and all.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopEverything()
        stopSelfSafely()
    }

    /** Ends every recording. Each one still writes its final state: see [record]. */
    private fun stopEverything() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        calls.values.forEach { runCatching { it.cancel() } }
        calls.clear()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as EnktelApp
        when (intent?.action) {
            ACTION_STOP -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                val running = jobs.remove(id)
                running?.cancel()
                calls.remove(id)?.let { runCatching { it.cancel() } }
                // A recording that was running closes its own row, with the
                // real size, as it unwinds. This is for the row that says
                // RECORDING with nothing behind it — the process died under
                // it — where there is nobody else to do it. On the app's
                // scope, not the service's: the next line may stop the
                // service, and its scope with it, before this has had a turn.
                if (running == null) {
                    app.graph.appScope.launch {
                        runCatching {
                            val dao = app.graph.db.recordingDao()
                            if (dao.byId(id)?.status == "RECORDING") dao.setStatus(id, "DONE")
                        }
                    }
                }
                if (jobs.isEmpty()) stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id > 0 && !jobs.containsKey(id)) {
                    if (!tv.enktel.app.data.Foreground.enter(this, NOTIF_ID, buildNotification("Recording…"))) {
                        // Refused — see Foreground. Say so on the row rather
                        // than leave it claiming to record.
                        app.graph.appScope.launch {
                            runCatching { app.graph.db.recordingDao().setStatus(id, "FAILED") }
                        }
                        if (jobs.isEmpty()) stopSelf()
                        return START_NOT_STICKY
                    }
                    // Registered before it runs: a recording that ends at once
                    // (its row already gone) must find itself in the map to
                    // remove, or the service waits for ever on a job that is
                    // already over.
                    val job = scope.launch(start = CoroutineStart.LAZY) { record(id) }
                    jobs[id] = job
                    job.start()
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Copy one stream to disk until its end time, a Stop, or the stream ending.
     *
     * Everything is inside the `try`, including the first look at the row: the
     * `finally` is what takes this recording out of [jobs] and stops the
     * service when it was the last, and an early return that skipped it left a
     * "Recording…" notification with nothing recording.
     */
    private suspend fun record(id: Long) {
        val app = application as EnktelApp
        val dao = app.graph.db.recordingDao()
        var file: File? = null
        var failed = false
        try {
            val rec = dao.byId(id) ?: return
            val dir = File(getExternalFilesDir(null) ?: filesDir, "recordings").apply { mkdirs() }
            val safeName = rec.title.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60)
            val target = File(dir, "${safeName}_${rec.id}.ts")
            file = target
            dao.update(rec.copy(status = "RECORDING", filePath = target.absolutePath))
            updateNotification("REC · ${rec.title}")

            val call = app.graph.streamHttp.newCall(Request.Builder().url(rec.streamUrl).build())
            calls[id] = call
            call.execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                resp.body.byteStream().use { input ->
                    target.outputStream().buffered(256 * 1024).use { out ->
                        val buf = ByteArray(64 * 1024)
                        val stopAt = rec.endMs
                        var written = 0L
                        var reported = 0L
                        while (currentCoroutineContext().isActive) {
                            if (stopAt > 0 && System.currentTimeMillis() >= stopAt) break
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            if (RecordingOutcome.progressDue(written, reported)) {
                                reported = written
                                // Nothing updated means there is no row: the
                                // recording was deleted from the list while it
                                // ran. Stop — there is nobody left to watch it.
                                if (dao.setSize(id, written) == 0) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // A Stop arrives here too — the cancelled call makes read() throw —
            // and a Stop is not a failure. What tells them apart is whether
            // this coroutine was still wanted when it happened.
            failed = currentCoroutineContext().isActive
        } finally {
            calls.remove(id)
            // NonCancellable, or none of this runs. A stopped recording is a
            // cancelled coroutine, and a cancelled coroutine cannot make a
            // database call: the final size and end time of every recording
            // the viewer stopped by hand were being thrown away right here,
            // leaving the list showing whatever the last progress tick said.
            withContext(NonCancellable) { settle(id, file, failed) }
            jobs.remove(id)
            if (jobs.isEmpty()) stopSelfSafely()
        }
    }

    private suspend fun settle(id: Long, file: File?, failed: Boolean) {
        val dao = (application as EnktelApp).graph.db.recordingDao()
        val row = runCatching { dao.byId(id) }.getOrNull()
        if (row == null) {
            // Deleted while it was recording. The list removed the file it
            // could see; anything written since goes too.
            file?.let { runCatching { it.delete() } }
            return
        }
        val bytes = file?.let { runCatching { it.length() }.getOrDefault(0L) } ?: 0L
        runCatching { dao.update(RecordingOutcome.settle(row, bytes, failed, System.currentTimeMillis())) }
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, EnktelApp.DVR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
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

        /**
         * @return false when Android would not start the service. It refuses
         * an app that is not on screen (Android 12 and later), and callers
         * include a background worker and a coroutine on a player screen —
         * neither of which survives an exception thrown from here.
         */
        fun start(context: Context, recordingId: Long): Boolean {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_ID, recordingId)
            return runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
                true
            }.getOrDefault(false)
        }

        /** @return false when the request could not be delivered. */
        fun stop(context: Context, recordingId: Long): Boolean {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_STOP).putExtra(EXTRA_ID, recordingId)
            return runCatching { context.startService(i); true }.getOrDefault(false)
        }
    }
}
