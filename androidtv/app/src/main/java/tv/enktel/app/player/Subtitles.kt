package tv.enktel.app.player

import android.graphics.Color as AGColor
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/**
 * Applies the user-picked caption look to a Media3 [SubtitleView]. Values map directly to the
 * settings written by SettingsScreen so preferences round-trip through the store.
 */
@androidx.media3.common.util.UnstableApi
object Subtitles {
    fun apply(
        view: SubtitleView,
        scalePct: Int,
        color: String,
        edge: String,
        bgAlpha: Int,
    ) {
        val fg = when (color) {
            "yellow" -> AGColor.YELLOW
            "cyan" -> AGColor.CYAN
            "green" -> AGColor.GREEN
            else -> AGColor.WHITE
        }
        val edgeType = when (edge) {
            "shadow" -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            "depressed" -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
            "raised" -> CaptionStyleCompat.EDGE_TYPE_RAISED
            "none" -> CaptionStyleCompat.EDGE_TYPE_NONE
            else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        }
        val bg = AGColor.argb(bgAlpha.coerceIn(0, 255), 0, 0, 0)
        view.setStyle(CaptionStyleCompat(fg, bg, AGColor.TRANSPARENT, edgeType, AGColor.BLACK, null))
        view.setFractionalTextSize(0.0533f * (scalePct.coerceIn(50, 300) / 100f))
        view.setApplyEmbeddedFontSizes(false)
    }
}
