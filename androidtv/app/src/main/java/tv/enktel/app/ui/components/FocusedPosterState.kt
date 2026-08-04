package tv.enktel.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v1.28.0 TV cinematic refactor phase 2 — hoisted state that tracks the
 * poster art of whichever rail item is currently focused, so the
 * AmbilightGlow backdrop can crossfade to match.
 *
 * The design brief calls for the backdrop to change "after >200 ms of
 * focus" so idle focus-sweep on a remote doesn't jitter the entire
 * background. [FocusedPosterState.report] enforces exactly that: it
 * schedules a delayed publish on focus-gained and cancels it on
 * focus-lost. Only a poster that has held focus for the full dwell
 * time actually reaches the visible state.
 *
 * Screens that want to react (Home, Movies, Series) read
 * [LocalFocusedPoster.current.currentUrl] and hand it to AmbilightGlow.
 * PosterCards (and any other focusable item that has a backdrop-worthy
 * image) call [FocusedPosterState.report] from their focus handler.
 */
class FocusedPosterState internal constructor(
    private val scope: CoroutineScope,
    private val dwellMs: Long = 220L,
    /**
     * v1.34.0 — auto-trailers dwell far longer than the backdrop does. Nobody
     * wants audio-less video flickering under every item a remote sweeps past,
     * so a title has to hold focus for most of a second before we'll even ask
     * TMDB whether it has a trailer.
     */
    private val trailerDwellMs: Long = 900L,
) {
    var currentUrl: String? by mutableStateOf(null)
        private set

    /**
     * The title that has held focus long enough to earn a background trailer,
     * or null when nothing qualifies (no TMDB id, focus lost, still dwelling).
     */
    var trailerTarget: TrailerTarget? by mutableStateOf(null)
        private set

    /** Identity of a focused VOD item, enough to look up its trailer. */
    data class TrailerTarget(val tmdbId: Long, val isSeries: Boolean, val title: String)

    private var pending: Job? = null
    private var pendingTrailer: Job? = null

    /**
     * Call from a focus listener. On focus gained, publishes [url] after
     * [dwellMs] of continuous focus. On focus lost, cancels a pending
     * publish (but keeps the last committed URL visible — the brief's
     * "keep the previous backdrop until something else earns it" feel).
     *
     * [tmdbId] drives the hover auto-trailer and is optional: poster cards for
     * content we have no TMDB id for (live channels, un-enriched catalogues)
     * simply never trigger one.
     */
    fun report(
        focused: Boolean,
        url: String?,
        tmdbId: Long = 0,
        isSeries: Boolean = false,
        title: String = "",
    ) {
        pending?.cancel()
        pendingTrailer?.cancel()
        if (!focused) {
            // Losing focus kills the trailer immediately — the backdrop image
            // lingers, but video that outlives its poster reads as a bug.
            trailerTarget = null
            return
        }
        if (url.isNullOrBlank() && tmdbId <= 0 && title.isBlank()) return
        if (!url.isNullOrBlank()) {
            pending = scope.launch {
                delay(dwellMs)
                currentUrl = url
            }
        }
        // A title alone is enough.
        //
        // This gate used to be `tmdbId > 0`, which is why hover trailers played
        // for nobody. The id is stamped by the enrichment worker, the worker can
        // only copy it from the panel, and most panels never publish one — so
        // tmdbId stayed 0 and no target was ever published. TrailerRepository
        // was taught to resolve an id by searching TMDB for the title, but that
        // path was unreachable from here: the caller refused to ask.
        if (tmdbId > 0 || title.isNotBlank()) {
            pendingTrailer = scope.launch {
                delay(trailerDwellMs)
                trailerTarget = TrailerTarget(tmdbId, isSeries, title)
            }
        } else {
            trailerTarget = null
        }
    }

    /** Stop any playing trailer — used when a screen loses focus or a dialog opens. */
    fun clearTrailer() {
        pendingTrailer?.cancel()
        trailerTarget = null
    }

    fun dispose() {
        pending?.cancel()
        pendingTrailer?.cancel()
        scope.cancel()
    }
}

val LocalFocusedPoster = compositionLocalOf<FocusedPosterState?> { null }

@Composable
fun rememberFocusedPosterState(): FocusedPosterState {
    val state = remember { FocusedPosterState(MainScope()) }
    androidx.compose.runtime.DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    return state
}
