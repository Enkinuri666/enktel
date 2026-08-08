package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Dvr
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * v1.29.0 TV cinematic refactor phase 3 — collapsible left navigation
 * rail per the design brief.
 *
 *   - **Collapsed:** 72 dp wide, icon-only. This is the resting state
 *     whenever focus lives inside the content area (the rail is out of
 *     the way so posters get the full width).
 *   - **Expanded:** 220 dp wide, icon + label + selection stripe.
 *     Triggered automatically the moment focus lands in the rail
 *     (D-Pad-Left from the leftmost content column).
 *   - Animation: `tween(220 ms)` matches the design brief's "smooth"
 *     expansion, run through `animateDpAsState`. The rail overlays the
 *     content rather than sharing a Row with it, so widening repaints the
 *     rail alone and leaves the content where it is.
 *
 * Screens are opted in via the [TvNavShell] wrapper in MainActivity;
 * immersive routes (players, onboarding, first-run tour) skip it.
 */
data class TvNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/** Icon column only. See the comment at the `width` animation for the arithmetic. */
private val RAIL_COLLAPSED = 72.dp

/** Icon + label. Comfortably clears the ~146 dp the widest label needs. */
private val RAIL_EXPANDED = 220.dp

/**
 * Below this the rail is too narrow to hold a label, so it is not drawn.
 *
 * `expanded` flips the instant focus arrives, but the rail takes 220 ms to get
 * there, so a label gated on the boolean was composed at full length into a
 * column still only part-way open — and with maxLines = 1 and the default Clip
 * overflow, it was cut mid-word. That is the "Watchli / My / Downlo / Record"
 * in the screenshots: not a truncation bug, a frame caught mid-animation.
 * Gating on the animated width means the label appears only once there is room
 * for it, and the ellipsis covers a label longer than any shipped today.
 */
private val RAIL_LABEL_MIN = 150.dp

/**
 * How many frames to keep offering focus to a freshly-opened destination.
 *
 * One was the original bet and it lost on a Fire TV Stick, where a screen full
 * of artwork needs several frames before anything in it is focusable. Twelve is
 * about 200 ms at 60 Hz — long enough for the slowest destination in the app,
 * short enough that a genuinely empty screen does not sit there trying. The
 * loop exits on the first success, which is the common case.
 */
private const val FOCUS_HANDOFF_FRAMES = 12

/**
 * The rail used emoji for its icons, and that was the single biggest reason it
 * read as cheap: a system emoji font renders roughly half of them as full-colour
 * pictures (🏠 📺 🗓 🎬 🎞 ⚽ 🔍) and the rest as hairline monochrome text glyphs
 * (☆ ⬇ ⏺ ⚙). One column, two completely different drawing styles, mismatched
 * weights and optical sizes, and no way to tint any of them with the theme —
 * so the accent colour stopped at the label and never reached the icon.
 *
 * Material icons are one family at one weight, they scale cleanly at 10-foot
 * distance, and they inherit `contentColor`, so the whole row (icon, label and
 * stripe) now moves together between the dim, selected and focused states.
 */
private val DEFAULT_ITEMS = listOf(
    TvNavItem("home", "Home", Icons.Rounded.Home, "home"),
    TvNavItem("live", "Live TV", Icons.Rounded.LiveTv, "channels"),
    TvNavItem("guide", "TV Guide", Icons.Rounded.CalendarMonth, "guide"),
    TvNavItem("movies", "Movies", Icons.Rounded.Movie, "movies"),
    TvNavItem("series", "Series", Icons.Rounded.Theaters, "series"),
    TvNavItem("sports", "Sports", Icons.Rounded.SportsSoccer, "sports"),
    TvNavItem("watchlist", "Watchlist", Icons.Rounded.BookmarkBorder, "watchlist"),
    TvNavItem("lists", "My Lists", Icons.AutoMirrored.Rounded.PlaylistPlay, "lists"),
    TvNavItem("downloads", "Downloads", Icons.Rounded.Download, "downloads"),
    TvNavItem("recordings", "Recordings", Icons.Rounded.Dvr, "recordings"),
    TvNavItem("catchup", "Catch-Up", Icons.Rounded.History, "catchup"),
    TvNavItem("search", "Search", Icons.Rounded.Search, "search"),
    TvNavItem("settings", "Settings", Icons.Rounded.Settings, "settings"),
)

@Composable
fun TvNavShell(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    /**
     * Title of the docked stream, or null when nothing is playing in the mini
     * window. Non-null puts a "Now playing" entry at the top of the rail.
     *
     * The mini window itself is deliberately not focusable on TV — a floating
     * overlay competing for D-pad focus with the content grid makes both harder
     * to use — so this is how a remote gets back to full screen.
     */
    nowPlayingLabel: String? = null,
    onNowPlaying: () -> Unit = {},
    items: List<TvNavItem> = DEFAULT_ITEMS,
    content: @Composable (PaddingValues) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // The content's FocusRequester is created below the rail, so a holder keeps
    // the escape hatch from needing a forward reference.
    val contentFocusRef = remember { mutableStateOf<FocusRequester?>(null) }
    val width by animateDpAsState(
        // 72, not 64. A collapsed item needs 8+8 dp of nesting padding either
        // side, a 3 dp stripe, a 10 dp gap and a 24 dp icon — 69 dp before the
        // rail has drawn anything of its own. At 64 the row was over-constrained
        // on every single frame, which is why the icons sat hard against the
        // left edge and looked squeezed rather than centred in their column.
        targetValue = if (expanded) RAIL_EXPANDED else RAIL_COLLAPSED,
        animationSpec = tween(durationMillis = 220),
        label = "tv-nav-rail-width",
    )

    // The rail overlays the content instead of sharing a Row with it.
    //
    // As a Row child the rail's width was part of the layout, so every
    // expansion re-laid out the whole content area — hero image, every row,
    // every poster — and shoved it 148 dp sideways and back. That is the
    // content shifting between screenshots, and on a Fire TV stick it is also
    // 220 ms of layout work each time focus enters or leaves the rail.
    //
    // Content is now permanently inset by the collapsed width and never moves.
    // The rail draws over it while expanded, which its near-opaque background
    // already supports. Collapsed, the two arrangements are pixel-identical.
    //
    // Content is declared first so the rail paints on top of it.
    Box(Modifier.fillMaxSize()) {
        // Focus has to land *somewhere* when a screen opens, and it did not.
        //
        // Of the twenty-four content screens in this app, not one asked for
        // focus on mount. Compose leaves focus unset until something claims it,
        // so arriving anywhere — Home, Movies, Settings, the guide — left the
        // first D-pad press with no origin to search from. It either did
        // nothing or jumped somewhere arbitrary, and on a remote that is
        // indistinguishable from a dropped button press. Every screen change
        // cost the user a wasted press, on every screen, which is most of what
        // "the remote isn't fully functional" describes.
        //
        // Doing it here rather than in twenty-four screens means it cannot be
        // forgotten by the next screen someone adds. focusRestorer also brings
        // you back to where you were when you return to a screen, instead of
        // resetting to the top-left every time.
        val contentFocus = remember { FocusRequester() }
        SideEffect { contentFocusRef.value = contentFocus }
        LaunchedEffect(currentRoute) {
            // One frame was a bet, and on a Fire TV Stick it lost.
            //
            // A fresh destination — posters, rails, artwork — does not finish
            // composing its focusable children in a single frame on that
            // hardware. requestFocus() then landed on a group with nothing
            // focusable in it, threw, and runCatching discarded the error.
            //
            // Focus therefore never left the rail. Because `expanded` was
            // `hasFocus`, the rail stayed open on every screen you selected,
            // and with focus still inside it there was nothing to move right
            // to. Nothing retried and nothing reported it, so the only way out
            // was killing the app.
            //
            // Retry across frames instead of betting on one. The loop costs
            // nothing once focus lands, which is the first attempt in the
            // common case.
            var landed = false
            repeat(FOCUS_HANDOFF_FRAMES) {
                if (landed) return@repeat
                withFrameNanos { }
                landed = runCatching { contentFocus.requestFocus() }.isSuccess
            }
            if (!landed) {
                // The rail is the only thing focusable if this failed, so say
                // so rather than leaving a silent trap. The D-pad-right escape
                // below is what actually keeps the screen usable.
                android.util.Log.w(
                    "TvNavShell",
                    "content did not take focus for route=$currentRoute after " +
                        "$FOCUS_HANDOFF_FRAMES frames; rail keeps focus",
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .focusGroup()
                .focusRestorer()
                .focusRequester(contentFocus),
        ) {
            // MainActivity applies this to the NavHost root, so every screen
            // inherits the inset without needing to know the rail exists.
            content(PaddingValues(start = RAIL_COLLAPSED))
        }
        Column(
            Modifier
                .width(width)
                .fillMaxHeight()
                // The panel used to end on a hard vertical cut against the
                // artwork, which is most of why the rail read as pasted on
                // rather than part of the screen. Opaque where the labels sit,
                // fading out over the last stretch so the edge dissolves into
                // whatever is behind it.
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF2090A0E),
                        0.70f to Color(0xE60B0C10),
                        1f to Color(0x000B0C10),
                    ),
                )
                // `hasFocus` on the outer Column covers any descendant, so the
                // rail flips open the moment D-Pad-Left brings focus onto any
                // of its buttons.
                //
                // focusGroup() keeps the rail a single stop in focus search, so
                // D-Pad-Left from the content area lands here as a unit instead
                // of threading between individual buttons. (An earlier comment
                // here claimed the extension didn't exist in this Compose
                // version — it does; it lives in androidx.compose.foundation,
                // not androidx.compose.ui.focus.)
                .focusGroup()
                // The escape hatch, and the only part of this that makes the
                // trap impossible rather than merely unlikely.
                //
                // Everything else reduces the odds of the handoff losing its
                // race. This does not care whether it lost: right always pushes
                // focus into the content, so a remote can leave the rail even
                // when nothing else worked. Without it, one lost race still
                // costs a restart.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        expanded = false
                        // Explicitly Boolean: requestFocus() returns Unit, so a
                        // safe call on it is Unit? and the elvis/getOrDefault
                        // forms widen to a nullable type the key handler cannot
                        // return.
                        val target = contentFocusRef.value
                        if (target == null) {
                            false
                        } else {
                            runCatching { target.requestFocus() }.isSuccess
                        }
                    } else {
                        false
                    }
                }
                // Open when focus is in the rail, but never *stay* open on a
                // lost handoff: selecting a route closes it explicitly below,
                // so a failed focus move cannot leave the menu covering the
                // screen it just opened.
                .onFocusChanged { if (it.hasFocus) expanded = true else expanded = false }
                // The rail must scroll, and it did not.
                //
                // A 1080p Android TV reports 960×540 dp — the panel is 1080
                // physical pixels at xhdpi, so the *layout* height is 540 dp,
                // not 1080. Each rail item is about 44 dp plus a 6 dp gap, so
                // thirteen destinations plus the "Now playing" entry need
                // roughly 720 dp of column. In a plain Column that simply
                // overflows: everything past Downloads was drawn below the
                // bottom edge of the screen, unreachable and invisible, and
                // adding Catch-Up pushed one more item off. Nothing about the
                // rail's own geometry said so, because a Column will happily
                // lay out past its parent's bounds.
                //
                // verticalScroll gives it somewhere to go, and Compose's focus
                // system scrolls a newly focused child into view for free — so
                // D-pad down through the rail now walks the whole list.
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            if (nowPlayingLabel != null) {
                NavRailItem(
                    item = TvNavItem("nowPlaying", nowPlayingLabel.take(22), Icons.Rounded.PlayArrow, ""),
                    selected = true,
                    showLabel = width >= RAIL_LABEL_MIN,
                    onClick = onNowPlaying,
                )
                Spacer(Modifier.height(14.dp))
            }
            items.forEach { item ->
                val selected = currentRoute?.let { it.startsWith(item.route.substringBefore("?")) } == true
                NavRailItem(
                    item = item,
                    selected = selected,
                    showLabel = width >= RAIL_LABEL_MIN,
                    onClick = {
                        // Collapse on selection rather than waiting for focus
                        // to move. If the handoff below loses its race, the
                        // rail is at least not left covering the screen.
                        expanded = false
                        onSelect(item.route)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            // Tail padding, so the last destination can scroll clear of the
            // bottom edge instead of sitting flush against it.
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NavRailItem(
    item: TvNavItem,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    // Focus is carried by one thing now: a filled gradient.
    //
    // It was carried by four at once — a 1.5 dp bright border, a blue drop
    // shadow, a translucent fill and a taller stripe — and stacking them is
    // what made the focused item read as a chunky outlined button rather than
    // a selection. The border was the worst of it: a hard bright rectangle at
    // 10 foot has no depth, and next to a selected item that also drew a
    // stripe, one row could show four separate focus signals at once.
    //
    // The border and the shadow are gone. Focus is a brand gradient filling
    // the row; selection is a dimmer fill plus the stripe. They read as
    // different states without competing, and neither moves the geometry.
    val fillAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(if (focused) 140 else 200),
        label = "navFillAlpha",
    )
    // The stripe marks the current route, so it tracks selection alone. Tying
    // it to focus as well meant the focused row grew a stripe it did not need
    // and the selected row lost emphasis the moment focus moved past it.
    val stripeHeight by animateDpAsState(
        targetValue = if (selected) 26.dp else 0.dp,
        animationSpec = tween(200),
        label = "navStripeHeight",
    )

    Surface(
        onClick = { NavSounds.click(); onClick() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        // Both containers transparent: the fill is a gradient drawn on the Row
        // below, which a flat container colour cannot express.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = if (selected) Color.White else EnktelTextDim,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border.None),
        // Focus must NOT change this item's geometry.
        //
        // A focusedScale of 1.04 once produced a misaligned "weird box" on Fire
        // TV: tv-material scales the surface's own graphics layer, and anything
        // drawn outside that layer kept its original size, so one menu item
        // showed two rectangles at different scales. A stacked, full-width rail
        // item has nowhere to grow into anyway.
        //
        // Focus is carried entirely by the gradient fill below, which shares
        // one set of bounds with every other state.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        // No .focusable() here.
        //
        // tv-material's clickable Surface is already a focus target. Adding one
        // put a second focusable node in the same chain, and the two disagreed
        // about which item held focus — so items kept their focus treatment
        // after focus had moved on, and walking down the rail lit one ring per
        // item visited.
        //
        // onFocusChanged observes the chain below it, which still includes the
        // Surface's own focusable, so removing the duplicate keeps this working
        // and leaves exactly one node to report state.
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                // Selected sits under focus rather than beside it, so a row
                // that is both does not double up — the focus gradient simply
                // covers the quieter selected wash.
                .background(
                    if (selected) {
                        Brush.horizontalGradient(
                            listOf(EnktelBlue.copy(alpha = 0.16f), Color.Transparent),
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    },
                )
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            EnktelBlue.copy(alpha = 0.55f * fillAlpha),
                            EnktelPurple.copy(alpha = 0.30f * fillAlpha),
                        ),
                    ),
                )
                .padding(vertical = 10.dp, horizontal = 8.dp),
        ) {
            // 3 dp brand-color selection stripe on the left, so users can spot the
            // active route even in the collapsed 64 dp state.
            Box(
                Modifier
                    .width(3.dp)
                    .height(stripeHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) {
                            Brush.verticalGradient(listOf(EnktelBlue, EnktelPurple))
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                // No explicit tint: the Surface's contentColor already carries
                // dim / selected / focused, so the icon tracks the label instead
                // of staying a fixed colour the way an emoji had to.
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
            if (showLabel) {
                Spacer(Modifier.width(12.dp))
                Text(
                    item.label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
