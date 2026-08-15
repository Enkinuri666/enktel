package tv.enktel.app.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.semantics.Role
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

/**
 * A tab entry.
 *
 * `special == "mic"` renders as the centred brand tile and fires the
 * voice-command bus instead of navigating. No tab declares it any more — the
 * voice tile was spending the largest slot on the bar on the least-used action
 * — but the branch stays because it is the mechanism, not the decision, and
 * reinstating it is a one-line change to [MOBILE_TABS].
 */
data class MobileTab(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val special: String? = null,
)

/**
 * The six destinations on the bar, in the order a viewer reaches for them.
 *
 * ### What this used to be, and why it changed
 *
 * `Home · Live TV · Search · Enki · Sports · More` — which put the two largest
 * things in the catalogue, **Movies and Series**, behind a menu. A subscription
 * of 200,000 films and 35,000 series was reachable only by opening a sheet, so
 * the app looked like it had live television and not much else, and a tester
 * said exactly that: the good stuff is hidden and it seems to show the same old
 * content.
 *
 * Meanwhile the biggest, brightest element on the bar — the centre Enki button,
 * rendered as a raised brand-gradient tile — navigated nowhere. It opened a
 * voice prompt, which is a thing people use by *speaking* (the wake word and
 * the remote's microphone both still work, and the entry survives in the menu),
 * and it was spending the most valuable slot in the interface on the least-used
 * action.
 *
 * So: Movies and Series come out of the menu and sit next to Live TV, and the
 * voice tile gives up its slot. Six is the practical maximum on a phone, which
 * makes Search the one that moves — it is an action rather than a section,
 * every screen it matters on can reach it, and it now leads the menu rather
 * than being buried in it.
 */
val MOBILE_TABS = listOf(
    MobileTab("Home", Icons.Filled.Home, "home"),
    MobileTab("Live TV", Icons.Filled.LiveTv, "channels"),
    MobileTab("Movies", Icons.Filled.Movie, "movies"),
    MobileTab("Series", Icons.Filled.Tv, "series"),
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
    //
    // Deny-list, not allow-list. This used to enumerate every screen that
    // *should* show the bar, which meant any destination nobody remembered to
    // add lost its navigation entirely — and several had: tapping the Live TV
    // tab went to "channels", which was not in the list, so the bar the user
    // had just tapped vanished and left them with no way anywhere except Back.
    // "lists" was in the same state. Naming the handful of screens that are
    // genuinely immersive is a list that stays correct as destinations are
    // added, because a new screen defaults to *having* navigation.
    val immersive = currentRoute == null ||
        currentRoute.startsWith("live?") ||
        currentRoute.startsWith("vodPlayer") ||
        currentRoute.startsWith("trailer") ||
        currentRoute.startsWith("multi") ||
        currentRoute.startsWith("onboard") ||
        currentRoute.startsWith("setup") ||
        currentRoute.startsWith("upgrade")
    val showBar = !kidsModeActive && !immersive

    // Ask the OS how tall its bottom system-nav (gesture pill / 3-button bar) is so we can
    // sit above it instead of getting overlapped, and how tall the status bar is so screen
    // headers don't run under the notch/status area.
    val sysNavPad = WindowInsets.navigationBars.asPaddingValues()

    // A rail on the side once there is width for one.
    //
    // A bottom bar spends a strip of *height* on navigation, and height is the
    // scarce axis on the layouts that get one: a tablet in landscape, or a
    // foldable opened out. On a 900 dp-wide viewport the bar was 72 dp of
    // vertical room given up so that six icons could sit in the middle of an
    // otherwise empty line. Moving the same destinations to a vertical rail
    // gives the height back and puts them within a thumb's reach of where the
    // hand already is on a held tablet.
    //
    // 600 dp is the same threshold ScreenShape already uses to mean "not a
    // phone", so the two agree rather than each having their own idea.
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val useRail = showBar && !shape.narrow
    val bottomPad = if (showBar && !useRail) {
        72.dp + sysNavPad.calculateBottomPadding()
    } else {
        sysNavPad.calculateBottomPadding()
    }

    val onTab: (MobileTab) -> Unit = { tab ->
        when {
            tab.special == "mic" ->
                voiceBus?.let { bus -> scope.launch { bus.micActivate.emit(Unit) } }
            tab.route == "__more" -> showMore = true
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
    }

    Box(Modifier.fillMaxSize().background(EnktelBg).statusBarsPadding()) {
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                SideNavRail(current = currentRoute.orEmpty(), onTab = onTab)
            }
            Box(Modifier.weight(1f)) {
                content(PaddingValues(bottom = bottomPad))
            }
        }

        if (showBar && !useRail) {
            BottomTabBar(
                current = currentRoute.orEmpty(),
                onTab = onTab,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        if (showMore) MoreSheet(
            nav = nav,
            onDismiss = { showMore = false },
        )
    }
}

/**
 * Vertical navigation rail for tablets and opened foldables.
 *
 * The same destinations as [BottomTabBar] and the same selection logic — only
 * the axis changes. Labels stay: at this width there is room for them, and an
 * icon-only rail makes people guess.
 */
@Composable
private fun SideNavRail(current: String, onTab: (MobileTab) -> Unit) {
    val active = activeTabRoute(current)
    Column(
        Modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(EnktelSurface)
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MOBILE_TABS.forEach { tab ->
            val selected = active == tab.route
            Column(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (tab.special == "mic") {
                    Box(
                        Modifier
                            .size(44.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = EnktelBlue, clip = false)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(EnktelBlue, EnktelPurple))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("E", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    Box(
                        Modifier
                            .height(30.dp)
                            .width(if (selected) 48.dp else 30.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (selected) EnktelBlue.copy(alpha = 0.18f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                if (selected) EnktelBlue else EnktelTextDim,
                            ),
                            modifier = Modifier.height(21.dp),
                        )
                    }
                }
                Text(
                    tab.label,
                    fontSize = 10.sp,
                    color = if (selected || tab.special == "mic") EnktelBlue else EnktelTextDim,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Which tab owns [route].
 *
 * The bar used to light up only on an exact route match, so it went blank the
 * moment you were anywhere real — a film's detail page, a channel, the guide —
 * and stopped telling the user where they were, which is the one job a tab bar
 * has. Sub-routes now roll up to their tab, and everything the More sheet
 * offers marks More.
 */
internal fun activeTabRoute(route: String): String = when {
    route == "home" -> "home"
    route.startsWith("channels") || route.startsWith("live") -> "channels"
    // Detail pages light their own tab. Reading a film's page is being in
    // Movies, and a bar that goes blank the moment you open something is a bar
    // that stops telling you where you are.
    route.startsWith("movies") || route.startsWith("movie/") -> "movies"
    route.startsWith("series") || route.startsWith("seriesDetails") -> "series"
    route.startsWith("sports") || route.startsWith("matchCenter") -> "sports"
    MORE_ROUTES.any { route.startsWith(it) } -> "__more"
    else -> ""
}

/** Everything reachable from the More sheet, including its detail sub-routes. */
private val MORE_ROUTES = listOf(
    "guide", "watchlist", "downloads", "recordings", "catchup", "settings",
    "lists", "speedTest", "manageCategories", "systemMonitor", "search",
    "comingSoon",
)

@Composable
private fun BottomTabBar(current: String, onTab: (MobileTab) -> Unit, modifier: Modifier) {
    val active = activeTabRoute(current)
    Column(
        modifier
            .fillMaxWidth()
            .background(
                // A flat surface with no separation read as part of the page.
                // A hairline plus a short lift gradient is what makes the bar
                // sit above the content instead of in it.
                Brush.verticalGradient(
                    listOf(Color.Black.copy(0.35f), EnktelSurface, EnktelSurface),
                ),
            ),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.07f)))
        Row(
            Modifier.fillMaxWidth().height(71.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
        MOBILE_TABS.forEach { tab ->
            val selected = active == tab.route
            Box(
                Modifier
                    .fillMaxWidth().weight(1f)
                    .padding(vertical = 6.dp)
                    // `selectable`, not a bare pointerInput.
                    //
                    // detectTapGestures answers a finger and nothing else: no
                    // ripple, no pressed state, no focus, no accessibility role,
                    // and — the reason this was reported as "doesn't function" —
                    // nothing at all for a D-pad. The mobile build gets
                    // sideloaded onto Fire TV sticks, where the entire bar was
                    // simply unreachable.
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTab(tab) },
                    ),
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
                        // A brand pill behind the active icon. Colour alone was
                        // carrying the whole selected state, at 10 sp, which is
                        // most of why the bar read as unfinished next to the
                        // apps this is compared against.
                        Box(
                            Modifier
                                .height(30.dp)
                                .width(if (selected) 52.dp else 30.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) EnktelBlue.copy(alpha = 0.18f) else Color.Transparent,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Image(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (selected) EnktelBlue else EnktelTextDim,
                                ),
                                modifier = Modifier.height(21.dp),
                            )
                        }
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
                    .background(EnktelTextDim, RoundedCornerShape(4.dp))
                    .padding(horizontal = 20.dp),
            )
            Text(
                "Menu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            )
            listOf(
                // Search leads. It came off the bar to make room for Movies and
                // Series, and a thing people reach for often belongs at the top
                // of the sheet rather than in the middle of it.
                "🔎  Search" to "search",
                "🎟  Coming Soon" to "comingSoon",
                "📺  TV Guide" to "guide",
                "⏪  Catch-Up" to "catchup",
                "☆  Watchlist" to "watchlist",
                "≡  My Lists" to "lists",
                "⬇  Downloads" to "downloads",
                "⏺  Recordings" to "recordings",
                "⚙  Settings" to "settings",
            ).forEach { (label, route) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // clickable, so the row ripples, takes focus and reads
                        // as a button to a screen reader and a D-pad alike —
                        // the sheet had the same finger-only problem the bar did.
                        .clickable(role = Role.Button) {
                            onDismiss()
                            nav.navigate(route) {
                                // Without this, opening the same entry twice
                                // stacked two copies on the back stack and Back
                                // appeared to do nothing the first press.
                                launchSingleTop = true
                                restoreState = true
                                popUpTo("home") { inclusive = false; saveState = true }
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
