package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.enktel.app.data.diag.CatchupScheme
import tv.enktel.app.data.diag.PanelDoctor

/** Pure helpers from the probe layer — the parts that decide what a response means. */
class PanelDoctorTest {

    @Test fun `container is detected from magic bytes not the url`() {
        val ebml = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x00, 0x00, 0x00, 0x00)
        assertEquals("MATROSKA", PanelDoctor.detectContainer(ebml, "video/mp4"))

        val ftyp = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(),
            'y'.code.toByte(), 'p'.code.toByte())
        assertEquals("MP4", PanelDoctor.detectContainer(ftyp, ""))

        val m3u = "#EXTM3U\n#EXT-X-VERSION:3\n".toByteArray()
        assertEquals("HLS", PanelDoctor.detectContainer(m3u, ""))
    }

    @Test fun `mpeg-ts needs the sync byte to repeat at 188`() {
        val ts = ByteArray(400).also { it[0] = 0x47; it[188] = 0x47 }
        assertEquals("MPEG-TS", PanelDoctor.detectContainer(ts, ""))
        // A lone 0x47 with no second sync is not TS.
        val notTs = ByteArray(400).also { it[0] = 0x47 }
        assertEquals("UNKNOWN", PanelDoctor.detectContainer(notTs, ""))
    }

    @Test fun `content-type is the fallback when bytes are unreadable`() {
        assertEquals("HLS", PanelDoctor.detectContainer(ByteArray(0), "application/x-mpegurl"))
        assertEquals("MPEG-TS", PanelDoctor.detectContainer(ByteArray(0), "video/mp2t"))
        assertEquals("UNKNOWN", PanelDoctor.detectContainer(ByteArray(0), ""))
    }

    @Test fun `expected mime types match the specs`() {
        assertEquals("mpegurl", PanelDoctor.expectedMimeFor("HLS"))
        assertEquals("mp2t", PanelDoctor.expectedMimeFor("MPEG-TS"))
        assertEquals("matroska", PanelDoctor.expectedMimeFor("MATROSKA"))
        assertEquals("", PanelDoctor.expectedMimeFor("UNKNOWN"))
    }

    @Test fun `content-range start is parsed`() {
        assertEquals(12345L, PanelDoctor.parseRangeStart("bytes 12345-13000/99999"))
        assertEquals(0L, PanelDoctor.parseRangeStart("bytes 0-1023/2048"))
    }

    @Test fun `a malformed content-range yields null rather than zero`() {
        // Returning 0 here would read as "the server put us at the start",
        // which is a different and wrong conclusion.
        assertNull(PanelDoctor.parseRangeStart(""))
        assertNull(PanelDoctor.parseRangeStart("garbage"))
    }

    @Test fun `catchup schemes are told apart`() {
        assertEquals(
            CatchupScheme.XTREAM_TIMESHIFT,
            PanelDoctor.detectCatchupScheme(null, "http://x/timeshift/u/p/60/2026-01-01:00-00/9.ts"),
        )
        assertEquals(CatchupScheme.APPEND, PanelDoctor.detectCatchupScheme("?utc=1234&lutc=1", null))
        assertEquals(CatchupScheme.SHIFT, PanelDoctor.detectCatchupScheme("/shift/9", null))
        assertEquals(CatchupScheme.FLUSSONIC, PanelDoctor.detectCatchupScheme("/archive-1234-3600.m3u8", null))
        assertEquals(CatchupScheme.UNKNOWN, PanelDoctor.detectCatchupScheme(null, null))
    }
}
