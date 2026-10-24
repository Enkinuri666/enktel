package tv.enktel.app.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Where a browse grid was, so coming back from a title lands there.
 *
 * ### The complaint
 *
 * > Pick Ex-Yu movies, pick 2010+, scroll down five or six rows, open a film,
 * > read the description, decide against it — and the only way out is back to
 * > the beginning. Do that for five or six films and you give up.
 *
 * Opening a film navigates, and navigating disposes the browse screen's
 * composition. Everything it held in `remember` — the category, the decade,
 * what was typed in the box, how far down the grid the viewer had got — went
 * with it, so "back" returned to a screen that was technically the right one
 * and had forgotten everything about why the viewer was on it. Six films meant
 * walking the same four steps six times.
 *
 * ### Why the scroll needs its own bookkeeping
 *
 * Saving the filters is a one-word change (`rememberSaveable`), but the scroll
 * position needs more than that even though `LazyGridState` already saves
 * itself. The screen's content arrives from Room *after* the first frame, so on
 * the way back the grid is measured once with nothing in it — and a measure
 * pass over an empty list resets the restored position to the top before the
 * titles land. The saved offset is thrown away a frame before it could be used.
 *
 * So the position is held here, keyed to the filters it belongs to, and put
 * back once the list actually has something in it. [BrowseSpot] holds the
 * arithmetic, because the interesting cases — a list that came back shorter, a
 * different filter, a viewer who never scrolled — are the ones worth testing
 * rather than watching.
 */
object BrowseSpot {

    /** A remembered position: which filters it belonged to, and where. */
    data class Spot(val signature: String, val index: Int, val offset: Int)

    /**
     * Where to put the grid back, or null when there is nothing to restore.
     *
     * Null rather than (0, 0) for "nothing to do": a viewer who never scrolled
     * should not have a scroll call made on their behalf, and a filter they
     * have since changed should start at the top of its own list rather than
     * at an index that meant something in a different one.
     *
     * A shorter list — the catalogue re-synced, or a title went away — is
     * clamped to the end rather than abandoned. Landing near where you were
     * beats landing at the top.
     */
    fun target(saved: Spot?, signature: String, itemCount: Int): Spot? {
        if (saved == null || itemCount <= 0) return null
        if (saved.signature != signature) return null
        if (saved.index <= 0 && saved.offset <= 0) return null
        val index = saved.index.coerceIn(0, itemCount - 1)
        // The offset is only meaningful for the row it was measured in.
        val offset = if (index == saved.index) saved.offset.coerceAtLeast(0) else 0
        return Spot(signature, index, offset)
    }

    /**
     * Is this position worth remembering?
     *
     * An empty list reports index 0, and recording that would overwrite a real
     * position with the artefact of a screen that had not loaded yet — which is
     * the very thing this class exists to prevent.
     */
    fun worthSaving(index: Int, offset: Int, itemCount: Int): Boolean =
        itemCount > 0 && index >= 0 && (index > 0 || offset > 0)
}

/**
 * A grid state that comes back where the viewer left it.
 *
 * @param signature everything that decides what is in the list — category,
 *   genre, decade, search text, sort order. When it changes the list is a
 *   different list, so the old position is not restored onto it.
 * @param itemCount how many items the grid currently has. The restore waits
 *   for this to be non-zero, because the screen's first frame has nothing in
 *   it and a measure over an empty list would throw the position away.
 */
@Composable
fun rememberBrowsePosition(signature: String, itemCount: Int): BrowsePosition {
    val state = rememberLazyGridState()

    var savedSignature by rememberSaveable { mutableStateOf("") }
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }

    // Deliberately not saveable: coming back from a film is exactly when the
    // restore has to happen again. Keyed on the signature so changing a filter
    // also re-arms it — with a signature that no longer matches, which means
    // the new list starts at its own top.
    var restored by remember(signature) { mutableStateOf(false) }

    // Where focus should enter the grid. Held as plain state rather than
    // saved: it is derived from the position that *is* saved.
    var anchorIndex by remember(signature) { mutableIntStateOf(0) }
    val focusAnchor = remember { FocusRequester() }

    LaunchedEffect(signature, itemCount) {
        if (restored || itemCount <= 0) return@LaunchedEffect
        val saved = BrowseSpot.Spot(savedSignature, savedIndex, savedOffset)
        BrowseSpot.target(saved, signature, itemCount)?.let {
            anchorIndex = it.index
            // requestScrollToItem rather than scrollToItem: it applies on the
            // next measure instead of scrolling, so the grid draws in the
            // right place once rather than appearing at the top and jumping.
            state.requestScrollToItem(it.index, it.offset)
        }
        restored = true
    }

    // Read through an updated state rather than captured: this collector is
    // not keyed on the count, so a captured one would still be the zero the
    // screen started with and nothing would ever be recorded.
    val liveCount by rememberUpdatedState(itemCount)

    LaunchedEffect(state, signature) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (!restored) return@collect
                if (!BrowseSpot.worthSaving(index, offset, liveCount)) return@collect
                savedSignature = signature
                savedIndex = index
                savedOffset = offset
            }
    }

    return remember(state, focusAnchor) { BrowsePosition(state, focusAnchor) }
        .also { it.anchorIndex = anchorIndex }
}

/**
 * A grid's remembered place: where it scrolls to, and where focus lands.
 *
 * The second half is only for a remote. On a television the shell re-requests
 * focus into the screen on every arrival, and focus entering a grid with no
 * memory of its own goes to the first cell — which scrolls the grid to the top
 * and undoes the restore a frame after it happened. Anchoring the entry point
 * to the restored row is what makes the scroll stick, and it also puts the
 * cursor back on the shelf the viewer was looking at rather than at the start
 * of the catalogue.
 */
class BrowsePosition internal constructor(
    val state: LazyGridState,
    private val requester: FocusRequester,
) {
    /** The item focus should enter at; 0 when there is nothing to restore. */
    internal var anchorIndex: Int = 0

    /** The fallback entry point for [tvGridFocus]. */
    val focusEntry: FocusRequester get() = requester

    /**
     * Attach to the cell at [index]: the anchor gets the requester, every
     * other cell gets nothing.
     */
    fun anchor(index: Int): Modifier =
        if (index == anchorIndex) Modifier.focusRequester(requester) else Modifier
}
