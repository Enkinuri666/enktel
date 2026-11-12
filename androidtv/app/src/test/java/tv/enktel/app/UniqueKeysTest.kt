package tv.enktel.app

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.repo.SeriesEpisodes
import tv.enktel.app.data.uniqueBy
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.theme.EnktelTheme

/**
 * A list that came from somewhere other than the database cannot close the
 * screen it is shown on.
 *
 * A lazy list throws when two of its items share a key. Database rows never
 * do. Feed rows do on the day the feed is unusual — a film with no id, a video
 * covering two matches, a double header, a panel listing an episode twice —
 * and what the viewer sees is the app closing when they open Coming Soon, or
 * Sport, or one particular series.
 *
 * The first case is the proof that this is real rather than folklore: it
 * builds the list the old code built and requires it to throw.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class UniqueKeysTest {

    @get:Rule val compose = createComposeRule()

    private data class Film(val id: Long, val title: String)

    /** Two films the feed sent without an id, which the parser makes 0. */
    private val feed = listOf(Film(11, "Dune"), Film(0, "Untitled A"), Film(0, "Untitled B"))

    @Test
    fun `control - a lazy list given one key twice throws`() {
        val thrown = runCatching {
            compose.setContent {
                LazyRow { items(feed, key = { it.id }) { Text(it.title) } }
            }
            compose.waitForIdle()
        }.exceptionOrNull()
        assertNotNull("if this stops throwing, the guard below is no longer needed", thrown)
        val chain = generateSequence(thrown) { it.cause }.joinToString(" / ") { "${it.javaClass.simpleName}: ${it.message}" }
        assertTrue("expected the duplicate-key complaint, got: $chain", chain.contains("was already used"))
    }

    @Test
    fun `the shared rail shows the same feed without throwing`() {
        compose.setContent {
            EnktelTheme { ContentRail("Coming soon", feed, key = { it.id }) { Text(it.title) } }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Dune").assertIsDisplayed()
        compose.onNodeWithText("Untitled A").assertIsDisplayed()
    }

    // ---- uniqueBy ----

    @Test
    fun `nothing repeated means the very same list comes back`() {
        val list = listOf(Film(1, "a"), Film(2, "b"), Film(3, "c"))
        assertSame("no copy on the ordinary day", list, list.uniqueBy { it.id })
    }

    @Test
    fun `a repeat keeps the first and the order`() {
        val list = listOf(Film(1, "first"), Film(2, "b"), Film(1, "again"), Film(3, "c"), Film(2, "b again"))
        assertEquals(listOf("first", "b", "c"), list.uniqueBy { it.id }.map { it.title })
    }

    @Test
    fun `empty and single lists are left alone`() {
        val none = emptyList<Film>()
        val one = listOf(Film(0, "only"))
        assertSame(none, none.uniqueBy { it.id })
        assertSame(one, one.uniqueBy { it.id })
    }

    @Test
    fun `a key may be null, and two nulls are still a repeat`() {
        val list = listOf("a" to null, "b" to 1, "c" to null)
        assertEquals(listOf("a", "b"), list.uniqueBy { it.second }.map { it.first })
    }

    // ---- episodes ----

    private fun episodes(json: String) = SeriesEpisodes.parse(LenientJson.parseToJsonElement(json))
    private fun ep(id: Int, season: Int, num: Int) =
        """{"id":"$id","season":$season,"episode_num":$num,"title":"S${season}E$num","container_extension":"mkv"}"""

    @Test
    fun `seasons keyed by number - the usual shape`() {
        val out = episodes("""{"1":[${ep(10, 1, 2)},${ep(11, 1, 1)}],"2":[${ep(20, 2, 1)}]}""")
        assertEquals(listOf(1, 2), out.keys.toList())
        assertEquals("sorted by episode number", listOf(11L, 10L), out[1]!!.map { it.id })
        assertEquals(listOf(20L), out[2]!!.map { it.id })
    }

    @Test
    fun `an array of season arrays`() {
        val out = episodes("""[[${ep(10, 1, 1)},${ep(11, 1, 2)}],[${ep(20, 2, 1)}]]""")
        assertEquals(listOf(10L, 11L), out[1]!!.map { it.id })
        assertEquals(listOf(20L), out[2]!!.map { it.id })
    }

    @Test
    fun `one flat array keeps every episode, not just the last`() {
        // The old reader assigned each episode to its season as it went, so
        // each one replaced the one before.
        val out = episodes("""[${ep(10, 1, 1)},${ep(11, 1, 2)},${ep(12, 1, 3)},${ep(20, 2, 1)}]""")
        assertEquals(listOf(10L, 11L, 12L), out[1]!!.map { it.id })
        assertEquals(listOf(20L), out[2]!!.map { it.id })
    }

    @Test
    fun `a season split across two keys is one season`() {
        val out = episodes("""{"1":[${ep(10, 1, 1)}],"1b":[${ep(11, 1, 2)}]}""")
        assertEquals(listOf(10L, 11L), out[1]!!.map { it.id })
    }

    @Test
    fun `an episode listed twice appears once`() {
        // Its id is the key of its row on the series page.
        val out = episodes("""{"1":[${ep(10, 1, 1)},${ep(10, 1, 1)},${ep(11, 1, 2)}]}""")
        assertEquals(listOf(10L, 11L), out[1]!!.map { it.id })
        assertEquals(out[1]!!.size, out[1]!!.map { it.id }.toSet().size)
    }

    @Test
    fun `anything else is no episodes rather than an error`() {
        assertTrue(episodes("null").isEmpty())
        assertTrue(episodes("\"none\"").isEmpty())
        assertTrue(episodes("{}").isEmpty())
        assertTrue(episodes("""{"1":[{"title":"no id"}]}""").isEmpty())
        assertTrue(SeriesEpisodes.parse(null).isEmpty())
    }
}
