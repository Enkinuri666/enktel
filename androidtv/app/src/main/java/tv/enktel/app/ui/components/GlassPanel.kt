package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * TV Cinematic glass card — the app's only "frosted panel" surface.
 *
 * Design-brief tokens: 14 dp corners, `#B012141D` tint, 1 dp `#1AFFFFFF`
 * stroke. Deliberately no blur.
 *
 * ### Don't add a blur here
 *
 * Compose has no backdrop-blur. `RenderEffect.createBlurEffect` on a Box
 * blurs **that Box's own children**, not what's painted behind it — so a
 * "glass" wrapper built on it doesn't frost the video underneath, it
 * smears the buttons sitting inside it.
 *
 * v1.27.0 shipped exactly that on the Live TV action bar and the OSD came
 * out as an unreadable grey stripe across the bottom of the picture;
 * v1.28.1 removed it. A `GlassPanel` helper survived that fix unused, still
 * carrying the blur and a doc comment claiming it blurred the backdrop —
 * a trap for the next person wanting a frosted overlay. It's gone now, and
 * this note is the reason why.
 *
 * The 70 % tint plus a hairline stroke reads as a floating card on its own,
 * which is how most iOS / Apple TV+ "glass" surfaces actually ship.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xB012141D), shape)
            .border(1.dp, Color(0x1AFFFFFF), shape),
    ) {
        content()
    }
}
