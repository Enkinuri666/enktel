package tv.enktel.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A value worked out off the UI thread, and worked out again when [keys] change.
 *
 * ## Why not `remember(keys) { … }`
 *
 * `remember`'s block runs during composition, and composition runs on the UI
 * thread. That is right for anything cheap. It was also where the app sorted
 * and filtered whole catalogues — sixty thousand films by lowercased title,
 * twenty thousand channels against a search — and for as long as that took,
 * nothing on screen could move: no focus ring, no scroll, no keystroke
 * appearing in the box that caused it.
 *
 * This does the same job on `Dispatchers.Default`. Two things follow from the
 * work being asynchronous, and both are what a browse screen wants:
 *
 *  - **The previous value stays until the new one is ready.** Changing a
 *    filter leaves the old shelf on screen for the few milliseconds the new
 *    one takes, instead of blanking it.
 *  - **A superseded computation is cancelled.** Type three letters quickly
 *    and the first two searches are abandoned at their next suspension point
 *    rather than queued behind each other.
 *
 * [initial] is what the value is before the first computation finishes. Make
 * it `null` when "not worked out yet" has to be told apart from "worked out,
 * and empty" — which, for anything that draws an empty state, it does.
 */
@Composable
fun <T> rememberComputed(
    initial: T,
    vararg keys: Any?,
    compute: suspend () -> T,
): State<T> = produceState(initial, *keys) {
    value = withContext(Dispatchers.Default) { compute() }
}
