package tv.enktel.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelBlueDeep
import tv.enktel.app.ui.theme.EnktelPurple
import kotlin.math.cos
import kotlin.math.sin

/**
 * The animated backdrop behind sign-in.
 *
 * The sign-in screen had no background of its own — a bare fillMaxSize Box over
 * whatever happened to be behind it — so the first thing anyone saw of the app
 * was its flattest surface, while every screen past it is layered artwork.
 *
 * ### Why this is drawn rather than played
 *
 * A looping video would be the obvious way to get motion, and it is the wrong
 * one here. It costs megabytes in the APK, holds a second decoder open next to
 * the one the player wants, and on a 1 GB Fire TV Stick it competes for exactly
 * the memory the rest of the app is careful about (see BufferProfiles for how
 * carefully). Three gradients on a Canvas cost no memory worth measuring and no
 * decoder at all.
 *
 * ### Built for the weakest device that runs it
 *
 * The target is a Fire TV Stick, not a phone, so:
 *
 *  - **No blur.** `Modifier.blur` needs API 31 and a RenderEffect; below that it
 *    silently does nothing, and above it on this class of GPU it is expensive.
 *    Soft edges come from radial gradients that fade to transparent, which the
 *    hardware draws for free.
 *  - **Four draws, no layers.** A base gradient, three glows and a vignette,
 *    all `drawRect` into the same layer. No saveLayer, no offscreen buffer.
 *  - **Slow.** The longest orbit is 34 s. Fast movement behind a form is
 *    distracting and it is also where a weak GPU starts dropping frames; slow
 *    drift reads as expensive and costs less.
 *
 * ### The look
 *
 * Two brand-coloured glows orbit on different periods so they meet and part
 * without ever repeating a visible pattern, over a deep vertical gradient. A
 * third, dimmer glow sweeps horizontally — the "signal" motion that reads as
 * broadcast rather than as a screensaver. A vignette closes the corners so the
 * form in the middle keeps its contrast no matter where the glows drift.
 */
@Composable
fun AuthBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "auth-backdrop")

    // Three periods, deliberately not multiples of each other: shared factors
    // make the glows realign on a fixed cycle, and a background that visibly
    // loops is worse than one that does not move at all.
    val slow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "slow",
    )
    val mid by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(23_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mid",
    )
    val sweep by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(19_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep",
    )

    val bg = EnktelBg
    val deep = EnktelBlueDeep
    val blue = EnktelBlue
    val purple = EnktelPurple

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val full = Size(w, h)

            // Base: deeper at the top so the logo sits against the darkest part
            // and the form below it against a slightly lifted floor.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to bg,
                    0.55f to deep.copy(alpha = 0.55f).compositeOverDark(bg),
                    1f to bg,
                ),
                size = full,
            )

            val tau = (2 * Math.PI).toFloat()

            // Two orbiting glows. Radii are a fraction of the diagonal so the
            // composition holds from a 540 dp TV layout up to a tablet.
            val r1 = maxOf(w, h) * 0.62f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(blue.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(
                        x = w * (0.30f + 0.16f * cos(slow * tau)),
                        y = h * (0.34f + 0.14f * sin(slow * tau)),
                    ),
                    radius = r1,
                ),
                size = full,
            )

            val r2 = maxOf(w, h) * 0.55f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(purple.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(
                        x = w * (0.72f - 0.18f * cos(mid * tau)),
                        y = h * (0.66f - 0.16f * sin(mid * tau)),
                    ),
                    radius = r2,
                ),
                size = full,
            )

            // The sweep: a wide, dim band travelling across and back. Reads as
            // signal moving rather than as decoration sitting still.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(blue.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(x = w * sweep, y = h * 0.5f),
                    radius = maxOf(w, h) * 0.42f,
                ),
                size = full,
            )

            // Vignette last, so the form keeps its contrast wherever the glows
            // happen to be. Transparent in the middle, so it costs nothing where
            // the content actually is.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = maxOf(w, h) * 0.78f,
                ),
                size = full,
            )
        }
        content()
    }
}

/**
 * Flattens [this] onto [base] at its own alpha.
 *
 * The mid-stop of the base gradient wants a tinted *opaque* colour, not a
 * translucent one: a translucent stop would let whatever is behind the screen
 * show through the middle band, and on a TV that is the previous activity.
 */
private fun Color.compositeOverDark(base: Color): Color {
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
        alpha = 1f,
    )
}
