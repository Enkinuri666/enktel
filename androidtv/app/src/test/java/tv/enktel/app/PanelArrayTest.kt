package tv.enktel.app

import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.enktel.app.data.str
import tv.enktel.app.data.long
import tv.enktel.app.data.xtream.PanelArray
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class PanelArrayTest {

    private fun stream(s: String): InputStream = ByteArrayInputStream(s.toByteArray())

    private fun names(json: String): List<String> =
        PanelArray.mapEntries(stream(json), "get_vod_streams") { e, _ -> e.str("name") }

    @Test fun `entries are mapped in order`() {
        val json = """[{"name":"Alpha"},{"name":"Beta"},{"name":"Gamma"}]"""
        assertEquals(listOf("Alpha", "Beta", "Gamma"), names(json))
    }

    @Test fun `a panel answering 200 with nothing is an empty list, not an error`() {
        // Panels really do this, and it means the line has no entries of that
        // kind — not that the sync is broken.
        assertEquals(emptyList<String>(), names(""))
    }

    @Test fun `an empty array is an empty list`() {
        assertEquals(emptyList<String>(), names("[]"))
    }

    @Test fun `an object instead of an array is surfaced, not swallowed`() {
        // The code this replaced quietly synced a panel error to an empty
        // catalogue and marked itself done.
        try {
            names("""{"user_info":{"auth":0},"server_info":{}}""")
            fail("expected the non-array to be reported")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("get_vod_streams"))
        }
    }

    @Test fun `an HTML error page is surfaced`() {
        try {
            names("<html><body>403 Forbidden</body></html>")
            fail("expected the error page to be reported")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("did not return a list"))
        }
    }

    @Test fun `a response cut off halfway is surfaced rather than half-synced`() {
        // The worst case of the old behaviour: a truncated payload that parses
        // to nothing and wipes the catalogue on the way through.
        try {
            names("""[{"name":"Alpha"},{"name":"Bet""")
            fail("expected the truncation to be reported")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("did not return a list"))
        }
    }

    @Test fun `the index is the position in the array, not among the survivors`() {
        // Channels are numbered from this index when the panel supplies no
        // `num`, so an entry skipped for a missing id must not renumber the
        // ones after it.
        val json = """[{"id":1},{"nope":true},{"id":3}]"""
        val seen = PanelArray.mapEntries(stream(json), "get_live_streams") { e, i ->
            e.long("id")?.let { "$it@$i" }
        }
        assertEquals(listOf("1@0", "3@2"), seen)
    }

    @Test fun `entries the mapper rejects are dropped`() {
        val json = """[{"name":"Alpha"},{"other":"x"},{"name":"Gamma"}]"""
        assertEquals(listOf("Alpha", "Gamma"), names(json))
    }

    @Test fun `a large array does not need the whole array in memory`() {
        // The point of the change: the mapper sees every entry, but nothing
        // holds them all. Counting without keeping anything must work, and
        // must not build a list of a hundred thousand JsonObjects to do it.
        val n = 100_000
        val json = buildString {
            append('[')
            for (i in 0 until n) {
                if (i > 0) append(',')
                append("""{"stream_id":$i,"name":"Title $i"}""")
            }
            append(']')
        }
        var count = 0
        val out = PanelArray.mapEntries(stream(json), "get_vod_streams") { e, _ ->
            if (e.long("stream_id") != null) count++
            null
        }
        assertEquals(n, count)
        assertEquals(emptyList<Any>(), out)
    }
}
