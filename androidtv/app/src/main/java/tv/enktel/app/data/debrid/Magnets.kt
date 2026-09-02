package tv.enktel.app.data.debrid

import java.net.URLDecoder

/**
 * Reading a magnet link the viewer supplied.
 *
 * A magnet is a link someone already has — pasted from wherever they got it,
 * exactly like the hoster links [RealDebridClient.unrestrict] already accepts.
 * Nothing here goes looking for one.
 *
 * Two things are wanted out of it. The **infohash**, because Real-Debrid can
 * be asked whether it already holds that torrent before the account commits to
 * fetching it, and the **display name**, so a queued item reads as a title
 * rather than as forty characters of hex while it downloads.
 */
object Magnets {

    /** A v1 infohash: 40 hex characters, or the 32-character base32 spelling. */
    private val HEX_HASH = Regex("^[0-9a-fA-F]{40}$")
    private val B32_HASH = Regex("^[A-Z2-7]{32}$")

    data class Magnet(
        /** Lowercase hex, which is the spelling Real-Debrid's API expects. */
        val infoHash: String,
        /** From `dn=`, or "" when the link carries none. */
        val displayName: String,
        /** The link as given, to hand back to the service unchanged. */
        val uri: String,
    )

    /**
     * Parse [raw], or null when it is not a usable magnet.
     *
     * Null rather than a partial result: a magnet without an infohash cannot
     * be added, cannot be checked for availability, and is not worth carrying
     * around as an object that looks like it might work.
     */
    fun parse(raw: String): Magnet? {
        val text = raw.trim()
        if (!text.startsWith("magnet:?", ignoreCase = true)) return null

        var hash = ""
        var name = ""
        for (part in text.removePrefix("magnet:?").removePrefix("magnet:?").split('&')) {
            val key = part.substringBefore('=', "").lowercase()
            val value = part.substringAfter('=', "")
            when (key) {
                // A magnet may carry several xt values — a v2 hash beside a v1
                // one, or a checksum. Only the v1 btih is taken, and the first
                // of those wins, because that is the one the service indexes on.
                "xt" -> if (hash.isEmpty()) {
                    val urn = value.substringAfterLast("urn:btih:", "")
                    if (urn.isNotEmpty()) normaliseHash(urn)?.let { hash = it }
                }
                "dn" -> if (name.isEmpty()) name = decode(value)
            }
        }
        return if (hash.isEmpty()) null else Magnet(hash, name, text)
    }

    /** True when [raw] is a magnet this can use. */
    fun isMagnet(raw: String): Boolean = parse(raw) != null

    /**
     * Hex, lowercased. Base32 is accepted and converted, because plenty of
     * links still use it and refusing them would look like the link was bad.
     */
    private fun normaliseHash(s: String): String? {
        val t = s.trim()
        if (HEX_HASH.matches(t)) return t.lowercase()
        val upper = t.uppercase()
        if (!B32_HASH.matches(upper)) return null
        return base32ToHex(upper)
    }

    /** RFC 4648 base32 to hex. 32 base32 characters carry the same 160 bits. */
    private fun base32ToHex(s: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var acc = 0L
        val out = StringBuilder(40)
        for (c in s) {
            val v = alphabet.indexOf(c)
            if (v < 0) return null
            acc = (acc shl 5) or v.toLong()
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.append("%02x".format(((acc shr bits) and 0xFF).toInt()))
            }
        }
        return out.toString().take(40).takeIf { it.length == 40 }
    }

    /**
     * Percent-decode a display name.
     *
     * URLDecoder is form decoding, which turns "+" into a space. In a magnet's
     * `dn` a plus is usually exactly that — the names come from filenames
     * where spaces were replaced — so form decoding is the right reading here
     * even though it is the wrong one for a URL path.
     */
    private fun decode(v: String): String =
        runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v).trim()
}
