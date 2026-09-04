package tv.enktel.app.data.share

/**
 * The machine-readable half of the sharing server, and the model behind it.
 *
 * The browser pages in [LanShareServer] were the whole feature: type an
 * address, type a PIN, click a title. They still are, for anyone who does not
 * want to install anything. But an EnkTel client on the PC can do two things a
 * browser cannot, and both were asked for:
 *
 * - **Save without being asked where, every time.** A browser prompts per
 *   file; a client with a chosen folder takes twelve of them unattended.
 * - **Drive the queue.** Pausing a download, retrying a failed one, cancelling
 *   one that turned out to be the wrong episode — from the PC, without picking
 *   the phone back up.
 *
 * That second one is why this is an API and not a scraped page: a client
 * parsing the HTML would be a client that breaks the next time someone edits
 * the stylesheet, and there is no honest way to express "pause download 4" in
 * a page meant for a person.
 *
 * Everything here is pure and Android-free so it can be tested on the JVM. The
 * sockets, and the [Remote] implementation that reaches into the download
 * engine, live elsewhere.
 */
object LanShareApi {

    /** Bumped when a field changes meaning. The client refuses what it cannot read. */
    const val VERSION = 1

    /**
     * One download, as the PC client sees it.
     *
     * Deliberately not [tv.enktel.app.data.db.DownloadEntry]: that is a
     * database row with a resume blob, a source URL and a profile id in it,
     * none of which a remote control needs and the first two of which are
     * nobody else's business — `sourceUrl` carries the line's username and
     * password.
     */
    data class Job(
        val id: String,
        val title: String,
        /** "Show · S02E04", or blank for a film. */
        val subtitle: String,
        /** QUEUED | RUNNING | PAUSED | DONE | FAILED */
        val status: String,
        val progressPct: Int,
        val sizeBytes: Long,
        val downloadedBytes: Long,
        val speedBps: Long,
        val error: String,
    )

    /** What the queue can be told to do. */
    enum class Action { PAUSE, RESUME, RETRY, CANCEL;
        companion object {
            fun parse(raw: String): Action? =
                entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }

    /**
     * The download engine, as far as the server is concerned.
     *
     * An interface rather than a direct call into `DownloadHub` so the server
     * keeps compiling — and testing — without Android, and so the set of
     * things a paired PC can do is one readable list rather than whatever
     * happens to be public on the hub.
     */
    interface Remote {
        fun jobs(): List<Job>
        /** True when the action was applied. */
        fun act(id: String, action: Action): Boolean
    }

    // ── JSON ───────────────────────────────────────────────────────────

    /**
     * Enough JSON to write these three replies.
     *
     * Hand-rolled because the alternatives are worse here: `org.json` is an
     * Android platform class that is a stub under plain JVM unit tests, and a
     * serialization library is a dependency and a plugin for four object
     * shapes that never change. Escaping is the only part that has to be
     * right, so that is the part that is tested.
     */
    object Json {
        fun escape(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (c in s) {
                when {
                    c == '"' -> sb.append("\\\"")
                    c == '\\' -> sb.append("\\\\")
                    c == '\n' -> sb.append("\\n")
                    c == '\r' -> sb.append("\\r")
                    c == '\t' -> sb.append("\\t")
                    // Anything below space, plus the two line separators that
                    // are legal JSON but break a JavaScript parser reading it
                    // as source.
                    c < ' ' || c == '\u2028' || c == '\u2029' ->
                        sb.append("\\u%04x".format(c.code))
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        fun str(s: String): String = "\"${escape(s)}\""

        fun obj(vararg fields: Pair<String, String>): String =
            fields.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:$v" }

        fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

        fun num(v: Long): String = v.toString()
        fun num(v: Int): String = v.toString()
        fun bool(v: Boolean): String = if (v) "true" else "false"
    }

    // ── replies ────────────────────────────────────────────────────────

    /** Answer to a correct PIN. The token is what every later call carries. */
    fun pairedJson(token: String, deviceName: String, appVersion: String): String = Json.obj(
        "version" to Json.num(VERSION),
        "token" to Json.str(token),
        "device" to Json.str(deviceName),
        "app" to Json.str(appVersion),
    )

    fun errorJson(message: String): String = Json.obj("error" to Json.str(message))

    fun filesJson(files: List<LanShareServer.Shared>): String = Json.obj(
        "version" to Json.num(VERSION),
        "files" to Json.arr(
            files.sortedBy { it.filename }.map {
                Json.obj(
                    "token" to Json.str(it.token),
                    "name" to Json.str(it.filename),
                    "size" to Json.num(it.size),
                )
            },
        ),
    )

    fun jobsJson(jobs: List<Job>): String = Json.obj(
        "version" to Json.num(VERSION),
        "downloads" to Json.arr(
            jobs.map {
                Json.obj(
                    "id" to Json.str(it.id),
                    "title" to Json.str(it.title),
                    "subtitle" to Json.str(it.subtitle),
                    "status" to Json.str(it.status),
                    "progressPct" to Json.num(it.progressPct),
                    "sizeBytes" to Json.num(it.sizeBytes),
                    "downloadedBytes" to Json.num(it.downloadedBytes),
                    "speedBps" to Json.num(it.speedBps),
                    "error" to Json.str(it.error),
                )
            },
        ),
    )

    fun actedJson(applied: Boolean): String = Json.obj("ok" to Json.bool(applied))

    // ── discovery ──────────────────────────────────────────────────────

    /**
     * The UDP port the phone answers a "who's there" broadcast on.
     *
     * One above the HTTP port so the pair is easy to remember and to open in a
     * firewall together.
     */
    const val DISCOVERY_PORT = 8788

    /** What the PC broadcasts. Short and distinctive so nothing else matches it. */
    const val PROBE = "ENKTEL-DISCOVER-1"

    /**
     * What the phone broadcasts back.
     *
     * It names the device and the HTTP port and stops there. No PIN, no file
     * list, no line credentials — everything past this point needs the PIN
     * that is on the phone's screen. The most an eavesdropper on the home
     * network learns is that an EnkTel device is sharing, which they could
     * also learn by trying port 8787.
     */
    fun announceJson(deviceName: String, port: Int): String = Json.obj(
        "enktel" to Json.num(VERSION),
        "device" to Json.str(deviceName),
        "port" to Json.num(port),
    )
}
