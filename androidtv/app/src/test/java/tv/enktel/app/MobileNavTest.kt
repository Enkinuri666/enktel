package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.mobile.MOBILE_TABS
import tv.enktel.app.ui.mobile.activeTabRoute

/**
 * "Movies and Series are hidden behind the drop-down menu and not on the main
 * navigation next to Live TV, instead huge space taken up by the unused Enki
 * voice icon."
 *
 * The bar is the app's whole table of contents on a phone. What is on it is
 * what the app appears to contain — so a subscription of 200,000 films and
 * 35,000 series that could only be reached by opening a sheet looked like a
 * live-TV app with a menu, which is what was reported.
 */
class MobileNavTest {

    @Test
    fun `the two biggest parts of the catalogue are on the bar`() {
        val routes = MOBILE_TABS.map { it.route }
        assertTrue("Movies must be reachable without opening a menu", "movies" in routes)
        assertTrue("Series must be reachable without opening a menu", "series" in routes)
    }

    @Test
    fun `they sit next to Live TV rather than after everything else`() {
        val routes = MOBILE_TABS.map { it.route }
        val live = routes.indexOf("channels")
        assertEquals("Movies belongs immediately after Live TV", live + 1, routes.indexOf("movies"))
        assertEquals("Series belongs immediately after Movies", live + 2, routes.indexOf("series"))
    }

    @Test
    fun `the voice tile no longer occupies a slot`() {
        assertTrue(
            "the largest element on the bar navigated nowhere",
            MOBILE_TABS.none { it.special == "mic" },
        )
    }

    @Test
    fun `the bar stays within what a phone can carry`() {
        // Six is the practical maximum before the labels stop being legible.
        assertTrue("${MOBILE_TABS.size} tabs is too many", MOBILE_TABS.size <= 6)
    }

    @Test
    fun `a detail page still lights the tab it belongs to`() {
        // A bar that goes blank the moment you open something has stopped
        // telling you where you are.
        assertEquals("movies", activeTabRoute("movie/1:42"))
        assertEquals("movies", activeTabRoute("movies"))
        assertEquals("series", activeTabRoute("seriesDetails/1:42"))
        assertEquals("series", activeTabRoute("series"))
        assertEquals("channels", activeTabRoute("live?ch=1:42"))
        assertEquals("sports", activeTabRoute("matchCenter?event=1"))
    }

    @Test
    fun `what came off the bar is still reachable from the menu`() {
        // Search is an action rather than a section, so it moved — but it must
        // still resolve to the menu rather than to nothing at all.
        assertEquals("__more", activeTabRoute("search"))
        assertEquals("__more", activeTabRoute("settings"))
        assertEquals("__more", activeTabRoute("guide"))
    }
}
