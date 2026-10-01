package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLayoutTest {
    private val folder = FolderEntry("folder:00000000-0000-0000-0000-000000000001", "Pair", listOf("f1", "f2"))
    private val inner = HomeLayout(
        slots = List<String?>(GRID_COLUMNS * 2) { null } + listOf("a", folder.id, null, "b"),
        dock = listOf("d", null, null, null),
        widgetPlacements = listOf(
            WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2),
            WidgetPlacement(1, 42, 1, 0, 0, 2, 2),
            WidgetPlacement(2, 43, 1, 2, 0, 2, 2),
            WidgetPlacement(3, DATE_WIDGET, -1, 0, 0, 2, 2),
        ),
        folders = listOf(folder),
        leadingSlots = List(HOME_CELLS) { if (it == 5) "leading" else null },
        rows = 7,
    )

    private fun restore(provider: String) = WidgetRestore(0, provider, 0, "Weather", "Personal")

    @Test fun `the copy keeps apps folders rows and built-in widgets`() {
        val cover = coverLayoutFrom(inner) { restore("com.example/.Weather") }
        assertEquals(inner.slots, cover.slots)
        assertEquals(inner.folders, cover.folders)
        assertEquals(7, cover.rows)
        assertEquals(DEFAULT_HOME_COLUMNS, cover.columns)
        assertEquals(inner.placement(0), cover.placement(0))
    }

    @Test fun `the unfolded-only page stays behind`() {
        val cover = coverLayoutFrom(inner) { null }
        assertTrue(cover.leadingSlots.all { it == null })
        assertNull(cover.placement(3))
    }

    @Test fun `android widgets become placeholders for the same provider`() {
        val cover = coverLayoutFrom(inner) { placement -> if (placement.id == 42) restore("com.example/.Weather") else null }
        assertEquals(inner.placement(1)!!.copy(id = NEEDS_BINDING_WIDGET), cover.placement(1))
        assertEquals(WidgetRestore(1, "com.example/.Weather", 0, "Weather", "Personal"), cover.widgetRestore(1))
        // A widget that cannot be described is left out rather than shown twice.
        assertNull(cover.placement(2))
        assertEquals(setOf(1), cover.widgetRestores.map { it.slot }.toSet())
    }

    @Test fun `placeholders already waiting keep their description`() {
        val waiting = WidgetRestore(5, "com.example/.Notes", 0, "Notes", "Personal")
        val withPlaceholder = inner.copy(widgetPlacements = listOf(WidgetPlacement(5, NEEDS_BINDING_WIDGET, 0, 0, 2, 2, 2)),
            widgetRestores = listOf(waiting))
        val cover = coverLayoutFrom(withPlaceholder) { null }
        assertEquals(withPlaceholder.placement(5), cover.placement(5))
        assertEquals(waiting, cover.widgetRestore(5))
    }
}
