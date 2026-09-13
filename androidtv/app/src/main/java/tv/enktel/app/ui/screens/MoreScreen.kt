package tv.enktel.app.ui.screens

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
    val gutter = if (tv.enktel.app.BuildConfig.FLAVOR == "mobile") 16.dp else 48.dp
    // Painted rather than inherited. Every other destination sits on the
    // shell's gradient, but this one is also drawn by the screenshot harness
    // and by anything that hosts it outside the shell, and a grid of light
    // grey tiles on whatever happens to be behind is not a design.
    Column(Modifier.fillMaxSize().background(EnktelBg)) {
        Column(Modifier.padding(start = gutter, top = 28.dp, end = gutter, bottom = 4.dp)) {
            Text(t.navMore, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                t.moreSubtitle,
                color = EnktelTextDim,
                fontSize = 13.sp,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier.fillMaxSize().tvGridFocus(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                MoreTile(item, blurb = t.moreBlurb(item.id).orEmpty(), onClick = { onSelect(item.route) })
            }
        }
    }
}

@Composable
private fun MoreTile(item: TvNavItem, blurb: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (focused) Color.White.copy(alpha = 0.18f) else EnktelBlue.copy(alpha = 0.18f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(item.localLabel(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    blurb,
                    color = if (focused) Color.White.copy(alpha = 0.85f) else EnktelTextDim,
                    fontSize = 11.sp,
                    maxLines = 2,
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
