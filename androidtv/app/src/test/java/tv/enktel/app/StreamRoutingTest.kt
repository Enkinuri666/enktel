package tv.enktel.app

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.enktel.app.player.PlayerEngine

/**
 * Container routing for Xtream URL shapes.
 *
 * These decide which extractor a stream reaches, and getting them wrong is not
 * subtle: an HLS playlist handed to a progressive source fails with
 * PARSING_CONTAINER_UNSUPPORTED every time, and a Matroska VOD reinterpreted as
 * HLS wastes a doomed round trip on the one screen where the user is already
 * waiting on a black frame.
 *
 * The functions under test are pure, so they are worth pinning even though the
 * engine around them needs a device.
 */
class StreamRoutingTest {

    // containerMimeFor / initialCandidate / asHlsRetry don't touch instance
    // state, so an unconstructed engine reference is not needed — they are
    // exercised through a lightweight holder.
    private val e = PlayerEngine.Routing

    @Test fun `mkv gets matroska pinned so sniffing is skipped`() {
        assertEquals(MimeTypes.VIDEO_MATROSKA, e.containerMimeFor("/movie/u/p/1234.mkv"))
        assertEquals(MimeTypes.VIDEO_MATROSKA, e.containerMimeFor("/movie/u/p/1234.MKV"))
    }

    @Test fun `webm and mp4 are pinned too`() {
        assertEquals(MimeTypes.VIDEO_WEBM, e.containerMimeFor("/a/b.webm"))
        assertEquals(MimeTypes.VIDEO_MP4, e.containerMimeFor("/a/b.mp4"))
        assertEquals(MimeTypes.VIDEO_MP4, e.containerMimeFor("/a/b.m4v"))
    }

    @Test fun `ts and extensionless stay unpinned`() {
        // A .ts URL is routinely answered with an HLS playlist by Xtream
        // panels, so it must keep sniffing and keep its HLS retry.
        assertNull(e.containerMimeFor("/live/u/p/99.ts"))
        assertNull(e.containerMimeFor("/live/u/p/99"))
    }

    @Test fun `m3u8 routes to HLS up front`() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            e.initialCandidate("http://x-api.cc/live/u/p/99.m3u8").mimeType,
        )
    }

    @Test fun `query strings do not defeat extension matching`() {
        assertEquals(
            MimeTypes.VIDEO_MATROSKA,
            e.initialCandidate("http://x-api.cc/movie/u/p/1.mkv?token=abc").mimeType,
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            e.initialCandidate("http://x-api.cc/live/u/p/9.m3u8?t=1#frag").mimeType,
        )
    }

    @Test fun `a matroska VOD is never retried as HLS`() {
        val mkv = e.initialCandidate("http://x-api.cc/movie/u/p/1.mkv")
        assertNull("reinterpreting an .mkv as HLS can only fail", e.asHlsRetry(mkv))
    }

    @Test fun `an extensionless live URL still gets its HLS retry`() {
        // This is the recovery path for PARSING_CONTAINER_UNSUPPORTED on
        // panels that serve a playlist from a URL that doesn't say so.
        val bare = e.initialCandidate("http://x-api.cc/live/u/p/99")
        assertEquals(MimeTypes.APPLICATION_M3U8, e.asHlsRetry(bare)?.mimeType)

        val ts = e.initialCandidate("http://x-api.cc/live/u/p/99.ts")
        assertEquals(MimeTypes.APPLICATION_M3U8, e.asHlsRetry(ts)?.mimeType)
    }

    @Test fun `an already-HLS candidate is not retried again`() {
        val hls = e.initialCandidate("http://x-api.cc/live/u/p/99.m3u8")
        assertNull(e.asHlsRetry(hls))
    }

    // ---- reinterpretation after a container failure ------------------------

    @Test fun `a lying extension is recoverable by unpinning it`() {
        // The bug this covers: Xtream panels routinely serve
        // /movie/user/pass/1234.mp4 that is Matroska on the wire. Pinned to MP4
        // by its extension, the MP4 extractor is handed MKV bytes and reports
        // the container malformed — and asHlsRetry declines any URL whose
        // extension names a container, so there was no second attempt at all.
        // The chain then walked to the next candidate, which carried the same
        // extension, took the same pin and failed identically.
        val mp4 = e.initialCandidate("http://x-api.cc/movie/u/p/1234.mp4")
        assertEquals(MimeTypes.VIDEO_MP4, mp4.mimeType)
        val retry = e.reinterpret(mp4)
        assertEquals("the pin must come off so the bytes get sniffed", "", retry?.mimeType)
        assertEquals(mp4.url, retry?.url)
    }

    @Test fun `mkv and webm unpin the same way`() {
        listOf("1.mkv", "1.webm", "1.m4v").forEach { name ->
            val c = e.initialCandidate("http://x-api.cc/movie/u/p/$name")
            assertEquals("$name should start pinned", true, c.mimeType.isNotBlank())
            assertEquals("$name should unpin", "", e.reinterpret(c)?.mimeType)
        }
    }

    @Test fun `an extensionless URL is still read as HLS`() {
        // Unchanged behaviour: nothing to unpin, so the HLS reading applies.
        val bare = e.initialCandidate("http://x-api.cc/live/u/p/99")
        assertEquals(MimeTypes.APPLICATION_M3U8, e.reinterpret(bare)?.mimeType)
    }

    @Test fun `reinterpretation terminates`() {
        // An unpinned progressive candidate must not produce another one, or a
        // malformed file would loop between readings instead of failing.
        val mp4 = e.initialCandidate("http://x-api.cc/movie/u/p/1234.mp4")
        val once = e.reinterpret(mp4)
        assertNull("a second reinterpretation would be a loop", once?.let { e.reinterpret(it) })
    }

    @Test fun `a playlist pin is never dropped`() {
        // Unpinning an .m3u8 would route a playlist to the progressive source,
        // which is a guaranteed failure rather than a recovery.
        val m3u8 = e.initialCandidate("http://x-api.cc/live/u/p/99.m3u8")
        assertEquals(MimeTypes.APPLICATION_M3U8, m3u8.mimeType)
        assertNull(e.asSniffRetry(m3u8))
        assertNull(e.reinterpret(m3u8))
    }
}
