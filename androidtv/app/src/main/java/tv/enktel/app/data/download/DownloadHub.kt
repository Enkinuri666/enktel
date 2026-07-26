package tv.enktel.app.data.download

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.enktel.app.data.db.DownloadDao
import tv.enktel.app.data.db.DownloadEntry
import java.io.File

/**
 * Wraps the platform DownloadManager for progressive movie/episode files. We keep
 * the *metadata* in Room (see [DownloadEntry]) so the file manager can render
 * offline without touching the OS DownloadManager cursor on every scroll — the
 * hub just mirrors progress into Room via a polling loop while jobs are active.
 *
 * Xtream VOD is progressive MP4/MKV/TS — download-and-play. Live/HLS/DASH would
 * need Media3's segmented DownloadManager and is intentionally out of scope
 * here (recording covers live capture already).
 */
class DownloadHub(
    private val app: Context,
    private val dao: DownloadDao,
) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "downloads"
        const val NOTIFICATION_CHANNEL_NAME = "Downloads"
        private const val POLL_INTERVAL_MS = 1_500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val jobs = mutableMapOf<String, Long>() // entryId -> system download id
    private val jobsLock = Any()
    private var poll: Job? = null

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: kotlinx.coroutines.flow.StateFlow<Long> = _totalBytes.asStateFlow()

    /** Root folder for finished downloads. We use *app-scoped external storage*
     *  because Android's DownloadManager can't target `filesDir` on many devices —
     *  writes there fail with SecurityException. `getExternalFilesDir` is still
     *  private per-app on API 19+, needs no storage permission, and is cleaned
     *  up automatically on uninstall — same UX guarantees. Falls back to
     *  `filesDir` on the very rare device with no external volume mounted so
     *  the app doesn't crash at startup. */
    val root: File = (app.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(app.filesDir, "downloads")).apply { mkdirs() }

    init {
        ensureNotificationChannel()
        registerCompletionReceiver()
    }

    /** Enqueue a movie or episode. Idempotent per [entry.id]. */
    fun enqueue(entry: DownloadEntry) {
        scope.launch {
            val existing = dao.byId(entry.id)
            if (existing != null && existing.status == "DONE") return@launch // already saved

            val safeName = sanitizeFilename(entry.title).ifBlank { entry.id }
            val ext = pickExtension(entry.sourceUrl)
            val outFile = File(root, "$safeName.$ext")

            val req = DownloadManager.Request(entry.sourceUrl.toUri())
                .setTitle(entry.title)
                .setDescription("EnkTel offline download")
                .setDestinationUri(Uri.fromFile(outFile))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            val sysId = try {
                dm.enqueue(req)
            } catch (t: Throwable) {
                dao.upsert(entry.copy(status = "FAILED", errorMessage = t.message ?: "enqueue failed"))
                return@launch
            }
            synchronized(jobsLock) { jobs[entry.id] = sysId }
            dao.upsert(entry.copy(status = "QUEUED", filePath = outFile.absolutePath))
            startPolling()
        }
    }

    fun cancel(entryId: String) {
        scope.launch {
            val sysId = synchronized(jobsLock) { jobs.remove(entryId) }
            if (sysId != null) dm.remove(sysId)
            val entry = dao.byId(entryId) ?: return@launch
            runCatching { if (entry.filePath.isNotBlank()) File(entry.filePath).delete() }
            dao.delete(entryId)
            refreshTotals()
        }
    }

    fun delete(entryId: String) = cancel(entryId)

    fun observe(): Flow<List<DownloadEntry>> = dao.all()

    fun observeCompleted(entryId: String): Flow<Boolean> = dao.completedFlow(entryId)

    fun observeExists(entryId: String): Flow<Boolean> = dao.existsFlow(entryId)

    suspend fun refreshTotals() {
        _totalBytes.value = dao.totalBytes()
    }

    private fun startPolling() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            while (isActive) {
                val active = synchronized(jobsLock) { jobs.toMap() }
                if (active.isEmpty()) break
                pollOnce(active)
                delay(POLL_INTERVAL_MS)
            }
            poll = null
        }
    }

    private suspend fun pollOnce(active: Map<String, Long>) {
        val query = DownloadManager.Query().setFilterById(*active.values.toLongArray())
        dm.query(query)?.use { c ->
            val idIdx = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val sizeIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val soFarIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
            val byIdReverse = active.entries.associate { it.value to it.key }
            while (c.moveToNext()) {
                val sysId = c.getLong(idIdx)
                val entryId = byIdReverse[sysId] ?: continue
                val status = c.getInt(statusIdx)
                val size = c.getLong(sizeIdx).coerceAtLeast(0)
                val soFar = c.getLong(soFarIdx).coerceAtLeast(0)
                val pct = if (size > 0) ((soFar * 100.0) / size).toInt().coerceIn(0, 100) else 0
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val entry = dao.byId(entryId)
                        if (entry != null) {
                            dao.updateProgress(entryId, "DONE", 100, size.coerceAtLeast(soFar), size.coerceAtLeast(soFar))
                            dao.markDone(entryId, entry.filePath)
                        }
                        synchronized(jobsLock) { jobs.remove(entryId) }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(reasonIdx)
                        dao.markFailed(entryId, "System code $reason")
                        synchronized(jobsLock) { jobs.remove(entryId) }
                    }
                    DownloadManager.STATUS_PAUSED -> dao.updateProgress(entryId, "PAUSED", pct, soFar, size)
                    DownloadManager.STATUS_PENDING -> dao.updateProgress(entryId, "QUEUED", pct, soFar, size)
                    DownloadManager.STATUS_RUNNING -> dao.updateProgress(entryId, "RUNNING", pct, soFar, size)
                }
            }
        }
        refreshTotals()
    }

    private fun registerCompletionReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val sysId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (sysId <= 0) return
                val entryId = synchronized(jobsLock) {
                    jobs.entries.firstOrNull { it.value == sysId }?.key
                } ?: return
                scope.launch { pollOnce(mapOf(entryId to sysId)) }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        // ContextCompat handles the API-33 export-flag requirement transparently;
        // DownloadManager's completion broadcast is a system-source event so
        // NOT_EXPORTED is sufficient and avoids inadvertently allowing third-party
        // apps to spoof completions.
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Offline movie and episode downloads"
            }
        )
    }

    private fun sanitizeFilename(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(96).trim().ifBlank { "download" }

    private fun pickExtension(url: String): String {
        val q = url.substringBefore('?').substringBefore('#')
        val tail = q.substringAfterLast('/', "")
        val dot = tail.lastIndexOf('.')
        val ext = if (dot in 0 until tail.length - 1) tail.substring(dot + 1).lowercase() else ""
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "flv", "wmv", "mpg", "mpeg" -> ext
            else -> "mp4"
        }
    }
}

/** Convenience: turn a raw byte count into a human string (1.4 GB, 620 MB). */
fun Long.humanBytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = this.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (u == 0) "$this B" else "%.1f %s".format(v, units[u])
}
