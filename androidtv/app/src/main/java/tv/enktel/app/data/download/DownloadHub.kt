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
    /** Needed for the line's declared `max_connections` — see [streamsFor]. */
    private val profiles: tv.enktel.app.data.db.ProfileDao,
    private val settings: SettingsStore,
    private val http: OkHttpClient,
) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "downloads"
        const val NOTIFICATION_CHANNEL_NAME = "Downloads"
        private const val POLL_INTERVAL_MS = 1_500L
        private const val UA = tv.enktel.app.DEFAULT_UA
        /** Used when the panel doesn't report a connection cap. */
        private const val DEFAULT_STREAMS = 4
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

    /** Last percentage the rail totals were recomputed at, so a whole-table
     *  recount happens once per twenty-percent mark rather than once per tick
     *  spent inside it. */
    @Volatile private var lastTotalsPct = -1

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: kotlinx.coroutines.flow.StateFlow<Long> = _totalBytes.asStateFlow()

    /**
     * Live transfer rate per download, bytes/sec.
     *
     * Deliberately in memory and not on the DownloadEntry row. A rate is only
     * meaningful while bytes are actually moving — persisting it would leave a
     * paused or failed download proudly displaying the speed it managed
     * several hours ago, and would cost a schema migration to store something
     * that's wrong the moment the app is reopened.
     */
    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val speeds: kotlinx.coroutines.flow.StateFlow<Map<String, Long>> = _speeds.asStateFlow()

    /** Last progress sample per download, for turning deltas into a rate. */
    private class RateSample(var atMs: Long, var bytes: Long)
    private val rateSamples = java.util.concurrent.ConcurrentHashMap<String, RateSample>()
    private val speedsLock = Any()

    /**
     * Folds a progress tick into a smoothed bytes/sec figure.
     *
     * Exponentially smoothed rather than instantaneous: range workers report in
     * bursts as their buffers flush, so a raw delta swings wildly between 0 and
     * several hundred MB/s and is unreadable. The 0.3 weight settles within a
     * few seconds while still tracking a genuine slowdown.
     */
    private fun recordRate(id: String, downloaded: Long) {
        val now = System.currentTimeMillis()
        val prev = rateSamples.putIfAbsent(id, RateSample(now, downloaded)) ?: return
        // Progress callbacks are dispatched as independent coroutines, so
        // several can land at once and out of order. Sampling under the entry's
        // own lock keeps one pair of (time, bytes) readings together; without
        // it two threads read the same baseline and both bill the same bytes.
        val instant = synchronized(prev) {
            val dtMs = now - prev.atMs
            // Ignore ticks closer than half a second — too short to divide by.
            if (dtMs < 500) return
            val dBytes = downloaded - prev.bytes
            // A lower figure is a callback that overtook a newer one, or a
            // resume from an earlier offset. Neither is a slowdown, and folding
            // it in makes the reading alternate between double and zero.
            if (dBytes <= 0) return
            prev.atMs = now
            prev.bytes = downloaded
            dBytes * 1000 / dtMs
        }
        synchronized(speedsLock) {
            val previous = _speeds.value[id] ?: instant
            val smoothed = (previous * 0.7 + instant * 0.3).toLong()
            _speeds.value = _speeds.value + (id to smoothed)
        }
    }

    private fun clearRate(id: String) {
        rateSamples.remove(id)
        synchronized(speedsLock) { _speeds.value = _speeds.value - id }
    }

    /** Default root under app-scoped external storage. Used when the user
     *  hasn't picked a folder via SAF (see [settings.downloadFolderUri]). */
    val defaultRoot: File = (app.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(app.filesDir, "downloads")).apply { mkdirs() }

    init {
        ensureNotificationChannel()
        registerCompletionReceiver()
        reconcileOnStart()
        watchForUnmeteredNetwork()
    }

    /**
     * Restarts downloads parked by the Wi-Fi-only policy once an unmetered
     * connection comes back.
     *
     * Without this, "Waiting for Wi-Fi" would mean "waiting for you to notice
     * and press play", which is not what the setting promises. Keyed off
     * [tv.enktel.app.data.net.NetworkClass]'s transport flow, which is already
     * maintained for the player's buffer sizing, so this costs one collector
     * and no extra system callbacks.
     */
    private fun watchForUnmeteredNetwork() {
        scope.launch {
            tv.enktel.app.data.net.NetworkClass.kind.collect {
                if (!settings.downloadsWifiOnlyNow()) return@collect
                if (blockedByMeteredPolicy(true)) return@collect
                // Unmetered again — pick up anything parked for this reason.
                try {
                    dao.waitingForWifi().forEach { e -> resume(e.id) }
                } catch (_: Throwable) { /* best effort */ }
            }
        }
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

    /**
     * True when downloading right now would run over a metered connection the
     * user has asked us to avoid.
     *
     * Deliberately checks the OS's own metered flag rather than just "is this
     * Wi-Fi": a tethered hotspot reports as Wi-Fi transport but is still the
     * user's cellular allowance, and that's exactly the case where an
     * accidental 4 GB download hurts most.
     */
    private fun blockedByMeteredPolicy(wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return false
        return try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            @Suppress("DEPRECATION")
            cm.isActiveNetworkMetered
        } catch (_: Throwable) {
            false // can't tell — don't block the user on a guess
        }
    }

    /** Enqueue a movie or episode. Idempotent per [entry.id]. */
    fun enqueue(entry: DownloadEntry) {
        scope.launch {
            val existing = dao.byId(entry.id)
            if (existing != null && existing.status == "DONE") return@launch

            // Park rather than fail. A download that silently does nothing is
            // the worst outcome here — the row states plainly why it's waiting,
            // and the network watcher below starts it the moment Wi-Fi returns.
            if (blockedByMeteredPolicy(settings.downloadsWifiOnlyNow())) {
                dao.upsert(
                    entry.copy(
                        status = "PAUSED",
                        errorMessage = "Waiting for Wi-Fi — connection is metered",
                    )
                )
                return@launch
            }

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

    /**
     * How many concurrent range fetches this profile's line can actually take.
     *
     * Xtream panels publish a `max_connections` cap per line and enforce it by
     * killing the surplus sockets mid-transfer. The downloader used to open
     * four regardless, so on the very common 1- and 2-connection lines most of
     * its workers were cut off — surfacing as "the server ended the transfer
     * early" and a download stuck at a few MB. The cap is already stored on the
     * profile at setup, so there's no reason to guess.
     *
     * One connection is always held back for playback: a download that locks
     * the user out of watching their own service has traded one problem for a
     * worse one. A 1-connection line therefore downloads single-stream, which
     * is the most that line can honestly support anyway.
     *
     * 0 means unknown (M3U playlists, or a panel that didn't report it) — those
     * keep the default, and the adaptive throttle still backs off if the host
     * turns out to be stricter than advertised.
     */
    private suspend fun streamsFor(profileId: Long): Int {
        val maxConn = try { profiles.byId(profileId)?.maxConnections ?: 0 } catch (_: Throwable) { 0 }
        return when {
            maxConn <= 0 -> DEFAULT_STREAMS
            maxConn == 1 -> 1
            else -> (maxConn - 1).coerceIn(1, DEFAULT_STREAMS)
        }
    }

    private fun launchParallel(
        entry: DownloadEntry,
        target: ParallelDownloader.Target,
        resumeState: String = "",
    ) {
        val id = entry.id
        scope.launch {
        val streams = streamsFor(entry.profileId)
        val handle = parallel.start(
            url = entry.sourceUrl,
            target = target,
            userAgent = UA,
            maxStreams = streams,
            resumeState = resumeState,
            onProgress = { downloaded, total, state ->
                scope.launch {
                    val pct = if (total > 0) ((downloaded * 100.0) / total).toInt().coerceIn(0, 100) else 0
                    // Persisting the segment offsets is what makes an
                    // interrupted download resumable rather than restartable.
                    // It arrives on a slower clock than the progress itself
                    // (see ProgressTicker), and a blank one means "no new
                    // record this tick" — writing it anyway would erase the
                    // offsets and turn every resume into a restart.
                    if (state.isNotBlank()) {
                        dao.updateResumeState(id, state, pct, downloaded, total)
                    } else {
                        dao.updateProgress(id, pct, downloaded, total)
                    }
                    recordRate(id, downloaded)
                    // Only when the number actually changes. `pct % 20 == 0`
                    // is true for every tick inside a whole percent, so each
                    // twenty-percent mark fired a full-table recount ten or
                    // more times over rather than once.
                    if (pct != lastTotalsPct && pct % 20 == 0) {
                        lastTotalsPct = pct
                        refreshTotals()
                    }
                }
            },
            onDone = { path ->
                scope.launch {
                    val e = dao.byId(id) ?: return@launch
                    val bytes = e.sizeBytes.coerceAtLeast(e.downloadedBytes)
                    dao.updateResumeState(id, "", 100, bytes, bytes)
                    dao.markDone(id, path)
                    synchronized(jobsLock) { parallelJobs.remove(id) }
                    clearRate(id)
                    refreshTotals()
                }
            },
            onError = { msg ->
                scope.launch {
                    dao.markFailed(id, msg)
                    synchronized(jobsLock) { parallelJobs.remove(id) }
                    clearRate(id)
                }
            },
            onPaused = { downloaded, total, state ->
                scope.launch {
                    val pct = if (total > 0) ((downloaded * 100.0) / total).toInt().coerceIn(0, 100) else 0
                    dao.markPaused(id, state, pct, downloaded, total)
                    synchronized(jobsLock) { parallelJobs.remove(id) }
                    clearRate(id)
                    refreshTotals()
                }
            },
        )
        synchronized(jobsLock) { parallelJobs[id] = handle }
        }
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
                        clearRate(entryId)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(reasonIdx)
                        dao.markFailed(entryId, "System code $reason")
                        synchronized(jobsLock) { sysJobs.remove(entryId) }
                    }
                    DownloadManager.STATUS_PAUSED -> dao.updateProgress(entryId, "PAUSED", pct, soFar, size)
                    DownloadManager.STATUS_PENDING -> dao.updateProgress(entryId, "QUEUED", pct, soFar, size)
                    DownloadManager.STATUS_RUNNING -> {
                        dao.updateProgress(entryId, "RUNNING", pct, soFar, size)
                        recordRate(entryId, soFar)
                    }
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
