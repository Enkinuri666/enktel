package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.UserAgents

/**
 * Which client the app claims to be, when four levels can each have an opinion.
 *
 * The precedence is the whole feature. A viewer with two lines sets an agent on
 * the strict one; if that leaked to the other, the fix for one provider would
 * break the provider that was working. And a level that answers "" has to mean
 * "no opinion" rather than "send an empty header" — an empty User-Agent is
 * itself a fingerprint some panels reject, so no path through here may produce
 * one.
 */
class UserAgentsTest {

    private val smartTv = UserAgents.SUGGESTIONS.first { it.label == "Smart TV" }.value

    @Test
    fun `nothing set falls back to the app default`() {
        assertEquals(UserAgents.APP_DEFAULT, UserAgents.effective())
    }

    @Test
    fun `the provider's agent beats the global override`() {
        // The point of the feature: one line's workaround must not be imposed
        // on another line that was working.
        assertEquals(
            "TiviMate/4.7.0 (Android)",
            UserAgents.effective(profile = "TiviMate/4.7.0 (Android)", global = smartTv),
        )
    }

    @Test
    fun `a channel's agent beats everything`() {
        // A single channel served from a different CDN is a problem neither the
        // provider nor the device can see.
        assertEquals(
            "Kodi/20.2",
            UserAgents.effective(channel = "Kodi/20.2", profile = smartTv, global = "VLC/1"),
        )
    }

    @Test
    fun `the global override still applies when the provider has no opinion`() {
        assertEquals(smartTv, UserAgents.effective(global = smartTv))
    }

    @Test
    fun `blank means no opinion, at every level`() {
        assertEquals(smartTv, UserAgents.effective(channel = "", profile = "", global = smartTv))
        assertEquals(smartTv, UserAgents.effective(channel = "   ", profile = smartTv))
    }

    @Test
    fun `it never returns an empty agent`() {
        // An empty User-Agent is a fingerprint of its own, and panels in this
        // space reject it — so a blank default must not pass through.
        assertEquals(UserAgents.APP_DEFAULT, UserAgents.effective(default = ""))
        assertEquals(UserAgents.APP_DEFAULT, UserAgents.effective(default = "   "))
        assertTrue(UserAgents.effective().isNotBlank())
    }

    @Test
    fun `whitespace around a typed value is trimmed`() {
        // A trailing space pasted from a forum post is invisible on screen and
        // changes the header.
        assertEquals(smartTv, UserAgents.effective(profile = "  $smartTv  "))
    }

    @Test
    fun `a stored value is matched back to its suggestion`() {
        // So the settings row can show "Smart TV" rather than 90 characters of
        // Tizen.
        assertNotNull(UserAgents.suggestionFor(smartTv))
        assertEquals("Smart TV", UserAgents.suggestionFor(smartTv)?.label)
        assertNull(UserAgents.suggestionFor("SomethingHandTyped/1.0"))
        assertNull(UserAgents.suggestionFor(""))
    }

    @Test
    fun `the source is named so the settings row can explain itself`() {
        // "Set to Smart TV" and "set to Smart TV by the global override rather
        // than by this provider" are different facts, and only the second
        // explains why editing the provider row appears to do nothing.
        assertEquals("the app default", UserAgents.sourceOf())
        assertEquals("the global override", UserAgents.sourceOf(global = smartTv))
        assertEquals("this provider", UserAgents.sourceOf(profile = smartTv, global = "x"))
        assertEquals("this channel", UserAgents.sourceOf(channel = "a", profile = "b", global = "c"))
    }

    @Test
    fun `the suggestions are usable`() {
        val s = UserAgents.SUGGESTIONS
        assertTrue("a list nobody reads is not a shortlist", s.size in 3..12)
        assertTrue("labels must be unique", s.map { it.label }.toSet().size == s.size)
        assertTrue("values must be unique", s.map { it.value }.toSet().size == s.size)
        s.forEach {
            assertTrue("${it.label} has a blank value", it.value.isNotBlank())
            assertTrue("${it.label} has no hint", it.hint.isNotBlank())
            assertEquals("${it.label} has stray whitespace", it.value.trim(), it.value)
        }
        // The default has to be offered, or "put it back" needs typing.
        assertNotNull(UserAgents.suggestionFor(UserAgents.APP_DEFAULT))
    }

    @Test
    fun `the app default here matches the one the client actually sends`() {
        // Two copies of a constant that must not drift: DEFAULT_UA is what the
        // OkHttp interceptor falls back to.
        assertEquals(tv.enktel.app.DEFAULT_UA, UserAgents.APP_DEFAULT)
    }
}
