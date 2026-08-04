package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * A single top-level mode switch, in the style streaming apps use for
 * "Series | Movies": one enclosed track, the active segment filled.
 *
 * Distinct from [GlassChip], which is a *filter* — many can be on at once and
 * they narrow the same list. A segmented control is a *mode*: exactly one is
 * active and it changes what the list is. Using chips for both is what makes a
 * filter bar read as an undifferentiated wall of pills.
 *
 * [Segment.count] is shown when present, because a mode that would land the
 * user on an empty screen should say so before they select it. The same
 * reasoning runs through the category chips in the channel browser and the
 * guide.
 */
data class Segment(
    val id: String,
    val label: String,
    val count: Int? = null,
)

@Composable
fun SegmentedControl(
    segments: List<Segment>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = EnktelBlue,
) {
    if (segments.isEmpty()) return
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEach { seg ->
            val selected = seg.id == selectedId
            // Selected weight is animated rather than switched so the fill
            // slides between segments instead of blinking, which is what
            // makes the control read as one track rather than four buttons.
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                label = "segment-${seg.id}",
            )
            Surface(
                onClick = { onSelect(seg.id) },
                modifier = Modifier.height(32.dp).tapClick { onSelect(seg.id) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = accent.copy(alpha = 0.9f * alpha),
                    focusedContainerColor = accent,
                    contentColor = if (selected) Color.White else EnktelTextDim,
                    focusedContentColor = Color.White,
                ),
            ) {
                Box(
                    Modifier.padding(horizontal = 16.dp).height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (seg.count != null) "${seg.label} (${seg.count})" else seg.label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
