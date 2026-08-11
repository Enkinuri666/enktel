package tv.enktel.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// ---------------------------------------------------------------------------
// Theme palettes — pick from Settings ▸ Appearance ▸ Theme.
// Each palette keeps the dark background but changes the accent / secondary
// pair. The static values further down are the *runtime* colors picked from
// whatever palette is active (via LocalPalette), so existing screens that
// reference `EnktelBlue` etc. keep working and re-color automatically.
// ---------------------------------------------------------------------------

data class EnktelPalette(
    val id: String,
    val label: String,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textDim: Color,
    val primary: Color,
    val primaryDeep: Color,
    val secondary: Color,
    val live: Color,
    val ok: Color,
    val border: Color,
    // ---- v1.35.0 focus tokens ---------------------------------------------
    // The D-pad focus treatment used to be hardcoded at each call site
    // (PosterCard's border width, TvFocusScale's constants), which meant
    // "make focus 2 dp instead of 4" was a hunt across files and the two
    // could drift apart. They're palette fields now, so a theme owns its
    // whole focus language and every focusable surface stays consistent.
    //
    // Defaults reproduce the v1.30.0 treatment exactly, so palettes that
    // don't opt in are visually unchanged.
    /** Thickness of the focus outline drawn around a focused card. */
    val focusRingWidth: Dp = 4.dp,
    /** Radius of the diffuse coloured glow behind a focused card. */
    val focusGlowRadius: Dp = 18.dp,
    /** Alpha applied to [primary] to produce that glow. */
    val focusGlowAlpha: Float = 0.5f,
    /** Scale a focusable grows to while focused. */
    val focusScale: Float = 1.08f,
    /** Tertiary text — dimmer than [textDim], for de-emphasised metadata. */
    val textFaint: Color = Color(0xFF64748B),
)

/**
 * v1.35.0 — "Deep Space" theme, and the new default for fresh installs.
 *
 * Implements the Deep Space & Neon Accent token set:
 *   Surface L0  #0B0E14   Surface L1 #121824   Surface L2 #1A2332
 *   Border      #2A364F   Primary    #00F0FF   Secondary  #7B2CBF
 *   Success     #10B981   Alert      #EF4444
 *   Text        #F8FAFC / #94A3B8 / #64748B
 *
 * Two deliberate mappings, because the token list and this app's palette
 * model don't line up one-to-one:
 *
 *  - **`live` is bound to the alert red (#EF4444), not the cyan primary.**
 *    `live` paints LIVE badges, stream-error banners and the "server
 *    offline" state. The spec assigns #EF4444 to exactly that role, and
 *    binding it to the cyan accent instead would make a dead stream and a
 *    focused poster the same colour — the one pair that must never collide.
 *  - **The cyan primary doubles as the focus ring**, which is what makes
 *    the 2 dp / 12 px-glow focus spec render as specified without any
 *    per-component overrides.
 *
 * Focus geometry: 12 dp glow at 35 % alpha and a 1.05× lift over 150 ms per
 * the brief, but the ring stays at v1.30.0's 4 dp — the brief's 2 dp did not
 * survive contact with an actual TV at viewing distance. See the note on
 * [EnktelPalette.focusRingWidth].
 */
private val PaletteDeepSpace = EnktelPalette(
    id = "deep_space", label = "Deep Space (Neon)",
    bg = Color(0xFF0B0E14), surface = Color(0xFF121824), surfaceHigh = Color(0xFF1A2332),
    text = Color(0xFFF8FAFC), textDim = Color(0xFF94A3B8),
    primary = Color(0xFF00F0FF), primaryDeep = Color(0xFF00A8B5), secondary = Color(0xFF7B2CBF),
    live = Color(0xFFEF4444), ok = Color(0xFF10B981), border = Color(0xFF2A364F),
    // Ring back to 4 dp after checking it on a real panel: the brief's 2 dp
    // was too thin to read from the couch, which is exactly why v1.30.0
    // widened it in the first place. Glow radius and scale stay at the Deep
    // Space values — it was the outline that was hard to see, not the lift.
    focusRingWidth = 4.dp, focusGlowRadius = 12.dp, focusGlowAlpha = 0.35f, focusScale = 1.05f,
    textFaint = Color(0xFF64748B),
)

/**
 * v1.46.0 — "EnkTel Neon", the OLED-native default.
 *
 * ## True black, and what that actually costs
 *
 * The background is `#000000`. On an OLED panel that is not a colour, it is
 * the pixel switched off: no backlight bleed behind the poster art, infinite
 * contrast against it, and measurably less power drawn on a screen that is
 * mostly background. That is the whole reason for this palette; every other
 * choice here follows from it.
 *
 * Two consequences worth writing down, because they are what makes a naive
 * "just set the background to black" theme look wrong on real hardware:
 *
 *  - **The first surface above black cannot be nearly black.** OLED panels
 *    are badly non-linear at the bottom of their range, and several crush
 *    everything under roughly 4 % luminance into the same off state — so a
 *    `#080A0F` card on a `#000000` page renders as *no card at all*, and the
 *    layout loses its structure entirely. [surface] sits at `#0C111C`, high
 *    enough to survive that crush on the panels that do it while still
 *    reading as black in a dark room.
 *  - **Pure white body text on pure black smears** on OLED during scroll,
 *    because the pixel transition from fully off is the slowest one the panel
 *    makes. [text] is `#F2F6FF` rather than `#FFFFFF` — imperceptible as a
 *    colour, visibly cleaner in motion on a scrolling rail.
 *
 * ## The neon
 *
 * Accents are the EnkTel brand blue pushed to the top of sRGB rather than a
 * different hue: `#3B9DFF` becomes `#29B6FF`. Against true black a saturated
 * accent reads as emissive without any glow effect doing the work, which is
 * what "neon" means on a self-lit panel. The violet secondary and mint
 * success sit at the same saturation so no one accent dominates.
 *
 * Focus keeps a 4 dp ring — the same lesson Deep Space learned, that a 2 dp
 * outline does not survive a 10-foot viewing distance — but carries a wider,
 * stronger glow than any other palette, because a glow bleeding into true
 * black is the one place the effect is genuinely free.
 */
private val PaletteEnktelNeon = EnktelPalette(
    id = "enktel_neon", label = "EnkTel Neon (OLED)",
    bg = Color(0xFF000000),
    surface = Color(0xFF0C111C),
    surfaceHigh = Color(0xFF161E30),
    text = Color(0xFFF2F6FF), textDim = Color(0xFF8FA2C0),
    primary = Color(0xFF29B6FF), primaryDeep = Color(0xFF0A72D0), secondary = Color(0xFFB14DFF),
    live = Color(0xFFFF3B5C), ok = Color(0xFF00E5A0), border = Color(0xFF1E2A44),
    focusRingWidth = 4.dp, focusGlowRadius = 22.dp, focusGlowAlpha = 0.55f, focusScale = 1.06f,
    textFaint = Color(0xFF5A6B87),
)

/**
 * v1.27.0 — "Cinematic" theme. Midnight Charcoal base with Electric
 * Indigo D-Pad focus and Cyber Cyan live/quality accents. Colour tokens
 * from the TV brief; sits alongside Obsidian as the new default premium
 * option for the 10-foot experience.
 *
 * - Base surface: #0B0C10 (Midnight Charcoal — never pure black so poster
 *   art doesn't lose its darkest tones against a #000 backdrop).
 * - Glass surface: sRGB 18,20,29 @ 70 % alpha (drawn via GlassCard).
 * - Focus accent: #5A52FF (Electric Indigo) — the D-Pad focus ring.
 * - Live / quality accent: #00F2FE (Cyber Cyan) — LIVE badge, HDR /
 *   quality indicators, "Now Playing" chip.
 */
private val PaletteCinematic = EnktelPalette(
    id = "cinematic", label = "Cinematic (Midnight Pro)",
    bg = Color(0xFF0B0C10), surface = Color(0xFF12141D), surfaceHigh = Color(0xFF1A1D28),
    text = Color(0xFFF3F4F8), textDim = Color(0xFF8C94AC),
    // Indigo lifted from #5A52FF to #8A83FF.
    //
    // The original sat at 3.80:1 against this background — the weakest accent
    // of any palette here by a wide margin, and the one place it could least
    // afford to be, because [primary] is what draws the D-pad focus ring. A
    // focus indicator that low in contrast is legible on a monitor at desk
    // distance and genuinely hard to find on a television across a room, which
    // is the only distance this app is used at. The lift takes it to 6.30:1
    // while staying recognisably Electric Indigo; primaryDeep rises with it so
    // pressed and secondary states keep their relationship.
    primary = Color(0xFF8A83FF), primaryDeep = Color(0xFF4F47D6), secondary = Color(0xFF00F2FE),
    // Opaque, like every other palette. This was `0x1AFFFFFF` — white at 10 %
    // — which is a different kind of value from the structural colours the
    // other themes use, so a divider composited differently here than
    // everywhere else depending on what sat behind it.
    live = Color(0xFF00F2FE), ok = Color(0xFF34D399), border = Color(0xFF262B3A),
    focusRingWidth = 4.dp, focusGlowRadius = 16.dp, focusGlowAlpha = 0.45f, focusScale = 1.06f,
    textFaint = Color(0xFF5F677D),
)

/**
 * Obsidian — the new premium default. Deep near-black backdrop (0x050608)
 * with a slight cool-tinted mid surface (0x0C1017), electric cyan-blue
 * primary (0x38BDF8), plum-purple secondary (0xA855F7). Designed for
 * high-contrast poster art + glass overlays without any of the muddy
 * navy that the old "EnkTel Blue" theme washed the UI in.
 */
private val PaletteObsidian = EnktelPalette(
    id = "obsidian", label = "Obsidian (Premium)",
    bg = Color(0xFF050608), surface = Color(0xFF0C1017), surfaceHigh = Color(0xFF141A26),
    text = Color(0xFFF1F5FA), textDim = Color(0xFF8B95A8),
    primary = Color(0xFF38BDF8), primaryDeep = Color(0xFF0284C7), secondary = Color(0xFFA855F7),
    // Border raised from #1B2436 (1.23:1 against the surface — effectively an
    // invisible divider) to 1.62:1. Structure you cannot see is structure the
    // layout does not have, and on a dense grid it is the difference between
    // rows reading as grouped and reading as floating.
    live = Color(0xFFF43F5E), ok = Color(0xFF22D3EE), border = Color(0xFF2A3850),
    focusRingWidth = 4.dp, focusGlowRadius = 16.dp, focusGlowAlpha = 0.42f, focusScale = 1.06f,
    textFaint = Color(0xFF5C6678),
)
private val PaletteEnktelBlue = EnktelPalette(
    id = "enktel_blue", label = "EnkTel Blue",
    bg = Color(0xFF0A0E17), surface = Color(0xFF121826), surfaceHigh = Color(0xFF1B2333),
    text = Color(0xFFEAF0FA), textDim = Color(0xFF93A0B8),
    primary = Color(0xFF3B9DFF), primaryDeep = Color(0xFF1B6AE5), secondary = Color(0xFF8B5CF6),
    live = Color(0xFFEF4444), ok = Color(0xFF34D399), border = Color(0xFF2A3550),
)
// ---------------------------------------------------------------------------
// The three accent themes.
//
// These were `PaletteEnktelBlue.copy(primary = …, secondary = …)`: the blue
// theme's navy-tinted greys with one colour swapped. That is why they never
// felt like separate designs — every surface, border and dim-text value in
// "Crimson Wolf" was still tuned to sit under a blue accent, so the accent read
// as applied to the theme rather than as the theme's own.
//
// Each now carries a surface ramp tinted a few points toward its own hue. The
// shift is small on purpose — these are near-blacks, and pushing the saturation
// far enough to name the colour is what makes a dark UI look tinted rather than
// deep. It is enough that a red accent lands on faintly warm greys and a green
// one on faintly cool greys, which is what stops an accent looking pasted on.
//
// Every one also states its own focus geometry rather than inheriting the
// v1.30.0 defaults, so the ring and glow are tuned to how bright the accent
// actually is: a luminous amber needs less glow than a deep red to read as
// equally focused.
// ---------------------------------------------------------------------------

private val PaletteCrimson = EnktelPalette(
    id = "crimson", label = "Crimson Wolf",
    bg = Color(0xFF0B0709), surface = Color(0xFF1B1015), surfaceHigh = Color(0xFF26171E),
    text = Color(0xFFFAF2F4), textDim = Color(0xFFB09AA0),
    // #FF4D4F was fine on navy and muddy on a warm ground. #FF5A5F is a touch
    // lighter and warmer: 6.56:1 here, so the focus ring carries at distance.
    primary = Color(0xFFFF5A5F), primaryDeep = Color(0xFFC42F35), secondary = Color(0xFFFF9E7A),
    // The one place a red theme has to be careful. `live` paints LIVE badges
    // and error banners, and on a red accent the obvious choices are all wrong:
    // red collides with focus outright, and amber — the first thing tried here
    // — sits 35° away in hue, which is close enough that red-green colour
    // blindness collapses the two. Cyan is 170° away and survives every common
    // form of it, which is why Cinematic uses it for exactly this job.
    live = Color(0xFF22D3EE), ok = Color(0xFF3DD68C), border = Color(0xFF4A2F3B),
    focusRingWidth = 4.dp, focusGlowRadius = 18.dp, focusGlowAlpha = 0.45f, focusScale = 1.06f,
    textFaint = Color(0xFF7C6A70),
)

private val PaletteEmerald = EnktelPalette(
    id = "emerald", label = "Emerald",
    bg = Color(0xFF050B09), surface = Color(0xFF0B1512), surfaceHigh = Color(0xFF121E1A),
    text = Color(0xFFF0FAF5), textDim = Color(0xFF93B0A5),
    // #10B981 is a mid-tone that reads as a "success" chip rather than as an
    // interface accent. #2DD4A0 is brighter and slightly cooler — 10.43:1, and
    // distinguishable from [ok] below, which matters because a green theme
    // otherwise renders "focused" and "fine" identically.
    primary = Color(0xFF2DD4A0), primaryDeep = Color(0xFF0E8F68), secondary = Color(0xFF6EE7C4),
    live = Color(0xFFFF5470), ok = Color(0xFF8CD98C), border = Color(0xFF2A423B),
    // The brightest accent of the three, so the glow is pulled in: at the
    // shared 18 dp it haloes rather than reads as a ring.
    focusRingWidth = 4.dp, focusGlowRadius = 14.dp, focusGlowAlpha = 0.38f, focusScale = 1.06f,
    textFaint = Color(0xFF64837A),
)

private val PaletteAmber = EnktelPalette(
    id = "amber", label = "Amber",
    bg = Color(0xFF0C0906), surface = Color(0xFF171106), surfaceHigh = Color(0xFF221A0C),
    text = Color(0xFFFCF6EC), textDim = Color(0xFFB6A588),
    primary = Color(0xFFFFB020), primaryDeep = Color(0xFFB9760A), secondary = Color(0xFFFFD974),
    // Rose rather than red. Red is only 39° from this accent — near enough
    // that a LIVE badge reads as a darker amber rather than as an alert, and
    // near enough to merge entirely under red-green colour blindness. Rose
    // clears it by 60°.
    live = Color(0xFFFF4D8D), ok = Color(0xFF4ADE80), border = Color(0xFF4C3A1C),
    // The most luminous accent in the app. Least glow of any palette — more
    // and the focused card looks lit rather than selected.
    focusRingWidth = 4.dp, focusGlowRadius = 12.dp, focusGlowAlpha = 0.32f, focusScale = 1.06f,
    textFaint = Color(0xFF7E7059),
)
private val PaletteMonochrome = PaletteEnktelBlue.copy(
    id = "monochrome", label = "Monochrome",
    bg = Color(0xFF000000), surface = Color(0xFF111111), surfaceHigh = Color(0xFF1F1F1F),
    text = Color(0xFFF2F2F2), textDim = Color(0xFFA8A8A8),
    primary = Color(0xFFEAEAEA), primaryDeep = Color(0xFFBDBDBD), secondary = Color(0xFF858585),
    // A greyscale theme has no accent hue to signal with, so `live` stays the
    // one coloured thing in it. That is the point rather than an inconsistency:
    // the only red on screen is unmissable.
    border = Color(0xFF3A3A3A),
    // No glow. A white accent glowing on grey is a smudge, not a highlight —
    // the ring alone does the work here, so it is a step wider.
    focusRingWidth = 5.dp, focusGlowRadius = 0.dp, focusGlowAlpha = 0f, focusScale = 1.07f,
    textFaint = Color(0xFF6E6E6E),
)
private val PaletteMidnight = PaletteEnktelBlue.copy(
    id = "midnight", label = "Midnight Purple",
    bg = Color(0xFF14082A), surface = Color(0xFF1F0F3D), surfaceHigh = Color(0xFF2A164F),
    text = Color(0xFFF6EFFF), textDim = Color(0xFFB4A0D4),
    primary = Color(0xFFC4A9FF), primaryDeep = Color(0xFF7C3AED), secondary = Color(0xFFF472B6),
    live = Color(0xFFFF6B81), ok = Color(0xFF4ADE80), border = Color(0xFF4A2E78),
    focusRingWidth = 4.dp, focusGlowRadius = 18.dp, focusGlowAlpha = 0.45f, focusScale = 1.06f,
    textFaint = Color(0xFF7E6B9E),
)
private val PaletteHighContrast = PaletteEnktelBlue.copy(
    id = "high_contrast", label = "High Contrast",
    bg = Color(0xFF000000), surface = Color(0xFF000000), surfaceHigh = Color(0xFF181818),
    text = Color(0xFFFFFFFF), textDim = Color(0xFFDDDDDD),
    primary = Color(0xFFFFEB3B), primaryDeep = Color(0xFFFBC02D), secondary = Color(0xFF00E5FF),
    live = Color(0xFFFF1744), ok = Color(0xFF00E676), border = Color(0xFF8A8A8A),
    // This palette exists for people who cannot pick the focused card out of a
    // subtle one, so it gets the widest ring in the app and no glow at all —
    // a soft halo is exactly the cue that fails for low vision, and a hard
    // edge is exactly the one that does not.
    focusRingWidth = 6.dp, focusGlowRadius = 0.dp, focusGlowAlpha = 0f, focusScale = 1.10f,
    textFaint = Color(0xFFBBBBBB),
)

val ALL_PALETTES = listOf(
    PaletteEnktelNeon, PaletteDeepSpace, PaletteCinematic, PaletteObsidian, PaletteEnktelBlue, PaletteCrimson, PaletteEmerald,
    PaletteAmber, PaletteMidnight, PaletteMonochrome, PaletteHighContrast,
)
fun paletteFor(id: String): EnktelPalette = ALL_PALETTES.firstOrNull { it.id == id } ?: PaletteEnktelNeon

private val LocalPalette = compositionLocalOf { PaletteEnktelNeon }
/** Alpha multiplier (0-1) for overlay surfaces — dialogs, info bars, panels. */
val LocalOverlayOpacity = compositionLocalOf { 0.92f }

// ---------------------------------------------------------------------------
// Compat aliases — existing screens grab these Color constants directly.
// They now resolve against the *active* palette rather than a hardcoded blue.
// ---------------------------------------------------------------------------
val EnktelBlue: Color @Composable get() = LocalPalette.current.primary
val EnktelBlueDeep: Color @Composable get() = LocalPalette.current.primaryDeep
val EnktelPurple: Color @Composable get() = LocalPalette.current.secondary
val EnktelBg: Color @Composable get() = LocalPalette.current.bg
val EnktelSurface: Color @Composable get() = LocalPalette.current.surface
val EnktelSurfaceHigh: Color @Composable get() = LocalPalette.current.surfaceHigh
val EnktelText: Color @Composable get() = LocalPalette.current.text
val EnktelTextDim: Color @Composable get() = LocalPalette.current.textDim
val EnktelLive: Color @Composable get() = LocalPalette.current.live
val EnktelOk: Color @Composable get() = LocalPalette.current.ok

// ---------------------------------------------------------------------------
// Design tokens. Read these instead of hardcoding a value at a call site —
// that's what keeps one theme's focus language consistent across every
// focusable surface in the app.
// ---------------------------------------------------------------------------

/** Structural divider / outline colour. */
val EnktelBorder: Color @Composable get() = LocalPalette.current.border

/** Tertiary text: de-emphasised metadata, below [EnktelTextDim]. */
val EnktelTextFaint: Color @Composable get() = LocalPalette.current.textFaint

/** Thickness of the D-pad focus outline. */
val EnktelFocusRingWidth: Dp @Composable get() = LocalPalette.current.focusRingWidth

/** Radius of the diffuse glow behind a focused card. */
val EnktelFocusGlowRadius: Dp @Composable get() = LocalPalette.current.focusGlowRadius

/** Scale a focusable grows to while focused. */
val EnktelFocusScale: Float @Composable get() = LocalPalette.current.focusScale

/**
 * Colour of the focus glow — the accent at the palette's glow alpha.
 *
 * Rendered via `Modifier.shadow(spotColor = …)`, which only honours a
 * coloured spot colour on API 28+. On API 23-27 it degrades to a neutral
 * shadow; the 2 dp ring still reads, so focus is never ambiguous on those
 * devices.
 */
val EnktelFocusGlow: Color
    @Composable get() = LocalPalette.current.primary.copy(alpha = LocalPalette.current.focusGlowAlpha)

@Composable
fun EnktelTheme(
    themeId: String = "enktel_neon",
    overlayOpacity: Float = 0.92f,
    textScalePct: Int = 100,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeId)
    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * (textScalePct / 100f))
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalOverlayOpacity provides overlayOpacity.coerceIn(0.6f, 1f),
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.primary,
                onPrimary = Color.White,
                secondary = palette.secondary,
                onSecondary = Color.White,
                background = palette.bg,
                onBackground = palette.text,
                surface = palette.surface,
                onSurface = palette.text,
                surfaceVariant = palette.surfaceHigh,
                onSurfaceVariant = palette.textDim,
                border = palette.border,
            ),
            content = content,
        )
    }
}
