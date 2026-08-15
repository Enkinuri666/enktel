package tv.enktel.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's frosted panel, as a container.
 *
 * A thin wrapper over [glassSurface] — that modifier is where the treatment
 * actually lives, and where the reasoning about what "glass" can mean without a
 * backdrop blur is written down. Use this when you want a Box; use the modifier
 * when you already have one.
 *
 * ### Don't add a blur here
 *
 * Compose has no backdrop blur. `RenderEffect.createBlurEffect` on a Box blurs
 * **that Box's own children**, not what is painted behind it — so a "glass"
 * wrapper built on it doesn't frost the video underneath, it smears the buttons
 * sitting inside it.
 *
 * v1.27.0 shipped exactly that on the Live TV action bar and the OSD came out
 * as an unreadable grey stripe across the bottom of the picture; v1.28.1
 * removed it. A `GlassPanel` helper survived that fix unused, still carrying the
 * blur and a doc comment claiming it blurred the backdrop — a trap for the next
 * person wanting a frosted overlay. It's gone, and this note is why.
 *
 * The capture-and-blur approach the Compose blur libraries use does not rescue
 * it either: video renders on a `SurfaceView`, which is a separate hardware
 * surface and is not in the Compose draw pass at all. There is nothing to
 * capture. [glassSurface] explains what is used instead.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.glassSurface(shape = RoundedCornerShape(16.dp), alpha = 0.70f, accent = accent)) {
        content()
    }
}
