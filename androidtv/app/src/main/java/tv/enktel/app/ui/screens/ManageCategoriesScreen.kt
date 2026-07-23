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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.ChipRowLabel
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.SectionTitle
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Content organiser: lets the user reorder + hide categories per kind
 * (Live TV, Movies, Series). Persists to SettingsStore under
 * `<kind>_category_order` and `<kind>_hidden_cats`. Views that render
 * categories (LiveChannel panel, Movies, Series, TV Guide) are expected to
 * apply the saved order + hidden filter.
 */
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun ManageCategoriesScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf("live") } // live | vod | series
    val categoriesRaw by graph.content.categories(p.id, kind).collectAsStateWithLifecycle(initialValue = emptyList())
    val savedOrder by graph.settings.categoryOrder(kind).collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by graph.settings.hiddenCategories(kind).collectAsStateWithLifecycle(initialValue = emptySet())

    // Merge saved order with any newly-added categories (append them at the end).
    val order = remember(categoriesRaw, savedOrder) {
        val byId = categoriesRaw.associateBy { it.categoryId }
        val kept = savedOrder.filter { it in byId }
        kept + categoriesRaw.map { it.categoryId }.filterNot { it in kept.toSet() }
    }

    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Manage Categories")
        Text(
            "Drag order isn't wired to touch yet — tap ↑/↓ to move a category up or down. " +
                "Toggle 👁 to hide/show. Changes apply everywhere you browse.",
            color = EnktelTextDim, fontSize = 12.sp,
        )
        ChipRowLabel("Content type")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Live TV", selected = kind == "live", onClick = { kind = "live" })
            GlassChip("Movies", selected = kind == "vod", accent = EnktelOk, onClick = { kind = "vod" })
            GlassChip("Series", selected = kind == "series", accent = androidx.compose.ui.graphics.Color(0xFF8B5CF6), onClick = { kind = "series" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("Reset order", onClick = {
                scope.launch { graph.settings.setCategoryOrder(kind, emptyList()) }
            })
            FocusButton("Show all", onClick = {
                scope.launch { graph.settings.setHiddenCategories(kind, emptySet()) }
            })
            FocusButton("Hide all", onClick = {
                scope.launch { graph.settings.setHiddenCategories(kind, categoriesRaw.map { it.categoryId }.toSet()) }
            })
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(order, key = { it }) { catId ->
                val cat = categoriesRaw.firstOrNull { it.categoryId == catId } ?: return@items
                val idx = order.indexOf(catId)
                val isHidden = catId in hidden
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnktelSurfaceHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${idx + 1}", color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Black,
                         modifier = Modifier.width(28.dp))
                    tv.enktel.app.ui.components.PlatformBadgeFor(cat.name)
                    Text(
                        cat.name,
                        color = if (isHidden) EnktelTextDim else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    FocusButton("↑", onClick = {
                        if (idx > 0) {
                            val newOrder = order.toMutableList().apply {
                                add(idx - 1, removeAt(idx))
                            }
                            scope.launch { graph.settings.setCategoryOrder(kind, newOrder) }
                        }
                    })
                    FocusButton("↓", onClick = {
                        if (idx < order.size - 1) {
                            val newOrder = order.toMutableList().apply {
                                add(idx + 1, removeAt(idx))
                            }
                            scope.launch { graph.settings.setCategoryOrder(kind, newOrder) }
                        }
                    })
                    FocusButton(
                        if (isHidden) "👁 show" else "🚫 hide",
                        accent = !isHidden,
                        onClick = {
                            val next = hidden.toMutableSet().also {
                                if (isHidden) it.remove(catId) else it.add(catId)
                            }
                            scope.launch { graph.settings.setHiddenCategories(kind, next) }
                        },
                    )
                }
            }
        }
    }
}
