package tv.enktel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

/** Icon and label laid out the way every button lays them out. */
@Composable
internal fun ControlContent(icon: ImageVector?, label: String, iconDescription: String?) {
    val iconOnly = label.isEmpty()
    Row(
        Modifier
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
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = EnktelType.headline,
                color = EnktelText,
                maxLines = 1,
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
            Spacer(Modifier.width(16.dp))
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
