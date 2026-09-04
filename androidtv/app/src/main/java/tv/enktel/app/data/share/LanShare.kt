package tv.enktel.app.data.share

import tv.enktel.app.data.net.NetworkClass
import java.security.SecureRandom
import java.util.Locale

/**
 * The decisions behind handing a downloaded file to a PC on the same network.
 *
 * The app opens a small web server and the viewer types its address into a
 * browser on their computer. That is the whole design: nothing to install on
 * the PC, no cloud, no account, and the file never leaves the house.
 *
 * It is also, unavoidably, **a server listening on the home network**, so the
 * rules below are the security model and each one is tested:
 *
 * - **Nothing is served by path.** A request names an opaque token, and a
 *   token is only valid while the viewer has explicitly shared that download.
 *   There is no code path from a request to an arbitrary file, so directory
 *   traversal has nothing to traverse.
 * - **A PIN is required**, compared in constant time, and generated fresh
 *   every time the server starts — so a PIN glimpsed last week is not a PIN
 *   today.
 * - **Wi-Fi or wired only.** On mobile data the "local network" is the
 *   carrier's, which is not a network to open a door onto.
 *
 * Kept free of sockets and of Android so all of that can be tested.
 */
object LanShare {

    /** The port to try first. Above 1024, so no privilege is needed. */
    const val DEFAULT_PORT = 8787

    private val random = SecureRandom()

    /**
     * A six-digit PIN.
     *
     * Six digits is a million possibilities against a server that exists for
     * minutes, on a network the viewer already trusts, behind a rate limit —
     * and it has to be typed on a keyboard by someone reading it off a
     * television across the room. Longer would be worse in practice, because
     * it would get written down once and reused.
     */
    fun newPin(): String = (1..6).map { random.nextInt(10) }.joinToString("")

    /** An unguessable name for one shared file. */
    fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compare a supplied PIN against the real one without leaking where they
     * diverge.
     *
     * String equality returns as soon as two characters differ, and that
     * difference is measurable across a network — which turns a million
     * guesses into sixty. This walks the whole length whatever happens.
     */
    fun pinMatches(supplied: String, actual: String): Boolean {
        val a = supplied.toByteArray()
        val b = actual.toByteArray()
        // Length is not secret — the PIN is always six digits — but a length
        // mismatch must still cost the same as a value mismatch.
        var diff = a.size xor b.size
        for (i in b.indices) {
            val x = if (i < a.size) a[i].toInt() else 0
            diff = diff or (x xor b[i].toInt())
        }
        return diff == 0
    }

    /** What the viewer types into the browser on their PC. */
    fun shareUrl(ip: String, port: Int): String = "http://$ip:$port"

    /**
     * A byte range from an HTTP `Range` header, or null when there is no
     * usable one.
     *
     * Worth getting right rather than ignoring: a browser pulling six
     * gigabytes over Wi-Fi will lose the connection at some point, and without
     * range support it starts again from zero every time.
     */
    fun parseRange(header: String?, size: Long): LongRange? {
        if (size <= 0) return null
        val raw = header?.trim().orEmpty()
        if (!raw.startsWith("bytes=", ignoreCase = true)) return null
        // Only the first range. A multi-range reply is a multipart body that
        // nothing downloading a film ever asks for.
        val spec = raw.substring("bytes=".length).substringBefore(',').trim()
        if (spec.isEmpty()) return null

        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()

        return when {
            // "bytes=-500" — the last 500 bytes.
            startText.isEmpty() -> {
                val n = endText.toLongOrNull() ?: return null
                if (n <= 0) return null
                ((size - n).coerceAtLeast(0)) until size
            }
            else -> {
                val start = startText.toLongOrNull() ?: return null
                // A start past the end is not satisfiable, and answering it
                // with the whole file is how a resume silently corrupts.
                if (start < 0 || start >= size) return null
                val end = if (endText.isEmpty()) size - 1
                else (endText.toLongOrNull() ?: return null).coerceAtMost(size - 1)
                if (end < start) return null
                start..end
            }
        }
    }

    /** A filename a browser will accept in `Content-Disposition`. */
    fun safeFilename(title: String, extension: String): String {
        val cleaned = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "")
            // Runs of dots go too. Stripping separators alone turns
            // "../../etc/passwd" into "....etcpasswd", which is harmless but
            // reaches the viewer's disk looking like something that tried.
            .replace(Regex("\\.{2,}"), ".")
            .trimStart('.')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { "download" }
        val ext = extension.trim().removePrefix(".").lowercase(Locale.US)
        return if (ext.isEmpty()) cleaned else "$cleaned.$ext"
    }

    /** Enough of a MIME guess for a browser to do the right thing. */
    fun contentType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "ts" -> "video/mp2t"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "srt" -> "application/x-subrip"
            // Deliberately never text/html: a file served as HTML is a file
            // that can run script on this server's origin.
            else -> "application/octet-stream"
        }
    }

    /** True only where opening a listening socket is reasonable. */
    fun allowedOn(kind: NetworkClass.Kind): Boolean =
        kind == NetworkClass.Kind.WIFI || kind == NetworkClass.Kind.WIRED

    /** Why the server will not start, for the screen to show. */
    fun blockedReason(kind: NetworkClass.Kind): String? =
        if (allowedOn(kind)) null
        else "Sending to a PC needs Wi-Fi. On mobile data the local network is your carrier's, " +
            "so the app will not open a server there."
}
