package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelText
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelType

private data class TourStep(val title: String, val body: String)

private val TOUR_TV = listOf(
    TourStep("Welcome to EnkTel IPTV", "Stream beyond limits. Let's walk you through the basics — takes 20 seconds."),
    TourStep("Remote control", "◀▶▲▼ to move · OK to select · BACK to leave · MENU for player options."),
    TourStep("Live TV", "Number keys 0–9 jump to that channel. Press OK on the player to open the channel list."),
    TourStep("Time-shift & catch-up", "On channels with catch-up you can restart the current programme or jump back 5 minutes at any time."),
    TourStep("Sports Hub", "Tap a live match to jump straight to its channel. Follow teams to get reminders before they play."),
    TourStep("Watchlist & recommendations", "Press ☆ on any movie or series to save it. Home shows Because-You-Watched suggestions as you use the app."),
    TourStep("You're set", "Change any of this in Settings. Enjoy!"),
)

private val TOUR_MOBILE = listOf(
    TourStep("Welcome to EnkTel IPTV", "Stream beyond limits. Let's walk you through the basics — takes 20 seconds."),
    TourStep("Bottom tabs", "Home, Live TV, Movies, Sports, Search and More live in the bottom bar."),
    TourStep("Live TV", "Tap channels or long-press to zap. Swipe up/down on the video for volume + brightness."),
    TourStep("Time-shift & catch-up", "On channels with catch-up you can restart the current programme or jump back 5 minutes at any time."),
    TourStep("Sports Hub", "Tap a live match to jump straight to its channel. Follow teams to get reminders before they play."),
    TourStep("Watchlist & recommendations", "Tap ☆ on any movie or series to save it. Home shows Because-You-Watched suggestions as you use the app."),
    TourStep("Voice", "Bottom-right mic — try \"turn to Nine HD\", \"latest movies\", \"what live sports is on\"."),
    TourStep("You're set", "Change any of this in Settings. Enjoy!"),
)

private val TOUR: List<TourStep>
    @androidx.compose.runtime.Composable
    get() = if (tv.enktel.app.BuildConfig.FLAVOR == "mobile") TOUR_MOBILE else TOUR_TV

/**
 * The first-run coach marks.
 *
 * ### Why none of the buttons could be reached with a remote
 *
 * This dialog claimed no focus at all — there was no `FocusRequester` anywhere
 * in the file. It is drawn as a sibling of the app shell, so it covered the
 * shell visually and changed nothing about focus: the nav rail underneath was
 * still a perfectly good target and had already taken focus on mount. What a
 * user sees is a dialog in front of them and a highlight moving around the menu
 * *behind* it, with no key that bridges the two. Touch worked, because a tap
 * addresses whatever it lands on instead of searching for it — which is exactly
 * the asymmetry that was reported.
 *
 * Two halves to the fix, and the dialog owns both of them.
 *
 * It claims focus onto its forward action, retried for three seconds because
 * a first run composes this over a cold Home screen, which is the slowest
 * moment the app ever has. And it answers every directional key itself,
 * moving between its own buttons by explicit [FocusRequester] and passing
 * none of them on — so focus has no route back out into the menu.
 *
 * The first attempt at the second half deactivated the *shell's* focus
 * subtree while the tour was up. That crashed the app at launch on a first
 * run, which is the only time the tour is visible; and because a crashed tour
 * cannot be dismissed, `firstRunDone` was never written and every relaunch
 * crashed identically. The lesson is in the shape of the fix rather than the
 * API: a component that governs its own input cannot break anything else,
 * while one that reaches across the app to switch off everyone else's focus
 * can — and did.
 *
 * Focus is re-claimed on every step because the button row's membership
 * changes as you move through it: "Back" appears after step 0 and "Next"
 * becomes "Get started" at the end, so the node that had focus can cease to
 * exist underneath it.
 */
@Composable
fun FirstRunTour(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val s = TOUR[step]
    val hasBack = step > 0

    // Buttons in screen order. Explicit requesters rather than focus search,
    // because focus search is what could not be relied on here: the dialog is
    // a sibling of the shell, so a search starting anywhere outside it has no
    // reason to prefer it, and one that starts inside it has no reason to stay.
    val backFr = remember { FocusRequester() }
    val primaryFr = remember { FocusRequester() }
    val skipFr = remember { FocusRequester() }
    val order = if (hasBack) listOf(backFr, primaryFr, skipFr) else listOf(primaryFr, skipFr)

    // Start on the forward action, so the whole tour can be walked with OK.
    var idx by remember(step) { mutableIntStateOf(if (hasBack) 1 else 0) }

    LaunchedEffect(step, idx) {
        // Up to three seconds. The tour appears over a freshly-composed Home
        // on a cold start, which on a Fire TV Stick is the slowest moment the
        // app ever has, and giving up early is what leaves it unreachable.
        repeat(60) {
            if (runCatching { order[idx].requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
        android.util.Log.w("FirstRunTour", "no focusable button took focus at step $step")
    }

    // Back steps backwards, and dismisses at the start. This is also the
    // escape hatch: if focus somehow never lands, Back still closes the tour
    // rather than leaving someone with a dialog they cannot dismiss.
    BackHandler { if (step > 0) step-- else onFinish() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.75f))
            // Every directional press is answered here and none are passed on,
            // which is what keeps focus inside the dialog.
            //
            // The previous attempt did this by deactivating the shell's focus
            // subtree while the tour was up. That is a much bigger hammer than
            // the job needs — it reaches across the whole app from a component
            // that should only govern itself — and on a first run it crashed
            // the app at launch, which is the worst possible time given the
            // tour cannot be dismissed and so came back on every relaunch.
            // Consuming the keys is local, needs nothing from the shell, and
            // cannot be defeated by a direction nobody thought about.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { idx = (idx - 1 + order.size) % order.size; true }
                    Key.DirectionRight -> { idx = (idx + 1) % order.size; true }
                    // Nothing sits above or below the button row, so these
                    // would only ever be a way out into the menu behind.
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(EnktelSurface, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.title, color = EnktelText, style = EnktelType.headline)
            Text(s.body, color = EnktelText.copy(0.9f), style = EnktelType.body)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(TOUR.size) { i ->
                    Box(
                        Modifier
                            .width(if (i == step) 22.dp else 8.dp)
                            .height(8.dp)
                            .padding(end = 4.dp)
                            .background(
                                if (i == step) EnktelBlue else EnktelTextDim.copy(0.4f),
                                CircleShape,
                            ),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("${step + 1} / ${TOUR.size}", color = EnktelTextDim, style = EnktelType.caption)
                Spacer(Modifier.fillMaxWidth().weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasBack) {
                    FocusButton(
                        "Back",
                        modifier = Modifier.focusRequester(backFr),
                        onClick = { step-- },
                    )
                }
                if (step < TOUR.lastIndex) {
                    FocusButton(
                        "Next", accent = true,
                        modifier = Modifier.focusRequester(primaryFr),
                        onClick = { step++ },
                    )
                } else {
                    FocusButton(
                        "Get started", accent = true,
                        modifier = Modifier.focusRequester(primaryFr),
                        onClick = onFinish,
                    )
                }
                FocusButton(
                    "Skip",
                    modifier = Modifier.focusRequester(skipFr),
                    onClick = onFinish,
                )
            }
        }
    }
}
