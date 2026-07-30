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
import androidx.documentfile.provider.DocumentFile
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
import okhttp3.OkHttpClient
import tv.enktel.app.data.db.DownloadDao
import tv.enktel.app.data.db.DownloadEntry
import tv.enktel.app.data.prefs.SettingsStore
import java.io.File

/**
 * Two-engine download hub:
 *   • System engine — the platform [DownloadManager]. Best OS notification
 *     integration, but writes only to a `file://` path (can't target a SAF
 *     tree URI) and no parallel-range support.
 *   • Parallel engine — [ParallelDownloader], a custom OkHttp client that
 *     does 4-way HTTP Range GETs when the server supports it. Writes to
 *     either a File or a SAF DocumentFile (single-stream in the SAF case
 *     because SAF's OutputStream has no seek).
 *
 * Engine selection per download:
 *   1. If the user picked a SAF folder in Settings → Downloads, the
 *      Parallel engine is used unconditionally (System can't write there).
 *   2. Otherwise the user's Settings → Downloads → Engine choice wins:
 *      "system", "parallel", or "auto" (defaults to Parallel — the ranged
 *      path is genuinely faster on Xtream VOD panels that support ranges).
 */
class DownloadHub(
    private val app: Context,
    private val dao: DownloadDao,
    private val settings: SettingsStore,
    private val http: OkHttpClient,
) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "downloads"
        const val NOTIFICATION_CHANNEL_NAME = "Downloads"
        private const val POLL_INTERVAL_MS = 1_500L
        private const val UA = tv.enktel.app.DEFAULT_UA
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val parallel = ParallelDownloader(app, http)

    // Two separate job maps — one per engine — sharing a single lock.
    // System jobs store the OS DownloadManager id (Long).
    // Parallel jobs store the ParallelDownloader.Handle so cancel() can
    // stop the coroutine mid-flight.
    private val sysJobs = mutableMapOf<String, Long>()
    private val parallelJobs = mutableMapOf<String, ParallelDownloader.Handle>()
    private val jobsLock = Any()
    private var poll: Job? = null

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: kotlinx.coroutines.flow.StateFlow<Long> = _totalBytes.asStateFlow()

    /** Default root under app-scoped external storage. Used when the user
     *  hasn't picked a folder via SAF (see [settings.downloadFolderUri]). */
    val defaultRoot: File = (app.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(app.filesDir, "downloads")).apply { mkdirs() }

    init {
        ensureNotificationChannel()
        registerCompletionReceiver()
        reconcileOnStart()
    }

    /**
     * Downloads don't survive the process dying (app swiped away, box
     * rebooted, low-memory kill). Rows left claiming RUNNING/QUEUED would
     * otherwise sit there with a frozen progress bar and no way back, so on
     * every start we move them to PAUSED — the bytes and the saved segment
     * offsets are still on disk, so ▶ resumes them exactly where they stopped.
     */
    private fun reconcileOnStart() {
        scope.launch {
            try {
                dao.inFlight().forEach { entry ->
                    dao.markPaused(
                        entry.id, entry.resumeState, entry.progressPct,
                        entry.downloadedBytes, entry.sizeBytes,
                    )
                }
                refreshTotals()
            } catch (_: Throwable) { /* best effort */ }
        }
    }

    /** Enqueue a movie or episode. Idempotent per [entry.id]. */
    fun enqueue(entry: DownloadEntry) {
        scope.launch {
            val existing = dao.byId(entry.id)
            if (existing != null && existing.status == "DONE") return@launch

            val chosenFolderUri = settings.downloadFolderUriNow()
            val engine = settings.downloadEngineNow()
            val useSaf = chosenFolderUri.isNotBlank() && chosenFolderUri.startsWith("content://")
            // System engine can't write to SAF; force parallel there. And
            // when the user hasn't overridden, prefer parallel — the ranged
            // GET path is materially faster on Xtream VOD.
            val forceParallel = useSaf || engine == "parallel" ||
                (engine == "auto" && chosenFolderUri.isBlank())
            val forceSystem = !useSaf && engine == "system"

            when {
                useSaf -> enqueueParallelSaf(entry, chosenFolderUri)
                forceSystem -> enqueueSystem(entry)
                forceParallel -> enqueueParallelFile(entry)
                else -> enqueueParallelFile(entry) // fallback
            }
        }
    }

    // ---- Pause / resume / retry --------------------------------------------

    /**
     * Stop a download but keep every byte already on disk.
     *
     * The parallel engine stops its range workers cleanly and flushes their
     * offsets, so resuming continues mid-file. The platform DownloadManager has
     * no pause API at all — `remove()` is the only stop, and it deletes the
     * partial file — so a paused system download is handed to the parallel
     * engine, which restarts it once and is resumable from then on.
     */
    fun pause(entryId: String) {
        scope.launch {
            val entry = dao.byId(entryId) ?: return@launch
            if (entry.status == "DONE") return@launch

            val parHandle = synchronized(jobsLock) { parallelJobs[entryId] }
            if (parHandle != null) {
                // markPaused lands via the downloader's onPaused callback once
                // the workers have flushed their offsets.
                parHandle.pause()
                return@launch
            }

            val sysId = synchronized(jobsLock) { sysJobs.remove(entryId) }
            if (sysId != null) {
                runCatching { dm.remove(sysId) }
                runCatching { if (entry.filePath.isNotBlank()) File(entry.filePath).delete() }
                dao.setEngine(entryId, "parallel")
                dao.markPaused(entryId, "", 0, 0, entry.sizeBytes)
            } else {
                // Nothing in flight (e.g. already reconciled at start-up).
                dao.markPaused(entryId, entry.resumeState, entry.progressPct, entry.downloadedBytes, entry.sizeBytes)
            }
            refreshTotals()
        }
    }

    /** Restart a paused or failed download, continuing from the saved offsets. */
    fun resume(entryId: String) {
        scope.launch {
            val entry = dao.byId(entryId) ?: return@launch
            if (entry.status == "DONE") return@launch
            val alreadyRunning = synchronized(jobsLock) {
                parallelJobs.containsKey(entryId) || sysJobs.containsKey(entryId)
            }
            if (alreadyRunning) return@launch

            val folderUri = settings.downloadFolderUriNow()
            val useSaf = folderUri.isNotBlank() && folderUri.startsWith("content://")
            if (useSaf && entry.filePath.startsWith("content://")) {
                dao.upsert(entry.copy(status = "RUNNING", errorMessage = ""))
                val doc = runCatching { DocumentFile.fromSingleUri(app, entry.filePath.toUri()) }.getOrNull()
                if (doc != null && doc.canWrite()) {
                    launchParallel(entry, ParallelDownloader.Target.SafTarget(app, doc), entry.resumeState)
                    return@launch
                }
                // The picked file vanished (folder permission revoked, user
                // deleted it) — fall through and start a fresh one.
                enqueueParallelSaf(entry.copy(resumeState = ""), folderUri)
                return@launch
            }
            val outFile = if (entry.filePath.isNotBlank() && !entry.filePath.startsWith("content://")) {
                File(entry.filePath)
            } else plannedFileFor(entry)
            outFile.parentFile?.mkdirs()
            // A resume blob is only meaningful while its partial file is intact.
            val state = if (outFile.exists()) entry.resumeState else ""
            dao.upsert(entry.copy(status = "RUNNING", errorMessage = "", filePath = outFile.absolutePath))
            launchParallel(entry, ParallelDownloader.Target.FileTarget(outFile), state)
        }
    }

    /** Failed downloads retry from wherever they got to, same as a resume. */
    fun retry(entryId: String) = resume(entryId)

    /** Enqueue many entries at once — used by "Download season" and
     *  "Download entire series". Skips items already saved offline, and
     *  paces enqueues so the platform DownloadManager isn't hit in one tick. */
    fun enqueueMany(entries: List<DownloadEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            entries.forEach { entry ->
                val existing = dao.byId(entry.id)
                if (existing?.status == "DONE") return@forEach
                enqueue(entry)
                delay(60)
            }
        }
    }

    // ---- System engine (platform DownloadManager) --------------------------

    private suspend fun enqueueSystem(entry: DownloadEntry) {
        val outFile = plannedFileFor(entry)
        outFile.parentFile?.mkdirs()

        val req = DownloadManager.Request(entry.sourceUrl.toUri())
            .setTitle(entry.title)
            .setDescription(descriptionFor(entry))
            .setDestinationUri(Uri.fromFile(outFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            // Ship the same UA the rest of the app uses; some Xtream/WAF
            // rulesets throttle or 407 the DownloadManager default UA.
            .addRequestHeader("User-Agent", UA)

        val sysId = try {
            dm.enqueue(req)
        } catch (t: Throwable) {
            dao.upsert(entry.copy(status = "FAILED", errorMessage = t.message ?: "enqueue failed"))
            return
        }
        synchronized(jobsLock) { sysJobs[entry.id] = sysId }
        dao.upsert(entry.copy(status = "QUEUED", engine = "system", filePath = outFile.absolutePath))
        startPolling()
    }

    // ---- Parallel engine (file target) -------------------------------------

    private suspend fun enqueueParallelFile(entry: DownloadEntry) {
        val outFile = plannedFileFor(entry)
        outFile.parentFile?.mkdirs()
        dao.upsert(entry.copy(status = "RUNNING", engine = "parallel", filePath = outFile.absolutePath))
        launchParallel(entry, ParallelDownloader.Target.FileTarget(outFile), entry.resumeState)
    }

    // ---- Parallel engine (SAF target) --------------------------------------

    private suspend fun enqueueParallelSaf(entry: DownloadEntry, treeUriStr: String) {
        val tree = try {
            DocumentFile.fromTreeUri(app, treeUriStr.toUri())
        } catch (t: Throwable) { null }
        if (tree == null || !tree.canWrite()) {
            dao.upsert(entry.copy(status = "FAILED", errorMessage = "Chosen folder is unwritable"))
            return
        }
        val doc = createSafFile(tree, entry) ?: run {
            dao.upsert(entry.copy(status = "FAILED", errorMessage = "Could not create file in chosen folder"))
            return
        }
        dao.upsert(entry.copy(status = "RUNNING", engine = "parallel", filePath = doc.uri.toString()))
        launchParallel(entry, ParallelDownloader.Target.SafTarget(app, doc), entry.resumeState)
    }

    private fun createSafFile(root: DocumentFile, entry: DownloadEntry): DocumentFile? {
        val ext = pickExtension(entry.sourceUrl)
        val safeTitle = sanitizeFilename(entry.title).ifBlank { entry.id }
        val mime = mimeForExt(ext)
        return when (entry.kind) {
            "episode" -> {
                val seriesFolder = sanitizeFilename(entry.seriesName).ifBlank { "Series" }
                val season = entry.season.coerceAtLeast(1)
                val epNum = "E%02d".format(entry.episode.coerceAtLeast(0))
                val epLabel = sanitizeFilename(
                    entry.title.substringAfter("·", entry.title).trim().ifBlank { entry.title }
                ).ifBlank { epNum }
                val seriesDir = orCreateSub(root, "Series") ?: return null
                val showDir = orCreateSub(seriesDir, seriesFolder) ?: return null
                val seasonDir = orCreateSub(showDir, "S%02d".format(season)) ?: return null
                seasonDir.createFile(mime, "$epNum - $epLabel")
            }
            else -> {
                val moviesDir = orCreateSub(root, "Movies") ?: return null
                moviesDir.createFile(mime, safeTitle)
            }
        }
    }

    private fun orCreateSub(parent: DocumentFile, name: String): DocumentFile? =
        parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)

    private fun launchParallel(
        entry: DownloadEntry,
        target: ParallelDownloader.Target,
        resumeState: String = "",
    ) {
        val id = entry.id
        val handle = parallel.start(
            url = entry.sourceUrl,
            target = target,
            userAgent = UA,
            resumeState = resumeState,
            onProgress = { downloaded, total, state ->
                scope.launch {
                    val pct = if (total > 0) ((downloaded * 100.0) / total).toInt().coerceIn(0, 100) else 0
                    // Persisting the segment offsets alongside the progress is
                    // what makes an interrupted download resumable rather than
                    // restartable — it costs one extra column on a write we
                    // were already doing.
                    dao.updateResumeState(id, state, pct, downloaded, total)
                    if (pct % 20 == 0) refreshTotals()
                }
            },
            onDone = { path ->
                scope.launch {
                    val e = dao.byId(id) ?: return@launch
                    val bytes = e.sizeBytes.coerceAtLeast(e.downloadedBytes)
                    dao.updateResumeState(id, "", 100, bytes, bytes)
                    dao.markDone(id, path)
                    synchronized(jobsLock) { parallelJobs.remove(id) }
                    refreshTotals()
                }
            },
            onError = { msg ->
                scope.launch {
                    dao.markFailed(id, msg)
                    synchronized(jobsLock) { parallelJobs.remove(id) }
                }
            },
            onPaused = { downloaded, total, state ->
                scope.launch {
                    val pct = if (total > 0) ((downloaded * 100.0) / total).toInt().coerceIn(0, 100) else 0
                    dao.markPaused(id, state, pct, downloaded, total)
                    synchronized(jobsLock) { parallelJobs.remove(id) }
                    refreshTotals()
                }
            },
        )
        synchronized(jobsLock) { parallelJobs[id] = handle }
    }

    private fun plannedFileFor(entry: DownloadEntry): File {
        val ext = pickExtension(entry.sourceUrl)
        val safeTitle = sanitizeFilename(entry.title).ifBlank { entry.id }
        return when (entry.kind) {
            "episode" -> {
                val seriesFolder = sanitizeFilename(entry.seriesName).ifBlank { "Series" }
                val season = entry.season.coerceAtLeast(1)
                val epNum = "E%02d".format(entry.episode.coerceAtLeast(0))
                val epLabel = sanitizeFilename(
                    entry.title.substringAfter("·", entry.title).trim().ifBlank { entry.title }
                ).ifBlank { epNum }
                File(File(File(defaultRoot, "Series"), seriesFolder).apply { mkdirs() }, "S%02d".format(season))
                    .apply { mkdirs() }
                    .let { File(it, "$epNum - $epLabel.$ext") }
            }
            else -> File(File(defaultRoot, "Movies").apply { mkdirs() }, "$safeTitle.$ext")
        }
    }

    // ---- Cancel / delete (both engines) ------------------------------------

    fun cancel(entryId: String) {
        scope.launch {
            val sysId = synchronized(jobsLock) { sysJobs.remove(entryId) }
            if (sysId != null) dm.remove(sysId)
            val parHandle = synchronized(jobsLock) { parallelJobs.remove(entryId) }
            parHandle?.cancel?.invoke()
            val entry = dao.byId(entryId) ?: return@launch
            runCatching {
                if (entry.filePath.startsWith("content://")) {
                    DocumentFile.fromSingleUri(app, entry.filePath.toUri())?.delete()
                } else if (entry.filePath.isNotBlank()) {
                    File(entry.filePath).delete()
                }
            }
            dao.delete(entryId)
            refreshTotals()
        }
    }

    fun delete(entryId: String) = cancel(entryId)

    fun observe(): Flow<List<DownloadEntry>> = dao.all()
    fun observeCompleted(entryId: String): Flow<Boolean> = dao.completedFlow(entryId)
    fun observeExists(entryId: String): Flow<Boolean> = dao.existsFlow(entryId)

    suspend fun refreshTotals() { _totalBytes.value = dao.totalBytes() }

    // ---- Polling loop (only used by the System engine) ---------------------

    private fun startPolling() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            while (isActive) {
                val active = synchronized(jobsLock) { sysJobs.toMap() }
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
                        synchronized(jobsLock) { sysJobs.remove(entryId) }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(reasonIdx)
                        dao.markFailed(entryId, "System code $reason")
                        synchronized(jobsLock) { sysJobs.remove(entryId) }
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
                    sysJobs.entries.firstOrNull { it.value == sysId }?.key
                } ?: return
                scope.launch { pollOnce(mapOf(entryId to sysId)) }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
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

    private fun descriptionFor(entry: DownloadEntry): String =
        if (entry.kind == "episode" && entry.seriesName.isNotBlank())
            "${entry.seriesName} — offline download"
        else "EnkTel offline download"

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

    private fun mimeForExt(ext: String): String = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        else -> "video/*"
    }
}

fun Long.humanBytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = this.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (u == 0) "$this B" else "%.1f %s".format(v, units[u])
}
