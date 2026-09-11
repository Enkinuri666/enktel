package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.SetupLink
import tv.enktel.app.data.repo.SetupLink.Setup

/**
 * What a subscriber can paste into the setup box.
 *
 * Worth pinning tightly: this is the first thing anyone does with the app, it
 * runs before any network call, and getting it wrong sends someone to a form
 * full of jargon — which is the thing it exists to avoid. Every case here is a
 * shape a real provider actually sends.
 */
class SetupLinkTest {

    @Test
    fun `the m3u link from our own welcome email becomes an xtream line`() {
        val pasted =
            "http://mastercode.hostpp.live:80/get.php?username=z19ci5cbxe9dre" +
                "&password=ibv7ybuqcp3b1f&type=m3u_plus&output=ts"
        val s = SetupLink.parse(pasted) as Setup.Xtream
        assertEquals("http://mastercode.hostpp.live:80", s.server)
        assertEquals("z19ci5cbxe9dre", s.username)
        assertEquals("ibv7ybuqcp3b1f", s.password)
    }

    @Test
    fun `a player_api link carries the same credentials`() {
        val s = SetupLink.parse("http://host.tv:8080/player_api.php?username=u1&password=p1") as Setup.Xtream
        assertEquals("http://host.tv:8080", s.server)
        assertEquals("u1", s.username)
        assertEquals("p1", s.password)
    }

    @Test
    fun `percent-encoded credentials are decoded`() {
        val s = SetupLink.parse(
            "http://host.tv:8080/get.php?username=a%40b.com&password=p%20%26q&type=m3u_plus",
        ) as Setup.Xtream
        assertEquals("a@b.com", s.username)
        assertEquals("p &q", s.password)
    }

    @Test
    fun `the welcome email's credentials block pasted whole`() {
        val pasted = """
            Username:       enktel_demo
            Password:       8fj3kd92
            Server address: http://x-api.cc:8080
            Plan:           3 months, runs until 5 October 2026
        """.trimIndent()
        val s = SetupLink.parse(pasted) as Setup.Xtream
        assertEquals("http://x-api.cc:8080", s.server)
        assertEquals("enktel_demo", s.username)
        assertEquals("8fj3kd92", s.password)
    }

    @Test
    fun `a labelled block with no scheme on the host still resolves`() {
        // Providers write the host bare all the time. normalizeServer's rule
        // applies: a non-standard port means plain HTTP.
        val s = SetupLink.parse("user: bob\npass: hunter2\nserver: panel.example.com:8080") as Setup.Xtream
        assertEquals("http://panel.example.com:8080", s.server)
        assertEquals("bob", s.username)
    }

    @Test
    fun `a bare host asks for the login rather than failing`() {
        val s = SetupLink.parse("http://panel.example.com:8080") as Setup.NeedsCredentials
        assertEquals("http://panel.example.com:8080", s.server)
    }

    @Test
    fun `a bare host plus typed fields is a complete line`() {
        val s = SetupLink.parse(
            "http://panel.example.com:8080", typedUser = "bob", typedPass = "hunter2",
        ) as Setup.Xtream
        assertEquals("bob", s.username)
        assertEquals("hunter2", s.password)
    }

    @Test
    fun `typed fields beat whatever the pasted link carried`() {
        // Someone pasted one line, then corrected the username by hand. The
        // correction is the more recent statement of intent.
        val s = SetupLink.parse(
            "http://host.tv:8080/get.php?username=old&password=stale",
            typedUser = "new", typedPass = "fresh",
        ) as Setup.Xtream
        assertEquals("new", s.username)
        assertEquals("fresh", s.password)
    }

    @Test
    fun `a plain m3u link stays an m3u profile`() {
        val s = SetupLink.parse("https://lists.example.com/playlists/abc123.m3u8") as Setup.M3u
        assertEquals("https://lists.example.com/playlists/abc123.m3u8", s.url)
    }

    @Test
    fun `a playlist link with a typed login is treated as a panel`() {
        // An Xtream profile gets categories, VOD, series and catch-up; a flat
        // playlist does not. If we have a login, use it.
        val s = SetupLink.parse(
            "https://lists.example.com/playlists/abc123.m3u8",
            typedUser = "bob", typedPass = "hunter2",
        )
        assertTrue("expected Xtream, got $s", s is Setup.Xtream)
    }

    @Test
    fun `a url pulled out of surrounding prose loses its punctuation`() {
        val s = SetupLink.parse(
            "Here you go: http://host.tv:8080/get.php?username=u1&password=p1&type=m3u_plus.",
        ) as Setup.Xtream
        assertEquals("p1", s.password)
    }

    @Test
    fun `nothing usable says what to do rather than what failed`() {
        val s = SetupLink.parse("please help") as Setup.Unrecognised
        assertTrue(s.reason, s.reason.contains("Paste"))
        // Never an exception type, a stack trace, or the word "parse".
        assertTrue(s.reason, !s.reason.lowercase().contains("parse"))
    }

    @Test
    fun `empty input asks for the link`() {
        assertTrue(SetupLink.parse("") is Setup.Unrecognised)
        assertTrue(SetupLink.parse("   \n  ") is Setup.Unrecognised)
    }

    @Test
    fun `an xtream shape with no credentials in it asks rather than guesses`() {
        // An edited link, or a panel that names its parameters differently.
        val s = SetupLink.parse("http://host.tv:8080/get.php?type=m3u_plus")
        assertTrue("expected NeedsCredentials, got $s", s is Setup.NeedsCredentials)
    }

    @Test
    fun `the suggested name is the host, not My Playlist`() {
        assertEquals(
            "host.tv",
            SetupLink.suggestedName(SetupLink.parse("http://host.tv:8080/get.php?username=u&password=p")),
        )
        assertEquals(
            "lists.example.com",
            SetupLink.suggestedName(SetupLink.parse("https://lists.example.com/a.m3u8")),
        )
    }

    @Test
    fun `a password containing the word user is not mistaken for a username`() {
        val s = SetupLink.parse(
            "Server: http://host.tv:8080\nUsername: bob\nPassword: myuser2024",
        ) as Setup.Xtream
        assertEquals("bob", s.username)
        assertEquals("myuser2024", s.password)
    }
}
