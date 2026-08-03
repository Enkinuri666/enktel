package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.repo.PlaylistRepository.Companion.normalizeServer

/**
 * Server-URL normalisation. Every failure mode here surfaces to the user as
 * "the panel rejected the credentials", so the rules are worth pinning:
 * an explicit scheme always wins, a bare hostname defaults to HTTPS, a bare
 * host:port on a non-standard port defaults to HTTP, and whatever path or
 * query the reseller pasted gets stripped.
 */
class PlaylistRepositoryTest {

    @Test fun `an explicit scheme is respected`() {
        assertEquals("http://username.eg4k-pass.my:80", normalizeServer("http://username.eg4k-pass.my:80"))
        assertEquals("https://x-api.cc", normalizeServer("https://x-api.cc"))
    }

    @Test fun `a bare hostname defaults to https`() {
        assertEquals("https://x-api.cc", normalizeServer("x-api.cc"))
    }

    @Test fun `a bare host with a non-standard port defaults to http`() {
        assertEquals("http://panel.example.com:8080", normalizeServer("panel.example.com:8080"))
    }

    @Test fun `a bare host on 443 stays https`() {
        assertEquals("https://panel.example.com:443", normalizeServer("panel.example.com:443"))
    }

    @Test fun `api paths are stripped`() {
        assertEquals("https://x-api.cc", normalizeServer("https://x-api.cc/player_api.php"))
        assertEquals("https://x-api.cc", normalizeServer("https://x-api.cc/panel_api.php"))
        assertEquals("https://x-api.cc", normalizeServer("https://x-api.cc/xmltv.php"))
    }

    @Test fun `a full get_php playlist URL reduces to the host`() {
        assertEquals(
            "https://x-api.cc",
            normalizeServer("https://x-api.cc/get.php?username=u&password=p&type=m3u_plus"),
        )
    }

    @Test fun `trailing slashes and surrounding whitespace are trimmed`() {
        assertEquals("https://x-api.cc", normalizeServer("  https://x-api.cc/  "))
    }
}
