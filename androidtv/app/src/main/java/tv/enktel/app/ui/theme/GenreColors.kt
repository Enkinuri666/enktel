package tv.enktel.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The eight genre tints used to colour programme blocks in the EPG grid.
 *
 * ## Why these are not theme colours
 *
 * Every other colour in the app comes from [EnktelPalette] and changes with the
 * viewer's theme. These deliberately do not. A categorical scale answers "which
 * of these eight things is this", and the answer has to stay the same when the
 * viewer switches theme, or the guide has to be re-learned each time. Sport is
 * green in every theme, the way a motorway is blue on every map.
 *
 * They live here rather than in the grid that draws them so there is one place
 * to look, and so [GenreColorsTest] can hold the two properties below.
 *
 * ## The two rules
 *
 * **Separation.** The scale is laid out on eight slots 45° apart around the hue
 * circle. That number is not decorative: it is the constraint the previous scale
 * failed. Those colours were picked one genre at a time and ended up with sport
 * at 162° and documentaries at 154° — eight degrees apart, which is to say the
 * same colour. Kids and comedy were 11° apart, news and drama 27°. Three of the
 * twenty-eight pairs were indistinguishable in a grid whose whole job is to be
 * scanned at a glance from across a room.
 *
 * **Legibility.** Each tint clears 5:1 against the guide's background. Hue alone
 * does not guarantee this — blue at 235° is intrinsically dark, and at the
 * saturation the rest of the scale uses it landed at 3.96:1. Its saturation is
 * dropped to 0.60 to lift it over the line rather than moving its hue, because
 * moving it would have collided with news.
 *
 * ## Assignment
 *
 * The genre-to-hue mapping keeps the associations people already hold — warm for
 * comedy and children's, green for sport and nature, cyan for news, violet for
 * film, pink for music. Within that, each one is snapped to its slot.
 *
 * | genre | hue | tint |
 * | :--- | ---: | :--- |
 * | [comedy] | 10° | warm red-orange |
 * | [kids] | 55° | yellow |
 * | [documentary] | 100° | leaf green |
 * | [sport] | 145° | emerald |
 * | [news] | 190° | cyan |
 * | [drama] | 235° | blue |
 * | [movie] | 280° | violet |
 * | [music] | 325° | magenta |
 *
 * Anything unrecognised gets no tint at all rather than a wrong one — see
 * [genreTintFor].
 */
object EnktelGenre {
    val comedy = Color(0xFFFF6A4D)
    val kids = Color(0xFFFFF04D)
    val documentary = Color(0xFF88FF4D)
    val sport = Color(0xFF4DFF97)
    val news = Color(0xFF4DE1FF)
    val drama = Color(0xFF6673FF)
    val movie = Color(0xFFC44DFF)
    val music = Color(0xFFFF4DB5)

    /** Every tint, in hue order — for tests and for a legend. */
    val all: List<Color> = listOf(comedy, kids, documentary, sport, news, drama, movie, music)

    /**
     * Match a channel's category name to a tint, or null if nothing fits.
     *
     * Order matters where a category could match twice. "Sports documentaries"
     * should read as sport, so [sport] is tested before [documentary].
     */
    fun genreTintFor(category: String): Color? {
        val c = category.lowercase()
        return when {
            c.isBlank() -> null
            "sport" in c || "football" in c || "soccer" in c -> sport
            "news" in c || "weather" in c || "current affairs" in c -> news
            "movie" in c || "film" in c || "cinema" in c -> movie
            "kid" in c || "child" in c || "cartoon" in c || "animation" in c -> kids
            "music" in c || "concert" in c -> music
            "document" in c || "nature" in c || "science" in c || "history" in c -> documentary
            "comedy" in c || "sitcom" in c -> comedy
            "drama" in c || "series" in c || "soap" in c -> drama
            else -> null
        }
    }
}
