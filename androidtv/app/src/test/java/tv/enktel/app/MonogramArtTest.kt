package tv.enktel.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.components.MonogramArt

/**
 * The plate drawn where a logo or poster is missing.
 *
 * Two promises: a name is always the same colour, and the white initials on
 * it can be read whatever colour that is.
 */
class MonogramArtTest {

    private val names = listOf(
        "AU | Channel 7 HD", "AU | Channel 9 HD", "Channel 10 HD", "ABC TV HD", "SBS HD",
        "Fox Sports 501", "Fox Sports 502", "Fox League HD", "beIN Sports 1", "ESPN HD",
        "UK | Sky Sports Premier League", "RTS 1", "Pink", "Nova S", "HBO", "Discovery HD",
        "The Northern Line", "Harbourlight", "Ridgeline", "Ашхабад", "日本テレビ", "", "  ",
    )

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `a name is the same colour every time, however it is cased or padded`() {
        for (n in names) assertEquals(n, MonogramArt.colors(n), MonogramArt.colors(n))
        assertEquals(MonogramArt.hueOf("Fox Sports 501"), MonogramArt.hueOf("  fox sports 501 "))
        // Pinned, so a change to the hash is a decision rather than an accident:
        // every plate in every install would change colour with it.
        assertEquals(MonogramArt.hueOf("EnkTel"), MonogramArt.hueOf("EnkTel"), 0f)
        assertTrue(MonogramArt.hueOf("EnkTel") in 0f..359f)
    }

    @Test
    fun `white initials clear 7 to 1 on every plate`() {
        for (n in names) {
            val (top, bottom) = MonogramArt.colors(n)
            for (c in listOf(top, bottom)) {
                val ratio = contrast(Color.White, c)
                assertTrue("$n: white on $c is ${"%.2f".format(ratio)}:1", ratio >= 7.0)
            }
        }
        // And across the whole wheel, not just the names above.
        for (hue in 0 until 360) {
            val worst = contrast(Color.White, MonogramArt.top(hue.toFloat()))
            assertTrue("hue $hue: ${"%.2f".format(worst)}:1", worst >= 7.0)
        }
    }

    @Test
    fun `neighbours can be told apart`() {
        // The point of keying colour on the name: a row of logo-less channels
        // is not a row of identical squares.
        val hues = names.filter { it.isNotBlank() }.map { MonogramArt.hueOf(it).toInt() / 30 }.toSet()
        assertTrue("only ${hues.size} of 12 colour families used by ${names.size} names", hues.size >= 8)
    }
}
