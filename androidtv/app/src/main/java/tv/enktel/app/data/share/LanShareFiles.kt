package tv.enktel.app.data.share

import android.content.Context
import androidx.core.net.toUri
import tv.enktel.app.data.db.DownloadEntry
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Turning what the app has into what [LanShareServer] can serve, and finding
 * the address to tell the viewer.
 *
 * Separate from the server so the server stays free of Android: it takes a
 * filename, a size and a way to open bytes, and does not care that one
 * download is a file on disk and the next is a document in a folder the viewer
 * picked.
 */
object LanShareFiles {

    /**
     * This device's address on the local network, or null when there isn't one.
     *
     * Walks the interfaces rather than asking WifiManager: the app also runs
     * on television boxes that are plugged in rather than on Wi-Fi, and
     * WifiManager reports nothing at all for those.
     */
    fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /**
     * Build the share list from finished downloads.
     *
     * Only `DONE` rows: a part-written file would transfer as a whole one and
     * arrive corrupt, which is worse than not offering it.
     */
    fun shareable(ctx: Context, entries: List<DownloadEntry>): List<LanShareServer.Shared> =
        entries.asSequence()
            .filter { it.status == "DONE" && it.filePath.isNotBlank() }
            .mapNotNull { entry -> shared(ctx, entry) }
            .toList()

    private fun shared(ctx: Context, entry: DownloadEntry): LanShareServer.Shared? {
        val path = entry.filePath
        val name = LanShare.safeFilename(displayName(entry), path.substringAfterLast('.', ""))

        if (path.startsWith("content://")) {
            val uri = path.toUri()
            val size = runCatching {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: return null
            if (size <= 0) return null
            return LanShareServer.Shared(
                token = LanShare.newToken(),
                filename = name,
                size = size,
                open = { runCatching { ctx.contentResolver.openInputStream(uri) }.getOrNull() },
            )
        }

        val file = File(path)
        if (!file.isFile || file.length() <= 0) return null
        return LanShareServer.Shared(
            token = LanShare.newToken(),
            filename = name,
            size = file.length(),
            // Re-opened per request rather than held: a browser resuming a
            // six-gigabyte film asks for it several times.
            open = { runCatching { file.inputStream() }.getOrNull() },
        )
    }

    /** "Show · S02E04 · Title", or just the title for a film. */
    private fun displayName(entry: DownloadEntry): String = when {
        entry.kind == "episode" && entry.seriesName.isNotBlank() ->
            "${entry.seriesName} S%02dE%02d %s".format(entry.season, entry.episode, entry.title)
        else -> entry.title
    }
}
