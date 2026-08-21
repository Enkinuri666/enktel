package tv.enktel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.m3u.M3uEntry

/**
 * Live-or-film classification, which decides whether an entry lands in Live TV
 * or in the movies library.
 *
 * The group title used to be consulted before the container, so any playlist
 * that buckets channels by genre had its film channels swallowed: `US - Movies`
 * is 157 live streams, and all of them were filed as VOD — missing from Live TV
 * and unplayable in Movies, since they are not files.
 */
class M3uIsVodTest {

    private fun entry(url: String, group: String) = M3uEntry(
        name = "Channel",
        url = url,
        tvgId = "",
        tvgName = "",
        logo = "",
        group = group,
        chno = 0,
        catchupDays = 0,
    )

    /** The regression: a genre bucket named after content, carrying live HLS. */
    @Test
    fun `a live stream in a Movies genre bucket is not VOD`() {
        assertFalse(entry("http://example.com/live/123.m3u8", "US - Movies").isVod)
        assertFalse(entry("http://example.com/live/123.m3u8", "GB - Movies").isVod)
        assertFalse(entry("http://example.com/live/9.ts", "Cinema / Film").isVod)
        assertFalse(entry("http://example.com/live/9.mpd", "VOD Channels").isVod)
    }

    /** A real film still reads as one, whatever its group is called. */
    @Test
    fun `a file container is VOD`() {
        assertTrue(entry("http://example.com/movie/1.mkv", "US - Movies").isVod)
        assertTrue(entry("http://example.com/movie/1.mp4", "Sports").isVod)
        assertTrue(entry("http://example.com/movie/1.avi", "").isVod)
    }

    /** No extension to go on — the group title is the only evidence there is. */
    @Test
    fun `an extensionless URL falls back to the group`() {
        assertTrue(entry("http://example.com/movie/1", "VOD | Action").isVod)
        assertTrue(entry("http://example.com/movie/1", "Films").isVod)
        assertFalse(entry("http://example.com/live/1", "US - News").isVod)
    }

    /** A query string must not hide the container from the check. */
    @Test
    fun `query strings do not change the container`() {
        assertFalse(entry("http://example.com/live/1.m3u8?token=abc", "US - Movies").isVod)
        assertTrue(entry("http://example.com/movie/1.mp4?token=abc", "US - Movies").isVod)
    }
}
