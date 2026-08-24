package tv.enktel.app.data.net

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Decompress a response body if — and only if — it is actually still gzipped.
 *
 * OkHttp decompresses transparently, but **only when it added `Accept-Encoding:
 * gzip` itself**. A request that sets that header by hand opts out of it: the
 * body arrives as raw gzip bytes and `Content-Encoding: gzip` stays on the
 * response for the caller to act on. Both of this app's downloaders used to
 * read that header and take it to mean the opposite — "OkHttp already decoded
 * this" — and hand the compressed bytes straight to a text parser.
 *
 * Nothing threw. `M3uParser` found no `#EXTINF` in a stream of gzip, returned
 * an empty playlist, and the sync reported success over an empty catalogue —
 * which is how a 2,923-channel playlist arrived as "Synced · 0 channels" with
 * no error anywhere on screen.
 *
 * So the header is not consulted at all. The first two bytes are the only
 * authority worth having: they say what the stream *is*, whoever decompressed
 * it and whatever anyone claimed in a header. Every case lands correctly —
 * OkHttp already decoded it (no magic, pass through), the caller asked for
 * gzip itself (magic, decompress), a `.gz` URL (magic, decompress), a server
 * that gzips without saying so (magic, decompress).
 */
fun gunzipIfNeeded(input: InputStream): InputStream {
    // GZIPInputStream needs to re-read the bytes the sniff consumed, so the
    // stream has to support mark/reset before anything looks at it.
    val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)

    buffered.mark(2)
    val first = buffered.read()
    val second = buffered.read()
    buffered.reset()

    // 0x1f 0x8b, the gzip magic number. A truncated stream reads -1 here and
    // is left alone — the parser downstream gives a better error than a
    // decompressor would.
    return if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
}
