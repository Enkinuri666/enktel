package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import tv.enktel.app.ui.components.glassFillAlphas
import tv.enktel.app.ui.components.scrimRamp
import org.junit.Test
import kotlin.math.abs

/**
 * The scrim curve, which is the part of the glass work that is an actual claim.
 *
 * ## The claim
 *
 * The players faded to black with a two-stop linear gradient. A linear alpha
 * ramp shows a visible line across the picture at the height the scrim starts,
 * because the eye finds the discontinuity in the *rate* of change rather than
 * the value — and a straight line goes from no slope to full slope instantly at
 * both ends.
 *
 * A smoothstep, `t²(3−2t)`, has zero slope at both ends. That is the entire
 * argument, and it is arithmetic, so it can be held here rather than asserted
 * in a commit message.
 *
 * ## The part that is easy to get backwards
 *
 * A softer scrim sounds like it must trade legibility for picture. This one
 * does not, and [the curve darkens more where the text actually sits] is the
 * test that says so: smoothstep sits *below* the linear ramp through the top
 * half and *above* it through the bottom half. The picture is less obscured
 * where there is nothing on top of it, and more obscured where the controls
 * are. Both ends improve, which is why it is worth doing rather than a matter
 * of taste.
 */
class GlassTest {

    private val max = 0.95f

    @Test
    fun `the ramp spans nothing to the requested maximum`() {
        val ramp = scrimRamp(max)
        assertEquals(0f, ramp.first(), 1e-6f)
        assertEquals(max, ramp.last(), 1e-6f)
    }

    @Test
    fun `the ramp never doubles back`() {
        // A non-monotonic scrim would show as a band of picture inside the fade.
        scrimRamp(max).zipWithNext { a, b ->
            assertTrue("scrim alpha went backwards: $a then $b", b >= a)
        }
    }

    @Test
    fun `it enters and leaves without an edge`() {
        // The property the whole change is for. Compare the first and last
        // increments against a straight line's constant increment: if the curve
        // starts at anything close to full slope, it draws a line across the
        // picture exactly where it begins.
        val steps = 12
        val ramp = scrimRamp(max, steps)
        val linearStep = max / steps

        val firstStep = ramp[1] - ramp[0]
        val lastStep = ramp[steps] - ramp[steps - 1]

        assertTrue(
            "scrim starts at %.4f per step against a linear %.4f — that is an edge"
                .format(firstStep, linearStep),
            firstStep < linearStep / 3f,
        )
        assertTrue(
            "scrim tops out at %.4f per step against a linear %.4f — that is a second edge"
                .format(lastStep, linearStep),
            lastStep < linearStep / 3f,
        )
    }

    @Test
    fun `the curve darkens more where the text actually sits`() {
        // Less scrim over the top half (more picture), more over the bottom half
        // (more legible controls). Not a trade.
        val steps = 12
        val ramp = scrimRamp(max, steps)
        ramp.forEachIndexed { i, eased ->
            val t = i / steps.toFloat()
            val linear = max * t
            when {
                t < 0.5f -> assertTrue(
                    "at t=%.2f the eased scrim (%.3f) should sit below linear (%.3f)"
                        .format(t, eased, linear),
                    eased <= linear + 1e-5f,
                )
                t > 0.5f -> assertTrue(
                    "at t=%.2f the eased scrim (%.3f) should sit above linear (%.3f)"
                        .format(t, eased, linear),
                    eased >= linear - 1e-5f,
                )
                else -> assertEquals("the curve should cross linear at the midpoint", linear, eased, 1e-5f)
            }
        }
    }

    @Test
    fun `it is symmetric about the midpoint`() {
        // Smoothstep is odd-symmetric around (0.5, 0.5). Worth holding because
        // it is what makes a top scrim and a bottom scrim mirror each other
        // rather than needing separate tuning.
        val steps = 12
        val ramp = scrimRamp(max, steps)
        for (i in 0..steps) {
            val mirrored = max - ramp[steps - i]
            assertTrue(
                "step $i breaks symmetry: %.4f vs %.4f".format(ramp[i], mirrored),
                abs(ramp[i] - mirrored) < 1e-5f,
            )
        }
    }

    @Test
    fun `a dense panel cannot ask for an impossible alpha`() {
        // 0.95 + 0.09 is 1.04, which is not a compile error and not a colour.
        val (dense, thin) = glassFillAlphas(0.95f)
        assertEquals(1f, dense, 1e-6f)
        assertTrue("thin edge should stay below the dense one", thin < dense)

        val (d2, t2) = glassFillAlphas(0.02f)
        assertEquals("a nearly-transparent panel must not go negative", 0f, t2, 1e-6f)
        assertTrue(d2 > t2)
    }

    @Test
    fun `an ordinary panel keeps its gradient`() {
        // The clamps must not flatten the common case into a single tone —
        // the graded fill is one of the three cues doing the work.
        val (dense, thin) = glassFillAlphas(0.72f)
        assertEquals(0.81f, dense, 1e-5f)
        assertEquals(0.63f, thin, 1e-5f)
    }
}
