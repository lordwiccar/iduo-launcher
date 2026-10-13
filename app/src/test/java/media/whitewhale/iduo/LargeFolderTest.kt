package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFolderTest {
    private val folderId = "folder:123e4567-e89b-12d3-a456-426614174000"
    private val folder = FolderEntry(folderId, "Group", listOf("m1", "m2"))

    /** Cell index on page 0 in stored rows of [GRID_COLUMNS]. */
    private fun cell(row: Int, column: Int) = row * GRID_COLUMNS + column

    private fun layoutWith(vararg placed: Pair<Int, String>, columns: Int = DEFAULT_HOME_COLUMNS,
        widgets: List<WidgetPlacement> = emptyList(), large: Boolean = false): HomeLayout {
        val slots = MutableList<String?>(placed.maxOf { it.first } + 1) { null }
        placed.forEach { (index, id) -> slots[index] = id }
        return HomeLayout(slots, listOf(null, null, null, null), widgets, listOf(folder.copy(large = large)), columns = columns)
    }

    private fun shortcuts(layout: HomeLayout) = layout.slots.filterNotNull().sorted()

    @Test fun growsWhereItIsWhenTheCellsAreFree() {
        val next = setFolderLarge(layoutWith(cell(2, 0) to folderId, cell(2, 3) to "a"), folderId, true)
        assertTrue(next.folder(folderId)!!.large)
        assertEquals(cell(2, 0), next.indexOfShortcut(folderId))
        assertTrue(next.unavailableCells().containsAll(setOf(cell(2, 0), cell(2, 1), cell(3, 0), cell(3, 1))))
    }

    @Test fun movesToTheNearestFreeSpaceOnItsPage() {
        val before = layoutWith(cell(2, 0) to folderId, cell(2, 1) to "a", cell(3, 0) to "b")
        val next = setFolderLarge(before, folderId, true)
        val anchor = next.indexOfShortcut(folderId)!!
        assertTrue(largeFolderFits(next, folderId, anchor))
        assertEquals(cell(2, 1), next.indexOfShortcut("a"))
        assertEquals(cell(3, 0), next.indexOfShortcut("b"))
    }

    @Test fun pushesAppsAsideWhenThePageHasNoFreeSpace() {
        // Rows 2 to 5 full except three scattered cells.
        val free = setOf(cell(2, 3), cell(4, 2), cell(5, 0))
        val apps = (2..5).flatMap { row -> (0..3).map { cell(row, it) } }.filterNot { it in free || it == cell(2, 0) }
            .mapIndexed { index, at -> at to "app$index" }
        val before = layoutWith(cell(2, 0) to folderId, *apps.toTypedArray(), widgets = DEFAULT_WIDGET_PLACEMENTS)
        val next = setFolderLarge(before, folderId, true)
        assertTrue(next.folder(folderId)!!.large)
        assertEquals(cell(2, 0), next.indexOfShortcut(folderId))
        assertEquals(shortcuts(before), shortcuts(next))
        listOf(cell(2, 1), cell(3, 0), cell(3, 1)).forEach { assertEquals(null, next.slotAt(it)) }
    }

    @Test fun neverGrowsOverAWidget() {
        // Widgets fill rows 0-1 and 3-5, so row 2 alone has no two by two space.
        val widgets = listOf(WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 4, 2), WidgetPlacement(1, DATE_WIDGET, 0, 0, 3, 4, 3))
        val before = layoutWith(cell(2, 0) to folderId, widgets = widgets)
        assertSame(before, setFolderLarge(before, folderId, true))
    }

    @Test fun shiftedAppsStepOverItsCells() {
        val before = layoutWith(cell(2, 0) to folderId, cell(2, 2) to "a", cell(2, 3) to "b", large = true)
        val next = dropApp(before, "x", DropTarget.Home(cell(2, 2)))
        assertEquals("x", next.slotAt(cell(2, 2)))
        assertEquals("a", next.slotAt(cell(2, 3)))
        assertEquals("b", next.slotAt(cell(3, 2)))
    }

    @Test fun widgetsCannotBePlacedOverIt() {
        val before = layoutWith(cell(2, 0) to folderId, large = true)
        assertEquals(null, widgetCandidate(before, 5, cell(3, 1), 2, 2))
    }

    @Test fun movesWholeOnlyWhereItFits() {
        val before = layoutWith(cell(2, 0) to folderId, cell(4, 3) to "a", large = true)
        assertEquals(cell(2, 2), dropApp(before, folderId, DropTarget.Home(cell(2, 2))).indexOfShortcut(folderId))
        assertSame(before, dropApp(before, folderId, DropTarget.Home(cell(3, 2))))
        // Partly over its own cells is fine.
        assertEquals(cell(3, 1), dropApp(before, folderId, DropTarget.Home(cell(3, 1))).indexOfShortcut(folderId))
    }

    @Test fun becomesSmallInTheDock() {
        val next = dropApp(layoutWith(cell(2, 0) to folderId, large = true), folderId, DropTarget.Dock(1))
        assertEquals(folderId, next.dock[1])
        assertFalse(next.folder(folderId)!!.large)
    }

    @Test fun becomesSmallWhenFewerColumnsLeaveNoRoom() {
        val before = layoutWith(cell(2, 3) to folderId, columns = 5, large = true)
        val next = fitLargeFolders(resizeHomeColumns(before, 4))
        assertFalse(next.folder(folderId)!!.large)
    }
}
