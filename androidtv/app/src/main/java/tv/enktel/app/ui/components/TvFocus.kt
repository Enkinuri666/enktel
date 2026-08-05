package tv.enktel.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
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
