package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.components.YouTubeEmbed

/**
 * A tester opened a trailer, got YouTube's grey "Video unavailable" panel, and
 * was then told by this app that *this device's browser engine could not play
 * it* — on a phone that plays YouTube perfectly well.
 *
 * Two things were wrong behind that. The page declared an `origin` it did not
 * really have, so YouTube refused the embed and returned error 5; and the
 * recovery path — try another upload — had nothing to try, because only a
 * personal TMDB key ever yields more than one upload and hardly anybody has
 * one. These pin the replacement: every upload gets a second attempt on the
 * other embed host, and a video that is genuinely un-embeddable does not waste
 * the viewer's time being asked twice.
 */
class YouTubeEmbedTest {

    // ── planning the attempts ──────────────────────────────────────────

    @Test
    fun `a single upload still gets two attempts`() {
        // The case that matters: this is what the overwhelming majority of
        // catalogues produce, and it used to mean exactly one attempt and then
        // a dead end.
        val plan = YouTubeEmbed.attempts(listOf("abc"))
        assertEquals(2, plan.size)
        // Privacy-enhanced host first — see the note in `attempts`. A tester
        // saw the default host refuse and the retry play the same trailer.
        assertEquals(YouTubeEmbed.HOST_NOCOOKIE, plan[0].host)
        assertEquals(YouTubeEmbed.HOST_DEFAULT, plan[1].host)
        assertTrue(plan.all { it.videoId == "abc" })
        assertEquals(2, plan.map { it.host }.distinct().size)
    }

    @Test
    fun `both hosts of one upload come before the next upload`() {
        // A host change is likelier to help than a different upload, and the
        // alternates are ordered best-first — so exhaust the good one before
        // dropping to a worse teaser.
        val plan = YouTubeEmbed.attempts(listOf("one", "two"))
        assertEquals(listOf("one", "one", "two", "two"), plan.map { it.videoId })
    }

    @Test
    fun `blanks and duplicates do not become attempts`() {
        val plan = YouTubeEmbed.attempts(listOf("a", "", "a", "  ".trim(), "b"))
        assertEquals(listOf("a", "a", "b", "b"), plan.map { it.videoId })
    }

    // ── choosing what to try next ──────────────────────────────────────

    @Test
    fun `a refusal that might be the host is retried on the other host`() {
        // Error 5 is the one from the report. It is the HTML5 player error, and
        // YouTube returns it for an embed it has decided not to serve as
        // readily as for a codec it cannot decode — so it is worth a retry.
        val plan = YouTubeEmbed.attempts(listOf("one", "two"))
        assertEquals(1, YouTubeEmbed.nextAttempt(0, plan, code = 5))
    }

    @Test
    fun `an upload that will never embed is not asked twice`() {
        val plan = YouTubeEmbed.attempts(listOf("one", "two"))
        // 101 and 150 are "the owner disallows embedding", which is a property
        // of the video and true on both hosts.
        assertEquals(2, YouTubeEmbed.nextAttempt(0, plan, code = 150))
        assertEquals(2, YouTubeEmbed.nextAttempt(0, plan, code = 101))
        // 100 is "removed", 2 is "bad id" — same reasoning.
        assertEquals(2, YouTubeEmbed.nextAttempt(0, plan, code = 100))
        assertEquals(2, YouTubeEmbed.nextAttempt(0, plan, code = 2))
    }

    @Test
    fun `the plan runs out rather than running off the end`() {
        val plan = YouTubeEmbed.attempts(listOf("only"))
        assertEquals(plan.size, YouTubeEmbed.nextAttempt(1, plan, code = 5))
        assertEquals(plan.size, YouTubeEmbed.nextAttempt(0, plan, code = 150))
        // An index nobody should pass, answered rather than thrown on.
        assertEquals(plan.size, YouTubeEmbed.nextAttempt(99, plan, code = 5))
    }

    @Test
    fun `a permanent failure on the last upload ends the plan`() {
        val plan = YouTubeEmbed.attempts(listOf("one", "two"))
        assertEquals(plan.size, YouTubeEmbed.nextAttempt(2, plan, code = 150))
    }

    // ── saying what happened ───────────────────────────────────────────

    @Test
    fun `error five is not blamed on the device`() {
        // The exact wording that sent a tester looking for a fault on their own
        // phone that was not there.
        val reason = YouTubeEmbed.errorReason(5)
        assertFalse(
            "code 5 must not be reported as a device fault: $reason",
            reason.contains("device", ignoreCase = true) ||
                reason.contains("browser engine", ignoreCase = true),
        )
    }

    @Test
    fun `a player that never loaded is reported as its own thing`() {
        // Distinct from YouTube's numbering because it is not YouTube's error —
        // the script did not arrive, which is the connection.
        val reason = YouTubeEmbed.errorReason(YouTubeEmbed.ERR_NO_PLAYER)
        assertTrue(reason.contains("connection", ignoreCase = true))
        assertTrue(
            "no point working through the other uploads when the API is unreachable",
            YouTubeEmbed.isTerminal(YouTubeEmbed.ERR_NO_PLAYER),
        )
        assertFalse(YouTubeEmbed.isTerminal(5))
        assertFalse(YouTubeEmbed.isTerminal(150))
    }

    @Test
    fun `every code says something`() {
        for (code in listOf(YouTubeEmbed.ERR_NO_PLAYER, 2, 5, 100, 101, 150, 999)) {
            assertTrue("code $code has no reason", YouTubeEmbed.errorReason(code).isNotBlank())
        }
    }

    // ── reading an id out of a link ────────────────────────────────────

    @Test
    fun `a highlights link yields the id it plays`() {
        // TheSportsDB publishes highlights as links, in every shape YouTube
        // has ever used. Each one used to be handed to an intent that left the
        // app; the id is what lets them play inside it.
        val id = "dQw4w9WgXcQ"
        for (url in listOf(
            "https://www.youtube.com/watch?v=$id",
            "http://youtube.com/watch?v=$id&t=42s",
            "https://www.youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id",
            "https://youtu.be/$id?t=90",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/v/$id",
            "www.youtube.com/watch?v=$id",
            "//www.youtube.com/watch?v=$id",
        )) {
            assertEquals("failed on $url", id, YouTubeEmbed.videoIdFrom(url))
        }
    }

    @Test
    fun `a link with no video in it yields nothing`() {
        assertEquals(null, YouTubeEmbed.videoIdFrom(""))
        assertEquals(null, YouTubeEmbed.videoIdFrom("https://www.youtube.com/"))
        assertEquals(null, YouTubeEmbed.videoIdFrom("https://www.youtube.com/@somechannel"))
        assertEquals(null, YouTubeEmbed.videoIdFrom("https://example.com/clip.mp4"))
        // Eleven characters is the id length; anything shorter is not one.
        assertEquals(null, YouTubeEmbed.videoIdFrom("https://youtu.be/short"))
    }

    @Test
    fun `a direct media file is not mistaken for YouTube`() {
        assertTrue(YouTubeEmbed.isYouTube("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YouTubeEmbed.isYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(YouTubeEmbed.isYouTube("https://cdn.example.com/highlights/final.mp4"))
    }
}
