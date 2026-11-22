package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.repo.ContentRepository

/**
 * The title index is only useful if two panels naming the same film the
 * same way key to the same bucket. Panels do not agree on casing, release
 * tags, year placement or language flags, so the cleaner has to walk the
 * surface noise rather than relying on anyone upstream to tidy it.
 */
class TitleKeyTest {

    @Test
    fun `plain title is lowercased and unchanged`() {
        assertEquals("dune", ContentRepository.titleKey("Dune"))
        assertEquals("the batman", ContentRepository.titleKey("The Batman"))
    }

    @Test
    fun `bracketed year drops`() {
        assertEquals("dune", ContentRepository.titleKey("Dune (2021)"))
        assertEquals("dune", ContentRepository.titleKey("Dune 2021"))
        assertEquals("dune", ContentRepository.titleKey("Dune [2021]"))
    }

    @Test
    fun `resolution and codec tags drop`() {
        assertEquals("the batman", ContentRepository.titleKey("The Batman 1080p HEVC"))
        assertEquals("the batman", ContentRepository.titleKey("The Batman [4K]"))
        assertEquals("dune", ContentRepository.titleKey("Dune 2160p HDR10+"))
    }

    @Test
    fun `source tags drop`() {
        assertEquals(
            "avengers endgame",
            ContentRepository.titleKey("Avengers Endgame 2019 BluRay"),
        )
        assertEquals(
            "the batman",
            ContentRepository.titleKey("The Batman WEB-DL"),
        )
    }

    @Test
    fun `language flag drops`() {
        assertEquals("the batman", ContentRepository.titleKey("The Batman FR"))
        assertEquals("dune", ContentRepository.titleKey("Dune MULTI"))
    }

    @Test
    fun `season episode pair drops`() {
        assertEquals(
            "breaking bad",
            ContentRepository.titleKey("Breaking Bad S01E05"),
        )
        assertEquals(
            "breaking bad",
            ContentRepository.titleKey("Breaking Bad s1e5"),
        )
    }

    @Test
    fun `combined panel noise drops down to the base title`() {
        assertEquals(
            "dune",
            ContentRepository.titleKey("Dune (2021) [4K] MULTI"),
        )
        assertEquals(
            "the batman",
            ContentRepository.titleKey("The.Batman.2022.1080p.BluRay.FR"),
        )
    }

    @Test
    fun `colons and legitimate numbers survive`() {
        // The cleaner must not eat a colon-subtitled film's title.
        assertEquals(
            "dune: part two",
            ContentRepository.titleKey("Dune: Part Two"),
        )
        // A known false positive: 2049 is a subtitle, but it falls in
        // 20\d\d so the cleaner reads it as a year and strips it. The two
        // Blade Runner films then collide in the index — the first-seen
        // one wins, the same way two rows with identical trim+lowercase
        // names collided before this change. Documented rather than
        // avoided; a lookup that would spare "2049" would need a release
        // year table the index deliberately does not carry.
        assertEquals(
            "blade runner",
            ContentRepository.titleKey("Blade Runner 2049"),
        )
    }
}
