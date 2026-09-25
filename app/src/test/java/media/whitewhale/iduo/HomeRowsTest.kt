package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class HomeRowsTest {
    private fun cell(page: Int, row: Int, column: Int) = homeCellIndex(page, row * GRID_COLUMNS + column)

    @Test fun `legacy six row indices keep their page row and column`() {
        assertEquals(cell(0, 5, 3), upgradeLegacyCellIndex(23))
        assertEquals(cell(1, 0, 0), upgradeLegacyCellIndex(24))
        assertEquals(cell(2, 2, 1), upgradeLegacyCellIndex(2 * LEGACY_HOME_CELLS + 9))
        val upgraded = upgradeLegacySlots(listOf("a", null) + List(21) { null } + listOf("z", "next"))
        assertEquals("a", upgraded[0]); assertEquals("z", upgraded[cell(0, 5, 3)]); assertEquals("next", upgraded[cell(1, 0, 0)])
        assertEquals(HOME_CELLS, upgradeLegacyLeadingSlots(List(LEGACY_HOME_CELLS) { null }).size)
    }

    @Test fun `legacy overflow panels move below the stored grid and ordinary widgets stay`() {
        val overflow = WidgetPlacement(5, 202, 1, 0, LEGACY_GRID_ROWS, 4, 4)
        assertEquals(GRID_ROWS, upgradeLegacyPlacement(overflow).row)
        val ordinary = WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 4, 2, 2)
        assertEquals(ordinary, upgradeLegacyPlacement(ordinary))
    }

    @Test fun `older backups are checked against the six rows they were written with`() {
        val tall = WidgetPlacement(1, NEEDS_BINDING_WIDGET, 0, 0, 0, 2, 7)
        assertFalse(validBackupPlacement(tall, LEGACY_GRID_ROWS))
        assertTrue(validBackupPlacement(tall))
        val legacyOverflow = WidgetPlacement(5, NEEDS_BINDING_WIDGET, 1, 0, LEGACY_GRID_ROWS, 4, 4)
        assertTrue(validBackupPlacement(legacyOverflow, LEGACY_GRID_ROWS))
    }

    @Test fun `hidden rows reject shortcuts and widgets until they are shown`() {
        val six = HomeLayout(listOf("a"), emptyList())
        val hidden = cell(0, 6, 0)
        assertSame(six, dropApp(six, "b", DropTarget.Home(hidden)))
        assertNull(widgetCandidate(six, 1, cell(0, 5, 0), 2, 2))
        assertTrue(hidden in six.unavailableCells())
        val eight = resizeHomeRows(six, GRID_ROWS)
        assertEquals("b", dropApp(eight, "b", DropTarget.Home(hidden)).slotAt(hidden))
        assertNotNull(widgetCandidate(eight, 1, cell(0, 5, 0), 2, 2))
    }

    @Test fun `new pins skip hidden rows`() {
        val six = HomeLayout((0 until DEFAULT_HOME_ROWS * GRID_COLUMNS).map { "app$it" }, emptyList())
        val pinned = pinHomeApp(six.slots, "new", true, six.unavailableCells().filterTo(mutableSetOf()) { it >= 0 })
        assertEquals(HOME_CELLS, pinned.indexOf("new"))
    }

    @Test fun `showing fewer rows moves shortcuts and widgets into visible free space`() {
        val widget = WidgetPlacement(3, 100, 0, 0, 6, 2, 2)
        val before = HomeLayout(
            slots = List(cell(0, 7, 3) + 1) { index -> when (index) { 0 -> "a"; cell(0, 6, 3) -> "b"; cell(0, 7, 3) -> "c"; else -> null } },
            dock = emptyList(), widgetPlacements = listOf(widget),
            leadingSlots = List(HOME_CELLS) { if (it == cell(-1, 7, 0) - homeCellIndex(-1, 0)) "l" else null },
            rows = GRID_ROWS)
        val after = resizeHomeRows(before, DEFAULT_HOME_ROWS)
        assertEquals(DEFAULT_HOME_ROWS, after.rows)
        assertEquals(DEFAULT_HOME_ROWS, after.requiredRows())
        val kept = (after.slots + after.leadingSlots).filterNotNull().toSet()
        assertEquals(setOf("a", "b", "c", "l"), kept)
        val moved = after.placement(3)!!
        assertTrue(moved.row + moved.spanY <= DEFAULT_HOME_ROWS)
        assertEquals(2, moved.spanY)
        assertEquals("a", after.slotAt(0))
        assertTrue(after.leadingSlots.indexOf("l") < DEFAULT_HOME_ROWS * GRID_COLUMNS)
    }

    @Test fun `a widget taller than the smaller grid is shortened rather than dropped`() {
        val tall = WidgetPlacement(7, 100, 0, 0, 0, 4, GRID_ROWS)
        val after = resizeHomeRows(HomeLayout(emptyList(), emptyList(), listOf(tall), rows = GRID_ROWS), DEFAULT_HOME_ROWS)
        assertEquals(DEFAULT_HOME_ROWS, after.placement(7)!!.spanY)
    }

    @Test fun `required rows grow to cover saved content`() {
        assertEquals(DEFAULT_HOME_ROWS, HomeLayout(listOf("a"), emptyList()).requiredRows())
        assertEquals(GRID_ROWS, HomeLayout(List(cell(1, 7, 0) + 1) { if (it == cell(1, 7, 0)) "a" else null }, emptyList()).requiredRows())
    }

    @Test fun `two extra rows push the grid up and report whether they fit`() {
        val six = homeGeometry(411f, 960f, LayoutPreset(), true)
        val eight = homeGeometry(411f, 960f, LayoutPreset(), true, homeRows = GRID_ROWS)
        assertTrue(eight.contentTop <= six.contentTop)
        assertTrue(six.gridFits && eight.gridFits)
        assertFalse(homeGeometry(411f, 700f, LayoutPreset(), true, homeRows = GRID_ROWS).gridFits)
    }
}
