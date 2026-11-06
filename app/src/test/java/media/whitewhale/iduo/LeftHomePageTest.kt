package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftHomePageTest {
    private fun left(local: Int) = homeCellIndex(LEFT_HOME_PAGE, local)
    private val layout = HomeLayout(listOf("home"), listOf("dock", null, null, null))

    /** Every place a shortcut can stand, so tests can check it stands in exactly one. */
    private fun HomeLayout.count(id: String) = (slots + leadingSlots + leftSlots + dock).count { it == id }

    @Test fun `cells of the left page are its own`() {
        assertEquals(LEFT_HOME_PAGE, homeCellPage(left(0)))
        assertEquals(7, homeCellLocal(left(7)))
        val next = layout.withSlot(left(3), "a")
        assertEquals("a", next.slotAt(left(3)))
        assertEquals(left(3), next.indexOfShortcut("a"))
        assertNull(next.slotAt(homeCellIndex(-1, 3)))
        assertEquals("a", next.slotsForPage(LEFT_HOME_PAGE)[3])
    }

    @Test fun `an app moves between the left page and every other place, standing in one at a time`() {
        val onLeft = dropApp(layout, "home", DropTarget.Home(left(1)))
        assertEquals("home", onLeft.leftSlots[1])
        assertEquals(1, onLeft.count("home"))

        val onLeading = dropApp(onLeft, "home", DropTarget.Home(homeCellIndex(-1, 2)))
        assertEquals("home", onLeading.leadingSlots[2])
        assertEquals(1, onLeading.count("home"))

        val fromDock = dropApp(onLeft, "dock", DropTarget.Home(left(5)))
        assertEquals("dock", fromDock.leftSlots[5])
        assertEquals(1, fromDock.count("dock"))

        val backHome = dropApp(onLeft, "home", DropTarget.Home(5))
        assertEquals("home", backHome.slots[5])
        assertEquals(1, backHome.count("home"))

        val toDock = dropApp(onLeft, "home", DropTarget.Dock(1))
        assertEquals("home", toDock.dock[1])
        assertEquals(1, toDock.count("home"))
    }

    @Test fun `a full cell makes room on the left page as on any other`() {
        val filled = layout.withSlot(left(0), "a").withSlot(left(1), "b")
        val next = dropApp(filled, "home", DropTarget.Home(left(0)))
        assertEquals(listOf("home", "a", "b"), next.leftSlots.take(3))
    }

    @Test fun `a widget fits on the left page and keeps apps out of its cells`() {
        val draft = widgetCandidate(layout, 7, left(0), 2, 2)
        assertNotNull(draft)
        assertEquals(LEFT_HOME_PAGE, draft!!.page)
        val placed = placeWidget(layout, draft.copy(id = CLOCK_WIDGET))
        assertEquals(CLOCK_WIDGET, placed.placement(7)?.id)
        assertTrue(left(0) in placed.placement(7)!!.coveredIndices())
        assertTrue(left(0) in placed.unavailableCells())
        assertEquals(placed, dropApp(placed, "home", DropTarget.Home(left(0))))
        assertNull(widgetCandidate(placed, 8, left(1), 2, 2))
    }

    @Test fun `a folder stands on the left page and leaves it when ungrouped`() {
        val apps = layout.withSlot(left(0), "a").withSlot(left(1), "b")
        val folder = FolderEntry(newFolderId(), "F", emptyList())
        val grouped = createFolder(apps, folder, "a", "b", left(0))
        assertEquals(folder.id, grouped.leftSlots[0])
        assertEquals(0, grouped.count("a"))
        val ungrouped = disbandFolder(grouped, folder.id)
        assertEquals("a", ungrouped.leftSlots[0])
        assertEquals(1, ungrouped.count("b"))
        assertFalse(ungrouped.folders.any { it.id == folder.id })
    }

    @Test fun `fewer rows and columns never drop what the left page holds`() {
        val tall = layout.copy(rows = GRID_ROWS, columns = GRID_COLUMNS)
            .withSlot(left(7 * GRID_COLUMNS), "low").withSlot(left(GRID_COLUMNS - 1), "wide")
        assertEquals(GRID_ROWS, tall.requiredRows())
        val shorter = resizeHomeRows(tall, DEFAULT_HOME_ROWS)
        assertEquals(1, shorter.count("low"))
        assertTrue(shorter.leftSlots.indexOf("low") < DEFAULT_HOME_ROWS * GRID_COLUMNS)
        val narrower = resizeHomeColumns(shorter, DEFAULT_HOME_COLUMNS)
        assertEquals(1, narrower.count("wide"))
        assertTrue(narrower.leftSlots.indexOf("wide") % GRID_COLUMNS < DEFAULT_HOME_COLUMNS)
    }

    @Test fun `apps already on the left page are not added to Home again`() {
        val placed = layout.withSlot(left(0), "a")
        assertEquals(1, appendHomeApps(placed, listOf("a", "b"), onNewPage = false).count("a"))
        assertEquals(1, addHomeAppsAt(placed, listOf("a", "b"), 0).count("a"))
    }
}
