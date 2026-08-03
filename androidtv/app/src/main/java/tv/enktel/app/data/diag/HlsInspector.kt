package tv.enktel.app.data.diag

/**
 * Reads an HLS playlist as text and reports the things that make ExoPlayer
 * stall.
 *
 * Pure and synchronous — it takes the playlist body, not a URL — so the whole
 * parser is unit-testable and the network layer stays in [PanelDoctor].
 *
 * Scope is deliberately the structural traps, not full RFC 8216 conformance:
 * a master playlist mistaken for a media playlist, variants that advertise
 * audio renditions the panel never serves, and discontinuity markers, which
 * are the usual cause of "plays for ten seconds then buffers forever".
 */
object HlsInspector {

    enum class Kind { MASTER, MEDIA, NOT_HLS }

    data class Variant(
        val bandwidth: Long = 0,
        val resolution: String = "",
        val codecs: String = "",
        /** AUDIO=... group this variant references. */
        val audioGroup: String = "",
        val uri: String = "",
    )

    data class Playlist(
        val kind: Kind = Kind.NOT_HLS,
        val version: Int = 0,
        /** EXT-X-PLAYLIST-TYPE: VOD | EVENT | (blank = live sliding window). */
        val playlistType: String = "",
        val targetDurationSec: Int = 0,
        val variants: List<Variant> = emptyList(),
        /** Rendition group ids declared by EXT-X-MEDIA. */
        val mediaGroups: Set<String> = emptySet(),
        val segmentCount: Int = 0,
        val discontinuities: Int = 0,
        val hasEndList: Boolean = false,
        /** Variants naming an AUDIO group with no matching EXT-X-MEDIA. */
        val danglingAudioGroups: List<String> = emptyList(),
        val error: String? = null,
    ) {
        /** A live sliding window: no ENDLIST and not declared VOD. */
        val isLive: Boolean get() = kind == Kind.MEDIA && !hasEndList && playlistType != "VOD"

        /** Seeking is only meaningful on a bounded playlist. */
        val seekable: Boolean get() = kind == Kind.MEDIA && (hasEndList || playlistType == "VOD")
    }

    fun parse(body: String): Playlist {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != "#EXTM3U") {
            return Playlist(kind = Kind.NOT_HLS, error = "missing #EXTM3U header")
        }

        var version = 0
        var playlistType = ""
        var target = 0
        var segments = 0
        var discontinuities = 0
        var endList = false
        val variants = mutableListOf<Variant>()
        val groups = mutableSetOf<String>()
        var pendingVariant: Variant? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-VERSION:") ->
                    version = line.substringAfter(':').trim().toIntOrNull() ?: 0

                line.startsWith("#EXT-X-PLAYLIST-TYPE:") ->
                    playlistType = line.substringAfter(':').trim().uppercase()

                line.startsWith("#EXT-X-TARGETDURATION:") ->
                    target = line.substringAfter(':').trim().toIntOrNull() ?: 0

                line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") ->
                    discontinuities++

                line == "#EXT-X-ENDLIST" -> endList = true

                line.startsWith("#EXT-X-MEDIA:") ->
                    attr(line, "GROUP-ID")?.let { groups += it }

                line.startsWith("#EXT-X-STREAM-INF:") ->
                    pendingVariant = Variant(
                        bandwidth = attr(line, "BANDWIDTH")?.toLongOrNull() ?: 0,
                        resolution = attr(line, "RESOLUTION").orEmpty(),
                        codecs = attr(line, "CODECS").orEmpty(),
                        audioGroup = attr(line, "AUDIO").orEmpty(),
                    )

                line.startsWith("#EXTINF:") -> segments++

                !line.startsWith("#") -> {
                    // A bare URI line closes whichever tag preceded it.
                    val v = pendingVariant
                    if (v != null) {
                        variants += v.copy(uri = line)
                        pendingVariant = null
                    }
                }
            }
        }

        val kind = when {
            variants.isNotEmpty() -> Kind.MASTER
            segments > 0 || target > 0 -> Kind.MEDIA
            else -> Kind.MEDIA // an empty media playlist is still a media playlist
        }

        // A variant pointing at an audio group the playlist never declares is
        // how ExoPlayer ends up waiting for a rendition that will never arrive.
        val dangling = variants.map { it.audioGroup }
            .filter { it.isNotBlank() && it !in groups }
            .distinct()

        return Playlist(
            kind = kind,
            version = version,
            playlistType = playlistType,
            targetDurationSec = target,
            variants = variants,
            mediaGroups = groups,
            segmentCount = segments,
            discontinuities = discontinuities,
            hasEndList = endList,
            danglingAudioGroups = dangling,
        )
    }

    /** Reads `KEY=value` or `KEY="value"` out of an EXT-X tag line. */
    internal fun attr(line: String, key: String): String? {
        val i = line.indexOf("$key=")
        if (i < 0) return null
        val rest = line.substring(i + key.length + 1)
        return if (rest.startsWith("\"")) {
            rest.drop(1).substringBefore('"').takeIf { it.isNotEmpty() }
        } else {
            rest.substringBefore(',').trim().takeIf { it.isNotEmpty() }
        }
    }
}
