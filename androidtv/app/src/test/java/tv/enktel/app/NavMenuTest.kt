package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.ui.components.FULL_ITEMS
import tv.enktel.app.ui.components.MORE_ITEM
import tv.enktel.app.ui.components.SIMPLE_ITEMS
import tv.enktel.app.ui.components.hiddenInSimpleMenu
import tv.enktel.app.ui.components.navItemsFor

/**
 * The short menu must not lose anything.
 *
 * Simple mode is on by default, so a destination that falls out of both the
 * rail and the More grid is one that, for most users, does not exist. That is
 * a silent failure — no crash, no log, just a feature nobody can find — and
 * the kind that survives a release.
 */
class NavMenuTest {

    @Test
    fun `every destination is reachable in simple mode`() {
        val reachable = (SIMPLE_ITEMS + hiddenInSimpleMenu()).map { it.id }.toSet()
        val missing = FULL_ITEMS.map { it.id }.toSet() - reachable
        assertTrue("unreachable in simple mode: $missing", missing.isEmpty())
    }

    @Test
    fun `the more grid is exactly what the rail left out`() {
        val shown = SIMPLE_ITEMS.map { it.id }.toSet()
        val hidden = hiddenInSimpleMenu().map { it.id }
        assertTrue("More repeats a rail item: ${hidden.filter { it in shown }}", hidden.none { it in shown })
        assertEquals(FULL_ITEMS.size, shown.size + hidden.size)
    }

    @Test
    fun `simple mode is shorter than full, and not by one`() {
        // Guards the case where someone adds to SIMPLE_ITEMS until the two are
        // the same list and the mode stops meaning anything.
        assertTrue(
            "simple menu has ${navItemsFor(true).size} entries against ${FULL_ITEMS.size}",
            navItemsFor(true).size <= FULL_ITEMS.size / 2 + 1,
        )
    }

    @Test
    fun `the full menu is unchanged by the preference`() {
        assertEquals(FULL_ITEMS, navItemsFor(false))
    }

    @Test
    fun `more is last, and only in simple mode`() {
        assertEquals(MORE_ITEM, navItemsFor(true).last())
        assertTrue(navItemsFor(false).none { it.id == MORE_ITEM.id })
    }

    @Test
    fun `the essentials stay on the rail`() {
        // These six are the claim simple mode makes. Changing the claim is
        // fine; changing it by accident is not.
        val ids = SIMPLE_ITEMS.map { it.id }
        assertEquals(listOf("home", "live", "movies", "series", "sports", "search"), ids)
    }

    @Test
    fun `no destination appears twice in either menu`() {
        for (menu in listOf(navItemsFor(true), navItemsFor(false))) {
            val ids = menu.map { it.id }
            assertEquals("duplicate in $ids", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `every menu entry routes somewhere`() {
        for (item in FULL_ITEMS + MORE_ITEM) {
            assertTrue("${item.id} has no route", item.route.isNotBlank())
            assertTrue("${item.id} has no label", item.label.isNotBlank())
        }
    }
}
