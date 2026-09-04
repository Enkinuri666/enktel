package tv.enktel.app.data.share

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tv.enktel.app.data.db.DownloadEntry
import tv.enktel.app.data.download.DownloadHub

/**
 * The download queue, as a paired EnkTel client on the PC may see and drive it.
 *
 * This is the whole of what the remote control can do — four verbs against one
 * id — and it is deliberately a short list. The server hands out a
 * [LanShareApi.Remote] and nothing else, so widening what a PC on the home
 * network can reach means editing this file, where the decision is visible,
 * rather than happening by accident because something new became public on the
 * hub.
 *
 * ### Why it mirrors rather than queries
 *
 * A request handler runs on a socket thread and Room's reads are suspending.
 * Blocking that thread on the database for every poll would be a database
 * round trip per second per connected PC, and `runBlocking` inside a request
 * is the kind of thing that looks fine until two clients poll at once.
 *
 * So it subscribes once and keeps the last list in memory. The flow is already
 * running for the Downloads screen, the snapshot is a few hundred bytes, and a
 * reply that is up to a second stale is exactly as stale as the polling client
 * asking for it.
 */
class DownloadRemote(
    private val hub: DownloadHub,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LanShareApi.Remote {

    @Volatile private var latest: List<LanShareApi.Job> = emptyList()

    private val subscription: Job = scope.launch {
        // Speeds live in memory and the rows live in the database; the PC
        // wants both in one row, so they are joined here rather than making
        // the client ask twice and correlate.
        combine(hub.observe(), hub.speeds) { entries, speeds ->
            entries.map { it.toJob(speeds[it.id] ?: 0L) }
        }.collect { latest = it }
    }

    override fun jobs(): List<LanShareApi.Job> = latest

    override fun act(id: String, action: LanShareApi.Action): Boolean {
        // Refused rather than silently accepted: a client that pauses a
        // download that no longer exists should be told, so its list refreshes
        // instead of showing a row that has been gone for a minute.
        if (latest.none { it.id == id }) return false
        when (action) {
            LanShareApi.Action.PAUSE -> hub.pause(id)
            LanShareApi.Action.RESUME -> hub.resume(id)
            LanShareApi.Action.RETRY -> hub.retry(id)
            // Cancel deletes the part-file as well as the row — the same thing
            // the phone's own cancel button does. It is the one destructive
            // verb here, which is why the PC client confirms it.
            LanShareApi.Action.CANCEL -> hub.cancel(id)
        }
        return true
    }

    /** Stops mirroring. Called when sharing stops. */
    fun close() {
        subscription.cancel()
        latest = emptyList()
    }
}

/**
 * One row, stripped to what a remote control needs.
 *
 * `sourceUrl` and `resumeState` are the two fields most obviously missing:
 * the first carries the line's username and password in the query string, and
 * neither belongs on a network hop just because the row they sit on is being
 * described.
 */
internal fun DownloadEntry.toJob(speedBps: Long): LanShareApi.Job = LanShareApi.Job(
    id = id,
    title = title,
    subtitle = if (kind == "episode" && seriesName.isNotBlank()) {
        "%s · S%02dE%02d".format(seriesName, season, episode)
    } else {
        ""
    },
    status = status,
    progressPct = progressPct,
    sizeBytes = sizeBytes,
    downloadedBytes = downloadedBytes,
    // A rate is only true while bytes are moving. Reporting the last known
    // figure for a paused download is how a PC ends up showing 12 MB/s next to
    // a progress bar that has not moved in an hour.
    speedBps = if (status == "RUNNING") speedBps else 0L,
    error = errorMessage.take(200),
)
