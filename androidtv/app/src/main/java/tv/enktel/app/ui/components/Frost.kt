package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/*
 * v1.89 "Frost" building blocks — the pieces the redesign repeats on every
 * screen: frosted glass panels, the cyan focus glow, the gold 4K tile and the
 * outlined format chips, and the live-channel card with its red progress bar.
 */

private val FrostCorner = 14.dp
private val Gold = Color(0xFFE9C46A)

/** A frosted glass slab: translucent surface fill and a hairline top-lit edge. */
fun Modifier.frostedGlass(corner: Dp = FrostCorner, alpha: Float = 0.72f): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(
        Brush.verticalGradient(
            listOf(Color(0xFF1C2D36).copy(alpha = alpha), Color(0xFF14222A).copy(alpha = alpha + 0.08f)),
        ),
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))),
        RoundedCornerShape(corner),
    )

/**
 * Format facts that can be read honestly off a title: "4K"/"UHD"/"2160",
 * "HDR10+"/"HDR"/"DV", "Atmos". Catalogue names and categories are where IPTV
 * panels put this, so that is what is sniffed — nothing is claimed that the
 * listing itself does not say.
 */
object FormatTags {
    data class Tags(val uhd: Boolean, val hdr: String?, val atmos: Boolean) {
        val isEmpty: Boolean get() = !uhd && hdr == null && !atmos
    }

    fun of(vararg text: String): Tags {
        val t = text.joinToString(" ").uppercase()
        val uhd = Regex("""\b(4K|UHD|2160P?)\b""").containsMatchIn(t)
        val hdr = when {
            t.contains("HDR10+") || t.contains("HDR10PLUS") -> "HDR10+"
            Regex("""\b(DV|DOLBY ?VISION)\b""").containsMatchIn(t) -> "Dolby Vision"
            Regex("""\bHDR(10)?\b""").containsMatchIn(t) -> "HDR10"
            else -> null
        }
        val atmos = t.contains("ATMOS")
        return Tags(uhd, hdr, atmos)
    }
}

/** The gold "4K" tile joined to an HDR label, and a separate Atmos chip. */
@Composable
fun QualityBadges(tags: FormatTags.Tags, modifier: Modifier = Modifier) {
    if (tags.isEmpty) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tags.uhd || tags.hdr != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Gold.copy(alpha = 0.85f), RoundedCornerShape(6.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tags.uhd) {
                    Text(
                        "4K",
                        color = Color(0xFF1A1405), fontWeight = FontWeight.Black, fontSize = 13.sp,
                        modifier = Modifier.background(Gold).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                tags.hdr?.let {
                    Text(
                        it, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (tags.atmos) {
            Text(
                "Dolby Atmos", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Small rounded info chip — "3840x2160 60fps", "HEVC", "5.1". */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Text(
        text,
        color = if (accent != null) Color.White else Color.White.copy(alpha = 0.9f),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent ?: Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Red dot + "LIVE". */
@Composable
fun LiveDotLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(EnktelLive))
        Spacer(Modifier.width(6.dp))
        Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * A focusable glass card with the Frost focus treatment: cyan border and a
 * soft cyan glow, a small lift. Content is drawn inside a 16:9-ish frame.
 */
@Composable
fun FrostCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 236.dp,
    height: Dp = 132.dp,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(FrostCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface,
            focusedContainerColor = EnktelSurfaceHigh,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, EnktelBlue)),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = EnktelBlue.copy(alpha = 0.55f), elevation = 18.dp),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier
            .width(width)
            .height(height)
            .onFocusChanged { focused = it.isFocused },
    ) {
        content(focused)
    }
}

/**
 * Live-channel card for "Live Now": the logo on a dark wash, a LIVE marker and
 * a red bar for how far through the current programme the channel is.
 */
@Composable
fun LiveNowCard(
    name: String,
    logo: String,
    nowTitle: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostCard(onClick = onClick, modifier = modifier) { _ ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF1F3038), Color(0xFF0E181D))),
            ),
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = logo, contentDescription = name, contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.Center).padding(top = 14.dp, bottom = 38.dp)
                        .fillMaxWidth(0.6f).fillMaxSize(),
                )
            } else {
                Text(
                    name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp, vertical = 30.dp),
                )
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDotLabel()
                    if (!nowTitle.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            nowTitle, color = EnktelTextDim, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth((progress ?: 0f).coerceIn(0f, 1f)).height(3.dp)
                            .background(EnktelLive),
                    )
                }
            }
        }
    }
}

/** Thin accent progress bar used under continue-watching and download cards. */
@Composable
fun FrostProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = EnktelBlue) {
    Box(
        modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(color))
    }
}

/** Section heading in the redesign's weight and size. */
@Composable
fun FrostSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}
