package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.diag.Ebml

/**
 * The EBML head reader exists to answer one question — will this Matroska
 * seek? — from a few kilobytes instead of a whole download. These pin the
 * answer, including the cases where the honest answer is "unknown".
 */
class EbmlTest {

    @Test fun `vint sizes are decoded from the leading-zero descriptor`() {
        // 0x82 -> 1-byte vint, value 2 with the marker stripped.
        assertEquals(2L to 1, Ebml.readVint(byteArrayOf(0x82.toByte()), 0, keepMarker = false))
        // Same byte kept as an ID retains the marker bit.
        assertEquals(0x82L to 1, Ebml.readVint(byteArrayOf(0x82.toByte()), 0, keepMarker = true))
    }

    @Test fun `a four-byte element id is read whole`() {
        val buf = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        assertEquals(Ebml.ID_EBML_HEADER to 4, Ebml.readVint(buf, 0, keepMarker = true))
    }

    @Test fun `a zero descriptor byte is rejected rather than looping`() {
        assertNull(Ebml.readVint(byteArrayOf(0x00), 0, keepMarker = false))
    }

    @Test fun `a truncated vint returns null instead of reading past the end`() {
        // Descriptor claims 4 bytes but only 2 are present.
        assertNull(Ebml.readVint(byteArrayOf(0x1A, 0x45), 0, keepMarker = true))
    }

    @Test fun `non-matroska bytes are not claimed as matroska`() {
        val mp4 = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(),
            'y'.code.toByte(), 'p'.code.toByte())
        val head = Ebml.parseHead(mp4)
        assertFalse(head.isMatroska)
        assertNull("seekability is meaningless for a non-EBML file", head.seekable)
    }

    @Test fun `empty input does not throw`() {
        assertFalse(Ebml.parseHead(ByteArray(0)).isMatroska)
    }

    @Test fun `a seekhead indexing cues reports seekable`() {
        val head = Ebml.parseHead(matroska(withCues = true))
        assertTrue(head.isMatroska)
        assertTrue(head.hasSeekHead)
        assertTrue(head.hasCues)
        assertEquals(true, head.seekable)
        assertTrue(head.indexed.contains("Cues"))
    }

    @Test fun `a seekhead without cues reports not seekable`() {
        // This is the conclusive negative: the file indexed its elements and
        // Cues was not among them.
        val head = Ebml.parseHead(matroska(withCues = false))
        assertTrue(head.isMatroska)
        assertTrue(head.hasSeekHead)
        assertFalse(head.hasCues)
        assertEquals(false, head.seekable)
    }

    @Test fun `doctype is read from the header`() {
        assertEquals("matroska", Ebml.parseHead(matroska(withCues = true)).docType)
    }

    // ---- synthetic file construction -------------------------------------

    /** Builds a minimal EBML head: header + Segment + SeekHead. */
    private fun matroska(withCues: Boolean): ByteArray {
        val docType = "matroska".toByteArray(Charsets.US_ASCII)
        // DocType element: id 0x4282, size, payload
        val docTypeEl = byteArrayOf(0x42, 0x82.toByte(), (0x80 or docType.size).toByte()) + docType
        val headerBody = docTypeEl
        val header = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            (0x80 or headerBody.size).toByte()) + headerBody

        val seekEntries = mutableListOf<ByteArray>()
        seekEntries += seekEntry(Ebml.ID_TRACKS)
        if (withCues) seekEntries += seekEntry(Ebml.ID_CUES)
        val seekHeadBody = seekEntries.reduce { a, b -> a + b }
        val seekHead = byteArrayOf(0x11, 0x4D, 0x9B.toByte(), 0x74,
            (0x80 or seekHeadBody.size).toByte()) + seekHeadBody

        // Segment with an unknown-ish but explicit size covering the SeekHead.
        val segment = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67,
            (0x80 or seekHead.size).toByte()) + seekHead
        return header + segment
    }

    /** A Seek element whose SeekID names [id]. */
    private fun seekEntry(id: Long): ByteArray {
        val idBytes = idToBytes(id)
        val seekId = byteArrayOf(0x53, 0xAB.toByte(), (0x80 or idBytes.size).toByte()) + idBytes
        return byteArrayOf(0x4D, 0xBB.toByte(), (0x80 or seekId.size).toByte()) + seekId
    }

    private fun idToBytes(id: Long): ByteArray {
        var v = id
        val out = mutableListOf<Byte>()
        while (v > 0) { out.add(0, (v and 0xFF).toByte()); v = v shr 8 }
        return out.toByteArray()
    }
}
