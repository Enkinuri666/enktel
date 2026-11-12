package tv.enktel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOnLive
import tv.enktel.app.ui.theme.EnktelOnPrimary
import tv.enktel.app.ui.theme.EnktelRadius
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelText
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelType

/**
 * How much a button is asking for.
 *
 * Before this there was one button and one flag. `accent = true` meant "the
 * thing you came to this screen to do" on a details page and "this setting is
 * switched on" in Settings, and it drew both the same way — a quarter-strength
 * blue wash. That was too loud for a page of toggles and far too quiet for a
 * call to action: on a phone, where nothing is ever focused, **Sign in** and
 * **Play** were the dimmest buttons on their own screens.
 *
 * Five kinds, because those are the five things a button in this app says.
 */
enum class ButtonKind {
    /** An ordinary action. Most buttons. */
    Neutral,

    /** A setting that is on, or the chosen one of several. What `accent = true` draws. */
    Tonal,

    /** The one thing this screen is for — Play, Sign in, Save. At most one per group. */
    Primary,

    /** Something you are allowed to ignore: Cancel, Not now, Skip. */
    Ghost,

    /** It deletes or stops something. */
    Danger,
}

/** The height every tappable control shares, so a row of mixed ones sits on one line. */
val ControlHeight: Dp = 40.dp

/** How far a control grows under the D-pad. Small: neighbours must not be overlapped. */
internal const val CONTROL_FOCUS_SCALE = 1.04f

/**
 * The surface under every button.
 *
 * One place owns the colours of each [ButtonKind] at rest and focused, the
 * focus scale and the shape, so [FocusButton], [FocusIconButton] and anything
 * built later cannot drift apart the way the hand-rolled `Surface(...)` blocks
 * around the app did.
 *
 * Two things here are corrections rather than restyling:
 *
 *  - **The focused label is drawn in [EnktelOnPrimary], not white.** White on
 *    the accent fails contrast in every palette; see `EnktelPalette.onPrimary`.
 *  - **The focus scale is stated.** Leaving it out takes tv-material's default
 *    of 1.1×, which on a row of buttons 10 dp apart makes the focused one
 *    overlap both neighbours.
 */
@Composable
internal fun ControlSurface(
    onClick: () -> Unit,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
    shape: Shape = EnktelRadius.shapeMd,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = EnktelBlue
    val onAccent = EnktelOnPrimary
    val text = EnktelText

    val container: Color
    val ink: Color
    val focusedContainer: Color
    val focusedInk: Color
    val outline: Color?
    when (kind) {
        ButtonKind.Neutral -> {
            container = EnktelSurfaceHigh; ink = text
            focusedContainer = accent; focusedInk = onAccent; outline = null
        }
        ButtonKind.Tonal -> {
            container = accent.copy(alpha = 0.18f); ink = text
            focusedContainer = accent; focusedInk = onAccent
            outline = accent.copy(alpha = 0.55f)
        }
        ButtonKind.Primary -> {
            container = accent; ink = onAccent
            focusedContainer = accent; focusedInk = onAccent; outline = null
        }
        ButtonKind.Ghost -> {
            container = Color.Transparent; ink = EnktelTextDim
            focusedContainer = accent; focusedInk = onAccent; outline = null
        }
        ButtonKind.Danger -> {
            container = EnktelLive.copy(alpha = 0.14f); ink = text
            focusedContainer = EnktelLive; focusedInk = EnktelOnLive
            outline = EnktelLive.copy(alpha = 0.5f)
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier.tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = ink,
            focusedContainerColor = focusedContainer,
            focusedContentColor = focusedInk,
            pressedContainerColor = focusedContainer,
            pressedContentColor = focusedInk,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CONTROL_FOCUS_SCALE, pressedScale = 0.98f),
        border = ClickableSurfaceDefaults.border(
            border = if (outline != null) Border(BorderStroke(1.dp, outline), shape = shape) else Border.None,
            // A Primary button is already the accent at rest, so filling it
            // on focus changes nothing. The ring is what says "you are here".
            focusedBorder = if (kind == ButtonKind.Primary) {
                Border(BorderStroke(2.dp, text), shape = shape)
            } else {
                Border.None
            },
        ),
        content = content,
    )
}

/**
 * Icon and label laid out the way every button lays them out.
 *
 * [centred] matters only when the button has been stretched. A stretched
 * Primary button is a call to action and reads from its middle; every other
 * stretched button in the app is a row in a menu — the player's options, a
 * context menu — and a menu reads down its left edge.
 */
@Composable
internal fun BoxScope.ControlContent(
    icon: ImageVector?,
    label: String,
    iconDescription: String?,
    centred: Boolean = false,
) {
    val iconOnly = label.isEmpty()
    Row(
        Modifier
            .align(if (centred) Alignment.Center else Alignment.CenterStart)
            .height(ControlHeight)
            .padding(
                start = when {
                    iconOnly -> 11.dp
                    icon != null -> 14.dp
                    else -> 18.dp
                },
                end = if (iconOnly) 11.dp else 18.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = iconDescription, modifier = Modifier.size(18.dp))
        }
        if (!iconOnly) {
            Text(label, style = EnktelType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The top of a screen: what it is, one line about it, and anything that
 * belongs beside the title.
 *
 * Every screen had written its own. Settings, Live TV and Search set a 26 sp
 * title 58 dp in; More set one 48 dp in with a subtitle; Movies put it in a
 * sidebar; Downloads ran a size figure into it with a middle dot; the Sports
 * Hub had none. Four title sizes and three left edges, on screens that are one
 * D-pad press apart — so the title jumped sideways every time you changed
 * destination.
 *
 * This takes no gutter of its own. The caller's page padding positions it, so
 * the title always shares a left edge with the content under it.
 *
 * ### When the title and its controls do not both fit
 *
 * A flow, not a row. In a row the title took what the controls left, and on a
 * phone "Connection Diagnostics" beside a "Run diagnostics" button left
 * neither of them room: the title ran under the button and the button's label
 * was cut to "Run di…". Here the controls sit at the far end of the title's
 * line when there is room for both, and drop to a line of their own when
 * there is not — the same header, laid out by the space it actually has
 * rather than by which build it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                title,
                style = EnktelType.headline,
                color = EnktelText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = EnktelType.body,
                    color = EnktelTextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = trailing,
            )
        }
    }
}

/**
 * The heading over a group of things on a page — a rail, a list section, a
 * block of settings.
 *
 * There were at least five: a sentence-case title behind a coloured bar
 * (Search, Home), an uppercase accent-coloured one with an icon (Sports Hub),
 * a bold white word (Settings ▸ Language), an uppercase dim one (Settings ▸
 * PLAYLISTS, Movies ▸ GENRE), and an accent-coloured "In progress · 3"
 * (Downloads). Each is a fine heading. Five of them is no system at all, and
 * the eye reads the differences as meaning something when they mean nothing.
 *
 * One form: a short accent bar, the title, an optional count, an optional
 * note. [accent] exists for the few places colour carries meaning — the live
 * red over what is on air — and should be left alone everywhere else.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    note: String = "",
    accent: Color = EnktelBlue,
    icon: ImageVector? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(EnktelRadius.shapeXs)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            Glyphs.strip(title),
            style = EnktelType.title,
            color = EnktelText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text("$count", style = EnktelType.subtitle, color = EnktelTextDim)
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(
                note,
                style = EnktelType.caption,
                color = EnktelTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * How a row or a card shows that the D-pad is on it: it lifts a shade and
 * takes a ring. It does not fill.
 *
 * ## Two focus treatments, on purpose
 *
 * A **control** — a button, a chip, a menu entry — fills with the accent when
 * focused. It holds one label, the label flips to the ink that reads on the
 * accent, and nothing else is in it to go wrong.
 *
 * A **row or card** holds more than a label: a title, a dimmer line under it,
 * a time, a badge, sometimes artwork. Those were filling with the accent too,
 * and every one of them then had the same fault — the title went white on
 * blue, which is the contrast failure described on `EnktelPalette.onPrimary`,
 * and the secondary line stayed whatever fixed grey it had been given, which
 * on a saturated fill is not readable at all. An episode row under focus was
 * a bright slab with half its text missing.
 *
 * Posters never had the problem, because a poster cannot be filled: it takes
 * a ring. This is the same answer for everything else that carries content.
 * The contents keep their own colours and stay exactly as legible focused as
 * not, and focus is the ring — which is also what makes a list of rows and a
 * rail of posters read as one system instead of two.
 */
object RowFocus {

    /** Container and content colours. [container] is the row at rest. */
    @Composable
    fun colors(
        container: Color = EnktelSurfaceHigh.copy(alpha = 0.5f),
        content: Color = EnktelText,
    ) = ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = content,
        focusedContainerColor = EnktelSurfaceHigh,
        focusedContentColor = EnktelText,
        pressedContainerColor = EnktelSurfaceHigh,
        pressedContentColor = EnktelText,
        // A row that cannot be chosen still has to be readable.
        disabledContainerColor = container,
        disabledContentColor = content.copy(alpha = 0.5f),
    )

    /**
     * The ring. One dp inside the poster ring's width, because a row is
     * already lifted against its neighbours and a poster is not — but it
     * follows the palette, so High Contrast still gets its wide one.
     */
    @Composable
    fun border(shape: Shape, rest: BorderStroke? = null) = ClickableSurfaceDefaults.border(
        border = rest?.let { Border(it, shape = shape) } ?: Border.None,
        focusedBorder = Border(BorderStroke(ringWidth(), EnktelBlue), shape = shape),
        pressedBorder = Border(BorderStroke(ringWidth(), EnktelBlue), shape = shape),
    )

    /** Barely grows. A row in a list has a row above and below it. */
    @Composable
    fun scale() = ClickableSurfaceDefaults.scale(focusedScale = 1.015f)

    @Composable
    private fun ringWidth(): Dp =
        (tv.enktel.app.ui.theme.EnktelFocusRingWidth - 1.dp).coerceAtLeast(2.dp)
}

/**
 * A pictograph that stands alone, drawn.
 *
 * For the places a single character was the whole control — the mini
 * player's ✕ and ⛶, the skip button's ⏭ — where there is no label to hang an
 * icon on. Falls back to the character itself for anything [Glyphs] does not
 * know, so nothing can go blank.
 */
@Composable
fun GlyphIcon(
    glyph: String,
    modifier: Modifier = Modifier,
    tint: Color = androidx.tv.material3.LocalContentColor.current,
    size: Dp = 18.dp,
) {
    val mark = androidx.compose.runtime.remember(glyph) { Glyphs.split(glyph).mark }
    if (mark != null) {
        Icon(mark.icon, contentDescription = mark.name, tint = tint, modifier = modifier.size(size))
    } else {
        Text(glyph, color = tint, modifier = modifier)
    }
}

/**
 * A line of text that may lead with a pictograph, with the pictograph drawn.
 *
 * The same job [FocusButton] does for its own label, for text that is not in
 * a button: a status line, a heading, a hint. The icon is sized from the text
 * it sits beside and takes the same colour, so it reads as part of the line
 * rather than as something placed next to it.
 */
@Composable
fun GlyphText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.tv.material3.LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
) {
    val parts = androidx.compose.runtime.remember(text) { Glyphs.split(text) }
    val ink = if (color != Color.Unspecified) color else androidx.tv.material3.LocalContentColor.current
    val mark = parts.mark
    if (mark == null) {
        Text(text, modifier = modifier, color = ink, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val iconSize = with(androidx.compose.ui.platform.LocalDensity.current) {
        val sp = if (style.fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) style.fontSize else EnktelType.body.fontSize
        (sp * 1.2f).toDp()
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(mark.icon, contentDescription = if (parts.text.isEmpty()) mark.name else null, tint = ink, modifier = Modifier.size(iconSize))
        if (parts.text.isNotEmpty()) {
            Spacer(Modifier.width(iconSize * 0.35f))
            Text(parts.text, color = ink, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}
