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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim

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

@Composable
fun FirstRunTour(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val s = TOUR[step]
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .background(EnktelSurface, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(s.body, color = Color.White.copy(0.9f), fontSize = 14.sp, lineHeight = 22.sp)
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
                Text("${step + 1} / ${TOUR.size}", color = EnktelTextDim, fontSize = 12.sp)
                Spacer(Modifier.fillMaxWidth().weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) FocusButton("Back", onClick = { step-- })
                if (step < TOUR.lastIndex) {
                    FocusButton("Next", accent = true, onClick = { step++ })
                } else {
                    FocusButton("Get started", accent = true, onClick = onFinish)
                }
                FocusButton("Skip", onClick = onFinish)
            }
        }
    }
}
