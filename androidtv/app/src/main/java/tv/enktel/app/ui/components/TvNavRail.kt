package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * v1.29.0 TV cinematic refactor phase 3 — collapsible left navigation
 * rail per the design brief.
 *
 *   - **Collapsed:** 64 dp wide, icon-only. This is the resting state
 *     whenever focus lives inside the content area (the rail is out of
 *     the way so posters get the full width).
 *   - **Expanded:** 220 dp wide, icon + label + selection stripe.
 *     Triggered automatically the moment focus lands in the rail
 *     (D-Pad-Left from the leftmost content column).
 *   - Animation: `tween(220 ms)` matches the design brief's "smooth"
 *     expansion, run through `animateDpAsState` so the content Row
 *     (rail | main) re-lays out incrementally.
 *
 * Screens are opted in via the [TvNavShell] wrapper in MainActivity;
 * immersive routes (players, onboarding, first-run tour) skip it.
 */
data class TvNavItem(
    val id: String,
    val label: String,
    val glyph: String,
    val route: String,
)

private val DEFAULT_ITEMS = listOf(
    TvNavItem("home", "Home", "🏠", "home"),
    TvNavItem("live", "Live TV", "📺", "live?ch="),
    TvNavItem("guide", "TV Guide", "🗓", "guide"),
    TvNavItem("movies", "Movies", "🎬", "movies"),
    TvNavItem("series", "Series", "🎞", "series"),
    TvNavItem("sports", "Sports", "⚽", "sports"),
    TvNavItem("watchlist", "Watchlist", "☆", "watchlist"),
    TvNavItem("downloads", "Downloads", "⬇", "downloads"),
    TvNavItem("recordings", "Recordings", "⏺", "recordings"),
    TvNavItem("search", "Search", "🔍", "search"),
    TvNavItem("settings", "Settings", "⚙", "settings"),
)

@Composable
fun TvNavShell(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    items: List<TvNavItem> = DEFAULT_ITEMS,
    content: @Composable (PaddingValues) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) 220.dp else 64.dp,
        animationSpec = tween(durationMillis = 220),
        label = "tv-nav-rail-width",
    )

    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .width(width)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xEA0B0C10), Color(0xEA0A0B0F)),
                    ),
                )
                // `hasFocus` on the outer Column covers any descendant, so
                // the rail flips open the moment D-Pad-Left brings focus onto
                // any of its buttons. (No `focusGroup()` needed — that
                // extension isn't in this Compose version and Compose's
                // focus tree already propagates hasFocus to the parent.)
                .onFocusChanged { expanded = it.hasFocus },
        ) {
            Spacer(Modifier.height(24.dp))
            items.forEach { item ->
                val selected = currentRoute?.let { it.startsWith(item.route.substringBefore("?")) } == true
                NavRailItem(item = item, selected = selected, expanded = expanded, onClick = { onSelect(item.route) })
                Spacer(Modifier.height(6.dp))
            }
        }
        Box(Modifier.weight(1f)) {
            content(PaddingValues(0.dp))
        }
    }
}

@Composable
private fun NavRailItem(
    item: TvNavItem,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = { NavSounds.click(); onClick() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) EnktelBlue.copy(alpha = 0.20f) else Color.Transparent,
            focusedContainerColor = EnktelBlue.copy(alpha = 0.35f),
            contentColor = if (selected) Color.White else EnktelTextDim,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, EnktelBlue),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .focusable(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        ) {
            // 3 dp brand-color selection stripe on the left, so users can spot the
            // active route even in the collapsed 64 dp state.
            Box(
                Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) EnktelBlue else Color.Transparent),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text(item.glyph, fontSize = 18.sp)
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    item.label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
