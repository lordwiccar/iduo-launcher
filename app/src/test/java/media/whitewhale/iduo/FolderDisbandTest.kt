package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class FolderDisbandTest {
    private val folder = FolderEntry(newFolderId(), "Folder", listOf("a", "b", "c"))

    @Test fun `ungrouping puts the first app in the folder's cell and the rest in the next free cells`() {
        val layout = HomeLayout(listOf("x", folder.id, "y"), emptyList(), folders = listOf(folder))
        val after = disbandFolder(layout, folder.id)
        assertNull(after.folder(folder.id))
        assertEquals("a", after.slotAt(1))
        assertEquals(listOf("x", "a", "y", "b", "c"), after.slots.take(5))
    }

    @Test fun `ungrouping skips widgets and hidden rows and spills onto the next page`() {
        val full = List(DEFAULT_HOME_ROWS * GRID_COLUMNS) { if (it == 9) folder.id else "app$it" }
        val widget = WidgetPlacement(1, 100, 0, 0, 0, 2, 2)
        val layout = HomeLayout(full.mapIndexed { i, id -> id.takeUnless { i in widget.coveredIndices() } }, emptyList(),
            widgetPlacements = listOf(widget), folders = listOf(folder))
        val after = disbandFolder(layout, folder.id)
        assertEquals("a", after.slotAt(9))
        assertEquals(HOME_CELLS, after.indexOfShortcut("b"))
        assertEquals(HOME_CELLS + 1, after.indexOfShortcut("c"))
        assertTrue(after.unavailableCells().none { after.slotAt(it) != null })
    }

    @Test fun `a folder on the unfolded-only page ungroups there first`() {
        val leading = List(HOME_CELLS) { if (it == 0) folder.id else null }
        val layout = HomeLayout(emptyList(), emptyList(), folders = listOf(folder), leadingSlots = leading)
        val after = disbandFolder(layout, folder.id)
        assertEquals(listOf("a", "b", "c"), (0 until 3).map { after.slotAt(homeCellIndex(-1, it)) })
    }

    @Test fun `an unknown folder leaves the layout unchanged`() {
        val layout = HomeLayout(listOf("x"), emptyList())
        assertSame(layout, disbandFolder(layout, newFolderId()))
    }
}
