package tv.enktel.app.ui.live

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
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * Full-screen player chrome for the TV build.
 *
 * The compact glass card the mobile build uses is right for a phone held at
 * arm's length and wrong for a television across a room: from the sofa the
 * channel name wants to be readable at a glance, and the things people
 * actually check mid-programme — how far through it is, what resolution is
 * really arriving, what is on next — should not need squinting.
 *
 * So the TV overlay is laid out cinematically: identity and programme bottom
 * left over a scrim, the stream's real properties as chips beside it, and a
 * footer strip carrying what's next and how to reach the menu. The mobile
 * build keeps its compact bar (see InfoBar in LivePlayerScreen).
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.num > 0) {
                            Text(
                                "${channel.num}",
                                color = EnktelBlue, fontSize = 30.sp, fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            channel.name, color = Color.White, fontSize = 30.sp,
                            fontWeight = FontWeight.Black, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            now.title, color = Color.White.copy(0.95f), fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        val span = (now.endMs - now.startMs).coerceAtLeast(1)
                        val frac = ((System.currentTimeMillis() - now.startMs).toFloat() / span)
                            .coerceIn(0f, 1f)
                        val minsLeft = ((now.endMs - System.currentTimeMillis()) / 60_000)
                            .coerceAtLeast(0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(300.dp)) { ProgressTrack(frac) }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "$minsLeft Minutes Left",
                                color = EnktelTextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(18.dp))
                            // What the stream really is, rather than what the
                            // channel name claims. A line advertised as HD that
                            // arrives at 720p is the single most common
                            // complaint, and this is where it becomes visible.
                            resolutionLabel(stats.width, stats.height).takeIf { it.isNotBlank() }
                                ?.let { InfoChip(it) }
                            if (stats.frameRate > 0f) {
                                Spacer(Modifier.width(8.dp))
                                InfoChip("%.0f FPS".format(stats.frameRate))
                            }
                            if (playlistName.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                InfoChip(
                                    playlistName +
                                        channel.categoryName.takeIf { it.isNotBlank() }
                                            ?.let { ", Group: $it" }.orEmpty(),
                                )
                            }
                        }
                        if (now.desc.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                now.desc, color = Color.White.copy(0.72f), fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No guide data for this channel",
                            color = EnktelTextDim, fontSize = 14.sp,
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
                        "Next at ${hhmm(next.startMs)}:  ",
                        color = EnktelTextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        next.title, color = Color.White.copy(0.9f), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Press ▼ for the quick menu",
                    color = EnktelTextDim.copy(0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ProgressTrack(frac: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(EnktelBlue, EnktelPurple))),
        )
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
    Surface(
        onClick = a.onClick,
        modifier = modifier.tapClick(a.onClick),
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
            // The focused item becomes a filled disc, which is what makes the
            // current position findable on a strip of near-identical glyphs.
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (a.active) Brush.linearGradient(listOf(EnktelBlue, EnktelPurple))
                        else Brush.linearGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.08f))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(a.glyph, fontSize = 19.sp, color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                a.label,
                color = if (a.active) EnktelBlue else Color.White.copy(0.82f),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
