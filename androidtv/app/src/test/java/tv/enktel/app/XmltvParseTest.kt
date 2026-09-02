package tv.enktel.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.epg.XmltvParser

/**
 * The parse loop itself, which nothing covered before.
 *
 * `XmltvParser.parse` calls `android.util.Xml`, so a plain JVM test cannot
 * reach it — which is why the existing [XmltvParserTest] only exercises
 * `parseTime`, and why the `<category>` handling shipped verified by reading
 * rather than by running. Robolectric supplies the framework class.
 *
 * Pinned below the compile SDK because Robolectric ships prebuilt Android
 * images and only up to the levels it has one for; the parser touches nothing
 * version-specific, so the level is an implementation detail of the harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XmltvParseTest {

    private fun parse(xml: String, keepFrom: Long = 0L, keepTo: Long = Long.MAX_VALUE): List<EpgProgram> {
        val out = mutableListOf<EpgProgram>()
        runBlocking {
            XmltvParser.parse(
                input = xml.byteInputStream(),
                profileId = 1L,
                wantedIds = emptySet(),
                keepFromMs = keepFrom,
                keepToMs = keepTo,
            ) { batch -> out += batch }
        }
        return out
    }

    private fun doc(body: String) = """<?xml version="1.0" encoding="UTF-8"?><tv>$body</tv>"""

    private fun programme(categories: String, title: String = "Some Film") = """
        <programme start="20260716200000 +0000" stop="20260716220000 +0000" channel="c1">
          <title>$title</title>
          <desc>A description.</desc>
          $categories
        </programme>
    """.trimIndent()

    @Test
    fun `a programme's categories reach the row`() {
        val rows = parse(doc(programme("<category>Movie</category><category>Drama</category>")))
        assertEquals(1, rows.size)
        assertEquals("Movie, Drama", rows[0].genre)
        assertEquals("A description.", rows[0].desc)
    }

    @Test
    fun `a programme with no categories has no genre`() {
        val rows = parse(doc(programme("")))
        assertEquals(1, rows.size)
        assertEquals("", rows[0].genre)
    }

    @Test
    fun `categories do not leak from one programme to the next`() {
        // The list is reused across the parse loop, so failing to clear it at
        // the start of a programme would give every later row the first one's
        // genres — invisible in a one-programme test and wrong in every guide.
        val rows = parse(
            doc(
                programme("<category>Movie</category>", title = "First") +
                    programme("", title = "Second"),
            ),
        )
        assertEquals(2, rows.size)
        assertEquals("Movie", rows[0].genre)
        assertEquals("", rows[1].genre)
    }

    @Test
    fun `a category split by an entity reference is not split into fragments`() {
        // A pull parser reports text around an entity as separate events, so
        // "Drama &amp; Crime" arrives in three pieces. Taking the first piece
        // would store "Drama".
        val rows = parse(doc(programme("<category>Drama &amp; Crime</category>")))
        assertEquals("Drama & Crime", rows[0].genre)
    }

    @Test
    fun `the guide's own noise does not reach the screen`() {
        // The rules ProgrammeGenres applies, verified through the parser
        // rather than against it: a bare DVB code and a repeated spelling.
        val rows = parse(
            doc(
                programme(
                    "<category>16</category><category>drama</category>" +
                        "<category>DRAMA</category><category>Crime</category>",
                ),
            ),
        )
        assertEquals("Drama, Crime", rows[0].genre)
    }

    @Test
    fun `titles are sanitised on the way in`() {
        val rows = parse(doc(programme("", title = "Some Film (HD)")))
        assertTrue(rows[0].title, !rows[0].title.contains("(HD)"))
    }

    @Test
    fun `a programme outside the kept window is dropped`() {
        // 2026-07-16 20:00 UTC is 1784232000000.
        val rows = parse(doc(programme("<category>Movie</category>")), keepFrom = 0L, keepTo = 1_000L)
        assertEquals(0, rows.size)
    }
}
