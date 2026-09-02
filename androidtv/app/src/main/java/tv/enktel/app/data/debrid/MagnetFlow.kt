package tv.enktel.app.data.debrid

/**
 * The waiting part of adding a magnet, as rules rather than as a loop.
 *
 * Adding a magnet is not one request. Real-Debrid reads the link, then waits
 * to be told which files it wants, then fetches them — and how long that takes
 * is the difference between a torrent it already holds (seconds) and one it
 * does not (minutes, sometimes longer). The app has to ask repeatedly, and
 * *how often* it asks is a real decision: the API allows 250 requests a minute
 * and counts refused ones toward that same limit, so a tight poll is not
 * merely rude, it can get the account blocked.
 *
 * The schedule below runs about thirty requests over two minutes, which leaves
 * the budget almost untouched while still feeling immediate on a cached
 * torrent.
 */
object MagnetFlow {

    /**
     * How long to keep watching before handing the viewer back their remote.
     *
     * Not a timeout on the download — Real-Debrid carries on fetching after
     * this, and the torrent appears in the account list when it is done. It is
     * a limit on how long a screen sits there saying "downloading".
     */
    const val MAX_WAIT_MS = 120_000L

    /** Quick at first, because a cached torrent is ready almost at once. */
    fun pollDelayMs(attempt: Int): Long = when {
        attempt < 5 -> 1_500L
        attempt < 15 -> 3_000L
        else -> 6_000L
    }

    /** Nothing more will happen to a torrent in one of these states. */
    fun isFailed(status: String): Boolean = status.trim().lowercase() in FAILED

    private val FAILED = setOf("error", "magnet_error", "virus", "dead")

    /**
     * What the screen says while it waits.
     *
     * Every one of Real-Debrid's own status words is a term of art —
     * "magnet_conversion", "waiting_files_selection" — and showing them raw
     * makes a working download look like an error message.
     */
    fun progressLine(status: String, progress: Int): String {
        val pct = progress.coerceIn(0, 100)
        return when (status.trim().lowercase()) {
            "magnet_conversion" -> "Reading the magnet…"
            "waiting_files_selection" -> "Choose what to fetch."
            "queued" -> "Queued at Real-Debrid."
            "downloading" -> "Real-Debrid is fetching this — $pct%."
            "compressing", "uploading" -> "Real-Debrid is finishing up — $pct%."
            "downloaded" -> "Ready to play."
            "error", "magnet_error" -> "Real-Debrid could not fetch this torrent."
            "virus" -> "Real-Debrid flagged this as unsafe and stopped."
            "dead" -> "No seeders — Real-Debrid could not fetch this."
            else -> "Working…"
        }
    }

    /**
     * What to say when the wait runs out with the torrent still going.
     *
     * It is not a failure and must not read as one: the account keeps
     * fetching, and the item is waiting under "In my account" when it lands.
     */
    fun stillGoingLine(name: String): String {
        val what = name.trim().ifEmpty { "This torrent" }
        return "$what is still downloading at Real-Debrid. " +
            "It will appear under \"In my account\" when it is ready."
    }
}
