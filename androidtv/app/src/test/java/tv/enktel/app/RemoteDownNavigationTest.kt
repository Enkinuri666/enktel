package tv.enktel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.enktel.app.data.db.Category
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTheme

/**
 * Pressing DOWN on a remote has to reach the next row.
 *
 * > On TV users are unable to navigate downwards with the remote.
 *
 * Home was the screen: focus landed on a category tile and nine more DOWN
 * presses left it exactly where it was, so nothing below the first row — not
 * Continue Watching, not a single rail — could be reached with a remote at
 * all. See [tv.enktel.app.ui.components.focusLift] for why, and
 * [RemoteFocusLiftTest] for the measurements that found it.
 *
 * Focus on a television is not something to reason about. It is resolved by
 * scoring every candidate on geometry, through whatever nest of focus groups,
 * restorers and lazy layouts a screen happens to build, and the answer is
 * routinely not the one the source implies — this bug was a 6 dp decoration.
 * So these drive the real screens with real key events and ask the only
 * question that matters: after pressing DOWN, is focus on a lower row?
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi-notouch")
@OptIn(ExperimentalTestApi::class)
class RemoteDownNavigationTest {

    @get:Rule val rule = createComposeRule()

    // ── a line with something on it ───────────────────────────────────

    private fun graph(): AppGraph = AppGraph(ApplicationProvider.getApplicationContext())

    private fun seedProfile(g: AppGraph): Long = runBlocking {
        g.db.profileDao().insert(
            Profile(
                name = "line", kind = "xtream", server = "https://example.invalid",
                username = "u", password = "p",
                expiresAt = System.currentTimeMillis() + 30L * 86_400_000,
                maxConnections = 3,
            ),
        )
    }

    private fun seedChannels(g: AppGraph, profileId: Long) = runBlocking {
        g.db.contentDao().upsertChannels(
            (1..40).map { i ->
                Channel(
                    key = "$profileId:$i", profileId = profileId, streamId = i.toLong(),
                    name = "Channel $i", categoryId = "c1", categoryName = "Entertainment",
                    epgId = "ch.$i", num = i,
                )
            },
        )
    }

    private fun seedMovies(g: AppGraph, profileId: Long) = runBlocking {
        g.db.contentDao().upsertCategories(
            listOf("Action", "Drama").mapIndexed { i, name ->
                Category(
                    key = "$profileId:vod:v$i", profileId = profileId, kind = "vod",
                    categoryId = "v$i", name = name, sortIdx = i,
                )
            },
        )
        g.db.contentDao().upsertMovies(
            (1..60).map { i ->
                Movie(
                    key = "$profileId:m$i", profileId = profileId, streamId = (3000 + i).toLong(),
                    name = "Film $i", year = 2000 + (i % 25), categoryId = "v${i % 2}",
                    genre = if (i % 2 == 0) "Action" else "Drama",
                    addedAt = System.currentTimeMillis() - i * 86_400_000L,
                )
            },
        )
    }

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent {
            EnktelTheme { Box(Modifier.fillMaxSize().background(EnktelBg)) { content() } }
        }
    }

    /** Lets the screen's async loads land, as ScreenshotCaptureTest does. */
    private fun settle(steps: Int = 20, stepMs: Long = 100) {
        repeat(steps) {
            rule.mainClock.advanceTimeBy(stepMs)
            rule.waitForIdle()
            Thread.sleep(5)
        }
    }

    // ── the walk ──────────────────────────────────────────────────────

    /**
     * Where focus is, as x to y.
     *
     * Both coordinates, because the way this failed was focus moving
     * *sideways* on a DOWN press — tracking only the row would have called
     * that "focus never moved" and sent the next reader looking in the wrong
     * place.
     */
    private fun spot(): Pair<Float, Float>? =
        rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
            ?.let { it.boundsInRoot.left to it.boundsInRoot.top }

    private fun walkDown(presses: Int): List<Pair<Float, Float>> {
        val trace = ArrayList<Pair<Float, Float>>()
        spot()?.let { trace += it }
        repeat(presses) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.waitForIdle()
            spot()?.let { trace += it }
        }
        return trace
    }

    /** Pressing DOWN [presses] times must end up lower than it started. */
    private fun assertWalksDown(screen: String, presses: Int = 8) {
        val trace = walkDown(presses)
        val pretty = trace.joinToString(" → ") { "${it.first.toInt()},${it.second.toInt()}" }
        assertTrue("$screen: nothing ever took focus", trace.isNotEmpty())
        val startY = trace.first().second
        assertTrue(
            "$screen: DOWN never reached a lower row — $pretty",
            trace.any { it.second > startY },
        )
    }

    // ── the screens ───────────────────────────────────────────────────

    @Test
    fun `home walks downwards`() {
        // The reported screen. Focus landed on a category tile and stayed
        // there: the tile lifted 6 dp when focused, which made the tile beside
        // it the lowest thing in reach, so DOWN went sideways and came back.
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        seedMovies(g, p)
        themed {
            tv.enktel.app.ui.screens.HomeScreen(g, androidx.navigation.compose.rememberNavController())
        }
        settle()
        assertWalksDown("Home", presses = 10)
    }

    @Test
    fun `the movies grid walks downwards`() {
        val g = graph()
        val p = seedProfile(g)
        seedMovies(g, p)
        themed {
            tv.enktel.app.ui.vod.MoviesScreen(g, androidx.navigation.compose.rememberNavController())
        }
        settle()
        assertWalksDown("Movies")
    }

    @Test
    fun `the live channel grid walks downwards`() {
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        themed {
            tv.enktel.app.ui.live.ChannelBrowserScreen(g, androidx.navigation.compose.rememberNavController())
        }
        settle()
        assertWalksDown("Live TV")
    }
}
