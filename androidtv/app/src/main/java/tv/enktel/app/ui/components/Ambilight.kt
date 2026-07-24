package tv.enktel.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Ambilight" style ambient glow that bleeds the dominant colour of an
 * asset's poster / logo behind whatever UI sits on top of it — the same
 * living-room effect as a Philips Ambilight TV, translated to the app.
 *
 * The dominant colour is extracted from the currently-focused asset's
 * artwork using AndroidX Palette on a background thread.  It then
 * animates via [animateColorAsState] so switching between rails or
 * highlighting a new hero produces a smooth cinematic wash rather than
 * a hard cut.  Renders as a large radial gradient at the top of the
 * screen with a soft falloff into the app's dark background.
 *
 * Behaviour:
 *  - null / blank [imageUrl] falls back to the app's brand-blue accent.
 *  - Palette extraction failures fall back to the current colour, so an
 *    unreachable poster URL never leaves the glow at plain black.
 *  - The composable itself is a positioned Box you can overlay on top
 *    of the actual UI (place it below the content in Z order).
 */
@Composable
fun AmbilightGlow(
    imageUrl: String?,
    fallback: Color = Color(0xFF3B9DFF),
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(fallback) }
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            target = fallback
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(ctx)
                val req = ImageRequest.Builder(ctx)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(120) // small — we only need palette signal
                    .build()
                val result = loader.execute(req)
                if (result !is SuccessResult) return@withContext null
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: return@withContext null
                val palette = Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()
                val rgb = palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                rgb?.let { Color(it) }
            } catch (_: Throwable) { null }
        }
        if (extracted != null) target = extracted
    }
    val animated by animateColorAsState(target, animationSpec = tween(700), label = "ambilight")
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        animated.copy(alpha = 0.32f),
                        animated.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    radius = 1600f,
                )
            )
    )
}
