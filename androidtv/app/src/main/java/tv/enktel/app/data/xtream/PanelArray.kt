package tv.enktel.app.data.xtream

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeToSequence
import tv.enktel.app.data.LenientJson
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream

/**
 * Turning a panel's list response into rows without ever holding the list.
 *
 * Separate from [XtreamClient] so it can be tested against a stream rather
 * than a socket: everything interesting here — what an empty body means, what
 * a non-array means, what a truncated response means — is a decision about
 * bytes, and none of it needs a panel to exercise.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object PanelArray {

    /**
     * Map each entry of a top-level JSON array as it is read.
     *
     * The index passed to [map] is the entry's position in the array, not its
     * position among the entries that survived — channels are numbered from it
     * when the panel supplies no `num`, so a skipped entry must not shift the
     * ones after it.
     */
    fun <T> mapEntries(
        stream: InputStream,
        action: String,
        map: (JsonElement, Int) -> T?,
    ): List<T> {
        // Peek one byte to tell "the panel sent nothing" from "the panel sent
        // something unreadable". Panels really do answer 200 with an empty
        // body, and that means an empty list, not a broken sync.
        val peekable = PushbackInputStream(stream, 1)
        val first = peekable.read()
        if (first < 0) return emptyList()
        peekable.unread(first)

        return try {
            LenientJson.decodeToSequence<JsonElement>(peekable, DecodeSequenceMode.ARRAY_WRAPPED)
                .mapIndexedNotNull { i, e -> map(e, i) }
                .toList()
        } catch (e: SerializationException) {
            // Deliberately louder than the code this replaced.
            //
            // That code read the whole payload, asked "is it an array?" and
            // quietly used an empty list when it was not — so a panel
            // answering `{"user_info":…}`, an HTML error page, and a response
            // cut off halfway all synced to an empty catalogue and then marked
            // themselves done. An empty catalogue is not a state a working
            // line should ever reach in silence.
            throw IOException("Panel did not return a list for $action (${e.message})", e)
        }
    }
}
