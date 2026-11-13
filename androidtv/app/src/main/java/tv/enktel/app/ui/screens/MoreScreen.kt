package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.i18n.LocalStrings
import tv.enktel.app.ui.components.TvNavItem
import tv.enktel.app.ui.components.hiddenInSimpleMenu
import tv.enktel.app.ui.components.localLabel
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.components.tvGridFocus
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Everything the short menu leaves out.
 *
 * Not a settings page and not a dumping ground: it is the other half of the
 * rail, drawn as tiles big enough to read across a room. The short menu is a
 * claim about what people open often, and this is the honest other side of
 * that claim — the eight destinations it deprioritised, all still one press
 * away, none of them removed.
 *
 * Each tile says what the screen is for underneath its name. "My Lists" and
 * "Catch-Up" are not self-explanatory to someone who has owned the app for a
 * day, and a grid of fourteen bare nouns is the problem this screen exists to
 * solve, not a thing to reproduce at one level deeper.
 */
@Composable
fun MoreScreen(onSelect: (String) -> Unit) {
    val items = remember { hiddenInSimpleMenu() }
    val t = LocalStrings.current
    // 48dp is a television's overscan gutter. On a 411dp handset it eats a
    // quarter of the width, so the tiles came out narrow enough to wrap their
    // own one-line descriptions.
    // The page gutter every screen shares, so the title does not step
    // sideways between destinations.
    val shape = tv.enktel.app.ui.components.rememberScreenShape()
    val gutter = shape.padH
    val narrow = shape.narrow
    // Painted rather than inherited. Every other destination sits on the
    // shell's gradient, but this one is also drawn by the screenshot harness
    // and by anything that hosts it outside the shell, and a grid of light
    // grey tiles on whatever happens to be behind is not a design.
    Column(Modifier.fillMaxSize().background(EnktelBg)) {
        Column(Modifier.padding(start = gutter, top = 28.dp, end = gutter, bottom = 4.dp)) {
            Text(t.navMore, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                t.moreSubtitle,
                color = EnktelTextDim,
                fontSize = 13.sp,
            )
        }
        // The tiles fill the page, rather than sitting in the top corner of it.
        //
        // Eight tiles in three columns are three rows, and a row was as tall
        // as its own 16 dp padding plus a 44 dp icon — 76 dp. On a television
        // that left the bottom two thirds of the screen black while the tiles
        // themselves were small enough to lean in for. The height each row can
        // have is simple arithmetic once the viewport is known, so the rows
        // take it: the same eight destinations, read from a sofa.
        //
        // Bounded at both ends. The lower bound is what the tiles were, for a
        // phone, where eight rows already overflow the screen and are meant
        // to scroll. The upper bound is so that a short list on a tall screen
        // does not become three enormous slabs.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = 12.dp
            val minTile = 240.dp
            val top = 16.dp
            val bottom = 48.dp
            val cols = (((maxWidth - gutter * 2) + gap) / (minTile + gap)).toInt().coerceAtLeast(1)
            val rows = (items.size + cols - 1) / cols
            // Only where there is room going spare. A phone is one column of
            // eight, which already fills the screen; giving those rows a fixed
            // height cost two of the blurbs their second line.
            val rowHeight = if (narrow) {
                null
            } else {
                ((maxHeight - top - bottom - gap * (rows - 1)) / rows)
                    .coerceIn(MORE_TILE_MIN, MORE_TILE_MAX)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize().tvGridFocus(),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = top, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(items, key = { it.id }) { item ->
                    MoreTile(
                        item,
                        blurb = t.moreBlurb(item.id).orEmpty(),
                        height = rowHeight,
                        onClick = { onSelect(item.route) },
                    )
                }
            }
        }
    }
}

/** What a tile was before it filled the page: padding, icon, padding. */
private val MORE_TILE_MIN = 76.dp

/** Three slabs is not a menu. */
private val MORE_TILE_MAX = 148.dp

@Composable
private fun MoreTile(
    item: TvNavItem,
    blurb: String,
    /** The row's share of the page, or null to be as tall as its own contents. */
    height: androidx.compose.ui.unit.Dp?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Everything inside grows with the tile, so a taller tile is a bigger
    // tile rather than the same small tile with air around it.
    val grown = height?.let {
        ((it - MORE_TILE_MIN) / (MORE_TILE_MAX - MORE_TILE_MIN)).coerceIn(0f, 1f)
    } ?: 0f
    fun lerp(from: Float, to: Float) = from + (to - from) * grown
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = tv.enktel.app.ui.components.RowFocus.colors(),
        border = tv.enktel.app.ui.components.RowFocus.border(RoundedCornerShape(16.dp)),
        scale = tv.enktel.app.ui.components.RowFocus.scale(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (height != null) Modifier.fillMaxSize() else Modifier)
                .padding(lerp(16f, 22f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(lerp(44f, 72f).dp)
                    .background(
                        if (focused) Color.White.copy(alpha = 0.18f) else EnktelBlue.copy(alpha = 0.18f),
                        RoundedCornerShape(lerp(12f, 18f).dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(lerp(22f, 36f).dp))
            }
            Spacer(Modifier.width(lerp(14f, 18f).dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.localLabel(),
                    fontSize = lerp(15f, 20f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(lerp(2f, 5f).dp))
                Text(
                    blurb,
                    color = if (focused) Color.White.copy(alpha = 0.85f) else EnktelTextDim,
                    fontSize = lerp(11f, 14f).sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/*
 * The one-line descriptions used to live here as `blurbFor(id)`. They moved to
 * tv.enktel.app.i18n.Strings.moreBlurb when the app gained a second language —
 * two copies of the same sentence, one translated and one not, is how a screen
 * ends up half in each.
 */
