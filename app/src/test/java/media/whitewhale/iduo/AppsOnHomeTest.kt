package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsOnHomeTest {
    private fun layout(vararg placed: Pair<Int, String>, rows: Int = DEFAULT_HOME_ROWS) = HomeLayout(
        slots = List((placed.maxOfOrNull { it.first } ?: -1) + 1) { index -> placed.firstOrNull { it.first == index }?.second },
        dock = listOf("dock", null, null, null), rows = rows)

    @Test fun startsOnANewPageAfterTheLastUsedOne() {
        val result = appendHomeApps(layout(0 to "a", 5 to "b"), listOf("c", "d"), onNewPage = true)
        assertEquals("c", result.slotAt(homeCellIndex(1, 0)))
        assertEquals("d", result.slotAt(homeCellIndex(1, 1)))
        assertEquals(2, result.pageCount)
    }

    @Test fun newAppsJoinTheLastPage() {
        val result = appendHomeApps(layout(0 to "a", 5 to "b"), listOf("c"), onNewPage = false)
        assertEquals("c", result.slotAt(6))
        assertEquals(1, result.pageCount)
    }

    @Test fun skipsAppsAlreadyPlacedAnywhere() {
        val start = layout(0 to "a").copy(folders = listOf(FolderEntry("folder:1", "F", listOf("inFolder", "other"))))
        val result = appendHomeApps(start, listOf("a", "dock", "inFolder", "new"), onNewPage = false)
        assertEquals(listOf("a", "new"), result.slots.filterNotNull())
    }

    @Test fun skipsHiddenRowsAndFillsFurtherPages() {
        val rows = DEFAULT_HOME_ROWS
        val visible = rows * DEFAULT_HOME_COLUMNS
        val ids = List(visible + 2) { "app$it" }
        val result = appendHomeApps(layout(), ids, onNewPage = true)
        // An empty Home starts on its first page; the rows below the visible ones stay empty.
        assertEquals("app0", result.slotAt(0))
        assertTrue((0 until HOME_CELLS).filter { !result.cellVisible(it) }.all { result.slotAt(it) == null })
        assertEquals("app$visible", result.slotAt(homeCellIndex(1, 0)))
        assertEquals(2, result.pageCount)
    }

    @Test fun turningOffTakesOnlyTheAddedAppsOffHome() {
        val start = layout(0 to "a").copy(folders = listOf(FolderEntry("folder:1", "F", listOf("moved", "other"))))
        val added = appendHomeApps(start, listOf("b", "c"), onNewPage = true)
        val after = removeHomeApps(added, setOf("b", "c", "moved"))
        // The pages On Home added disappear with their apps; an app moved into a folder stays there.
        assertEquals(listOf("a"), after.slots)
        assertEquals(1, after.pageCount)
        assertEquals(start.folders, after.folders)
        assertEquals(start.dock, after.dock)
    }

    @Test fun nothingToAddKeepsTheLayout() {
        val start = layout(0 to "a")
        assertSame(start, appendHomeApps(start, listOf("a"), onNewPage = true))
    }
}
