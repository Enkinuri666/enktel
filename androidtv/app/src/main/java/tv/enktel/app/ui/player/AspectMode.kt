package tv.enktel.app.ui.player

import androidx.compose.ui.layout.ContentScale

/**
 * What the Aspect button cycles through.
 *
 * `PlayerView` carried this as an `AspectRatioFrameLayout.RESIZE_MODE_*` int,
 * cycled with a `when` that was written out twice in the live player and twice
 * again in the VOD player. With the players on `ContentFrame` the setting is a
 * [ContentScale], so it may as well be one type with the cycle and the label
 * attached — the label being the part that was missing: the old button changed
 * the picture and told you nothing about which of the three modes you had
 * landed in, which matters most for the one that distorts.
 */
enum class AspectMode(val label: String, val scale: ContentScale) {
    /** Letterbox — the whole picture, correct shape. The sane default. */
    FIT("Fit", ContentScale.Fit),

    /**
     * Stretch to the screen, distorting. Kept because some SD channels are
     * transmitted 4:3-in-16:9 and this is the only thing that fixes them.
     */
    FILL("Stretch", ContentScale.FillBounds),

    /** Fill the screen, correct shape, crop the overhang. */
    ZOOM("Zoom", ContentScale.Crop),
    ;

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]
}
