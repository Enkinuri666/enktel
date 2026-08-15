package tv.enktel.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.theme.ALL_PALETTES
import tv.enktel.app.ui.theme.EnktelPalette
import kotlin.math.abs
import kotlin.math.pow

/**
 * Every theme has to stay legible from a sofa.
 *
 * ## Why this is a test and not a review note
 *
 * A palette is eleven colours, and the app has eleven palettes. Nobody is
 * going to re-check a hundred-odd pairings by eye after changing one accent,
 * which is how the Cinematic theme ended up shipping a focus ring at 3.80:1 —
 * comfortable on a monitor at desk distance, genuinely hard to find on a
 * television across a room. Nothing was wrong with the judgement that picked
 * it; there was simply no way to notice.
 *
 * These are the floors, not targets. A palette that only just clears them is
 * not thereby good — but one that fails them is definitely broken, and that is
 * worth catching before it reaches a living room.
 *
 * ## The two rules that are not about contrast
 *
 * The focus accent and the LIVE colour must not collide. `primary` draws the
 * D-pad focus ring and `live` paints LIVE badges and dead-stream banners, so
 * if they read as the same colour then a selected card and a broken channel
 * are indistinguishable — the single worst confusion this UI can produce.
 * Luminance separation alone does not settle it, because two colours can be
 * equally bright and obviously different; hue distance is the check that
 * matters, and it is measured in a way that also survives the common forms of
 * colour blindness. Writing it down caught two collisions in palettes added
 * with exactly this hazard in mind.
 */
class PaletteContrastTest {

    // ---- WCAG relative luminance -------------------------------------------

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Hue in degrees, and saturation, from RGB. */
    private fun hueSat(c: Color): Pair<Double, Double> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 0.0 to 0.0
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return ((h + 360f) % 360f).toDouble() to (d / max).toDouble()
    }

    /** Shortest angular distance between two hues, 0-180. */
    private fun hueGap(a: Color, b: Color): Double {
        val d = abs(hueSat(a).first - hueSat(b).first) % 360.0
        return minOf(d, 360.0 - d)
    }

    private fun each(check: (EnktelPalette) -> Unit) = ALL_PALETTES.forEach(check)

    private fun assertAtLeast(min: Double, actual: Double, what: String, p: EnktelPalette) {
        assertTrue(
            "${p.id}: $what is %.2f:1, below the %.1f:1 floor".format(actual, min),
            actual >= min,
        )
    }

    @Test
    fun `body text carries against the background`() {
        // Well above the 4.5 AA threshold on purpose: this is read at three
        // metres, not thirty centimetres.
        each { assertAtLeast(7.0, contrast(it.text, it.bg), "text on background", it) }
    }

    @Test
    fun `secondary text is dimmer without becoming unreadable`() {
        // Subtitles, episode counts, channel numbers. Dim is the point; it
        // still has to be readable rather than merely present.
        each { assertAtLeast(4.5, contrast(it.textDim, it.surface), "textDim on surface", it) }
    }

    @Test
    fun `the focus accent is findable from the sofa`() {
        // The rule Cinematic broke. primary draws the D-pad focus ring, and a
        // focus indicator is the one thing in a TV UI that must never be
        // subtle — if you cannot see where you are, nothing else about the
        // design matters.
        each { assertAtLeast(4.5, contrast(it.primary, it.bg), "focus accent on background", it) }
    }

    @Test
    fun `alert and success colours read against the background`() {
        each {
            assertAtLeast(3.5, contrast(it.live, it.bg), "live on background", it)
            assertAtLeast(3.5, contrast(it.ok, it.bg), "ok on background", it)
            assertAtLeast(3.5, contrast(it.warn, it.bg), "warn on background", it)
        }
    }

    @Test
    fun `the three status colours stay apart from each other`() {
        // ok / warn / live are not three independent colours — they are one
        // three-position scale, and they appear side by side in a single row on
        // the System Monitor and the speed test. If any two merge, a row of
        // numbers stops being readable as good, marginal and bad, which is the
        // entire content of that row.
        //
        // Deliberately *not* asserted here: warn against primary. It collides in
        // two palettes — Amber puts warn 5° from its accent, High Contrast 11° —
        // and both are correct as they stand. The warm band is what those themes
        // are, and the only hues far enough away are blue and violet, so
        // enforcing separation would mean shipping a blue caution colour, which
        // is a worse outcome than the collision.
        //
        // It is also a much weaker collision than primary-against-live. Focus is
        // not identified by colour: every palette states a ring width, a scale
        // above 1.0 and a glow, so a focused card lifts and outlines itself
        // whatever colour it is. `live` and `warn` are flat tints with no
        // geometry to fall back on, which is why they get the rule and primary
        // does not.
        each { p ->
            listOf(
                Triple(p.ok, p.warn, "ok and warn"),
                Triple(p.warn, p.live, "warn and live"),
                Triple(p.ok, p.live, "ok and live"),
            ).forEach { (a, b, what) ->
                val gap = hueGap(a, b)
                assertTrue(
                    "${p.id}: $what are only %.0f° apart — the status scale collapses".format(gap),
                    gap >= 20.0,
                )
            }
        }
    }

    @Test
    fun `dividers are actually visible`() {
        // A border at 1.05:1 is a border that is not there, and a layout whose
        // structure is invisible reads as items floating rather than grouped.
        each { assertAtLeast(1.25, contrast(it.border, it.surface), "border on surface", it) }
    }

    @Test
    fun `a card is distinguishable from the page behind it`() {
        // By tone or by outline — not necessarily by tone.
        //
        // The first version of this rule demanded tonal separation from every
        // palette and immediately failed High Contrast, which sets surface and
        // background both to pure black on purpose: it separates cards with a
        // 6:1 border instead, and keeps the background at black so text lands
        // at the full 21:1. That is a better answer for low vision than a
        // subtle tonal step, not a worse one, so the rule was wrong rather than
        // the palette. What actually matters is that a card has *some* visible
        // edge, by whichever means the theme chose.
        each { p ->
            val byTone = contrast(p.surface, p.bg) >= 1.05
            val byOutline = contrast(p.border, p.surface) >= 3.0
            assertTrue(
                "${p.id}: a card is invisible against the page — tone %.2f:1 and border %.2f:1 are both too weak"
                    .format(contrast(p.surface, p.bg), contrast(p.border, p.surface)),
                byTone || byOutline,
            )
        }
    }

    @Test
    fun `the focus accent never collides with the live colour`() {
        // Selected and broken must not look the same. Greyscale palettes are
        // exempt by construction: with no accent hue to separate, `live` is
        // the only coloured thing on screen, which separates it far more
        // strongly than any angle would.
        each { p ->
            val (_, sat) = hueSat(p.primary)
            if (sat < 0.15) return@each
            val gap = hueGap(p.primary, p.live)
            assertTrue(
                "${p.id}: focus accent and live are only %.0f° apart in hue — near enough to merge, and to merge completely under red-green colour blindness".format(gap),
                gap >= 40.0,
            )
        }
    }

    @Test
    fun `every palette states its own focus geometry`() {
        // Not a contrast rule: a sanity floor. A ring thinner than 2 dp did not
        // survive a real television, which is recorded twice in Theme.kt after
        // being learned twice.
        each { p ->
            assertTrue("${p.id}: focus ring too thin", p.focusRingWidth.value >= 2f)
            assertTrue("${p.id}: focus scale must lift, not shrink", p.focusScale > 1f)
            assertTrue("${p.id}: glow alpha out of range", p.focusGlowAlpha in 0f..1f)
        }
    }

    @Test
    fun `ids are unique and stable`() {
        // The id is what is written to preferences. A duplicate silently makes
        // one theme unreachable; a rename silently resets everyone using it.
        val ids = ALL_PALETTES.map { it.id }
        assertTrue("duplicate palette ids: $ids", ids.size == ids.toSet().size)
        listOf(
            "enktel_neon", "deep_space", "cinematic", "obsidian", "enktel_blue",
            "crimson", "emerald", "amber", "midnight", "monochrome", "high_contrast",
        ).forEach { assertTrue("palette id '$it' went missing", it in ids) }
    }
}
