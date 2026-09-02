package tv.enktel.app.data.debrid

/**
 * Deciding which files inside a torrent are worth fetching.
 *
 * Real-Debrid will not start until it is told which files it wants, and the
 * honest default — everything — is the wrong one often enough to matter. A
 * season pack fetched whole pulls twenty episodes to watch one, and almost
 * every release carries a sample clip, a subtitle folder and an .nfo beside
 * the film.
 *
 * So this picks a sensible default and the viewer overrides it. Nothing here
 * refuses a selection; it only decides what is ticked when the picker opens.
 */
object TorrentFiles {

    private val VIDEO = setOf(
        "mkv", "mp4", "avi", "m4v", "mov", "ts", "m2ts", "mpg", "mpeg",
        "webm", "wmv", "flv", "divx", "ogm", "vob", "iso",
    )

    /**
     * A sample is at most this fraction of the biggest video in the torrent.
     *
     * Deliberately generous. Samples run under a hundredth of a feature; a
     * twentieth still catches them while leaving a genuinely short episode in
     * a mixed pack alone, and the cost of guessing wrong here is a file the
     * viewer has to tick by hand, not a file they cannot get.
     */
    private const val SAMPLE_FRACTION = 20

    /** "Sample" as a word, not as a substring — "Samples of Grace" is a title. */
    private val SAMPLE_WORD =
        Regex("(^|[^a-z0-9])samples?([^a-z0-9]|$)", RegexOption.IGNORE_CASE)

    fun isVideo(path: String): Boolean = extensionOf(path) in VIDEO

    /**
     * The last segment's extension, lowercased, or "" when there is none.
     *
     * Reads the segment rather than the whole path, because a directory with a
     * dot in its name — which release folders very often have — would
     * otherwise supply the extension for every file beneath it.
     */
    private fun extensionOf(path: String): String {
        val name = path.trimStart('/').substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** Is this the throwaway clip rather than the film? */
    fun isSample(path: String, bytes: Long, largestBytes: Long): Boolean {
        // The biggest video in a torrent is never the sample, whatever it is
        // called — which is what saves a film actually titled "Free Samples"
        // from being dropped as clutter by the name check below.
        if (largestBytes > 0 && bytes >= largestBytes) return false

        val segments = path.trimStart('/').split('/')
        if (SAMPLE_WORD.containsMatchIn(segments.last())) return true
        // A folder is only a sample folder when that is all it is called.
        // Matching the word anywhere in the path would take "Free.Samples.2011"
        // down with it, and the folder is where release groups put these.
        if (segments.dropLast(1).any { it.trim().lowercase() in SAMPLE_DIRS }) return true

        // Size alone is enough when nothing in the name says so, but only in
        // comparison: a small file in a torrent of small files is just a small
        // file.
        return largestBytes > 0 && bytes > 0 && bytes * SAMPLE_FRACTION < largestBytes
    }

    private val SAMPLE_DIRS = setOf("sample", "samples")

    /**
     * The file ids to tick when the picker opens.
     *
     * Falls back to everything when the filter would leave nothing — a torrent
     * of files this does not recognise is still a torrent the viewer asked
     * for, and an empty selection is one Real-Debrid refuses.
     */
    fun suggested(files: List<RealDebridClient.TorrentFile>): List<Int> {
        if (files.isEmpty()) return emptyList()
        val videos = files.filter { isVideo(it.path) }
        if (videos.isEmpty()) return files.map { it.id }
        val largest = videos.maxOf { it.bytes }
        val kept = videos.filterNot { isSample(it.path, it.bytes, largest) }
        return (kept.ifEmpty { videos }).map { it.id }
    }
}
