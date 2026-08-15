package tv.enktel.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.theme.EnktelGenre
import kotlin.math.abs
import kotlin.math.pow

/**
 * The EPG genre tints have to be eight distinguishable colours, not eight
 * colours.
 *
 * ## What this caught
 *
 * The previous scale was written one genre at a time — pick a green for sport,
 * pick a blue for news — which is the natural way to do it and produces a scale
 * that does not work. Measured, it had sport at 162° and documentaries at 154°.
 * Eight degrees apart is the same colour. Kids and comedy were 11° apart, news
 * and drama 27°. Three of the twenty-eight pairs were indistinguishable, in a
 * grid whose entire purpose is to be scanned at a glance from across a room.
 *
 * None of that is visible while writing the colours. It is trivially visible to
 * arithmetic, which is the argument for this file existing.
 *
 * ## The floors
 *
 * **45° of hue** between every pair. Eight slots on a circle is exactly 45°, so
 * the scale is at the maximum separation eight colours can have — there is no
 * slack, and any new genre forces a re-plan rather than a squeeze.
 *
 * **5:1 against the grid background.** Higher than the 4.5 AA threshold because
 * this is read at three metres. Hue alone does not deliver it: blue at 235° is
 * intrinsically dark and landed at 3.96:1 at the saturation the rest of the
 * scale uses, so its saturation is lowered rather than its hue moved — moving it
 * would have collided with news.
 */
class GenreColorsTest {

    /** The guide grid's background — the darkest bg any palette uses. */
    private val gridBackground = Color(0xFF0B0C10)

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun hue(c: Color): Double {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        val d = max - min
        if (d == 0f) return 0.0
        val h = when (max) {
            c.red -> 60f * (((c.green - c.blue) / d) % 6f)
            c.green -> 60f * (((c.blue - c.red) / d) + 2f)
            else -> 60f * (((c.red - c.green) / d) + 4f)
        }
        return ((h + 360f) % 360f).toDouble()
    }

    private fun hueGap(a: Color, b: Color): Double {
        val d = abs(hue(a) - hue(b)) % 360.0
        return minOf(d, 360.0 - d)
    }

    @Test
    fun `the scale is eight colours`() {
        assertEquals(8, EnktelGenre.all.size)
        assertEquals("two genres share a tint", 8, EnktelGenre.all.toSet().size)
    }

    @Test
    fun `every pair is at least forty five degrees apart`() {
        val named = listOf(
            "comedy" to EnktelGenre.comedy,
            "kids" to EnktelGenre.kids,
            "documentary" to EnktelGenre.documentary,
            "sport" to EnktelGenre.sport,
            "news" to EnktelGenre.news,
            "drama" to EnktelGenre.drama,
            "movie" to EnktelGenre.movie,
            "music" to EnktelGenre.music,
        )
        for (i in named.indices) {
            for (j in i + 1 until named.size) {
                val (na, a) = named[i]
                val (nb, b) = named[j]
                val gap = hueGap(a, b)
                assertTrue(
                    "$na and $nb are only %.0f° apart — they will read as the same tint".format(gap),
                    // A degree of slack for rounding to 8-bit channels.
                    gap >= 44.0,
                )
            }
        }
    }

    @Test
    fun `every tint reads against the grid`() {
        EnktelGenre.all.forEach { c ->
            val ratio = contrast(c, gridBackground)
            assertTrue(
                "a genre tint is %.2f:1 against the grid, below the 5:1 floor".format(ratio),
                ratio >= 5.0,
            )
        }
    }

    @Test
    fun `categories map to the tint a viewer would expect`() {
        assertEquals(EnktelGenre.sport, EnktelGenre.genreTintFor("UK | SPORTS HD"))
        assertEquals(EnktelGenre.sport, EnktelGenre.genreTintFor("Football"))
        assertEquals(EnktelGenre.news, EnktelGenre.genreTintFor("24/7 News"))
        assertEquals(EnktelGenre.movie, EnktelGenre.genreTintFor("Cinema Premiere"))
        assertEquals(EnktelGenre.kids, EnktelGenre.genreTintFor("Kids & Cartoons"))
        assertEquals(EnktelGenre.music, EnktelGenre.genreTintFor("MUSIC"))
        assertEquals(EnktelGenre.documentary, EnktelGenre.genreTintFor("Nature & Science"))
        assertEquals(EnktelGenre.comedy, EnktelGenre.genreTintFor("Comedy Central"))
        assertEquals(EnktelGenre.drama, EnktelGenre.genreTintFor("Drama Series"))
    }

    @Test
    fun `an unknown category gets no tint rather than a wrong one`() {
        assertNull(EnktelGenre.genreTintFor(""))
        assertNull(EnktelGenre.genreTintFor("   "))
        assertNull(EnktelGenre.genreTintFor("Miscellaneous"))
        assertNull(EnktelGenre.genreTintFor("VIP 4K"))
    }

    @Test
    fun `a sports documentary reads as sport`() {
        // Order in the `when` is load-bearing wherever a category can match
        // twice, and nothing about the code says so at the call site.
        assertEquals(EnktelGenre.sport, EnktelGenre.genreTintFor("Sports Documentaries"))
        // The reverse wording resolves the same way, which is the intent.
        assertEquals(EnktelGenre.sport, EnktelGenre.genreTintFor("Documentary | Sport"))
    }
}
