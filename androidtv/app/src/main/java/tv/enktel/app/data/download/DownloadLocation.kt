package tv.enktel.app.data.download

/**
 * Whether a finished download can actually be shown to the viewer, and what to
 * say when it cannot.
 *
 * ## The thing that makes this awkward
 *
 * By default downloads land in the app's own external directory —
 * `/Android/data/<package>/files/Movies`. From Android 11 that path is closed:
 * no third-party file manager may browse `/Android/data`, the system Files app
 * will not show it, and `ACTION_OPEN_DOCUMENT_TREE` **refuses to grant it**
 * even if the viewer navigates there by hand. So a "show me the folder" button
 * on a default download is not a button that is hard to write; it is a button
 * that cannot exist.
 *
 * The app already has the answer: Settings can point downloads at a folder the
 * viewer picks, which is an ordinary folder they can open, back up, and read
 * from a PC over USB. So the button's job on a default download is not to fail
 * quietly — it is to explain that in one sentence and offer to set that folder,
 * which fixes every download from then on.
 */
object DownloadLocation {

    /** What the folder button should do for a given download. */
    sealed interface Reveal {
        /** The file is in a folder the viewer chose; it can be opened. */
        data class Folder(val treeUri: String) : Reveal

        /**
         * The file is somewhere the system will not let anything open.
         * [because] is one sentence for the viewer, not a stack trace.
         */
        data class Sealed(val path: String, val because: String) : Reveal

        /** An ordinary path on older Android, where a file manager can reach it. */
        data class Path(val path: String) : Reveal

        /** Nothing has been written yet — the download has not finished. */
        data object NotYet : Reveal
    }

    /** Android 11. The release that closed `/Android/data` to everything else. */
    const val SCOPED_STORAGE_SDK = 30

    private const val PRIVATE_MARKER = "/Android/data/"

    /**
     * @param filePath what the download recorded — a `content://` document URI
     *   when the viewer picked a folder, otherwise a filesystem path.
     * @param sdkInt the running Android version, passed in so this stays
     *   testable off-device.
     */
    fun reveal(filePath: String, sdkInt: Int): Reveal {
        val path = filePath.trim()
        if (path.isEmpty()) return Reveal.NotYet

        // A document URI is in the viewer's own folder by construction: SAF
        // will not grant one under /Android/data.
        if (path.startsWith("content://")) return Reveal.Folder(path)

        if (path.contains(PRIVATE_MARKER) && sdkInt >= SCOPED_STORAGE_SDK) {
            return Reveal.Sealed(
                path,
                "Android keeps this folder private to the app, so no file manager can open it. " +
                    "Pick a download folder and everything you save from now on lands somewhere " +
                    "you can reach — including from a PC.",
            )
        }
        return Reveal.Path(path)
    }

    /**
     * The label for the button, which differs because the two cases do
     * different things and a single word for both would be a small lie.
     */
    fun buttonLabel(reveal: Reveal): String = when (reveal) {
        is Reveal.Folder -> "📁 Show folder"
        is Reveal.Path -> "📁 Show folder"
        is Reveal.Sealed -> "📁 Where is this?"
        Reveal.NotYet -> "📁 Show folder"
    }
}
