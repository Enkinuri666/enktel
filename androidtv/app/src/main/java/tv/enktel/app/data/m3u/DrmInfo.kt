package tv.enktel.app.data.m3u

/**
 * The DRM a playlist declares for a channel.
 *
 * IPTV playlists carry this in Kodi's `#KODIPROP:` lines, which is the only
 * convention there is — the M3U format has nothing to say about encryption, so
 * every list that needs it borrowed inputstream.adaptive's properties:
 *
 * ```
 * #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
 * #KODIPROP:inputstream.adaptive.license_key=https://lic.example/wv|Content-Type=application/octet-stream|R{SSM}|
 * ```
 *
 * Two fields are stored per channel rather than five, because what a list
 * writes in `license_key` is one string with its own internal structure and
 * splitting it at sync time would mean re-deciding that structure in the
 * database. It is kept exactly as written and read apart here, once, where the
 * rules can be tested.
 *
 * ### What this cannot do
 *
 * Supporting Widevine is not the same as being entitled to a stream. An
 * operator's licence server issues keys to *its subscribers*; a request from
 * outside is refused whatever the player supports. This makes a protected
 * stream playable when the list supplies a licence endpoint that will answer —
 * which is the common case for the free-to-air DASH channels these lists carry,
 * and not the case for a commercial IPTV product's own CDN.
 */
data class DrmInfo(
    /** `widevine` | `playready` | `clearkey`, or blank for none. */
    val scheme: String,
    /** `license_key` exactly as the playlist wrote it. */
    val license: String,
) {
    val isEmpty: Boolean get() = scheme.isBlank() || license.isBlank()

    /**
     * The licence endpoint, for the schemes that fetch one.
     *
     * `license_key` is pipe-separated with up to four parts —
     * `url|headers|body|response` — of which a player needs the first two. The
     * rest describe how to wrap the challenge, which ExoPlayer does itself.
     */
    val licenseUrl: String
        get() = if (scheme == CLEARKEY) "" else license.substringBefore('|').trim()

    /**
     * Headers to send with the licence request.
     *
     * `k=v&k2=v2`, with values percent-encoded because they routinely contain
     * `&`, `=` and spaces — a bearer token or a JSON blob is common. Decoding
     * is not optional: sending the encoded form is sending the wrong header.
     */
    val licenseHeaders: Map<String, String>
        get() {
            if (scheme == CLEARKEY) return emptyMap()
            val raw = license.split('|').getOrNull(1)?.trim().orEmpty()
            if (raw.isEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (pair in raw.split('&')) {
                val k = pair.substringBefore('=').trim()
                if (k.isEmpty()) continue
                out[k] = decode(pair.substringAfter('=', ""))
            }
            return out
        }

    /**
     * ClearKey keys as the JSON a licence response would have carried.
     *
     * Lists write these two ways: the JSON itself, or Kodi's `kid:key`
     * shorthand in hex. The shorthand is the common one and has to be
     * converted, since what the player ultimately needs is a JWK set with the
     * values in base64url — the same bytes, spelled differently.
     *
     * Blank when this is not inline ClearKey, including when it is ClearKey
     * served from a licence URL, which needs no local response at all.
     */
    val clearKeyJson: String
        get() {
            if (scheme != CLEARKEY) return ""
            val v = license.trim()
            if (v.startsWith("{")) return v
            val keys = v.split(',').mapNotNull { pair ->
                val kid = hexToB64(pair.substringBefore(':').trim()) ?: return@mapNotNull null
                val key = hexToB64(pair.substringAfter(':', "").trim()) ?: return@mapNotNull null
                """{"kty":"oct","kid":"$kid","k":"$key"}"""
            }
            if (keys.isEmpty()) return ""
            return """{"keys":[${keys.joinToString(",")}],"type":"temporary"}"""
        }

    /** True when the keys are carried in the playlist rather than fetched. */
    val isInlineClearKey: Boolean get() = clearKeyJson.isNotBlank()

    /** True when a licence has to be fetched from a server. */
    val needsLicenseServer: Boolean get() = !isEmpty && licenseUrl.isNotBlank()

    companion object {
        const val WIDEVINE = "widevine"
        const val PLAYREADY = "playready"
        const val CLEARKEY = "clearkey"

        val NONE = DrmInfo(scheme = "", license = "")

        /**
         * Normalise the `license_type` a list writes.
         *
         * The DRM system identifiers are what appear in practice, but plenty of
         * lists write the short name, so both are accepted.
         */
        fun scheme(raw: String): String = when (raw.trim().lowercase()) {
            "com.widevine.alpha", "widevine" -> WIDEVINE
            "com.microsoft.playready", "playready" -> PLAYREADY
            "org.w3.clearkey", "clearkey" -> CLEARKEY
            else -> ""
        }

        /**
         * Read a `#KODIPROP:` line, returning the property and its value.
         *
         * Only the two that matter are recognised by the caller; this just
         * splits. `inputstream.adaptive.` is stripped because both the
         * qualified and bare spellings occur.
         */
        fun kodiProp(line: String): Pair<String, String>? {
            val body = line.substringAfter(':', "").trim()
            if (body.isEmpty() || '=' !in body) return null
            val key = body.substringBefore('=').trim().lowercase()
                .removePrefix("inputstream.adaptive.")
                .removePrefix("inputstreamaddon.")
            val value = body.substringAfter('=').trim()
            if (key.isEmpty() || value.isEmpty()) return null
            return key to value
        }

        private fun decode(s: String): String =
            runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

        private const val B64URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /**
         * 16 bytes of hex to base64url without padding, which is the only
         * spelling a JWK accepts. Null for anything that is not a key.
         *
         * Encoded by hand rather than with `android.util.Base64`, which is a
         * stub that throws under plain JVM unit tests — these rules are exactly
         * the kind that need testing, and reaching for the framework here would
         * have put them out of reach.
         */
        private fun hexToB64(hex: String): String? {
            val h = hex.replace("-", "")
            if (h.length != 32 || !h.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
            val bytes = ByteArray(16) { i ->
                ((Character.digit(h[i * 2], 16) shl 4) or Character.digit(h[i * 2 + 1], 16)).toByte()
            }
            // 16 bytes is not a multiple of 3, so the last group is a partial
            // one: 5 full triplets, then a byte that yields two characters.
            val sb = StringBuilder(22)
            var i = 0
            while (i + 2 < bytes.size) {
                val n = (bytes[i].toInt() and 0xFF shl 16) or
                    (bytes[i + 1].toInt() and 0xFF shl 8) or
                    (bytes[i + 2].toInt() and 0xFF)
                sb.append(B64URL[n ushr 18 and 0x3F])
                sb.append(B64URL[n ushr 12 and 0x3F])
                sb.append(B64URL[n ushr 6 and 0x3F])
                sb.append(B64URL[n and 0x3F])
                i += 3
            }
            when (bytes.size - i) {
                1 -> {
                    val n = bytes[i].toInt() and 0xFF shl 16
                    sb.append(B64URL[n ushr 18 and 0x3F])
                    sb.append(B64URL[n ushr 12 and 0x3F])
                }
                2 -> {
                    val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                    sb.append(B64URL[n ushr 18 and 0x3F])
                    sb.append(B64URL[n ushr 12 and 0x3F])
                    sb.append(B64URL[n ushr 6 and 0x3F])
                }
            }
            return sb.toString()
        }
    }
}
