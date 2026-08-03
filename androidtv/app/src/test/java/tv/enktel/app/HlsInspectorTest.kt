package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.diag.HlsInspector

/**
 * The structural traps that make ExoPlayer stall on an HLS line. Each case is
 * a shape seen in the wild, not a spec exercise.
 */
class HlsInspectorTest {

    @Test fun `a master playlist is told apart from a media playlist`() {
        val master = HlsInspector.parse(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080
            1080/index.m3u8
            """.trimIndent(),
        )
        assertEquals(HlsInspector.Kind.MASTER, master.kind)
        assertEquals(2, master.variants.size)
        assertEquals(1_200_000L, master.variants[0].bandwidth)
        assertEquals("1280x720", master.variants[0].resolution)
        assertEquals("720/index.m3u8", master.variants[0].uri)
    }

    @Test fun `a live media playlist has no endlist and is not seekable`() {
        val live = HlsInspector.parse(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg1.ts
            #EXTINF:6.0,
            seg2.ts
            """.trimIndent(),
        )
        assertEquals(HlsInspector.Kind.MEDIA, live.kind)
        assertEquals(6, live.targetDurationSec)
        assertEquals(2, live.segmentCount)
        assertTrue(live.isLive)
        assertFalse("a sliding window cannot be seeked", live.seekable)
    }

    @Test fun `a VOD playlist is seekable`() {
        val vod = HlsInspector.parse(
            """
            #EXTM3U
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            a.ts
            #EXT-X-ENDLIST
            """.trimIndent(),
        )
        assertTrue(vod.seekable)
        assertFalse(vod.isLive)
        assertTrue(vod.hasEndList)
    }

    @Test fun `discontinuities are counted but the sequence tag is not`() {
        val p = HlsInspector.parse(
            """
            #EXTM3U
            #EXT-X-DISCONTINUITY-SEQUENCE:4
            #EXTINF:6.0,
            a.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:6.0,
            b.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:6.0,
            c.ts
            """.trimIndent(),
        )
        assertEquals("the SEQUENCE tag is metadata, not a discontinuity", 2, p.discontinuities)
    }

    @Test fun `a variant naming an undeclared audio group is flagged`() {
        // ExoPlayer waits on a rendition the panel never serves — this is the
        // classic "plays ten seconds then buffers forever".
        val p = HlsInspector.parse(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="aac-main"
            v.m3u8
            """.trimIndent(),
        )
        assertEquals(listOf("aac-main"), p.danglingAudioGroups)
    }

    @Test fun `a declared audio group is not flagged`() {
        val p = HlsInspector.parse(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac-main",NAME="English",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="aac-main"
            v.m3u8
            """.trimIndent(),
        )
        assertTrue(p.danglingAudioGroups.isEmpty())
        assertTrue(p.mediaGroups.contains("aac-main"))
    }

    @Test fun `non-HLS text is rejected rather than half-parsed`() {
        val p = HlsInspector.parse("<html><body>404 Not Found</body></html>")
        assertEquals(HlsInspector.Kind.NOT_HLS, p.kind)
        assertEquals("missing #EXTM3U header", p.error)
    }

    @Test fun `quoted and bare attribute values both parse`() {
        val line = """#EXT-X-STREAM-INF:BANDWIDTH=1200000,CODECS="avc1.4d401f",RESOLUTION=1280x720"""
        assertEquals("1200000", HlsInspector.attr(line, "BANDWIDTH"))
        assertEquals("avc1.4d401f", HlsInspector.attr(line, "CODECS"))
        assertEquals("1280x720", HlsInspector.attr(line, "RESOLUTION"))
    }
}
