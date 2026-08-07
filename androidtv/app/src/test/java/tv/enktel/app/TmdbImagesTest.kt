package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.metadata.TmdbImages

class TmdbImagesTest {

    @Test
    fun `builds a poster url from a tmdb path`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/kqjL17yufvn9OVLyXYpvtyrFfak.jpg",
            TmdbImages.poster("/kqjL17yufvn9OVLyXYpvtyrFfak.jpg"),
        )
    }

    @Test
    fun `builds a backdrop url at the larger size`() {
        val url = TmdbImages.backdrop("/xJHokMbljvjADYdit5fK5VQsXEG.jpg")
        assertEquals("https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg", url)
        // Posters and backdrops must not collapse to the same size: a 2:3
        // poster at w1280 is a wasted 3× decode, and a 16:9 backdrop at w500
        // is visibly soft stretched across a TV.
        assertTrue(TmdbImages.poster("/a.jpg") != TmdbImages.backdrop("/a.jpg"))
    }

    @Test
    fun `empty rather than a half-built url when there is no image`() {
        // These go straight into non-null columns where "" already means "no
        // image". Returning the bare base would store a row that looks like it
        // has art and 404s on load.
        assertEquals("", TmdbImages.poster(null))
        assertEquals("", TmdbImages.poster(""))
        assertEquals("", TmdbImages.poster("   "))
        assertEquals("", TmdbImages.backdrop(null))
        assertEquals("", TmdbImages.backdrop(""))
    }

    @Test
    fun `a value that is already a url is not prefixed`() {
        // A panel-supplied poster is an absolute URL, not a TMDB path. Gluing
        // the TMDB base in front of one produces a URL that resolves to
        // nothing, and the only symptom is missing artwork.
        assertEquals("", TmdbImages.poster("https://cdn.example.com/art/1.jpg"))
        assertEquals("", TmdbImages.poster("art/1.jpg"))
    }

    @Test
    fun `a lone slash is not an image path`() {
        assertEquals("", TmdbImages.poster("/"))
    }
}
