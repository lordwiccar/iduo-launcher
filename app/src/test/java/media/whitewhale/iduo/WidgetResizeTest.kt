package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class WidgetResizeTest {
    private val line = { row: Int -> if (row <= 2) row * 100f else 200f + (row - 2) * 80f }
    private fun drag(start: WidgetFootprint, corner: ResizeCorner, dx: Float, dy: Float) =
        draggedFootprint(start, corner, dx, dy, 90f, line, columns = 4, rows = 6)

    @Test fun `each corner moves only its own edges`() {
        val start = WidgetFootprint(1, 2, 2, 2)
        assertEquals(WidgetFootprint(1, 2, 3, 3), drag(start, ResizeCorner.BOTTOM_RIGHT, 90f, 80f))
        assertEquals(WidgetFootprint(0, 1, 3, 3), drag(start, ResizeCorner.TOP_LEFT, -90f, -100f))
        assertEquals(WidgetFootprint(1, 3, 3, 1), drag(start, ResizeCorner.TOP_RIGHT, 90f, 80f))
        assertEquals(WidgetFootprint(0, 2, 3, 1), drag(start, ResizeCorner.BOTTOM_LEFT, -90f, -80f))
    }

    @Test fun `dragging past the widget leaves one cell and past the grid stops at its edge`() {
        val start = WidgetFootprint(1, 2, 2, 2)
        assertEquals(WidgetFootprint(1, 2, 1, 1), drag(start, ResizeCorner.BOTTOM_RIGHT, -900f, -900f))
        assertEquals(WidgetFootprint(2, 3, 1, 1), drag(start, ResizeCorner.TOP_LEFT, 900f, 900f))
        assertEquals(WidgetFootprint(1, 2, 3, 4), drag(start, ResizeCorner.BOTTOM_RIGHT, 900f, 900f))
    }

    @Test fun `rows snap to their own heights`() {
        val start = WidgetFootprint(0, 0, 2, 2)
        assertEquals(2, drag(start, ResizeCorner.BOTTOM_RIGHT, 0f, 39f).spanY)
        assertEquals(3, drag(start, ResizeCorner.BOTTOM_RIGHT, 0f, 41f).spanY)
        assertEquals(1, drag(start, ResizeCorner.BOTTOM_RIGHT, 0f, -51f).spanY)
    }

    private val widget = WidgetPlacement(0, 100, 0, 0, 0, 2, 2)
    private fun cell(row: Int, column: Int) = row * GRID_COLUMNS + column

    @Test fun `apps in the way move aside and none is lost`() {
        val slots = MutableList<String?>(cell(2, 2) + 1) { null }
        slots[cell(2, 0)] = "a"; slots[cell(2, 1)] = "b"; slots[cell(2, 2)] = "c"
        val after = setWidgetFootprint(HomeLayout(slots, emptyList(), listOf(widget)), 0, WidgetFootprint(0, 0, 2, 3))
        assertEquals(WidgetFootprint(0, 0, 2, 3), after.placement(0)!!.run { WidgetFootprint(column, row, spanX, spanY) })
        assertEquals(setOf("a", "b", "c"), after.slots.filterNotNull().toSet())
        val covered = after.placement(0)!!.coveredIndices()
        assertTrue(after.slots.indices.none { after.slots[it] != null && it in covered })
        assertEquals("c", after.slotAt(cell(2, 2)))
    }

    @Test fun `a widget can grow left and up into free cells`() {
        val moved = WidgetPlacement(0, 100, 0, 2, 2, 1, 1)
        val after = setWidgetFootprint(HomeLayout(emptyList(), emptyList(), listOf(moved)), 0, WidgetFootprint(1, 1, 2, 2))
        assertEquals(WidgetPlacement(0, 100, 0, 1, 1, 2, 2), after.placement(0))
    }

    @Test fun `another widget or the grid edge refuses the size`() {
        val other = WidgetPlacement(1, 101, 0, 2, 0, 2, 2)
        val before = HomeLayout(emptyList(), emptyList(), listOf(widget, other))
        assertSame(before, setWidgetFootprint(before, 0, WidgetFootprint(0, 0, 3, 2)))
        assertSame(before, setWidgetFootprint(before, 0, WidgetFootprint(0, 0, 5, 2)))
        assertSame(before, setWidgetFootprint(before, 0, WidgetFootprint(0, 0, 2, DEFAULT_HOME_ROWS + 1)))
    }

    @Test fun `a one row page keeps what it held`() {
        val slots = List(cell(5, 3) + 1) { index -> if (index >= cell(2, 0) && index % GRID_COLUMNS < 4) "app$index" else null }
        val before = HomeLayout(slots, emptyList(), DEFAULT_WIDGET_PLACEMENTS)
        val after = resizeHomeRows(before, MIN_HOME_ROWS)
        assertEquals(MIN_HOME_ROWS, after.rows)
        assertEquals(slots.filterNotNull().toSet(), after.slots.filterNotNull().toSet())
        assertTrue(after.slots.indices.all { after.slots[it] == null || after.cellVisible(it) })
        assertEquals(MIN_HOME_ROWS, after.requiredRows())
    }

    @Test fun `a one row page still has its widget band and row`() {
        val g = homeGeometry(475f, 800f, LayoutPreset(), true, homeRows = MIN_HOME_ROWS)
        assertTrue(g.gridFits)
        assertEquals(homeGeometry(475f, 800f, LayoutPreset(), true).rowHeight, g.rowHeight, .01f)
    }
}
