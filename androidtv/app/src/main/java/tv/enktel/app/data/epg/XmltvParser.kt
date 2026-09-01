package tv.enktel.app.data.epg

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import tv.enktel.app.data.db.EpgProgram
import java.io.InputStream
import java.util.Locale

/**
 * Streaming XMLTV parser. Emits programmes in batches so multi-hundred-MB guides
 * never sit in memory. Only channels present in [wantedIds] are kept (empty = keep all).
 */
object XmltvParser {

    suspend fun parse(
        input: InputStream,
        profileId: Long,
        wantedIds: Set<String>,
        keepFromMs: Long,
        keepToMs: Long,
        batchSize: Int = 800,
        emit: suspend (List<EpgProgram>) -> Unit,
    ): Int {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val batch = ArrayList<EpgProgram>(batchSize)
        var total = 0

        var channelId = ""
        var startMs = 0L
        var endMs = 0L
        var title = ""
        var desc = ""
        // A programme may carry any number of <category> elements. Collected
        // whole rather than read like title and desc, because the useful value
        // is the set of them and the decision about which to keep belongs to
        // ProgrammeGenres, not here.
        val categories = ArrayList<String>(4)
        val categoryBuf = StringBuilder()
        var tag = ""
        var inProgramme = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "programme") {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel").orEmpty()
                        startMs = parseTime(parser.getAttributeValue(null, "start"))
                        endMs = parseTime(parser.getAttributeValue(null, "stop"))
                        title = ""; desc = ""
                        categories.clear(); categoryBuf.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> if (inProgramme) {
                    when (tag) {
                        "title" -> if (title.isEmpty()) title = parser.text.orEmpty().trim()
                        "desc" -> if (desc.isEmpty()) desc = parser.text.orEmpty().trim()
                        // Accumulated rather than taken whole: a parser splits
                        // text around an entity reference, so "Drama &amp; Crime"
                        // arrives as three events. Appending and flushing on the
                        // end tag keeps one category as one string instead of
                        // three fragments.
                        "category" -> categoryBuf.append(parser.text.orEmpty())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "category" && inProgramme) {
                        if (categoryBuf.isNotBlank()) categories += categoryBuf.toString()
                        categoryBuf.setLength(0)
                    }
                    if (parser.name == "programme") {
                        inProgramme = false
                        val keep = startMs in 1 until keepToMs && endMs > keepFromMs &&
                            (wantedIds.isEmpty() || channelId in wantedIds)
                        if (keep && title.isNotEmpty()) {
                            batch += EpgProgram(
                                profileId = profileId, epgId = channelId,
                                startMs = startMs, endMs = endMs,
                                // Sanitised once here, at parse time, so every
                                // reader (guide, Sports Hub, now-playing bar,
                                // search) gets the clean title without each
                                // having to strip it again. Mirrors how channel
                                // and VOD names are handled at sync time.
                                title = tv.enktel.app.data.metadata.TitleSanitizer.cleanProgramme(title),
                                desc = desc,
                                // Normalised here for the same reason the title
                                // is: every screen that shows it gets the clean
                                // value without repeating the work.
                                genre = ProgrammeGenres.normalise(categories),
                            )
                            if (batch.size >= batchSize) {
                                emit(batch.toList()); total += batch.size; batch.clear()
                            }
                        }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        if (batch.isNotEmpty()) { emit(batch.toList()); total += batch.size }
        return total
    }

    /**
     * XMLTV time: `20260716203000 +0000` (zone optional).
     *
     * Three legal spellings used to return 0, which is worse than it sounds: a
     * programme timestamped 0 lands in January 1970, so it silently disappears
     * from the guide instead of failing loudly. All three came from deciding the
     * format on string length and handing the rest straight to SimpleDateFormat:
     *
     *  - `20260716203000 Z` and `20260716203000Z` — `Z` is XMLTV's spelling of
     *    UTC, but the `Z` *pattern* letter means an RFC-822 offset like `+0000`
     *    and rejects a literal `Z`.
     *  - `20260716203000+0000` — no space. Longer than 14 characters, so the
     *    zoned pattern was chosen, and that pattern demands a literal space.
     *
     * Normalising the string first means one pattern handles every shape, and
     * named zones (`GMT`, `UTC`) keep working through the general-timezone path.
     */
    fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0
        val raw = value.trim()
        // 14 digits, optionally followed by a zone that may or may not have a
        // space in front of it.
        val m = TIME_RE.matchEntire(raw) ?: return 0
        val stamp = m.groupValues[1]
        val zone = m.groupValues[2].trim().let { if (it.equals("Z", true)) "+0000" else it }

        return try {
            // Called twice for every programme in the guide, so the formatter
            // is fetched from the per-thread cache rather than rebuilt — a full
            // XMLTV runs to six figures of programmes.
            val fmt = if (zone.isEmpty()) {
                tv.enktel.app.data.TimeFormat.formatter("yyyyMMddHHmmss", Locale.US)
            } else if (zone.first() == '+' || zone.first() == '-') {
                tv.enktel.app.data.TimeFormat.formatter("yyyyMMddHHmmss Z", Locale.US)
            } else {
                // Named zone — GMT, UTC, CET. General-timezone pattern.
                tv.enktel.app.data.TimeFormat.formatter("yyyyMMddHHmmss z", Locale.US)
            }
            val text = if (zone.isEmpty()) stamp else "$stamp $zone"
            fmt.parse(text)?.time ?: 0
        } catch (_: Exception) { 0 }
    }

    private val TIME_RE = Regex("""^(\d{14})\s*([+-]\d{4}|[A-Za-z]{1,5})?$""")
}
