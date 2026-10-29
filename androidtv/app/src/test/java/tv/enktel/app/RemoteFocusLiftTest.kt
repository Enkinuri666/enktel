package tv.enktel.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.enktel.app.ui.components.focusLift

/**
 * Why a 6 dp decoration cost the app its vertical navigation.
 *
 * Every card in the app rose 6 dp when focused, written as a layout `offset`.
 * A card's layout bounds are what Compose's directional focus search scores
 * against, so a focused card sat 6 dp above the cards either side of it —
 * making those neighbours, geometrically, *below* it. DOWN went sideways; the
 * new card lifted, the old one dropped, and the next DOWN came straight back.
 * On Home that meant focus ping-ponging between two tiles and nothing under
 * the first row reachable by remote at all.
 *
 * These are the measurements. Each case is the same five rails of six cards,
 * differing only in what focus does to a card, and each records where focus
 * lands after six DOWN presses. They are here rather than in a comment
 * because two plausible fixes do not work, and the next person to meet this
 * should not have to find that out twice.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi-notouch")
@OptIn(ExperimentalTestApi::class)
class RemoteFocusLiftTest {

    @get:Rule val rule = createComposeRule()

    private companion object {
        const val CARD = 100
        const val ROW = 120
    }

    /** Five rails of six cards; [decorate] is what focus does to a card. */
    @Composable
    private fun Rails(
        first: FocusRequester,
        rail: Modifier,
        decorate: @Composable (Boolean) -> Modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
            items(5) { row ->
                LazyRow(Modifier.fillMaxWidth().height(ROW.dp).then(rail)) {
                    items(6) { col ->
                        var focused by remember { mutableStateOf(false) }
                        Box(
                            Modifier
                                .size(CARD.dp)
                                .then(decorate(focused))
                                .onFocusChanged { focused = it.isFocused }
                                .then(
                                    if (row == 0 && col == 0) Modifier.focusRequester(first)
                                    else Modifier,
                                )
                                .focusable(),
                        )
                    }
                }
            }
        }
    }

    /** The rows focus visited, top-most coordinate first. */
    private fun rowsReached(
        rail: Modifier = Modifier.focusGroup().focusRestorer(),
        decorate: @Composable (Boolean) -> Modifier,
    ): List<Int> {
        val first = FocusRequester()
        rule.setContent { Rails(first, rail, decorate) }
        rule.runOnIdle { first.requestFocus() }
        rule.waitForIdle()
        val rows = linkedSetOf<Int>()
        fun record() {
            rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let {
                // Rounded to the rail it belongs to: a lifted card reports a
                // few pixels high, and the question is which row, not which
                // pixel.
                rows += (it.boundsInRoot.top / (ROW * 2)).toInt()
            }
        }
        record()
        repeat(6) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.waitForIdle()
            record()
        }
        return rows.toList()
    }

    /** A card that lifts the way the app's cards used to. */
    @Composable
    private fun liftDp(focused: Boolean) =
        animateDpAsState(if (focused) (-6).dp else 0.dp, label = "lift").value

    // ---- the bug -----------------------------------------------------------

    @Test
    fun `a layout offset lift strands the remote in the first row`() {
        val rows = rowsReached { focused ->
            val lift = liftDp(focused)
            Modifier.offset { IntOffset(0, lift.roundToPx()) }
        }
        assertEquals("DOWN should never have left row 0, and that is the bug", listOf(0), rows)
    }

    @Test
    fun `a focus group does not save it`() {
        // Worth pinning: tvRailFocus exists precisely to stop DOWN walking
        // sideways, so the obvious conclusion on meeting this is that the
        // grouping is wrong. It is not — the group is doing its job, and the
        // lift hands the search a candidate inside the group that genuinely
        // is lower.
        val bare = rowsReached(Modifier) { focused ->
            val lift = liftDp(focused)
            Modifier.offset { IntOffset(0, lift.roundToPx()) }
        }
        assertEquals(listOf(0), bare)
    }

    @Test
    fun `nor does a graphics layer, which is the tempting fix`() {
        // translationY looks like it should be draw-only. It is not: a layer
        // transform is reported through LayoutCoordinates, so the focus rect
        // moves with the picture and nothing changes.
        val rows = rowsReached { focused ->
            val lift = liftDp(focused)
            val px = with(LocalDensity.current) { lift.toPx() }
            Modifier.graphicsLayer { translationY = px }
        }
        assertEquals(listOf(0), rows)
    }

    // ---- what the cause actually is ----------------------------------------

    @Test
    fun `recomposing on focus is not the problem`() {
        // The card recomposes on every focus change here too, and DOWN is
        // fine. It is the geometry, not the recomposition — which rules out
        // the other tempting fix, hoisting the focus state out of the card.
        val rows = rowsReached { focused ->
            Modifier.background(if (focused) Color.Red else Color.Blue)
        }
        assertTrue("should have walked the rails: $rows", rows.size > 3)
    }

    @Test
    fun `a lift every card carries is not a problem either`() {
        // Confirms it is the *relative* displacement. Offset every card by the
        // same amount and the rails stay aligned, so DOWN works.
        val rows = rowsReached { focused ->
            val same = if (focused) 0 else 0
            Modifier.offset { IntOffset(0, -12 + same) }
        }
        assertTrue("should have walked the rails: $rows", rows.size > 3)
    }

    // ---- the fix -----------------------------------------------------------

    @Test
    fun `focusLift keeps the lift and gives back the remote`() {
        val rows = rowsReached { focused -> Modifier.focusLift(liftDp(focused)) }
        assertTrue("focusLift should walk the rails: $rows", rows.size > 3)
        assertEquals("and it should start at the top", 0, rows.first())
    }
}
