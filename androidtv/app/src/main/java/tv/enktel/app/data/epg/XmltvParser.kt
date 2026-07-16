package tv.enktel.app.data.epg

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import tv.enktel.app.data.db.EpgProgram
import java.io.InputStream
import java.text.SimpleDateFormat
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
                    }
                }
                XmlPullParser.TEXT -> if (inProgramme) {
                    when (tag) {
                        "title" -> if (title.isEmpty()) title = parser.text.orEmpty().trim()
                        "desc" -> if (desc.isEmpty()) desc = parser.text.orEmpty().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme") {
                        inProgramme = false
                        val keep = startMs in 1 until keepToMs && endMs > keepFromMs &&
                            (wantedIds.isEmpty() || channelId in wantedIds)
                        if (keep && title.isNotEmpty()) {
                            batch += EpgProgram(
                                profileId = profileId, epgId = channelId,
                                startMs = startMs, endMs = endMs, title = title, desc = desc,
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

    /** XMLTV time: `20260716203000 +0000` (zone optional). */
    fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0
        return try {
            val fmt = if (value.trim().length > 14) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            }
            fmt.parse(value.trim())?.time ?: 0
        } catch (_: Exception) { 0 }
    }
}
