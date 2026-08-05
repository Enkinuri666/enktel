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
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
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
    modifier: Modifier = Modifier,
    fallback: Color = Color(0xFF3B9DFF),
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
                // The shared loader, not a fresh one: it is the only loader
                // configured with the app's OkHttp client (see EnktelApp),
                // and it already holds the poster in cache from whatever
                // rail is on screen, so this costs no second fetch.
                val loader = SingletonImageLoader.get(ctx)
                val req = ImageRequest.Builder(ctx)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(120) // small — we only need palette signal
                    .build()
                val result = loader.execute(req)
                if (result !is SuccessResult) return@withContext null
                // Coil 3 hands back its own Image rather than a Drawable, so
                // the old BitmapDrawable cast has nothing to match on — it
                // would compile away to null and leave the glow permanently
                // on the fallback blue. toBitmap() is the supported bridge.
                val bitmap = result.image.toBitmap()
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
