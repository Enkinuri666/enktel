package tv.enktel.app.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import kotlinx.coroutines.delay

/**
 * Claims D-pad focus for a screen once it is actually on-screen.
 *
 * ### Why a remote felt dead (v1.38.1)
 *
 * Nothing outside the three player screens ever called `requestFocus`. On a
 * phone that is invisible — you touch what you want. On a Fire TV Stick it is
 * the whole experience: Compose starts a destination with *no* focused node, a
 * D-pad press has no origin to search from, and the remote does nothing at all.
 * Every browse screen in the app was in that state, which is why navigating was
 * a nightmare rather than merely awkward.
 *
 * ### Why it retries
 *
 * [FocusRequester.requestFocus] throws if the node it points at isn't attached
 * yet, and after a navigation transition the first frame routinely isn't — the
 * destination is composed but not placed. A single request in a `LaunchedEffect`
 * therefore fails silently and leaves the screen dead exactly as before. Retrying
 * over a short window costs nothing on a fast device and is the difference
 * between working and not on a slow one.
 *
 * Attach the returned requester with `Modifier.focusRequester(...)` on a
 * [androidx.compose.ui.focus.focusGroup] container (focus enters the group and
 * lands on its first focusable child) or directly on the control that should
 * start focused.
 *
 * ### Why [enabled] exists
 *
 * The request has to be suppressed while something is covering the shell. On a
 * first run the welcome video plays over a freshly-composed Onboarding screen,
 * whose first focusable child is a text field — so this would reach through the
 * video, focus the field, and raise the soft keyboard on top of the playing
 * intro. Focus belongs to whatever the user can actually see.
 */
@Composable
fun rememberScreenFocus(vararg keys: Any?, enabled: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    // `enabled` has to be part of the key set, or flipping it false→true when
    // the splash ends would not restart the effect and the screen would stay
    // focusless.
    val allKeys: Array<Any?> = arrayOf(*keys, enabled)
    @Suppress("SpreadOperator")
    LaunchedEffect(keys = allKeys) {
        if (!enabled) return@LaunchedEffect
        // ~1.5 s of attempts. A low-end Fire TV Stick composing a poster grid
        // over a cold Room query can genuinely take that long to place its
        // first focusable, and giving up early is what leaves the remote inert.
        repeat(30) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    return requester
}

/**
 * A horizontal strip of focusables that lives inside a vertical scroller —
 * a poster rail, a filter-chip row, a row of quick actions.
 *
 * ### Why DOWN moved sideways
 *
 * Compose resolves a directional focus move by scoring every candidate on
 * geometry. Without a group, the twenty cards in a rail and the twenty in the
 * rail beneath it are all flat peers in one search, and a rail is far wider
 * than it is tall — so from a card near the left edge, a card several columns
 * to the right on the *next* row routinely scores better than the one directly
 * below. The visible symptom is a DOWN press that walks diagonally, or a
 * sequence of DOWN presses that never leaves the row it started in. Holding the
 * key eventually escaped only because the repeat scrolled the list far enough
 * to compose a fresh candidate.
 *
 * [focusGroup] collapses the strip to a single stop, so the enclosing vertical
 * search sees one target per row and steps between them cleanly. [focusRestorer]
 * then returns to the item you were last on when focus re-enters, rather than
 * snapping to the far left.
 *
 * This was diagnosed once for the Home rails (see `ContentRail`) and fixed
 * there and nowhere else, which is why every other screen in the app kept the
 * fault. Applying the named modifier rather than the two raw ones is what stops
 * the next rail someone adds from being the next screen that misbehaves.
 */
fun Modifier.tvRailFocus(): Modifier = this.focusGroup().focusRestorer()

/**
 * A grid or vertical list that fills a screen.
 *
 * Grids do not have the rail's diagonal-DOWN problem — a regular lattice
 * scores the way you would expect — so this deliberately does *not* add
 * [focusGroup], which on a full-screen grid would collapse the whole thing to
 * one stop and break arrow movement inside it. What it fixes is the other
 * half: leaving a grid (into a filter row, a dialog, the nav rail) and coming
 * back used to dump focus on the first cell, losing the user's place in a
 * catalogue that may be thousands of titles long.
 *
 * Restoration is best-effort by design: if the previously focused cell has
 * been recycled out of composition, Compose falls back to the default entry
 * point instead of failing the move.
 */
fun Modifier.tvGridFocus(): Modifier = this.focusRestorer()
