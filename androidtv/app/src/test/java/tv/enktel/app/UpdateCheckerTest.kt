package tv.enktel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.update.UpdateChecker

/**
 * The comparison carries one responsibility: does the release feed carry
 * something newer than this build? Getting it wrong either nags everybody
 * into reinstalling what they already have (false positive), or lets a
 * whole release slide past them (false negative), so the common and
 * uncommon shapes both get a case.
 */
class UpdateCheckerTest {

    @Test
    fun `later patch wins`() {
        assertTrue(UpdateChecker.isNewer(current = "1.84.2", latest = "1.84.3"))
    }

    @Test
    fun `later minor wins`() {
        assertTrue(UpdateChecker.isNewer(current = "1.84.9", latest = "1.85.0"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(UpdateChecker.isNewer(current = "1.85.0", latest = "1.85.0"))
    }

    @Test
    fun `older release does not nag`() {
        assertFalse(UpdateChecker.isNewer(current = "1.85.0", latest = "1.84.2"))
    }

    @Test
    fun `mobile suffix on current build is ignored`() {
        // The mobile flavour carries "-mobile" in versionName; the release
        // tag never does. A naive string comparison would then permanently
        // find mobile "behind" tv and nag on every open.
        assertFalse(UpdateChecker.isNewer(current = "1.85.0-mobile", latest = "1.85.0"))
        assertTrue(UpdateChecker.isNewer(current = "1.85.0-mobile", latest = "1.86.0"))
    }

    @Test
    fun `double-digit minor beats single-digit`() {
        // The one place a lexical compare goes wrong first.
        assertTrue(UpdateChecker.isNewer(current = "1.9.0", latest = "1.10.0"))
    }

    @Test
    fun `malformed tag cannot trigger a nag`() {
        assertFalse(UpdateChecker.isNewer(current = "1.85.0", latest = "weekly-build"))
        assertFalse(UpdateChecker.isNewer(current = "", latest = "1.85.0"))
    }
}
