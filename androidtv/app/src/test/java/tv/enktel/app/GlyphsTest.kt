package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.components.Glyphs
import java.io.File

/**
 * The leading-pictograph adapter, and the scan that keeps it honest.
 *
 * [Glyphs] replaces a character at the front of a button label with a drawn
 * icon. It is deliberately forgiving — a label it does not recognise passes
 * through untouched — which is what makes it safe to put under 320 call sites,
 * and also what makes it easy to defeat by accident: write a button with a
 * pictograph the table has not got and it ships as an emoji again, silently.
 * The scan at the bottom is what turns that into a failing build.
 */
class GlyphsTest {

    // ---- splitting ----------------------------------------------------------

    @Test
    fun `a leading pictograph comes off and the words are kept`() {
        val p = Glyphs.split("▶ Play")
        assertNotNull(p.mark)
        assertEquals("Play", p.text)
    }

    @Test
    fun `the gap between pictograph and words is not kept`() {
        // Call sites pad with one space, two, or a pair of them for effect.
        // The control draws its own gap, so none of that may survive.
        assertEquals("Trailer", Glyphs.split("▶  Trailer").text)
        assertEquals("Search", Glyphs.split("🔎  Search").text)
        assertEquals("Pause", Glyphs.split("⏸\u00A0Pause").text)
    }

    @Test
    fun `a label with no pictograph is returned exactly as written`() {
        // The property the whole design leans on: the worst case is the old
        // behaviour.
        listOf("Play", "Sync now", "", "   ", "My EnkTel line (xtream)", "IMDb ↗").forEach {
            val p = Glyphs.split(it)
            assertNull("'$it' should not have produced an icon", p.mark)
            assertEquals(it, p.text)
        }
    }

    @Test
    fun `a pictograph the table does not know is left alone`() {
        val p = Glyphs.split("🦄 Unicorn mode")
        assertNull(p.mark)
        assertEquals("🦄 Unicorn mode", p.text)
    }

    @Test
    fun `a pictograph on its own is an icon with a name and no words`() {
        // Downloads draws "▶", "⏸", "↻" and "✕" as whole buttons. Without a
        // name an icon-only control is silent to a screen reader.
        listOf("▶", "⏸", "↻", "✕", "★", "☆").forEach {
            val p = Glyphs.split(it)
            assertNotNull("'$it' should be an icon", p.mark)
            assertEquals("", p.text)
            assertTrue("'$it' has no spoken name", p.mark!!.name.isNotBlank())
        }
    }

    @Test
    fun `the emoji variation selector is not left behind`() {
        // "☀️" is U+2600 followed by U+FE0F. Matching only the first leaves an
        // invisible character at the front of the label, which then measures
        // as one space wider than it draws.
        val p = Glyphs.split("☀\uFE0F Brightness")
        assertNotNull(p.mark)
        assertEquals("Brightness", p.text)
    }

    @Test
    fun `the longer pictograph wins`() {
        // "▤▤ Multi-view" is a grid; "▤ Dock" is a docked player. Matching the
        // short one first would draw the dock icon in front of "▤ Multi-view".
        assertEquals("Multi-view", Glyphs.split("▤▤ Multi-view").text)
        assertEquals("Dock & browse", Glyphs.split("▤ Dock & browse").text)
        assertTrue(Glyphs.split("▤▤ Multi-view").mark != Glyphs.split("▤ Dock").mark)
    }

    @Test
    fun `the two stars are different drawings`() {
        // The favourite toggle is written `if (fav) "★ Unstar" else "☆ Star"`.
        // If both mapped to one icon the button would stop showing its state.
        assertTrue(Glyphs.split("★ Unstar").mark!!.icon != Glyphs.split("☆ Star").mark!!.icon)
    }

    @Test
    fun `strip keeps only the words`() {
        assertEquals("Just Added", Glyphs.strip("⚡  Just Added"))
        assertEquals("Top Rated", Glyphs.strip("★  Top Rated"))
        assertEquals("Trending now", Glyphs.strip("Trending now"))
    }

    // ---- the scan -----------------------------------------------------------

    private fun uiSources(): List<File>? {
        val candidates = listOf(
            "src/main/java/tv/enktel/app",
            "app/src/main/java/tv/enktel/app",
            "androidtv/app/src/main/java/tv/enktel/app",
        )
        val root = candidates.map(::File).firstOrNull { it.isDirectory } ?: return null
        return root.walkTopDown()
            // Glyphs.kt is the table itself.
            .filter { it.isFile && it.extension == "kt" && it.name != "Glyphs.kt" }
            .toList()
    }

    /** Colour emoji and the symbols several vendors draw as emoji. */
    private fun isPictograph(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF ||   // emoji proper
            cp in 0x23E9..0x23FA || // ⏩ ⏪ ⏭ ⏮ ⏸ ⏺ — emoji-presentation on Fire OS and One UI
            cp == 0x2B07 ||         // ⬇
            cp == 0x26BD ||         // ⚽
            cp == 0x2B50 ||         // ⭐
            cp == 0x26A1 ||         // ⚡
            cp == 0x2753 ||         // ❓
            cp == 0x26D4

    @Test
    fun `every pictograph that leads a control's label has a drawing`() {
        val files = uiSources() ?: return
        // A string literal passed as the first argument of one of the controls
        // that route their label through Glyphs.
        val call = Regex("""\b(FocusButton|GlassChip|ShareButton)\(\s*(?:text\s*=\s*)?"((?:[^"\\]|\\.)*)"""")
        val offenders = mutableListOf<String>()

        files.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                call.findAll(line).forEach { m ->
                    val label = m.groupValues[2]
                    val first = label.trimStart().codePoints().findFirst()
                    if (!first.isPresent || !isPictograph(first.asInt)) return@forEach
                    if (Glyphs.split(label).mark == null) {
                        offenders += "${f.name}:${i + 1}  \"$label\""
                    }
                }
            }
        }

        assertTrue(
            "these labels lead with a pictograph Glyphs cannot draw, so they will render as " +
                "an emoji. Add it to the table in Glyphs.kt, or pass icon = instead:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
