package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fire OS launcher integration lands here: a catalog row or an Alexa "play X on
 * EnkTel" arrives as a VIEW intent carrying one of these URIs. The app had no
 * VIEW filter and no parser, so the whole integration had nowhere to land.
 *
 * The negatives matter more than the positives — a malformed link must open the
 * app normally, never navigate to whatever happens to live at stream 0.
 */
class DeepLinkTest {

    private fun parse(s: String) = DeepLink.parse(s)

    @Test
    fun `custom scheme resolves each content kind`() {
        assertEquals(DeepLink.Target.Movie(42), parse("enktel://play/movie/42"))
        assertEquals(DeepLink.Target.Series(7), parse("enktel://play/series/7"))
        assertEquals(DeepLink.Target.Channel(300), parse("enktel://play/channel/300"))
    }

    @Test
    fun `https links from the website resolve the same way`() {
        assertEquals(DeepLink.Target.Movie(42), parse("https://enktel.tv/play/movie/42"))
        assertEquals(DeepLink.Target.Channel(9), parse("https://enktel.tv/play/live/9"))
    }

    @Test
    fun `alexa phrases arrive as a search`() {
        assertEquals(
            DeepLink.Target.Search("action movies"),
            parse("enktel://play/search?q=action%20movies"),
        )
    }

    @Test
    fun `a malformed id is not treated as zero`() {
        assertNull(parse("enktel://play/movie/not-a-number"))
        assertNull(parse("enktel://play/movie/0"))
        assertNull(parse("enktel://play/movie"))
    }

    @Test
    fun `unknown kinds and foreign hosts are refused`() {
        assertNull(parse("enktel://play/podcast/5"))
        assertNull(parse("enktel://elsewhere/movie/5"))
        assertNull(parse("https://example.com/play/movie/5"))
        assertNull(parse("https://enktel.tv/blog/movie/5"))
    }

    @Test
    fun `an empty search query is refused rather than opening a blank search`() {
        assertNull(parse("enktel://play/search"))
        assertNull(parse("enktel://play/search?q="))
    }

    @Test
    fun `the feed generator and the parser agree on every shape`() {
        // These two disagreeing is a bug that only shows up in production, on
        // someone else's television.
        listOf(
            DeepLink.Target.Movie(42),
            DeepLink.Target.Series(7),
            DeepLink.Target.Channel(300),
            DeepLink.Target.Search("the batman"),
        ).forEach { target ->
            assertEquals(target, parse(DeepLink.uriFor(target)))
        }
    }
}
