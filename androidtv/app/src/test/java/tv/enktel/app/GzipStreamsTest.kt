package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.net.gunzipIfNeeded
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The M3U and XMLTV downloaders both used to decide this from the
 * `Content-Encoding` header, and both read it backwards: OkHttp only
 * decompresses transparently when it added `Accept-Encoding: gzip` itself, so
 * on a request that sets the header by hand the body is still compressed and
 * the header is still there. A 2,923-channel playlist reached the parser as
 * gzip, parsed to nothing, and reported a successful sync of an empty
 * catalogue.
 */
class GzipStreamsTest {

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray): String =
        gunzipIfNeeded(ByteArrayInputStream(bytes)).reader(Charsets.UTF_8).use { it.readText() }

    private val playlist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"BBCOne.uk\",BBC One\nhttp://example.com/1.m3u8\n"

    /** The case that shipped broken: still compressed when the parser gets it. */
    @Test
    fun `decompresses a gzipped body`() {
        assertEquals(playlist, read(gzip(playlist)))
    }

    /** OkHttp already decoded it — the bytes are plain and must pass through. */
    @Test
    fun `leaves plain text alone`() {
        assertEquals(playlist, read(playlist.toByteArray()))
    }

    /** Nothing here should care what a header claimed; the bytes decide. */
    @Test
    fun `decides from the bytes, not from any declared encoding`() {
        // Same two calls, no encoding argument to get wrong in either direction.
        assertEquals(playlist, read(gzip(playlist)))
        assertEquals(playlist, read(playlist.toByteArray()))
    }

    /** A body shorter than the two magic bytes must not throw. */
    @Test
    fun `handles a body too short to sniff`() {
        assertEquals("#", read("#".toByteArray()))
        assertEquals("", read(ByteArray(0)))
    }

    /** Large enough to cross the buffer boundary the sniff marks against. */
    @Test
    fun `decompresses a body larger than the sniff buffer`() {
        val big = buildString {
            append("#EXTM3U\n")
            repeat(20_000) { append("#EXTINF:-1,Channel $it\nhttp://example.com/$it.m3u8\n") }
        }
        assertEquals(big, read(gzip(big)))
    }
}
