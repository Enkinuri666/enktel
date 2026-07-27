package tv.enktel.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    text = Color(0xFFF1F5FA), textDim = Color(0xFF7A8598),
    primary = Color(0xFF38BDF8), primaryDeep = Color(0xFF0284C7), secondary = Color(0xFFA855F7),
    live = Color(0xFFF43F5E), ok = Color(0xFF22D3EE), border = Color(0xFF1B2436),
)
private val PaletteEnktelBlue = EnktelPalette(
    id = "enktel_blue", label = "EnkTel Blue",
    bg = Color(0xFF0A0E17), surface = Color(0xFF121826), surfaceHigh = Color(0xFF1B2333),
    text = Color(0xFFEAF0FA), textDim = Color(0xFF93A0B8),
    primary = Color(0xFF3B9DFF), primaryDeep = Color(0xFF1B6AE5), secondary = Color(0xFF8B5CF6),
    live = Color(0xFFEF4444), ok = Color(0xFF34D399), border = Color(0xFF2A3550),
)
private val PaletteCrimson = PaletteEnktelBlue.copy(
    id = "crimson", label = "Crimson Wolf",
    primary = Color(0xFFFF4D4F), primaryDeep = Color(0xFFC72026), secondary = Color(0xFFFF8A65),
)
private val PaletteEmerald = PaletteEnktelBlue.copy(
    id = "emerald", label = "Emerald",
    primary = Color(0xFF10B981), primaryDeep = Color(0xFF047857), secondary = Color(0xFF34D399),
)
private val PaletteAmber = PaletteEnktelBlue.copy(
    id = "amber", label = "Amber",
    primary = Color(0xFFF59E0B), primaryDeep = Color(0xFFB45309), secondary = Color(0xFFFCD34D),
)
private val PaletteMonochrome = PaletteEnktelBlue.copy(
    id = "monochrome", label = "Monochrome",
    bg = Color(0xFF000000), surface = Color(0xFF111111), surfaceHigh = Color(0xFF1F1F1F),
    primary = Color(0xFFEAEAEA), primaryDeep = Color(0xFFBDBDBD), secondary = Color(0xFF858585),
    border = Color(0xFF2E2E2E),
)
private val PaletteMidnight = PaletteEnktelBlue.copy(
    id = "midnight", label = "Midnight Purple",
    bg = Color(0xFF14082A), surface = Color(0xFF1F0F3D), surfaceHigh = Color(0xFF2A164F),
    primary = Color(0xFFA78BFA), primaryDeep = Color(0xFF7C3AED), secondary = Color(0xFFEC4899),
    border = Color(0xFF382060),
)
private val PaletteHighContrast = PaletteEnktelBlue.copy(
    id = "high_contrast", label = "High Contrast",
    bg = Color(0xFF000000), surface = Color(0xFF000000), surfaceHigh = Color(0xFF181818),
    text = Color(0xFFFFFFFF), textDim = Color(0xFFDDDDDD),
    primary = Color(0xFFFFEB3B), primaryDeep = Color(0xFFFBC02D), secondary = Color(0xFF00E5FF),
    live = Color(0xFFFF1744), ok = Color(0xFF00E676), border = Color(0xFF666666),
)

val ALL_PALETTES = listOf(
    PaletteObsidian, PaletteEnktelBlue, PaletteCrimson, PaletteEmerald, PaletteAmber,
    PaletteMidnight, PaletteMonochrome, PaletteHighContrast,
)
fun paletteFor(id: String): EnktelPalette = ALL_PALETTES.firstOrNull { it.id == id } ?: PaletteObsidian

private val LocalPalette = compositionLocalOf { PaletteObsidian }
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

@Composable
fun EnktelTheme(
    themeId: String = "obsidian",
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
