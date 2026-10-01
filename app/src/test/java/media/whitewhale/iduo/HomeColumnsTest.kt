package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeColumnsTest {
    @Test fun `four-column indices keep their row and column in five-column storage`() {
        assertEquals(0, upgradeLegacyCellIndex(0, GRID_ROWS))
        assertEquals(GRID_COLUMNS + 2, upgradeLegacyCellIndex(6, GRID_ROWS))
        assertEquals(HOME_CELLS + 1, upgradeLegacyCellIndex(SCHEMA9_HOME_CELLS + 1, GRID_ROWS))
        // Six-row pages from before schema 9 start their second page after 24 cells.
        assertEquals(HOME_CELLS, upgradeLegacyCellIndex(LEGACY_HOME_CELLS, LEGACY_GRID_ROWS))
        assertEquals(-HOME_CELLS + GRID_COLUMNS, upgradeLegacyCellIndex(-SCHEMA9_HOME_CELLS + 4, GRID_ROWS))
    }

    @Test fun `the unfolded-only page keeps its cells`() {
        val stored = List<String?>(SCHEMA9_HOME_CELLS) { if (it == 5) "a" else null }
        val upgraded = upgradeLegacyLeadingSlots(stored, GRID_ROWS)
        assertEquals(HOME_CELLS, upgraded.size)
        assertEquals("a", upgraded[GRID_COLUMNS + 1])
    }

    @Test fun `a four-column layout hides the fifth column`() {
        val layout = HomeLayout(emptyList(), emptyList())
        assertFalse(layout.cellVisible(4))
        assertTrue(4 in layout.unavailableCells())
        assertTrue(layout.copy(columns = 5).cellVisible(4))
        assertFalse(4 in layout.copy(columns = 5).unavailableCells())
    }

    @Test fun `new apps fill five columns when shown`() {
        val layout = HomeLayout(emptyList(), emptyList(), columns = 5)
        val next = appendHomeApps(layout, List(6) { "app$it" }, onNewPage = false)
        assertEquals(listOf("app0", "app1", "app2", "app3", "app4", "app5"), next.slots)
    }

    @Test fun `hiding the fifth column moves its apps and widgets without losing any`() {
        val widget = WidgetPlacement(0, 100, 0, 3, 2, 2, 2)
        val slots = List<String?>(GRID_COLUMNS * 2) { if (it % GRID_COLUMNS == 4) "edge$it" else null } + listOf("a")
        val before = HomeLayout(slots, emptyList(), listOf(widget), columns = 5)
        val after = resizeHomeColumns(before, 4)
        assertEquals(4, after.columns)
        val placed = after.slots.filterNotNull()
        assertEquals(before.slots.filterNotNull().toSet(), placed.toSet())
        assertEquals(placed.distinct(), placed)
        assertTrue(after.slots.indices.filter { after.slots[it] != null }.all(after::cellVisible))
        val moved = after.placement(0)!!
        assertTrue(moved.column + moved.spanX <= 4)
        assertTrue(after.unavailableCells(exceptSlot = 0).let { blocked -> moved.coveredIndices().none { it in blocked } })
    }

    @Test fun `showing the fifth column changes nothing else`() {
        val before = HomeLayout(listOf("a"), emptyList())
        assertEquals(before.copy(columns = 5), resizeHomeColumns(before, 5))
        assertNull(resizeHomeColumns(before, 5).slotAt(4))
    }
}
