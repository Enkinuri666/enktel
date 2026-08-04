package tv.enktel.app.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.voice.VoiceCommandBus

/** A tab entry.  `special == "mic"` renders as the centered brand FAB and fires the
 *  voice-command bus instead of navigating; other tabs behave normally. */
data class MobileTab(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val special: String? = null,
)

val MOBILE_TABS = listOf(
    MobileTab("Home", Icons.Filled.Home, "home"),
    MobileTab("Live TV", Icons.Filled.LiveTv, "channels"),
    MobileTab("Search", Icons.Filled.Search, "search"),
    MobileTab("Enki", Icons.Filled.Mic, "__mic", special = "mic"),
    MobileTab("Sports", Icons.Filled.SportsSoccer, "sports"),
    MobileTab("More", Icons.Filled.Menu, "__more"),
)

/**
 * Frame around the navigation host on phone/tablet builds: bottom-tab bar with icons + label
 * and a slide-up "More" sheet giving access to Movies, Series, Watchlist, Recordings, Guide,
 * TV Guide and Settings.  The centered Mic tab activates the voice-command bus rather than
 * navigating anywhere — one-tap access to voice from every top-level screen.
 */
@Composable
fun MobileScaffold(
    nav: NavHostController,
    currentRoute: String?,
    voiceBus: VoiceCommandBus? = null,
    /** True while Kids Mode is showing on the "home" route — hides the bottom
     *  nav so a child can't navigate out of the filtered content via Live TV
     *  / Search / More, defeating the whole point of the mode. */
    kidsModeActive: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Hide the bar on immersive destinations (fullscreen players, multi-view, onboarding)
    // and while Kids Mode owns the Home route.
    val showBar = !kidsModeActive && currentRoute in setOf(
        "home", "movies", "series", "sports", "search", "watchlist", "recordings", "settings", "guide",
        "speedTest", "manageCategories", "downloads", "sportsFinder", "systemMonitor",
    )

    // Ask the OS how tall its bottom system-nav (gesture pill / 3-button bar) is so we can
    // sit above it instead of getting overlapped, and how tall the status bar is so screen
    // headers don't run under the notch/status area.
    val sysNavPad = WindowInsets.navigationBars.asPaddingValues()
    val bottomPad = if (showBar) 72.dp + sysNavPad.calculateBottomPadding() else sysNavPad.calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(EnktelBg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                content(PaddingValues(bottom = bottomPad))
            }
        }

        if (showBar) {
            BottomTabBar(
                current = currentRoute.orEmpty(),
                onTab = { tab ->
                    when {
                        tab.special == "mic" -> {
                            // Fire mic activation on the bus; VoiceHost's collector
                            // toggles listening.  No navigation.
                            voiceBus?.let { bus -> scope.launch { bus.micActivate.emit(Unit) } }
                        }
                        tab.route == "__more" -> showMore = true
                        // Home tap: pop everything back to the start destination so it
                        // works from any depth (previous popUpTo(home)+saveState combo
                        // could look inert when a channel had been opened via an intent).
                        tab.route == "home" -> {
                            nav.popBackStack("home", inclusive = false)
                            if (currentRoute != "home") nav.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        else -> nav.navigate(tab.route) {
                            popUpTo("home") { inclusive = false; saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        if (showMore) MoreSheet(
            nav = nav,
            onDismiss = { showMore = false },
        )
    }
}

@Composable
private fun BottomTabBar(current: String, onTab: (MobileTab) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(EnktelSurface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MOBILE_TABS.forEach { tab ->
            val selected = current == tab.route ||
                (tab.route == "channels" && current.startsWith("live?ch=")) ||
                (tab.route == "search" && current == "search")
            Box(
                Modifier
                    .fillMaxWidth().weight(1f)
                    .padding(vertical = 6.dp)
                    .pointerInput(tab) { detectTapGestures { onTab(tab) } },
                contentAlignment = Alignment.Center,
            ) {
                if (tab.special == "mic") {
                    // Brand assistant button.
                    //
                    // Was a stock Material mic glyph on a flat gradient disc,
                    // which read as a generic voice-input affordance dropped
                    // into the bar rather than part of the product. Now a
                    // softer squircle carrying the brand mark, with a real
                    // shadow so it sits above the bar instead of on it.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    spotColor = EnktelBlue,
                                    clip = false,
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(EnktelBlue, EnktelPurple),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "E",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Text(
                            tab.label,
                            fontSize = 10.sp,
                            color = EnktelBlue,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                if (selected) EnktelBlue else EnktelTextDim,
                            ),
                            modifier = Modifier.padding(bottom = 2.dp).height(22.dp),
                        )
                        Text(
                            tab.label,
                            fontSize = 10.sp,
                            color = if (selected) EnktelBlue else EnktelTextDim,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreSheet(nav: NavHostController, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // navigationBarsPadding here keeps the sheet — especially its last row —
        // out from under the system gesture bar so every entry stays tappable.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(EnktelSurfaceHigh)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .pointerInput(Unit) { detectTapGestures { /* absorb bg taps */ } },
        ) {
            // Drag-handle: signals "swipe to close" without needing a Close button.
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
                    .height(4.dp)
                    .background(EnktelTextDim, RoundedCornerShape(2.dp))
                    .padding(horizontal = 20.dp),
            )
            Text(
                "Menu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            )
            listOf(
                "🎬  Movies" to "movies",
                "🎮  Series" to "series",
                "📺  TV Guide" to "guide",
                "☆  Watchlist" to "watchlist",
                "⬇  Downloads" to "downloads",
                "⏺  Recordings" to "recordings",
                "⚙  Settings" to "settings",
            ).forEach { (label, route) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(route) {
                            detectTapGestures {
                                onDismiss()
                                nav.navigate(route)
                            }
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // Small tail spacer so the last row never kisses the sheet edge visually.
            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        }
    }
}
