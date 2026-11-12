package tv.enktel.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Text
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * A database query is run once, not once per frame of UI.
 *
 * ## What was happening
 *
 * Forty-one places in the app read like this:
 *
 * ```
 * val channels by graph.content.channels(p.id).collectAsStateWithLifecycle(…)
 * ```
 *
 * `channels(p.id)` is a Room DAO method, and a Room DAO method builds a *new*
 * `Flow` every time it is called. `collectAsStateWithLifecycle` restarts its
 * collection whenever it is handed a different flow. And a composable's body
 * runs again on every recomposition. Put together: every time one of those
 * screens recomposed — for a keystroke, a focus change, a clock tick — it
 * cancelled the query it had, started a new one, and Room read the whole
 * table again.
 *
 * On the Live TV grid that is the entire channel list re-read for every
 * letter typed into the search box. On Home it is seven queries, one of them
 * the entire channel list, re-run every thirty seconds because a clock in the
 * corner ticked. In the live *player* it was the whole channel table re-read
 * while a stream was playing, on the same weak CPU the decoder needs. On
 * Coming Soon every card re-read the entire film library to check for one
 * title.
 *
 * Nothing looked wrong, because the answer was always the same list and
 * Compose correctly declined to redraw for an equal value. The cost was
 * invisible: allocation, garbage collection and disk, on the hardware that
 * has least of all three.
 *
 * ## The two halves of this test
 *
 * The first two cases pin the *mechanism*, with a flow that counts how many
 * times it is collected, because the claim above is surprising enough to
 * deserve proof rather than a link to documentation. The last case scans the
 * sources for the pattern, so the forty-second one fails the build.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FlowCollectionTest {

    @get:Rule val rule = createComposeRule()

    private var collections = 0

    /** What a DAO method does: a brand-new cold flow on every call. */
    private fun fresh(): Flow<Int> = flow {
        collections++
        emit(7)
        awaitCancellation()
    }

    @Test
    fun `a flow built in the body is collected again on every recomposition`() {
        var tick by mutableIntStateOf(0)
        rule.setContent {
            val v by fresh().collectAsStateWithLifecycle(initialValue = 0)
            Text("$tick $v")
        }
        rule.waitForIdle()
        val settled = collections
        assertTrue("never collected at all", settled >= 1)

        repeat(5) {
            tick++
            rule.waitForIdle()
        }
        // Five recompositions, five fresh queries. This is the bug.
        assertEquals(settled + 5, collections)
    }

    @Test
    fun `remembered, it is collected once however often the screen recomposes`() {
        var tick by mutableIntStateOf(0)
        rule.setContent {
            val v by remember { fresh() }.collectAsStateWithLifecycle(initialValue = 0)
            Text("$tick $v")
        }
        rule.waitForIdle()
        val settled = collections

        repeat(5) {
            tick++
            rule.waitForIdle()
        }
        assertEquals(settled, collections)
    }

    @Test
    fun `remembered with a key, it is collected again only when the key changes`() {
        // The key matters as much as the remember: a category switch has to
        // start a new query, or the grid keeps showing the old shelf.
        var tick by mutableIntStateOf(0)
        var category by mutableIntStateOf(0)
        rule.setContent {
            val v by remember(category) { fresh() }.collectAsStateWithLifecycle(initialValue = 0)
            Text("$tick $category $v")
        }
        rule.waitForIdle()
        val settled = collections

        repeat(3) { tick++; rule.waitForIdle() }
        assertEquals("recomposing alone must not re-query", settled, collections)

        category = 1
        rule.waitForIdle()
        assertEquals("a new key must re-query exactly once", settled + 1, collections)
    }

    // ---- the scan -----------------------------------------------------------

    private fun sources(): List<File>? {
        val candidates = listOf(
            "src/main/java/tv/enktel/app",
            "app/src/main/java/tv/enktel/app",
            "androidtv/app/src/main/java/tv/enktel/app",
        )
        val root = candidates.map(::File).firstOrNull { it.isDirectory } ?: return null
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `no screen collects a flow it builds in its own body`() {
        val files = sources() ?: return
        // `by <receiver>.collectAsState…(`, where the receiver is everything
        // back to the nearest `by`.
        val statement = Regex("""\bby\s+((?:(?!\bby\b)[\s\S]){1,400}?)\.collectAsState(?:WithLifecycle)?\(""")
        val offenders = mutableListOf<String>()
        var seen = 0

        files.forEach { f ->
            val text = f.readText()
            statement.findAll(text).forEach { m ->
                seen++
                val receiver = m.groupValues[1].trim()
                // A property (`graph.settings.vodSort`) is one flow for the
                // life of the object. A call builds a new one.
                val buildsAFlow = '(' in receiver
                val remembered = receiver.startsWith("remember")
                if (buildsAFlow && !remembered) {
                    val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                    offenders += "${f.name}:$line  ${receiver.replace(Regex("\\s+"), " ").take(90)}"
                }
            }
        }

        // If the pattern stops matching anything, the scan has gone blind
        // rather than the code having gone clean.
        assertTrue("the scan found only $seen collectors — it is not reading the sources", seen > 100)
        assertTrue(
            "these collect a flow that is rebuilt on every recomposition, so the query behind it " +
                "re-runs every time the screen redraws. Wrap the call in remember(keys) { … }:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
