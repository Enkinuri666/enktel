package tv.enktel.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.theme.EnktelRadius
import tv.enktel.app.ui.theme.EnktelSpace
import java.io.File

/**
 * Holds the corner-radius and spacing scales, and stops the drift returning.
 *
 * ## What went wrong the first time
 *
 * There is no story here about a bad decision. Before this test the app had 199
 * `RoundedCornerShape(N.dp)` call sites using eighteen distinct radii — 2, 3, 4,
 * 6, 7, 8, 10, 12, 14, 15, 16, 18, 19, 20, 24, 28, 32 and 50. Every one of them
 * was a reasonable local choice. Nobody could see the eighteen, because nobody
 * ever has all 199 files open at once.
 *
 * That is the whole problem, and it is why a test is the right shape for the
 * fix. A convention in a document decays; a convention that fails a build does
 * not. The rules below are cheap to satisfy deliberately and hard to break by
 * accident, which is the only useful property a consistency rule can have.
 *
 * ## The source scan
 *
 * [no radii outside the scale] reads the UI sources and re-derives the
 * distribution. It is the rule that actually does the work — the others just
 * pin the scale itself, and a scale nothing is checked against is a suggestion.
 *
 * It **passes** when it cannot locate the source tree, rather than failing.
 * A test whose subject is unreachable has learned nothing, and turning that into
 * a red build would mean a Gradle layout change breaks CI for a reason that has
 * nothing to do with the code. Silence here means "not checked", not "clean" —
 * which is the correct thing for it to mean.
 */
class DesignTokensTest {

    private val density = Density(1f)
    private val box = Size(1000f, 1000f)

    // ---- the scales ---------------------------------------------------------

    @Test
    fun `the radius scale is five ascending steps on a four dp rhythm`() {
        val steps = EnktelRadius.all
        assertEquals("the scale grew or lost a step", 5, steps.size)
        assertEquals(
            "the scale is not the one the app was snapped to",
            listOf(4f, 8f, 12f, 16f, 24f),
            steps.map { it.value },
        )
        steps.zipWithNext { a, b ->
            assertTrue("radius steps must ascend: $a then $b", b > a)
        }
        steps.forEach {
            assertTrue("$it is off the 4 dp rhythm", it.value % 4f == 0f)
        }
    }

    @Test
    fun `the spacing scale is six ascending steps on a four dp rhythm`() {
        val steps = EnktelSpace.all
        assertEquals("the scale grew or lost a step", 6, steps.size)
        assertEquals(
            listOf(4f, 8f, 12f, 16f, 24f, 32f),
            steps.map { it.value },
        )
        steps.zipWithNext { a, b ->
            assertTrue("spacing steps must ascend: $a then $b", b > a)
        }
        steps.forEach {
            assertTrue("$it is off the 4 dp rhythm", it.value % 4f == 0f)
        }
    }

    @Test
    fun `each shape rounds to its own step`() {
        // Guards the copy-paste hazard in a block of six near-identical lines:
        // `shapeLg = RoundedCornerShape(md)` reads fine and is wrong.
        listOf(
            EnktelRadius.shapeXs to 4f,
            EnktelRadius.shapeSm to 8f,
            EnktelRadius.shapeMd to 12f,
            EnktelRadius.shapeLg to 16f,
            EnktelRadius.shapeXl to 24f,
        ).forEach { (shape, expected) ->
            assertEquals(expected, shape.topStart.toPx(box, density), 0.01f)
            assertEquals(
                "corners disagree with each other",
                shape.topStart.toPx(box, density),
                shape.bottomEnd.toPx(box, density),
                0.01f,
            )
        }
    }

    @Test
    fun `the pill is a proportion, not a large number`() {
        // `999.dp` renders identically — Compose clamps a radius to half the
        // shorter side — so the difference only shows up in a test like this
        // one. It matters because the two behave differently the moment
        // something resizes: a proportion stays a pill, a big constant is a pill
        // by luck.
        assertEquals(500f, EnktelRadius.shapePill.topStart.toPx(Size(1000f, 1000f), density), 0.01f)
        assertEquals(50f, EnktelRadius.shapePill.topStart.toPx(Size(100f, 100f), density), 0.01f)
    }

    // ---- the scan -----------------------------------------------------------

    /** `app/src/main/java/tv/enktel/app/ui`, or null if the layout moved. */
    private fun uiSources(): List<File>? {
        val candidates = listOf(
            "src/main/java/tv/enktel/app/ui",
            "app/src/main/java/tv/enktel/app/ui",
            "androidtv/app/src/main/java/tv/enktel/app/ui",
        )
        val root = candidates.map(::File).firstOrNull { it.isDirectory } ?: return null
        // Shape.kt is excluded from every scan: it is the file that *documents*
        // the forms being banned, so its doc comment quotes them verbatim. Both
        // scans found it on the first run, which is a reasonable sign they work.
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Shape.kt" }
            .toList()
    }

    @Test
    fun `no radii outside the scale`() {
        val files = uiSources() ?: return
        val allowed = EnktelRadius.all.map { it.value.toInt() }.toSet()
        val pattern = Regex("""RoundedCornerShape\(\s*(\d+)\.dp""")
        val offenders = mutableListOf<String>()

        files.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                pattern.findAll(line).forEach { m ->
                    val dp = m.groupValues[1].toInt()
                    if (dp !in allowed) offenders += "${f.name}:${i + 1}  ${dp}.dp"
                }
            }
        }

        assertTrue(
            "corner radii off the scale (${allowed.sorted().joinToString()} dp, or " +
                "RoundedCornerShape(percent = 50) for a pill):\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `pills are written as a percentage`() {
        // The trap this closes: `RoundedCornerShape(50)` is the Int overload and
        // means 50 percent, while `RoundedCornerShape(50.dp)` is a 50 dp corner.
        // One character apart, different shapes, and a code review will not
        // reliably catch it. Naming the argument makes the intent survive.
        val files = uiSources() ?: return
        val bare = Regex("""RoundedCornerShape\(\s*\d+\s*\)""")
        val offenders = mutableListOf<String>()

        files.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (bare.containsMatchIn(line)) offenders += "${f.name}:${i + 1}  ${line.trim()}"
            }
        }

        assertTrue(
            "write these as RoundedCornerShape(percent = 50):\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
