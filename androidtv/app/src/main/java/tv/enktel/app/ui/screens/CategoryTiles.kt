package tv.enktel.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.components.tvRailFocus
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelBlueDeep
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelPurple

/**
 * The four things people came for, as tiles.
 *
 * ## Why this is back
 *
 * Home used to open with eleven pill shortcuts and they were removed, for a
 * good reason at the time: every one of them was also a permanent entry in a
 * fourteen-item nav rail three centimetres to the left, so Home began with a
 * complete second copy of the menu instead of with the viewer's content.
 *
 * Two things have changed since. The rail is six entries now, not fourteen,
 * so a short row of tiles is no longer a duplicate of a long list. And the
 * user research said the app is unfamiliar: every IPTV app these subscribers
 * have used before — Eagle, IBO, Smarters — opens on exactly this, big
 * labelled tiles for Live, Movies, Series. Being unlike them is not a virtue
 * when the complaint is that people cannot find anything.
 *
 * So: four categories and a search, not eleven shortcuts. Continue Watching
 * still follows immediately underneath, which was the other half of why the
 * old row was removed.
 *
 * ## On the artwork
 *
 * Drawn from this app's own palette and icon set rather than imitating
 * anyone's tiles. Each category keeps one hue everywhere it appears, so the
 * colour becomes the thing recognised at a glance from across a room — which
 * is the entire point of a tile over a menu row.
 */
private data class HomeCategory(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val from: @Composable () -> Color,
    val to: @Composable () -> Color,
)

private val HOME_TILES = listOf(
    HomeCategory("Live TV", Icons.Rounded.LiveTv, "channels", { EnktelBlue }, { EnktelBlueDeep }),
    HomeCategory("Movies", Icons.Rounded.Movie, "movies", { EnktelPurple }, { EnktelBlueDeep }),
    HomeCategory("Series", Icons.Rounded.Theaters, "series", { EnktelOk }, { EnktelBlueDeep }),
    HomeCategory("Sports", Icons.Rounded.SportsSoccer, "sports", { EnktelLive }, { EnktelPurple }),
    HomeCategory("Search", Icons.Rounded.Search, "search", { EnktelBlueDeep }, { EnktelBlue }),
)

/**
 * @param expiry a line like "Expires in 14 days", or null when there is
 *   nothing worth saying. Shown beside the tiles because it is the other
 *   thing people open the app to check, and it was two screens deep.
 */
@Composable
fun CategoryTiles(
    padHoriz: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    expiry: String? = null,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (expiry != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = padHoriz, end = padHoriz, bottom = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(expiry, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                }
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().tvRailFocus(),
            contentPadding = PaddingValues(horizontal = padHoriz),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            items(HOME_TILES, key = { it.label }) { c ->
                CategoryTile(c, compact = compact, onClick = { onSelect(c.route) })
            }
        }
    }
}

@Composable
private fun CategoryTile(c: HomeCategory, compact: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val w = if (compact) 132.dp else 210.dp
    val h = if (compact) 92.dp else 132.dp

    val lift by animateDpAsState(
        targetValue = if (focused) (-6).dp else 0.dp,
        animationSpec = tween(if (focused) 170 else 250),
        label = "tileLift",
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) 18.dp else 3.dp,
        animationSpec = tween(if (focused) 170 else 250),
        label = "tileElevation",
    )
    // The gradient brightens on focus rather than the tile growing. These sit
    // in a row with fixed gaps; scaling one pushes its neighbours' shadows
    // under it, and on a 10-foot display a 6dp lift plus a coloured glow reads
    // from further away than a 4 % size change does anyway.
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0.78f,
        animationSpec = tween(180),
        label = "tileGlow",
    )
    val from = c.from()
    val to = c.to()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(w)
            .height(h)
            .offset { IntOffset(0, lift.roundToPx()) }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(16.dp),
                clip = false,
                spotColor = if (focused) from else Color.Black,
            )
            .onFocusChanged { focused = it.isFocused }
            .tapClick(onClick),
        // Full-bleed gradient of our own, so the Surface must not paint over
        // it. See the Box below.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(16.dp),
            ),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(from.copy(alpha = glow), to.copy(alpha = glow)),
                    ),
                ),
        ) {
            // A soft wash from the bottom, so a white label stays readable over
            // the lighter end of every gradient without dimming the whole tile.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.35f),
                    ),
                ),
            )
            Icon(
                c.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (compact) 12.dp else 16.dp)
                    .size(if (compact) 28.dp else 40.dp),
            )
            Text(
                c.label,
                color = Color.White,
                fontSize = if (compact) 13.sp else 17.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (compact) 12.dp else 16.dp),
            )
        }
    }
    Spacer(Modifier.width(0.dp))
}
