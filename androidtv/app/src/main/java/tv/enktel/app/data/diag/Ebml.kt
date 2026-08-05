package tv.enktel.app.data.diag

/**
 * Minimal EBML reader — enough to answer one question about a Matroska file:
 * **will seeking work?**
 *
 * That question has a precise answer inside the container. Media3's
 * `MatroskaExtractor` emits a real seek map only when it can locate the Cues
 * element; otherwise it emits `SeekMap.Unseekable` and every scrub restarts
 * the file. Cues are almost always written at the *end* of the file, so the
 * only way to know they exist without downloading gigabytes is to read the
 * SeekHead — the index near the start that records where each top-level
 * element lives.
 *
 * So this parses just the head of the file: the EBML header, the Segment, and
 * the SeekHead entries. A few kilobytes answers a question that would
 * otherwise take the whole download.
 *
 * Deliberately not a general Matroska parser. It reads what it needs, refuses
 * to allocate on hostile input, and reports what it could not determine rather
 * than guessing.
 */
object Ebml {

    // Top-level element IDs, as they appear on the wire (including the length
    // descriptor bits, which is how the spec writes them).
    const val ID_EBML_HEADER = 0x1A45DFA3L
    const val ID_SEGMENT = 0x18538067L
    const val ID_SEEK_HEAD = 0x114D9B74L
    const val ID_SEEK = 0x4DBBL
    const val ID_SEEK_ID = 0x53ABL
    const val ID_SEEK_POSITION = 0x53ACL
    const val ID_CUES = 0x1C53BB6BL
    const val ID_CLUSTER = 0x1F43B675L
    const val ID_INFO = 0x1549A966L
    const val ID_TRACKS = 0x1654AE6BL

    /** What the head of the file says about the container. */
    data class Head(
        /** The magic bytes matched an EBML header. */
        val isMatroska: Boolean = false,
        /** DocType, e.g. "matroska" or "webm". Blank when not read. */
        val docType: String = "",
        /** A SeekHead was present and parsed. */
        val hasSeekHead: Boolean = false,
        /** The SeekHead indexes a Cues element — seeking will work. */
        val hasCues: Boolean = false,
        /** Top-level element IDs found in the SeekHead, for the report. */
        val indexed: List<String> = emptyList(),
        /** Set when the head was truncated before the answer was reachable. */
        val truncated: Boolean = false,
    ) {
        /**
         * Whether Media3 will be able to seek this file.
         *
         * A Matroska with a SeekHead pointing at Cues seeks properly. One
         * without is at the mercy of where the Cues physically sit, which we
         * cannot see from the head alone — so this reports null ("unknown")
         * rather than claiming a negative it has not proven.
         */
        val seekable: Boolean? get() = when {
            !isMatroska -> null
            hasCues -> true
            hasSeekHead -> false // a SeekHead that indexes no Cues is conclusive
            else -> null
        }
    }

    /**
     * Reads a variable-length integer at [pos].
     *
     * @param keepMarker true for element IDs (the length marker is part of the
     *   ID as conventionally written); false for sizes, where it is stripped.
     * @return value to length-in-bytes, or null if [buf] is too short or the
     *   descriptor is invalid.
     */
    fun readVint(buf: ByteArray, pos: Int, keepMarker: Boolean): Pair<Long, Int>? {
        if (pos < 0 || pos >= buf.size) return null
        val first = buf[pos].toInt() and 0xFF
        if (first == 0) return null // more than 8 leading zero bits — not valid here
        var length = 1
        var mask = 0x80
        while (mask != 0 && (first and mask) == 0) {
            length++
            mask = mask shr 1
        }
        if (length > 8 || pos + length > buf.size) return null
        var value = if (keepMarker) first.toLong() else (first and (mask - 1)).toLong()
        for (i in 1 until length) {
            value = (value shl 8) or (buf[pos + i].toInt() and 0xFF).toLong()
        }
        return value to length
    }

    /**
     * Parses the head of a Matroska file.
     *
     * [buf] should hold the first few kilobytes. Anything the parser cannot
     * reach within those bytes is reported as [Head.truncated] rather than
     * assumed absent.
     */
    fun parseHead(buf: ByteArray): Head {
        val header = readVint(buf, 0, keepMarker = true) ?: return Head()
        if (header.first != ID_EBML_HEADER) return Head()

        var docType = ""
        // Walk to the Segment. The EBML header's own size tells us where it ends.
        val headerSize = readVint(buf, header.second, keepMarker = false)
            ?: return Head(isMatroska = true, truncated = true)
        val headerBodyStart = header.second + headerSize.second
        val headerBodyEnd = headerBodyStart + headerSize.first.toInt()
        // DocType (0x4282) lives inside the header body.
        docType = readDocType(buf, headerBodyStart, minOf(headerBodyEnd, buf.size))

        if (headerBodyEnd >= buf.size) {
            return Head(isMatroska = true, docType = docType, truncated = true)
        }

        val segment = readVint(buf, headerBodyEnd, keepMarker = true)
            ?: return Head(isMatroska = true, docType = docType, truncated = true)
        if (segment.first != ID_SEGMENT) {
            // Not fatal — the file is EBML, we just cannot index it.
            return Head(isMatroska = true, docType = docType, truncated = true)
        }
        val segSize = readVint(buf, headerBodyEnd + segment.second, keepMarker = false)
            ?: return Head(isMatroska = true, docType = docType, truncated = true)
        var pos = headerBodyEnd + segment.second + segSize.second

        // First child of Segment is conventionally the SeekHead.
        var hasSeekHead = false
        var hasCues = false
        val indexed = mutableListOf<String>()
        var guard = 0
        while (pos < buf.size && guard++ < 64) {
            val id = readVint(buf, pos, keepMarker = true) ?: break
            val size = readVint(buf, pos + id.second, keepMarker = false) ?: break
            val bodyStart = pos + id.second + size.second
            val bodyEnd = bodyStart + size.first.toInt()
            if (id.first == ID_SEEK_HEAD) {
                hasSeekHead = true
                val end = minOf(bodyEnd, buf.size)
                scanSeekHead(buf, bodyStart, end).forEach { seekId ->
                    indexed += nameFor(seekId)
                    if (seekId == ID_CUES) hasCues = true
                }
                if (bodyEnd > buf.size) {
                    return Head(true, docType, true, hasCues, indexed, truncated = true)
                }
            }
            // A Cluster means media data has started; nothing useful follows
            // for our purposes.
            if (id.first == ID_CLUSTER) break
            if (size.first <= 0 || bodyEnd <= pos) break
            pos = bodyEnd
        }
        return Head(
            isMatroska = true,
            docType = docType,
            hasSeekHead = hasSeekHead,
            hasCues = hasCues,
            indexed = indexed,
            truncated = false,
        )
    }

    /** Collects the target IDs recorded by each Seek entry in a SeekHead. */
    private fun scanSeekHead(buf: ByteArray, start: Int, end: Int): List<Long> {
        val out = mutableListOf<Long>()
        var pos = start
        var guard = 0
        while (pos < end && guard++ < 64) {
            val id = readVint(buf, pos, keepMarker = true) ?: break
            val size = readVint(buf, pos + id.second, keepMarker = false) ?: break
            val bodyStart = pos + id.second + size.second
            val bodyEnd = bodyStart + size.first.toInt()
            if (id.first == ID_SEEK) {
                // Inside a Seek: a SeekID element holds the indexed element's ID.
                var p = bodyStart
                var g2 = 0
                while (p < minOf(bodyEnd, end) && g2++ < 16) {
                    val cid = readVint(buf, p, keepMarker = true) ?: break
                    val csize = readVint(buf, p + cid.second, keepMarker = false) ?: break
                    val cBody = p + cid.second + csize.second
                    val cEnd = cBody + csize.first.toInt()
                    if (cid.first == ID_SEEK_ID && cEnd <= buf.size && csize.first in 1..8) {
                        var v = 0L
                        for (i in cBody until cEnd) v = (v shl 8) or (buf[i].toInt() and 0xFF).toLong()
                        out += v
                    }
                    if (cEnd <= p) break
                    p = cEnd
                }
            }
            if (bodyEnd <= pos) break
            pos = bodyEnd
        }
        return out
    }

    private fun readDocType(buf: ByteArray, start: Int, end: Int): String {
        var pos = start
        var guard = 0
        while (pos < end && guard++ < 32) {
            val id = readVint(buf, pos, keepMarker = true) ?: break
            val size = readVint(buf, pos + id.second, keepMarker = false) ?: break
            val bodyStart = pos + id.second + size.second
            val bodyEnd = bodyStart + size.first.toInt()
            if (id.first == 0x4282L && bodyEnd <= end) {
                return String(buf, bodyStart, size.first.toInt(), Charsets.US_ASCII).trim()
            }
            if (bodyEnd <= pos) break
            pos = bodyEnd
        }
        return ""
    }

    private fun nameFor(id: Long): String = when (id) {
        ID_CUES -> "Cues"
        ID_INFO -> "Info"
        ID_TRACKS -> "Tracks"
        ID_CLUSTER -> "Cluster"
        ID_SEEK_HEAD -> "SeekHead"
        else -> "0x${id.toString(16).uppercase()}"
    }
}
