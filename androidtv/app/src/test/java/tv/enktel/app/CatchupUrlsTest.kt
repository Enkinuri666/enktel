package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.catchup.CatchupUrls
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.diag.CatchupScheme
import java.util.TimeZone

/**
 * Catch-Up built one URL, in one shape, for Xtream profiles only — so a panel
 * that serves its archive from `streaming/timeshift.php` and an M3U line
 * carrying a perfectly good `catchup-source` both failed with no explanation.
 * These pin the shapes and, more importantly, pin the cases that must produce
 * *nothing* rather than a plausible-looking URL that cannot work.
 */
class CatchupUrlsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private val xtream = Profile(
        id = 1, name = "line", kind = "xtream",
        server = "http://panel.example:8080", username = "u", password = "pw",
    )
    private val m3u = Profile(id = 2, name = "list", kind = "m3u", m3uUrl = "http://x/list.m3u")

    private fun channel(
        p: Profile,
        archive: Boolean = true,
        days: Int = 7,
        url: String = "",
        type: String = "",
        source: String = "",
    ) = Channel(
        key = "${p.id}:55", profileId = p.id, streamId = 55, name = "Channel 55",
        url = url, hasArchive = archive, archiveDays = days,
        catchupType = type, catchupSource = source,
    )

    // 2024-03-05 14:00:00 UTC, one hour long.
    private val start = 1_709_647_200_000L
    private val end = start + 60 * 60_000L
    private val now = start + 5 * 86_400_000L

    @Test
    fun `xtream produces the path shape first and the php shape as a real fallback`() {
        val urls = CatchupUrls.candidates(xtream, channel(xtream), start, end, now, utc)
        assertEquals(
            "http://panel.example:8080/timeshift/u/pw/60/2024-03-05:14-00/55.ts",
            urls.first(),
        )
        assertTrue(
            "the php shape is the whole reason catch-up looked broken on some panels: $urls",
            urls.any { it.startsWith("http://panel.example:8080/streaming/timeshift.php?") },
        )
        assertTrue(urls.any { it.endsWith("/55.m3u8") })
    }

    @Test
    fun `the php fallback carries the same start and duration as the path shape`() {
        val urls = CatchupUrls.candidates(xtream, channel(xtream), start, end, now, utc)
        val php = urls.single { it.contains("timeshift.php") }
        assertTrue(php, php.contains("stream=55"))
        assertTrue(php, php.contains("start=2024-03-05:14-00"))
        assertTrue(php, php.contains("duration=60"))
    }

    @Test
    fun `an m3u line with a source template uses the providers own url first`() {
        val ch = channel(
            m3u,
            url = "http://cdn.example/live/55.m3u8",
            type = "default",
            source = "http://cdn.example/archive/55?from=\${start}&to=\${end}",
        )
        val urls = CatchupUrls.candidates(m3u, ch, start, end, now, utc)
        assertEquals(
            "http://cdn.example/archive/55?from=1709647200&to=1709650800",
            urls.first(),
        )
    }

    @Test
    fun `append scheme hangs utc and lutc off the live url`() {
        val ch = channel(m3u, url = "http://cdn.example/live/55.m3u8", type = "append")
        val urls = CatchupUrls.candidates(m3u, ch, start, end, now, utc)
        assertEquals(
            "http://cdn.example/live/55.m3u8?utc=1709647200&lutc=1710079200",
            urls.first(),
        )
    }

    @Test
    fun `append scheme respects a live url that already has a query`() {
        val ch = channel(m3u, url = "http://cdn.example/live/55.m3u8?token=abc", type = "append")
        val urls = CatchupUrls.candidates(m3u, ch, start, end, now, utc)
        assertTrue(urls.first(), urls.first().startsWith("http://cdn.example/live/55.m3u8?token=abc&utc="))
    }

    @Test
    fun `flussonic rewrites the playlist file into an archive segment`() {
        val ch = channel(m3u, url = "http://flus.example/ch55/index.m3u8", type = "flussonic")
        val urls = CatchupUrls.candidates(m3u, ch, start, end, now, utc)
        assertEquals("http://flus.example/ch55/archive-1709647200-3600.m3u8", urls.first())
        assertTrue(urls.any { it == "http://flus.example/ch55/archive-1709647200-3600.ts" })
    }

    @Test
    fun `an m3u channel with no scheme and no template produces nothing`() {
        // The important negative: without this the caller would navigate to a
        // guessed Xtream path on a profile that has no Xtream credentials, and
        // the user would get a player error instead of an explanation.
        val ch = channel(m3u, url = "http://cdn.example/live/55.m3u8")
        assertEquals(emptyList<String>(), CatchupUrls.candidates(m3u, ch, start, end, now, utc))
        assertFalse(CatchupUrls.isSupported(m3u, ch))
    }

    @Test
    fun `a channel with no archive is never supported`() {
        assertFalse(CatchupUrls.isSupported(xtream, channel(xtream, archive = false)))
        assertFalse(CatchupUrls.isSupported(m3u, channel(m3u, archive = false, type = "append")))
    }

    @Test
    fun `a zero-length programme produces nothing rather than a zero duration url`() {
        val ch = channel(xtream)
        assertEquals(emptyList<String>(), CatchupUrls.candidates(xtream, ch, start, start, now, utc))
    }

    @Test
    fun `an m3u line declaring append is supported even though it is not xtream`() {
        // The old gate was `kind == "xtream"`, which refused this line on
        // paperwork rather than on anything about the stream.
        val ch = channel(m3u, url = "http://cdn.example/live/55.m3u8", type = "append")
        assertTrue(CatchupUrls.isSupported(m3u, ch))
    }

    @Test
    fun `scheme detection prefers the declared type over the template shape`() {
        // A flussonic source legitimately contains a start placeholder, so
        // reading the template first would misfile it as `append`.
        val ch = channel(
            m3u, url = "http://f.example/ch/index.m3u8",
            type = "flussonic", source = "http://f.example/ch/archive-\${start}-\${duration}.ts",
        )
        assertEquals(CatchupScheme.FLUSSONIC, CatchupUrls.schemeOf(m3u, ch))
    }

    @Test
    fun `the archive window excludes programmes older than the advertised days`() {
        val ch = channel(xtream, days = 3)
        val n = 1_710_000_000_000L
        assertTrue(CatchupUrls.isWithinWindow(ch, n - 2 * 86_400_000L, n))
        assertFalse("a four-day-old programme is gone from a three-day archive",
            CatchupUrls.isWithinWindow(ch, n - 4 * 86_400_000L, n))
        assertFalse("a programme still to air cannot be caught up",
            CatchupUrls.isWithinWindow(ch, n + 3_600_000L, n))
    }

    @Test
    fun `unknown placeholders are left visible rather than blanked`() {
        val out = CatchupUrls.fillTemplate(
            template = "http://x/a?s=\${start}&junk=\${nonsense}",
            startSec = 1_709_647_200, endSec = 1_709_650_800,
            nowSec = 1_710_079_200, durationSec = 3600, tz = utc,
        )
        assertEquals("http://x/a?s=1709647200&junk=\${nonsense}", out)
    }

    @Test
    fun `date component placeholders use the panel timezone`() {
        val template = "http://x/{Y}/{m}/{d}/{H}-{M}.ts"
        assertEquals(
            "http://x/2024/03/05/14-00.ts",
            CatchupUrls.fillTemplate(template, start / 1000, end / 1000, now / 1000, 3600, utc),
        )
        assertEquals(
            "http://x/2024/03/05/15-00.ts",
            CatchupUrls.fillTemplate(
                template, start / 1000, end / 1000, now / 1000, 3600,
                TimeZone.getTimeZone("Europe/Berlin"),
            ),
        )
    }
}
