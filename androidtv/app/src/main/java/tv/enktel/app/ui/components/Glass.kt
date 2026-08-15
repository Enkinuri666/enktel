package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import tv.enktel.app.ui.theme.EnktelBorder
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelText

/**
 * The app's glass surfaces, and the scrims they sit on.
 *
 * ## What "glass" can and cannot mean here
 *
 * It cannot mean a blurred backdrop. Compose has no backdrop blur, and the
 * usual workaround does not work over a player: `RenderEffect.createBlurEffect`
 * on a Box blurs *that Box's own children*, so a glass wrapper built on it
 * smears the buttons inside it rather than frosting the video underneath.
 * v1.27.0 shipped exactly that on the Live TV action bar and the OSD came out
 * as an unreadable grey stripe; v1.28.1 removed it.
 *
 * The capture-and-blur technique that libraries use does not rescue it either.
 * Video renders on a `SurfaceView` — a separate hardware surface punched
 * through the window — so it is not in the Compose draw pass at all and cannot
 * be captured into a graphics layer to blur. Whatever the panels do, the
 * picture behind them is unreachable.
 *
 * ## So what actually makes something read as glass
 *
 * Blur is the famous ingredient, not the load-bearing one. Three cues do most
 * of the work, and all three are free here:
 *
 * **A specular edge.** Real glass catches the light along the edge nearest it.
 * A single flat hairline all the way round reads as a sticker; an edge that is
 * bright at the top-left and fades to almost nothing at the bottom-right reads
 * as a solid object under a light. This is the cue people actually respond to,
 * and the app had none of it.
 *
 * **A graded fill.** A flat alpha is a filter over the picture. A fill that is
 * denser at the top and thinner at the bottom implies thickness.
 *
 * **A soft scrim underneath rather than a hard one.** See [scrimBrush].
 *
 * ## Cost
 *
 * Deliberately nothing per-frame. No blur, no `Modifier.shadow` by default, no
 * saved layers — two gradients and a stroke, on a surface that is already being
 * composited. This matters more than usual: these panels sit over decoding
 * video on hardware that has been fighting to hold frame rate, and a prettier
 * OSD that costs frames is not a better OSD. [elevation] exists for the panels
 * that are *not* over video, and defaults to zero so a caller has to ask.
 */

/** Where a scrim's opaque end is. */
enum class ScrimEdge { Top, Bottom }

/**
 * A scrim whose alpha follows a curve rather than a straight line.
 *
 * ## Why the straight line is wrong
 *
 * The VOD player faded `Transparent → Black at 95 %` in two stops, and the live
 * player did much the same in three. A linear alpha ramp is the obvious way to
 * write a scrim and it always shows a visible edge where it starts, because the
 * eye finds the *discontinuity in the rate of change*, not the value. The
 * gradient begins at full slope, so there is a line across the picture at the
 * exact height the scrim was declared, and it moves whenever the layout does.
 *
 * A smoothstep — `t²(3−2t)` — has zero slope at both ends. Nothing marks where
 * it begins, and nothing marks where it tops out either, which is the second
 * artefact a two-stop ramp produces and nobody names. Sampled across enough
 * stops it also sidesteps the banding that sRGB interpolation gives you over a
 * long dark gradient.
 *
 * @param base the colour to fade — black over video, because a themed scrim
 *   over arbitrary picture content stops doing its job.
 * @param maxAlpha alpha at the opaque end.
 * @param steps how many stops to sample. Twelve is past the point where more
 *   helps; two is where the artefact lives.
 */
fun scrimBrush(
    base: Color = Color.Black,
    maxAlpha: Float = 0.95f,
    edge: ScrimEdge = ScrimEdge.Bottom,
    steps: Int = 12,
): Brush {
    val ramp = scrimRamp(maxAlpha, steps).map { base.copy(alpha = it) }
    return Brush.verticalGradient(if (edge == ScrimEdge.Top) ramp.reversed() else ramp)
}

/**
 * The alpha values [scrimBrush] samples, transparent end first.
 *
 * Separate from the brush so `GlassTest` can hold the properties the curve is
 * chosen for — a Brush is opaque once built, and the whole argument for this
 * curve is about its shape.
 */
fun scrimRamp(maxAlpha: Float = 0.95f, steps: Int = 12): List<Float> =
    List(steps + 1) { i ->
        val t = i / steps.toFloat()
        maxAlpha * t * t * (3f - 2f * t)
    }

/**
 * The two fill alphas of a glass panel, denser end first.
 *
 * Split out for the same reason as [scrimRamp], and because the clamping is
 * easy to get wrong in a way nothing would show: a panel asked for at 0.95
 * wants a top edge at 1.04, and a `Color.copy(alpha = 1.04f)` is not a compile
 * error, it is an invalid colour.
 */
fun glassFillAlphas(alpha: Float, spread: Float = 0.09f): Pair<Float, Float> =
    (alpha + spread).coerceIn(0f, 1f) to (alpha - spread).coerceIn(0f, 1f)

/**
 * Paint an eased scrim behind content — the player-chrome background.
 *
 * Replaces `.background(Brush.verticalGradient(listOf(Transparent, Black)))`
 * wherever that appears. Same idea, no visible starting edge.
 */
fun Modifier.cinematicScrim(
    maxAlpha: Float = 0.95f,
    edge: ScrimEdge = ScrimEdge.Bottom,
    base: Color = Color.Black,
): Modifier = this.background(scrimBrush(base, maxAlpha, edge))

/**
 * The app's glass panel treatment, as a modifier.
 *
 * Applies, in order: the graded fill, an optional accent wash, and the specular
 * edge. Clips to [shape] first so all three agree on the outline.
 *
 * @param tint what the glass is made of. Defaults to the theme's surface, so a
 *   panel follows the viewer's chosen palette — which the three glass surfaces
 *   over video notably used not to.
 * @param alpha density of the fill at its midpoint. The gradient runs ±0.09
 *   either side of this.
 * @param accent an optional colour wash across the top, for vibrancy. Real
 *   frosted glass picks up what is behind it; this is the closest thing
 *   available without being able to sample the backdrop. VOD passes the
 *   poster's dominant colour, which is already extracted for the Ambilight.
 * @param elevation opt-in drop shadow. Leave at zero for anything over video —
 *   a shadow forces a hardware layer, and frames matter more there than depth.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = EnktelSurface,
    alpha: Float = 0.72f,
    accent: Color? = null,
    border: Color = EnktelBorder,
    specular: Color = EnktelText,
): Modifier {
    val (dense, thin) = glassFillAlphas(alpha)
    val fill = Brush.verticalGradient(listOf(tint.copy(alpha = dense), tint.copy(alpha = thin)))
    // Top-left bright, bottom-right nearly gone: one light source, above and
    // to the left, which is where every UI in this idiom puts it.
    //
    // The highlight is the theme's text colour rather than white, so it stays
    // correct if a palette is ever built on a light surface — a white specular
    // on a pale panel is invisible, a dark one is the same cue inverted.
    val edge = Brush.linearGradient(
        colors = listOf(
            specular.copy(alpha = 0.22f),
            border,
            specular.copy(alpha = 0.03f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    return this
        .clip(shape)
        .background(fill, shape)
        .then(
            if (accent == null) {
                Modifier
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    ),
                    shape,
                )
            },
        )
        .border(1.dp, edge, shape)
}

/**
 * A floating glass chip — the pill form of [glassSurface].
 *
 * Separate because the chips over video (Skip Intro, Resumed from, the clock)
 * want a denser fill than a panel: they are small, they carry short text, and
 * they have to survive being placed over a bright frame with no say in what is
 * behind them.
 */
@Composable
fun Modifier.glassChip(
    tint: Color = EnktelSurface,
    alpha: Float = 0.82f,
    accent: Color? = null,
): Modifier = glassSurface(
    shape = RoundedCornerShape(percent = 50),
    tint = tint,
    alpha = alpha,
    accent = accent,
)
