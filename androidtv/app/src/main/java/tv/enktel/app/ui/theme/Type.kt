package tv.enktel.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's type scale.
 *
 * ## Why this exists
 *
 * There were 481 hardcoded `fontSize = N.sp` call sites across 26 distinct
 * values, including 8.5, 11.5, 12.5, 17, 19 and 23 sp. Nothing was choosing
 * those numbers — they accumulated one screen at a time, so a "section
 * heading" was 19 sp on Home, 22 sp on Sports and 18 sp in a dialog, and the
 * weights attached to them drifted the same way. That is most of what makes a
 * dense TV UI read as assembled rather than designed: not any single wrong
 * size, but the absence of a repeating rhythm for the eye to lock onto.
 *
 * These are the eight roles the app actually has. Reach for the nearest one
 * rather than adding a ninth.
 *
 * ## Sizes are TV-first
 *
 * The scale is set for a 10-foot viewing distance, which is the harder of the
 * two cases: a phone held at arm's length can read anything the television can,
 * but not the reverse. Both flavours share these components, so a single scale
 * that satisfies the television satisfies both.
 *
 * User text scaling still applies on top. [EnktelTheme] folds the Settings
 * text-scale percentage into `LocalDensity.fontScale`, so every size here is
 * multiplied by it — which is why they are `sp` and not `dp`, and why nothing
 * in this file needs to know the setting exists.
 *
 * ## Colour is deliberately absent from most of these
 *
 * A style that carried a colour would be wrong half the time it was used: the
 * same rail heading renders on the app background in one place and over poster
 * artwork in another, and those want different colours for a real reason (see
 * [EnktelTextOnArt]). The on-background styles default to [EnktelText]; the
 * rest leave it to the call site.
 */
object EnktelType {

    /** Hero title. One per screen at most — the thing you see from the door. */
    val display: TextStyle
        @Composable get() = TextStyle(
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            letterSpacing = (-0.4).sp,
        )

    /** Screen title, and the programme headline in the player overlay. */
    val headline: TextStyle
        @Composable get() = TextStyle(
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 30.sp,
            letterSpacing = (-0.2).sp,
        )

    /** Rail heading, card group heading, dialog title. */
    val title: TextStyle
        @Composable get() = TextStyle(
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 23.sp,
            letterSpacing = 0.3.sp,
        )

    /** The supporting line under a title. Bold so it holds against artwork. */
    val subtitle: TextStyle
        @Composable get() = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 19.sp,
        )

    /** Running text: descriptions, synopses, explanatory copy in Settings. */
    val body: TextStyle
        @Composable get() = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp,
        )

    /** Buttons, chips, and anything the user is meant to act on. */
    val label: TextStyle
        @Composable get() = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
        )

    /** Metadata beside a title: year, rating, channel number, duration. */
    val caption: TextStyle
        @Composable get() = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp,
        )

    /**
     * The uppercase eyebrow above a group — "FEATURED", "NEXT", "LIVE SCORES".
     *
     * Wide tracking is not decoration at this size: uppercase letterforms set
     * tight are the first thing to turn into a grey smear at distance, and 10 sp
     * is already the floor of what a television resolves.
     */
    val overline: TextStyle
        @Composable get() = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 13.sp,
            letterSpacing = 1.4.sp,
        )
}

/**
 * Text drawn over artwork — a poster caption, the hero title, the player
 * overlay — as opposed to text on one of the palette's own surfaces.
 *
 * This really is pure white, and that is not an oversight. [EnktelText] is
 * deliberately *not* pure white on the OLED palettes, because a `#FFFFFF`
 * glyph on a `#000000` background smears during scroll: switching a pixel up
 * from fully off is the slowest transition an OLED panel makes. That reasoning
 * applies to text sitting on the app's own background and does not apply to
 * text sitting on a photograph, where the pixel underneath is already lit and
 * the panel is nowhere near that transition. Over artwork, maximum contrast
 * against an unpredictable image is what matters, so white wins.
 *
 * The distinction is worth keeping straight because the app got it backwards
 * everywhere: `Color.White` appeared 270 times and [EnktelText] once, so the
 * off-white every palette defines — and documents at length — never rendered.
 */
val EnktelTextOnArt: Color = Color.White

/** Secondary text over artwork: the subtitle line on a poster, hero metadata. */
val EnktelTextOnArtDim: Color = Color.White.copy(alpha = 0.75f)
