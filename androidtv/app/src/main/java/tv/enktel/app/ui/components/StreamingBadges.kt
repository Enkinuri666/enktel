package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** A streaming platform / broadcast network that we recognise by name. */
data class StreamingPlatform(
    val label: String,
    val bg: Color,
    val fg: Color = Color.White,
)

/**
 * Recognise a streaming-service or premium-network brand inside a category
 * name so we can render a coloured badge next to it. Names come from IPTV
 * panels which are messy and unregulated, so match on a broad substring
 * (case-insensitive). Priority is longest-match-wins — a category called
 * "Disney+ Hotstar" hits Disney+ before Disney.
 */
object StreamingBadges {
    private val PLATFORMS = listOf(
        // major SVOD
        "netflix"       to StreamingPlatform("NETFLIX",  Color(0xFFE50914)),
        "hulu"          to StreamingPlatform("HULU",     Color(0xFF1CE783), Color.Black),
        "disney+"       to StreamingPlatform("DISNEY+",  Color(0xFF0063E5)),
        "disney"        to StreamingPlatform("DISNEY+",  Color(0xFF0063E5)),
        "prime video"   to StreamingPlatform("PRIME",    Color(0xFF00A8E1)),
        "amazon prime"  to StreamingPlatform("PRIME",    Color(0xFF00A8E1)),
        "amazon"        to StreamingPlatform("PRIME",    Color(0xFF00A8E1)),
        "paramount+"    to StreamingPlatform("PARAMOUNT+", Color(0xFF0064FF)),
        "paramount"     to StreamingPlatform("PARAMOUNT+", Color(0xFF0064FF)),
        "hbo max"       to StreamingPlatform("MAX",       Color(0xFF002BE7)),
        "hbo"           to StreamingPlatform("HBO",       Color(0xFF7B00FF)),
        "max "          to StreamingPlatform("MAX",       Color(0xFF002BE7)),
        "apple tv"      to StreamingPlatform("APPLE TV+", Color(0xFF000000), Color.White),
        "peacock"       to StreamingPlatform("PEACOCK",   Color(0xFFF9CE28), Color.Black),
        "discovery+"    to StreamingPlatform("DISCOVERY+", Color(0xFF001EFF)),
        "starz"         to StreamingPlatform("STARZ",     Color(0xFF000000), Color.White),
        "showtime"      to StreamingPlatform("SHOWTIME",  Color(0xFFE20E0E)),
        "crunchyroll"   to StreamingPlatform("CRUNCHY",   Color(0xFFF47521)),
        "youtube tv"    to StreamingPlatform("YOUTUBE TV", Color(0xFFFF0000)),

        // international
        "viaplay"       to StreamingPlatform("VIAPLAY",   Color(0xFFFF3C00)),
        "canal+"        to StreamingPlatform("CANAL+",    Color(0xFF000000), Color.White),
        "canal plus"    to StreamingPlatform("CANAL+",    Color(0xFF000000), Color.White),
        "movistar"      to StreamingPlatform("MOVISTAR+", Color(0xFF0091D5)),
        "dstv"          to StreamingPlatform("DSTV",      Color(0xFF00A0DC)),
        "sky "          to StreamingPlatform("SKY",       Color(0xFF01267F)),
        "showmax"       to StreamingPlatform("SHOWMAX",   Color(0xFFEB008B)),

        // networks / cable
        "bet"           to StreamingPlatform("BET",       Color(0xFF000000), Color.White),
        "mtv"           to StreamingPlatform("MTV",       Color(0xFFFCED00), Color.Black),
        "vh1"           to StreamingPlatform("VH1",       Color(0xFFEE1D2C)),
        "cnn"           to StreamingPlatform("CNN",       Color(0xFFCC0000)),
        "fox news"      to StreamingPlatform("FOX NEWS",  Color(0xFF003366)),
        "fox sports"    to StreamingPlatform("FOX SPORTS", Color(0xFFEA1D25)),
        "espn"          to StreamingPlatform("ESPN",      Color(0xFFED1C24)),
        "bbc"           to StreamingPlatform("BBC",       Color(0xFF000000), Color.White),
        "itv"           to StreamingPlatform("ITV",       Color(0xFFFFCE00), Color.Black),
        "channel 4"     to StreamingPlatform("CHANNEL 4", Color(0xFFAA00AA)),
        "channel 5"     to StreamingPlatform("CHANNEL 5", Color(0xFF008FDA)),
        "nbc"           to StreamingPlatform("NBC",       Color(0xFF6455A7)),
        "cbs"           to StreamingPlatform("CBS",       Color(0xFF033B71)),
        "abc"           to StreamingPlatform("ABC",       Color(0xFF000000), Color.White),
        "sky sports"    to StreamingPlatform("SKY SPORT", Color(0xFF01267F)),
        "bt sport"      to StreamingPlatform("BT SPORT",  Color(0xFF1AC7E5)),
        "tnt sport"     to StreamingPlatform("TNT SPORT", Color(0xFFFFCC00), Color.Black),
        "dazn"          to StreamingPlatform("DAZN",      Color(0xFFF8F800), Color.Black),
        "bein"          to StreamingPlatform("beIN",      Color(0xFF670E36)),
    )

    /** Return the platform whose keyword appears in [text] (case-insensitive). */
    fun detect(text: String): StreamingPlatform? {
        val lower = text.lowercase()
        // Longest-match-first so "disney+" wins over "disney".
        return PLATFORMS.sortedByDescending { it.first.length }
            .firstOrNull { lower.contains(it.first) }?.second
    }
}

/** Pill badge rendering the given platform. */
@Composable
fun PlatformBadge(platform: StreamingPlatform, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(platform.bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            platform.label, color = platform.fg, fontSize = 8.sp,
            fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
        )
    }
}

/** Convenience: renders a badge if [text] contains a recognised platform; nothing otherwise. */
@Composable
fun PlatformBadgeFor(text: String, modifier: Modifier = Modifier) {
    StreamingBadges.detect(text)?.let { PlatformBadge(it, modifier) }
}
