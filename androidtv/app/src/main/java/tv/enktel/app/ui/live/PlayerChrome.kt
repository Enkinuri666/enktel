package tv.enktel.app.ui.live

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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.repo.NowNext
import tv.enktel.app.player.StreamStats
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextFaint

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
    stats: StreamStats,
    playlistName: String,
    recording: Boolean,
    shiftedFrom: Long,
    sleepUntil: Long,
    modifier: Modifier = Modifier,
) {
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
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xC012141D))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                TimeFormat.now("HH:mm"),
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black,
            )
            Text("  |  ", color = EnktelTextDim, fontSize = 15.sp)
            Text(
                TimeFormat.now("EEE, d MMM yyyy"),
                color = Color.White.copy(0.92f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xB3000000), Color(0xF0000000)),
                    ),
                )
                .padding(start = 44.dp, end = 44.dp, top = 60.dp, bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                // Channel mark, bottom-left, at a size that reads from a sofa.
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EnktelSurface)
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo, contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                        )
                    } else {
                        Text(
                            channel.name.take(3).uppercase(), color = Color.White,
                            fontWeight = FontWeight.Black, fontSize = 20.sp,
                        )
                    }
                }
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    // Channel identity is the *supporting* line, not the
                    // headline.
                    //
                    // It used to be 30 sp Black — larger and heavier than the
                    // programme title underneath it — so the biggest thing on
                    // screen was the name of the channel you had just chosen
                    // and already knew, while the thing you actually wanted to
                    // read sat below it in a smaller, lighter weight. There is
                    // an 84 dp logo immediately to the left saying the same
                    // thing. One eyebrow row carries number, name, group and
                    // status; the programme gets the headline.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.num > 0) {
                            Text(
                                "${channel.num}",
                                color = EnktelBlue, fontSize = 15.sp, fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                            )
                            Dot()
                        }
                        Text(
                            channel.name.uppercase(),
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Playlist and group belong with the channel's identity,
                        // not in the chip row beside the resolution readout —
                        // mixing editorial with telemetry at one weight is what
                        // made that row read as chip soup.
                        val provenance = listOfNotNull(
                            playlistName.takeIf { it.isNotBlank() },
                            channel.categoryName.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (provenance.isNotBlank()) {
                            Dot()
                            Text(
                                provenance, color = EnktelTextFaint, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (recording) StatusPill("● REC", EnktelLive)
                        if (shiftedFrom > 0) StatusPill("⏪ ${hhmm(shiftedFrom)}", EnktelLive)
                        if (sleepUntil > 0) {
                            val mins = ((sleepUntil - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                            StatusPill("☾ ${mins}m", EnktelPurple)
                        }
                        if (channel.hasArchive) StatusPill("CATCH-UP", EnktelOk)
                    }
                    if (now != null) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            now.title, color = Color.White, fontSize = 32.sp,
                            fontWeight = FontWeight.Black, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, letterSpacing = (-0.4).sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        val span = (now.endMs - now.startMs).coerceAtLeast(1)
                        val frac = ((System.currentTimeMillis() - now.startMs).toFloat() / span)
                            .coerceIn(0f, 1f)
                        // The programme's actual clock times, which the overlay
                        // never showed — it offered a progress bar and a
                        // countdown and left "what time is this on?" unanswered.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${hhmm(now.startMs)} – ${hhmm(now.endMs)}",
                                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(14.dp))
                            // A full-width timeline rather than a fixed 300 dp
                            // stub floating in the middle of a 960 dp row.
                            Box(Modifier.weight(1f)) { ProgressTrack(frac) }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                remainingLabel(now.endMs),
                                color = EnktelBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        if (now.desc.isNotBlank()) {
                            Spacer(Modifier.height(11.dp))
                            Text(
                                now.desc, color = EnktelTextDim, fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            channel.name,
                            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No guide listing for this channel",
                            color = EnktelTextFaint, fontSize = 14.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (next != null) {
                    Text(
                        "NEXT",
                        color = EnktelTextFaint, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        hhmm(next.startMs),
                        color = EnktelTextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        next.title, color = Color.White.copy(0.9f), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Stream telemetry lives here, away from the programme.
                //
                // Resolution and frame rate were chips in the middle of the
                // programme row, styled identically to the playlist name beside
                // them — five interchangeable grey pills, which is what made the
                // overlay look cheap. They are readouts, not labels: quiet,
                // monospaced-feeling, and grouped at the far end where a viewer
                // checking "am I really getting HD?" will look for them.
                resolutionLabel(stats.width, stats.height).takeIf { it.isNotBlank() }?.let {
                    Readout(it)
                }
                if (stats.frameRate > 0f) Readout("%.0f fps".format(stats.frameRate))
                Spacer(Modifier.width(14.dp))
                Text(
                    "▼  Menu",
                    color = EnktelTextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** "· " separator between eyebrow fields, at the faint weight. */
@Composable
private fun Dot() {
    Text(
        "  ·  ", color = EnktelTextFaint, fontSize = 13.sp, fontWeight = FontWeight.Bold,
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
        mins == 1L -> "1 min left"
        mins < 60 -> "$mins min left"
        else -> {
            val h = mins / 60
            val m = mins % 60
            if (m == 0L) "${h}h left" else "${h}h ${m}m left"
        }
    }
}

/** Quiet technical readout — telemetry, deliberately not styled as a label. */
@Composable
private fun Readout(text: String) {
    Text(
        text,
        color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 14.dp),
    )
}

@Composable
private fun ProgressTrack(frac: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(EnktelBlue, EnktelPurple))),
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp,
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(0.10f))
            .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** One entry in the bottom quick-menu strip. */
data class QuickAction(
    val glyph: String,
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
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))),
            )
            .padding(top = 30.dp, bottom = 18.dp),
    ) {
        Text(
            "▲",
            color = Color.White.copy(0.55f), fontSize = 13.sp,
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
                            focused -> Brush.linearGradient(listOf(EnktelBlue, EnktelPurple))
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
                Text(a.glyph, fontSize = 19.sp, color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                a.label,
                color = when {
                    focused -> Color.White
                    a.active -> EnktelBlue
                    else -> Color.White.copy(0.82f)
                },
                fontSize = 11.sp,
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
