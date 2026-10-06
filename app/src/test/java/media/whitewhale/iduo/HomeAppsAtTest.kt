package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppsAtTest {
    private val perPage = DEFAULT_HOME_ROWS * DEFAULT_HOME_COLUMNS

    private fun layout(vararg placed: Pair<Int, String>) = HomeLayout(
        slots = List((placed.maxOfOrNull { it.first } ?: -1) + 1) { index -> placed.firstOrNull { it.first == index }?.second },
        dock = listOf("dock", null, null, null))

    @Test fun fillsThePageFromTheHeldCellThenItsEarlierGaps() {
        val start = layout(0 to "a", 2 to "b")
        val result = addHomeAppsAt(start, listOf("c", "d", "e"), from = 3)
        assertEquals("c", result.slotAt(3))
        // Cell 4 lies in the hidden fifth column, so the next free one is the start of row two.
        assertEquals("d", result.slotAt(GRID_COLUMNS))
        assertEquals(1, result.pageCount)
    }

    @Test fun overflowGoesToNewPagesAfterTheLastOne() {
        val start = layout(0 to "a", homeCellIndex(1, 0) to "b")
        val count = perPage - 1 + perPage + 3
        val ids = (1..count).map { "app$it" }
        assertEquals(2, homePagesAddedForApps(start, from = 1, count = count))
        val result = addHomeAppsAt(start, ids, from = 1)
        // Page 0 fills, page 1 keeps its single app, and the rest start pages 2 and 3.
        assertEquals("app${perPage}", result.slotAt(homeCellIndex(2, 0)))
        assertEquals(4, result.pageCount)
        assertEquals("b", result.slotAt(homeCellIndex(1, 0)))
        assertEquals(1, (0 until HOME_CELLS).count { result.slotAt(homeCellIndex(1, it)) != null })
    }

    @Test fun noNewPagesWhileTheHeldPageHasRoom() {
        assertEquals(0, homePagesAddedForApps(layout(0 to "a"), from = 1, count = perPage - 1))
        assertEquals(1, homePagesAddedForApps(layout(0 to "a"), from = 1, count = perPage))
    }

    @Test fun skipsAppsAlreadyOnHomeInTheDockOrInFolders() {
        val start = layout(0 to "a").copy(folders = listOf(FolderEntry("folder:1", "F", listOf("inFolder"))))
        val result = addHomeAppsAt(start, listOf("a", "dock", "inFolder", "new"), from = 1)
        assertEquals(listOf("a", "new"), result.slots.filterNotNull())
    }

    @Test fun widgetsKeepTheirCells() {
        val start = layout().copy(widgetPlacements = listOf(WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2)))
        val result = addHomeAppsAt(start, listOf("x"), from = 0)
        assertEquals("x", result.slotAt(2))
    }
}
