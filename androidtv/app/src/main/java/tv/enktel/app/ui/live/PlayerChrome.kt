package tv.enktel.app.ui.live

import tv.enktel.app.ui.components.frostedGlass
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.epg.NowPlaying
import tv.enktel.app.data.repo.NowNext
import tv.enktel.app.player.StreamStats
import tv.enktel.app.ui.components.cinematicScrim
import tv.enktel.app.ui.components.glassSurface
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelBrandGradient
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextFaint
import tv.enktel.app.ui.theme.EnktelTextOnArt
import tv.enktel.app.ui.theme.EnktelTextOnArtDim
import tv.enktel.app.ui.theme.EnktelType

/**
 * Full-screen player chrome for the TV build.
 *
 * The compact glass card the mobile build uses is right for a phone held at
 * arm's length and wrong for a television across a room: from the sofa the
 * channel name wants to be readable at a glance, and the things people
 * actually check mid-programme — how far through it is, what resolution is
 * really arriving, what is on next — should not need squinting.
 *
 * So the TV overlay is laid out cinematically: a quiet eyebrow line carrying
 * channel identity, the programme title as the headline, a full-width timeline
 * with real clock times under it, and a footer strip holding what's next on the
 * left and the stream's actual resolution and frame rate on the right. The
 * mobile build keeps its compact bar (see InfoBar in LivePlayerScreen).
 */
private fun hhmm(ms: Long): String = TimeFormat.format("HH:mm", ms)

/** "1920x1080 (Full HD)" — the label people recognise, not just a number. */
internal fun resolutionLabel(w: Int, h: Int): String {
    if (w <= 0 || h <= 0) return ""
    val name = when {
        h >= 2000 -> "4K UHD"
        h >= 1400 -> "2K"
        h >= 1000 -> "Full HD"
        h >= 700 -> "HD"
        h >= 500 -> "SD+"
        else -> "SD"
    }
    return "${w}x$h ($name)"
}

@Composable
fun LiveInfoOverlay(
    channel: Channel,
    nowNext: NowNext,
    statsFlow: kotlinx.coroutines.flow.StateFlow<StreamStats>,
    playlistName: String,
    recording: Boolean,
    shiftedFrom: Long,
    sleepUntil: Long,
    modifier: Modifier = Modifier,
) {
    // Collected here, not by the live player. See the note where the flow is
    // passed in: this overlay only exists while the OSD is up, so binding the
    // per-second stats emission to it keeps the rest of playback still.
    val stats by statsFlow.collectAsStateWithLifecycle()
    val now = nowNext.now
    val next = nowNext.next
    Box(modifier.fillMaxSize()) {
        // Clock and date, top right — the reference puts it on a light plate
        // so it stays legible over any picture. Ours is the brand's dark
        // glass for the same reason.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(28.dp)
                .glassSurface(shape = RoundedCornerShape(12.dp), alpha = 0.75f)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                TimeFormat.now("HH:mm"),
                color = EnktelTextOnArt, style = EnktelType.title,
            )
            Text("  |  ", color = EnktelTextDim, style = EnktelType.subtitle)
            Text(
                TimeFormat.now("EEE, d MMM yyyy"),
                color = EnktelTextOnArt.copy(0.92f), style = EnktelType.subtitle,
            )
        }

        // v1.89 floating glass info bar: round channel mark, headline, format
        // chips on the right, a timeline, and the actions the ▼ menu offers.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 24.dp)
                .frostedGlass(corner = 24.dp, alpha = 0.78f)
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF1B4B8F), Color(0xFF0B1C3A)),
                            ),
                        )
                        .border(2.dp, EnktelBlue.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo, contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                        )
                    } else {
                        tv.enktel.app.ui.components.Monogram(channel.name)
                    }
                }
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (shiftedFrom > 0) "" else "LIVE: ") + (now?.title ?: channel.name),
                            color = EnktelTextOnArt, fontSize = 30.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (recording) tv.enktel.app.ui.components.InfoChip("● REC", accent = EnktelLive)
                            if (shiftedFrom > 0) tv.enktel.app.ui.components.InfoChip("⏪ ${hhmm(shiftedFrom)}")
                            else tv.enktel.app.ui.components.InfoChip("LIVE", accent = EnktelLive)
                            val res = if (stats.width > 0 && stats.height > 0) {
                                "${stats.width}x${stats.height}" +
                                    if (stats.frameRate > 0f) " %.0ffps".format(stats.frameRate) else ""
                            } else ""
                            if (res.isNotBlank()) tv.enktel.app.ui.components.InfoChip(res)
                            codecLabel(stats.videoCodec)?.let { tv.enktel.app.ui.components.InfoChip(it) }
                            codecLabel(stats.audioCodec)?.let { tv.enktel.app.ui.components.InfoChip(it) }
                            if (sleepUntil > 0) {
                                val mins = ((sleepUntil - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                                tv.enktel.app.ui.components.InfoChip("☾ ${mins}m")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val ident = listOfNotNull(
                        channel.num.takeIf { it > 0 }?.toString(),
                        channel.name,
                        channel.categoryName.takeIf { it.isNotBlank() },
                        playlistName.takeIf { it.isNotBlank() },
                    ).joinToString("  ·  ")
                    Text(ident, color = EnktelTextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    if (now != null) {
                        val span = (now.endMs - now.startMs).coerceAtLeast(1)
                        val frac = ((System.currentTimeMillis() - now.startMs).toFloat() / span).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth()) { ProgressTrack(frac) }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text("${hhmm(now.startMs)} – ${hhmm(now.endMs)}", color = EnktelTextDim, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            if (next != null) {
                                Text(
                                    "Next ${hhmm(next.startMs)}  ${next.title}",
                                    color = EnktelTextDim, fontSize = 14.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            Text(remainingLabel(now.endMs), color = EnktelBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("No guide listing for this channel", color = EnktelTextFaint, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // The actions themselves live in the ▼ quick menu (the overlay is
            // display-only so it never steals the D-pad from zapping); these
            // pills say what is there, in the redesign's places.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▼ Menu", color = EnktelTextFaint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                if (channel.hasArchive) ActionPill("WATCH FROM START", highlighted = true)
                ActionPill("MULTI-VIEW")
                ActionPill("AUDIO / SUBTITLES")
                ActionPill("EPG")
            }
        }
    }
}

/** "H264"/"HEVC"/"AAC"/"E-AC-3" from a MIME-ish codec string, or null. */
private fun codecLabel(codec: String): String? {
    val c = codec.lowercase()
    return when {
        c.isBlank() -> null
        "hevc" in c || "h265" in c || "hvc" in c -> "HEVC"
        "avc" in c || "h264" in c -> "H.264"
        "av01" in c || "av1" in c -> "AV1"
        "vp9" in c -> "VP9"
        "eac3" in c || "e-ac-3" in c || "ec-3" in c -> "E-AC-3"
        "ac3" in c || "ac-3" in c -> "AC-3"
        "dts" in c -> "DTS"
        "truehd" in c -> "TrueHD"
        "aac" in c || "mp4a" in c -> "AAC"
        "opus" in c -> "Opus"
        "mpeg" in c || "mp2" in c || "mp3" in c -> "MPEG"
        else -> null
    }
}

@Composable
private fun ActionPill(text: String, highlighted: Boolean = false) {
    Text(
        text,
        color = if (highlighted) Color(0xFF0A1317) else Color.White,
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = if (highlighted) 0f else 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** "· " separator between eyebrow fields, at the faint weight. */
@Composable
private fun Dot() {
    Text(
        "  ·  ", color = EnktelTextFaint, style = EnktelType.label, fontWeight = FontWeight.Bold,
    )
}

/**
 * "24 min left" / "Ends in under a minute" / "Just finished".
 *
 * The old string was `"$minsLeft Minutes Left"`, which capitalised mid-phrase
 * like a system dialog and read "1 Minutes Left" and "0 Minutes Left" at the
 * two moments a viewer is most likely to be looking at it.
 */
internal fun remainingLabel(endMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val mins = (endMs - nowMs) / 60_000
    return when {
        mins < 0 -> "Just finished"
        mins < 1 -> "Ends in under a minute"
        // The wording for everything above a minute belongs to NowPlaying, so
        // the overlay and the channel card cannot disagree about the same
        // programme. The two boundaries stay here: they are phrases only the
        // overlay has the width to print.
        else -> NowPlaying.minutesLeft(mins)
    }
}

/** Quiet technical readout — telemetry, deliberately not styled as a label. */
@Composable
private fun Readout(text: String) {
    Text(
        text,
        color = EnktelTextDim, style = EnktelType.caption,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 14.dp),
    )
}

@Composable
private fun ProgressTrack(frac: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(EnktelBrandGradient)),
        )
        // The playhead. A bare filled bar reads as a loading indicator; a head
        // at the boundary reads as "you are here on a timeline", which is what
        // it actually is.
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(6.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    tv.enktel.app.ui.components.GlyphText(
        text,
        color = color, style = EnktelType.overline.copy(letterSpacing = 0.6.sp),
        maxLines = 1,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        color = Color.White.copy(0.92f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.10f))
            .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** One entry in the bottom quick-menu strip. */
data class QuickAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val active: Boolean = false,
)

/**
 * The quick menu as a bottom strip rather than a side drawer.
 *
 * A vertical list pinned to the right edge covered a third of the picture and
 * put fifteen equally-weighted buttons in a column, which on a D-pad means
 * counting presses. A horizontal strip along the bottom leaves the programme
 * visible, matches how every set-top box in the world behaves, and makes the
 * common actions one left/right movement apart.
 */
@Composable
fun QuickMenuBar(actions: List<QuickAction>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .cinematicScrim(maxAlpha = 0.95f)
            .padding(top = 30.dp, bottom = 18.dp),
    ) {
        Text(
            "▲",
            color = EnktelTextOnArt.copy(0.55f), style = EnktelType.label,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        // Something has to be focused the moment the strip appears, or the
        // first D-pad press goes nowhere and the menu reads as unresponsive —
        // which is most of "I can't reach the settings in the player".
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        LazyRow(
            // focusGroup keeps the strip one stop in focus search rather than
            // sixteen, and focusRestorer brings you back to the action you were
            // on instead of the far left. Same fix the Home rails needed.
            modifier = Modifier.focusGroup().focusRestorer(),
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(actions.size) { i ->
                QuickAction(
                    actions[i],
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(a: QuickAction, modifier: Modifier = Modifier) {
    // The strip drew no focus state at all.
    //
    // The intent was documented — "the focused item becomes a filled disc" —
    // but the code that implemented it read `a.active`, which is whether the
    // *feature* is on (subtitles enabled, recording running), not where the
    // D-pad is. Both container colours were Transparent, there was no border
    // and no focusedScale, so nothing on screen changed as focus moved. On a
    // strip of sixteen near-identical glyph discs that makes the menu look
    // frozen: you press right, nothing moves, you press right again, and now
    // you are two actions past the one you wanted.
    //
    // Focus and active are genuinely different things and both need saying, so
    // they are drawn differently: focus is the brand-gradient fill plus a lift,
    // active is a small dot under the label. A recording that is running still
    // reads as running when the D-pad is elsewhere.
    var focused by remember { mutableStateOf(false) }
    val discScale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "quickActionDisc",
    )
    Surface(
        onClick = a.onClick,
        modifier = modifier
            .tapClick(a.onClick)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
    ) {
        Column(
            Modifier.width(94.dp).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .scale(discScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            focused -> Brush.linearGradient(listOf(EnktelBlue, EnktelBlue))
                            a.active -> Brush.linearGradient(
                                listOf(EnktelBlue.copy(0.30f), EnktelPurple.copy(0.30f)),
                            )
                            else -> Brush.linearGradient(
                                listOf(Color.White.copy(0.08f), Color.White.copy(0.08f)),
                            )
                        },
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = when {
                            focused -> Color.White
                            a.active -> EnktelBlue.copy(0.55f)
                            else -> Color.White.copy(0.10f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Drawn, and tinted with the state. These were characters —
                // 🗓 🔴 🎞 💬 📊 — so on one box the strip was a row of thin
                // outlines and on the next a row of full-colour stickers, and
                // on neither did the picture change when its disc lit up.
                androidx.tv.material3.Icon(
                    a.icon,
                    contentDescription = null,
                    tint = if (focused) tv.enktel.app.ui.theme.inkOn(EnktelBlue) else EnktelTextOnArt,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                a.label,
                color = when {
                    focused -> EnktelTextOnArt
                    a.active -> EnktelBlue
                    else -> EnktelTextOnArtDim
                },
                style = EnktelType.caption,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // "This one is currently on" — kept separate from the focus
            // treatment so the two never have to compete for the same pixels.
            if (a.active) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(EnktelBlue),
                )
            }
        }
    }
}
