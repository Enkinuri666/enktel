package tv.enktel.app.data.m3u

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Playlists the viewer supplies as a file rather than a URL.
 *
 * The file is **copied into app storage** at import rather than kept as a
 * `content://` reference. A picked URI is a grant, not a path: it can be
 * revoked, the document can be moved or deleted, and on most providers the
 * permission does not survive a reboot without being explicitly persisted. A
 * playlist has to be re-readable on every sync for the lifetime of the
 * profile, so the only dependable thing to hold is a copy.
 *
 * Extension is deliberately not consulted anywhere here. These files arrive as
 * `.m3u`, `.m3u8`, `.dat`, `.txt` and with no extension at all, and the name
 * says nothing about the contents — the parser decides.
 */
object PlaylistFiles {

    private const val DIR = "playlists"

    /** `file://` marks a playlist this app holds a copy of. */
    fun isLocal(url: String): Boolean = url.startsWith("file://", ignoreCase = true)

    /** Where imported copies live. Private to the app, so no permission applies. */
    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    /**
     * Copy a picked document into app storage.
     *
     * @return a `file://` URL for [tv.enktel.app.data.repo.PlaylistRepository.addM3u]
     * @throws java.io.IOException when the document cannot be opened or is empty
     */
    fun copyIn(ctx: Context, uri: Uri): String {
        val target = File(dir(ctx), "${System.currentTimeMillis()}.m3u")

        ctx.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw java.io.IOException("Could not open the selected file")

        // An empty copy would import as a profile that syncs to nothing and
        // reports it as an empty playlist, which reads as "your file is wrong"
        // when the truth is that nothing was read.
        if (target.length() == 0L) {
            target.delete()
            throw java.io.IOException("The selected file was empty")
        }

        return Uri.fromFile(target).toString()
    }

    /** Open an imported copy for reading. */
    fun open(url: String): java.io.InputStream {
        val path = Uri.parse(url).path ?: throw java.io.IOException("Bad playlist path")
        return File(path).inputStream()
    }

    /**
     * Delete the copy behind a profile, if it has one.
     *
     * Called when a profile is removed so an import does not leak a file that
     * nothing references any more.
     */
    fun forget(url: String) {
        if (!isLocal(url)) return
        runCatching { Uri.parse(url).path?.let { File(it).delete() } }
    }

    /**
     * A display name for a picked document.
     *
     * Falls back to the last path segment, which is usually the filename, and
     * finally to a generic label — an unnamed profile is worse than an
     * approximately named one.
     */
    fun displayName(ctx: Context, uri: Uri): String {
        val fromProvider = runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()

        val name = fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported playlist"
    }
}
