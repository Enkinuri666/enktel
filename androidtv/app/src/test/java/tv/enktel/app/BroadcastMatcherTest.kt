package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.BroadcastMatcher

/**
 * "Users often have trouble finding the exact live sports fixture displayed in
 * the Sports Hub, because it does not say what channel it is broadcast on."
 *
 * The Hub knows the fixture and the Match Centre knows the broadcaster; neither
 * helped, because between the published name of a broadcaster and the name of a
 * line on an IPTV playlist there is a layer of decoration that a substring test
 * does not survive. And the EPG cannot be used to bridge it — unreliable guide
 * data is the premise of the request.
 *
 * These are real shapes taken from IPTV channel lists.
 */
class BroadcastMatcherTest {

    private data class Ch(val key: String, val name: String)

    private fun best(broadcaster: String, vararg names: String): String? =
        BroadcastMatcher
            .find(broadcaster, names.map { Ch(it, it) }, name = { it.name })
            .firstOrNull()?.channel?.name

    // ── the decoration ─────────────────────────────────────────────────

    @Test
    fun `a country prefix does not hide the channel`() {
        assertEquals(
            "UK: Sky Sports Main Event",
            best("Sky Sports Main Event", "UK: Sky Sports Main Event"),
        )
        assertEquals("|UK| Sky Sports Main Event", best("Sky Sports Main Event", "|UK| Sky Sports Main Event"))
        assertEquals("EN - Sky Sports Main Event", best("Sky Sports Main Event", "EN - Sky Sports Main Event"))
    }

    @Test
    fun `quality and codec suffixes do not hide the channel`() {
        for (name in listOf(
            "Sky Sports Main Event HD",
            "Sky Sports Main Event FHD",
            "Sky Sports Main Event 4K",
            "Sky Sports Main Event 1080p",
            "Sky Sports Main Event H265",
        )) {
            assertEquals(name, best("Sky Sports Main Event", name))
        }
    }

    @Test
    fun `case and punctuation do not matter`() {
        assertEquals(
            "UK: SKY SPORTS MAIN-EVENT FHD",
            best("Sky Sports Main Event", "UK: SKY SPORTS MAIN-EVENT FHD"),
        )
    }

    @Test
    fun `superscript decoration does not hide the channel`() {
        // Providers really do this.
        assertEquals("TNT Sports 1 ᴴᴰ", best("TNT Sports 1", "TNT Sports 1 ᴴᴰ"))
    }

    // ── not matching the wrong thing ───────────────────────────────────

    @Test
    fun `a sibling channel of the same brand is not offered`() {
        // The failure that would make this feature worse than nothing: sending
        // someone confidently to the wrong Sky Sports channel.
        assertEquals(null, best("Sky Sports Football", "UK: Sky Sports Cricket HD"))
        assertEquals(null, best("Sky Sports Main Event", "UK: Sky Sports Football HD"))
        assertEquals(null, best("TNT Sports 1", "TNT Sports 2 HD"))
    }

    @Test
    fun `a broadcaster the playlist does not carry matches nothing`() {
        assertEquals(null, best("Fox Sports 502", "UK: BBC One HD", "ITV1", "Channel 4"))
    }

    @Test
    fun `an empty name on either side is not a match`() {
        assertEquals(null, best("", "UK: Sky Sports Main Event"))
        assertEquals(null, best("Sky Sports Main Event", ""))
    }

    // ── ranking ────────────────────────────────────────────────────────

    @Test
    fun `the plainest line wins`() {
        // All three carry it. The one that adds nothing of its own is the one
        // that was asked for; the others are different feeds of the brand.
        val hits = BroadcastMatcher.find(
            "Sky Sports Main Event",
            listOf(
                Ch("a", "UK: Sky Sports Main Event Extra HD"),
                Ch("b", "UK: Sky Sports Main Event HD"),
                Ch("c", "UK: Sky Sports Main Event Ireland HD"),
            ),
            name = { it.name },
        )
        assertEquals("UK: Sky Sports Main Event HD", hits.first().channel.name)
        assertTrue(hits.first().score > hits.last().score)
    }

    @Test
    fun `the same line is not offered twice for two broadcasters`() {
        // A fixture carried on two brands, and one playlist line that matches
        // both — offering it twice reads as two different answers.
        val hits = BroadcastMatcher.findAny(
            broadcasters = listOf("Sky Sports", "Sky Sports Main Event"),
            channels = listOf(Ch("a", "UK: Sky Sports Main Event HD")),
            key = { it.key },
            name = { it.name },
        )
        assertEquals(1, hits.size)
    }

    @Test
    fun `results are capped so a rail stays a rail`() {
        val many = (1..40).map { Ch("k$it", "UK: Sky Sports Main Event $it HD") }
        assertTrue(BroadcastMatcher.find("Sky Sports Main Event", many, limit = 4, name = { it.name }).size <= 4)
    }

    // ── the tokeniser, since everything rests on it ────────────────────

    @Test
    fun `tokens keep what identifies a channel and drop what decorates it`() {
        assertEquals(
            listOf("sky", "sports", "main", "event"),
            BroadcastMatcher.tokens("UK: Sky Sports Main Event FHD"),
        )
        // A number is never noise — it is usually the whole distinction.
        assertEquals(listOf("tnt", "sports", "1"), BroadcastMatcher.tokens("|UK| TNT Sports 1 HD"))
    }

    @Test
    fun `a two letter word inside a name is not mistaken for a prefix`() {
        // Only a *leading* country code is decoration. "BBC News UK" ends with
        // one and it is part of the name.
        assertTrue(BroadcastMatcher.tokens("BBC News UK").contains("uk"))
    }
}
