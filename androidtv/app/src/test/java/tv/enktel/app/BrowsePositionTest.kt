package tv.enktel.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import tv.enktel.app.ui.components.tvGridFocus
import tv.enktel.app.ui.components.BrowseSpot
import tv.enktel.app.ui.components.rememberBrowsePosition
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import tv.enktel.app.ui.components.rememberRailPosition

/**
 * Browsing should not start over every time you look at something.
 *
 * > Pick Ex-Yu movies, pick 2010+, scroll down five or six rows, open a film,
 * > read the description, decide against it — and the only way back is to the
 * > beginning. After five or six films you give up.
 *
 * Opening a title disposes the browse screen, and everything it held in
 * `remember` went with it. The filters are fixed with `rememberSaveable`; the
 * scroll position needs the rest of this file, because the screen's content
 * arrives from the database a frame *after* the screen does, and a grid
 * measured over an empty list has already forgotten where it was.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class BrowsePositionTest {

    @get:Rule val rule = createComposeRule()

    private val signature = "exyu\u00012010\u0001added"

    // ---- the arithmetic ----------------------------------------------------

    @Test
    fun `a viewer who never scrolled is left alone`() {
        // Null rather than (0, 0): "put it back at the top" is a scroll call
        // made on behalf of someone who did not scroll, and on a grid still
        // settling that is a visible twitch for no reason.
        assertNull(BrowseSpot.target(BrowseSpot.Spot(signature, 0, 0), signature, 200))
        assertNull(BrowseSpot.target(null, signature, 200))
    }

    @Test
    fun `a different filter starts at the top of its own list`() {
        // Index 42 of the Ex-Yu 2010s is not index 42 of anything else, and
        // restoring it onto another list drops the viewer somewhere arbitrary
        // in a list they just asked to see from the start.
        val saved = BrowseSpot.Spot(signature, 42, 17)
        assertNull(BrowseSpot.target(saved, "comedies\u0001any\u0001added", 200))
        assertEquals(42, BrowseSpot.target(saved, signature, 200)?.index)
    }

    @Test
    fun `nothing is restored onto a list that has not arrived yet`() {
        // The whole reason this class exists. The screen composes before the
        // database answers, and a position applied to an empty grid is thrown
        // away by the measure pass that follows.
        assertNull(BrowseSpot.target(BrowseSpot.Spot(signature, 42, 17), signature, 0))
    }

    @Test
    fun `a list that came back shorter lands near the end, not at the top`() {
        // A re-synced catalogue can drop titles. Landing close to where you
        // were beats landing at the beginning.
        val target = BrowseSpot.target(BrowseSpot.Spot(signature, 90, 30), signature, 50)
        assertEquals(49, target?.index)
        // The offset belonged to a row that is no longer there.
        assertEquals(0, target?.offset)
    }

    @Test
    fun `an empty grid is never recorded over a real position`() {
        // The restore and the recording run against the same state, so a
        // recording taken while the list is empty would overwrite the position
        // with the artefact of a screen that had not loaded.
        assertTrue(BrowseSpot.worthSaving(index = 12, offset = 4, itemCount = 200))
        assertTrue(BrowseSpot.worthSaving(index = 0, offset = 60, itemCount = 200))
        assertEquals(false, BrowseSpot.worthSaving(index = 0, offset = 0, itemCount = 200))
        assertEquals(false, BrowseSpot.worthSaving(index = 12, offset = 4, itemCount = 0))
    }

    // ---- the behaviour, through a real grid --------------------------------

    /**
     * Renders a grid whose contents arrive late, like the real screen's do.
     *
     * [loaded] is held outside the composition on purpose: it survives the
     * simulated restore, so the test can put the screen back in the state the
     * viewer actually meets — composed, and empty for a frame.
     */
    private class Fixture(private val rule: androidx.compose.ui.test.junit4.ComposeContentTestRule) {
        var loaded by mutableStateOf(false)
        lateinit var state: LazyGridState

        @androidx.compose.runtime.Composable
        fun Content(stateFor: @androidx.compose.runtime.Composable (Int) -> LazyGridState) {
            val count = if (loaded) ITEMS else 0
            state = stateFor(count)
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = state,
                modifier = Modifier.size(400.dp).testTag(TAG),
            ) {
                items(count) { Box(Modifier.size(100.dp)) }
            }
        }

        fun load() {
            rule.runOnIdle { loaded = true }
            rule.waitForIdle()
        }

        companion object {
            const val ITEMS = 300
            const val COLUMNS = 3
            const val TAG = "grid"
        }
    }

    @Test
    fun `a grid comes back where it was, once its contents return`() {
        val restorer = StateRestorationTester(rule)
        val f = Fixture(rule)
        restorer.setContent { f.Content { count -> rememberBrowsePosition(signature, count).state } }

        f.load()
        rule.onNodeWithTag(Fixture.TAG).performScrollToIndex(120)
        rule.waitForIdle()
        val wasAt = rule.runOnIdle { f.state.firstVisibleItemIndex }
        assertTrue("the test needs to have scrolled somewhere", wasAt > 0)

        // Open a film, come back: the screen is composed again, and for a
        // frame it has nothing in it.
        rule.runOnIdle { f.loaded = false }
        restorer.emulateSavedInstanceStateRestore()
        f.load()

        assertEquals(wasAt, rule.runOnIdle { f.state.firstVisibleItemIndex })
    }

    @Test
    fun `a remote re-enters the grid at the restored row, not the first cell`() {
        // The television half. The shell re-requests focus into the content on
        // every arrival, and focus entering a grid with no memory of its own
        // goes to cell zero — which scrolls the grid back to the top a frame
        // after the position was restored. Anchoring the entry point to the
        // restored row is what makes the scroll stick.
        val restorer = StateRestorationTester(rule)
        val f = Fixture(rule)
        var focused = -1
        val intoContent = FocusRequester()
        restorer.setContent {
            val count = if (f.loaded) Fixture.ITEMS else 0
            val pos = rememberBrowsePosition(signature, count)
            f.state = pos.state
            LazyVerticalGrid(
                columns = GridCells.Fixed(Fixture.COLUMNS),
                state = pos.state,
                modifier = Modifier
                    .size(400.dp)
                    .testTag(Fixture.TAG)
                    .focusRequester(intoContent)
                    .tvGridFocus(pos.focusEntry),
            ) {
                items(count) { i ->
                    Box(
                        Modifier
                            .size(100.dp)
                            .then(pos.anchor(i))
                            .onFocusChanged { if (it.isFocused) focused = i }
                            .focusable(),
                    )
                }
            }
        }

        f.load()
        rule.onNodeWithTag(Fixture.TAG).performScrollToIndex(120)
        rule.waitForIdle()
        val wasAt = rule.runOnIdle { f.state.firstVisibleItemIndex }
        // Otherwise cell zero would satisfy the assertion below by accident.
        assertTrue("the test needs to have scrolled somewhere", wasAt > 0)

        rule.runOnIdle { f.loaded = false }
        restorer.emulateSavedInstanceStateRestore()
        f.load()
        rule.runOnIdle { intoContent.requestFocus() }
        rule.waitForIdle()

        assertEquals("focus must enter at the restored row", wasAt, focused)
    }

    @Test
    fun `a plain grid state loses the position, which is why the other one exists`() {
        // The negative half. LazyGridState saves itself, so this looks like it
        // should already work — and it does not, because the restored position
        // is discarded by the measure pass over the empty list that the screen
        // starts with. Without this test the fix above reads as superstition.
        val restorer = StateRestorationTester(rule)
        val f = Fixture(rule)
        restorer.setContent { f.Content { rememberLazyGridState() } }

        f.load()
        rule.onNodeWithTag(Fixture.TAG).performScrollToIndex(120)
        rule.waitForIdle()
        assertTrue(rule.runOnIdle { f.state.firstVisibleItemIndex } > 0)

        rule.runOnIdle { f.loaded = false }
        restorer.emulateSavedInstanceStateRestore()
        f.load()

        assertEquals(0, rule.runOnIdle { f.state.firstVisibleItemIndex })
    }

    // ---- the same screen, built out of rails -------------------------------

    /**
     * The Sports Hub's shape: a column of rails whose contents arrive late.
     */
    private class RailFixture(private val rule: androidx.compose.ui.test.junit4.ComposeContentTestRule) {
        var loaded by mutableStateOf(false)
        lateinit var state: LazyListState

        @androidx.compose.runtime.Composable
        fun Content(stateFor: @androidx.compose.runtime.Composable (Int) -> LazyListState) {
            val count = if (loaded) RAILS else 0
            state = stateFor(count)
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxWidth().height(400.dp).testTag(TAG),
            ) {
                items(count) { Box(Modifier.fillMaxWidth().height(90.dp)) }
            }
        }

        fun load() {
            rule.runOnIdle { loaded = true }
            rule.waitForIdle()
        }

        companion object {
            const val RAILS = 60
            const val TAG = "rails"
        }
    }

    @Test
    fun `a hub of rails comes back where it was`() {
        // Tuning to a channel, opening the Match Centre or watching a replay
        // all leave the Sports Hub, and the EPG scan that fills it takes a
        // second or two to come back — so the restored offset is measured away
        // against an empty list before the fixtures land. Scrolling past LIVE
        // SCORES, ON TODAY and HIGHLIGHTS to reach the replays, watching one,
        // and being returned to the top is the browse complaint in another
        // room.
        val restorer = StateRestorationTester(rule)
        val f = RailFixture(rule)
        restorer.setContent { f.Content { count -> rememberRailPosition(signature, count) } }

        f.load()
        rule.onNodeWithTag(RailFixture.TAG).performScrollToIndex(30)
        rule.waitForIdle()
        val wasAt = rule.runOnIdle { f.state.firstVisibleItemIndex }
        assertTrue("the test needs to have scrolled somewhere", wasAt > 0)

        rule.runOnIdle { f.loaded = false }
        restorer.emulateSavedInstanceStateRestore()
        f.load()

        assertEquals(wasAt, rule.runOnIdle { f.state.firstVisibleItemIndex })
    }

    @Test
    fun `a plain list state loses it too`() {
        // The negative control, for the same reason as the grid's.
        val restorer = StateRestorationTester(rule)
        val f = RailFixture(rule)
        restorer.setContent { f.Content { rememberLazyListState() } }

        f.load()
        rule.onNodeWithTag(RailFixture.TAG).performScrollToIndex(30)
        rule.waitForIdle()
        assertTrue(rule.runOnIdle { f.state.firstVisibleItemIndex } > 0)

        rule.runOnIdle { f.loaded = false }
        restorer.emulateSavedInstanceStateRestore()
        f.load()

        assertEquals(0, rule.runOnIdle { f.state.firstVisibleItemIndex })
    }

    @Test
    fun `picking a different sport starts that rail at its own top`() {
        // The signature is the sport filter and the my-teams toggle. A
        // position recorded under one is meaningless under another.
        val saved = BrowseSpot.Spot("Football\u0001false", 12, 40)
        assertNull(BrowseSpot.target(saved, "Cricket\u0001false", 60))
        assertNull(BrowseSpot.target(saved, "Football\u0001true", 60))
        assertEquals(12, BrowseSpot.target(saved, "Football\u0001false", 60)?.index)
    }
}
