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
import androidx.compose.foundation.focusGroup
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
 * Two halves to the fix, and both are needed. This claims focus onto its
 * primary action (retried, because the first frame after the welcome video
 * ends has nothing attached yet). The caller wraps the shell in
 * [Modifier.focusBlocked] so focus cannot wander back out into the menu —
 * Compose has no z-order notion of modality, and drawing on top of something
 * does not put it out of reach.
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
    val primary = remember { FocusRequester() }

    LaunchedEffect(step) {
        repeat(30) {
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    // Back should step backwards through the tour and leave it at the start,
    // rather than falling through to whatever is behind — which, while the
    // tour is up, would be the app exiting on the first press.
    BackHandler { if (step > 0) step-- else onFinish() }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.75f)).focusGroup(),
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
                if (step > 0) FocusButton("Back", onClick = { step-- })
                // The forward action is where focus lands on every step, so
                // the tour can be walked through on the OK button alone.
                if (step < TOUR.lastIndex) {
                    FocusButton(
                        "Next", accent = true,
                        modifier = Modifier.focusRequester(primary),
                        onClick = { step++ },
                    )
                } else {
                    FocusButton(
                        "Get started", accent = true,
                        modifier = Modifier.focusRequester(primary),
                        onClick = onFinish,
                    )
                }
                FocusButton("Skip", onClick = onFinish)
            }
        }
    }
}
