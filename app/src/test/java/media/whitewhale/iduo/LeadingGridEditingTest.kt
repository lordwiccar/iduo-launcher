package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class LeadingGridEditingTest {
    private val L = -HOME_CELLS // first cell of the unfolded-only page
    @Test fun `signed leading addresses round trip without changing normal pages`() {
        assertEquals(-1, homeCellPage(L)); assertEquals(0, homeCellLocal(L))
        assertEquals(-1, homeCellPage(-1)); assertEquals(HOME_CELLS - 1, homeCellLocal(-1))
        assertEquals(L, homeCellIndex(-1, 0)); assertEquals(-1, homeCellIndex(-1, HOME_CELLS - 1))
        assertEquals(0, homeCellIndex(0, 0)); assertEquals(HOME_CELLS, homeCellIndex(1, 0))
        val layout = HomeLayout(listOf("screen1"), emptyList(), leadingSlots = List(HOME_CELLS) { if (it == 23) "leading" else null })
        assertEquals("leading", layout.slotAt(L + 23)); assertEquals(L + 23, layout.indexOfShortcut("leading"))
        assertEquals("screen1", layout.slotAt(0)); assertEquals(1, layout.pageCount)
    }

    @Test fun `leading moves into empty cells directly and occupied insertion stays bounded`() {
        val leading = MutableList<String?>(24) { null }.apply { this[0] = "a"; this[1] = "b"; this[23] = "z" }
        val before = HomeLayout(listOf("home"), emptyList(), leadingSlots = leading)
        val direct = dropApp(before, "a", DropTarget.Home(L + 2))
        assertNull(direct.slotAt(L)); assertEquals("a", direct.slotAt(L + 2)); assertEquals("b", direct.slotAt(L + 1))
        val inserted = dropApp(before, "new", DropTarget.Home(L + 1))
        assertEquals(listOf("a", "new", "b"), listOf(L, L + 1, L + 2).map(inserted::slotAt))
        assertEquals(listOf("home"), inserted.slots)
        val full = HomeLayout(listOf("home"), emptyList(), leadingSlots = List(DEFAULT_HOME_ROWS * GRID_COLUMNS) { "l$it" })
        assertSame(full, dropApp(full, "new", DropTarget.Home(L)))
    }

    @Test fun `cross-surface moves are atomic and never duplicate shortcuts`() {
        val before = HomeLayout(listOf("home"), listOf(null, null, null, null), leadingSlots = List(HOME_CELLS) { null })
        val toLeading = dropApp(before, "home", DropTarget.Home(L))
        assertEquals("home", toLeading.slotAt(L)); assertNull(toLeading.slotAt(0))
        val back = dropApp(toLeading, "home", DropTarget.Home(4))
        assertNull(back.slotAt(L)); assertEquals("home", back.slotAt(4))
        assertEquals(1, (back.leadingSlots + back.slots + back.dock).count { it == "home" })
    }

    @Test fun `leading widgets collide with leading apps using signed cells`() {
        val widget = WidgetPlacement(4, 26, -1, 0, 0, 2, 2)
        val leading = List<String?>(24) { if (it == 2) "app" else null }
        val layout = HomeLayout(emptyList(), emptyList(), listOf(widget), leadingSlots = leading)
        assertEquals(setOf(L, L + 1, L + 4, L + 5), widget.coveredIndices())
        assertNull(widgetCandidate(layout, 5, L, 2, 2))
        assertNull(widgetCandidate(layout, 5, L + 2, 1, 1))
        assertEquals(WidgetPlacement(5, EMPTY_WIDGET, -1, 2, 1, 1, 1), widgetCandidate(layout, 5, L + 6, 1, 1))
        assertEquals(widget.copy(column = 2), moveWidget(layout.copy(leadingSlots = List(HOME_CELLS) { null }), 4, L + 2).placement(4))
    }

    @Test fun `folders create dissolve and transfer on leading surface`() {
        val folder = FolderEntry("folder:00000000-0000-0000-0000-000000000008", "Pair", emptyList())
        val before = HomeLayout(listOf("b"), emptyList(), leadingSlots = List(HOME_CELLS) { if (it == 0) "a" else null })
        val created = createFolder(before, folder, "a", "b", L)
        assertEquals(folder.id, created.slotAt(L)); assertNull(created.slotAt(0))
        val extracted = removeAppFromFolder(created, folder.id, "a", DropTarget.Home(3))
        assertEquals("b", extracted.slotAt(L)); assertEquals("a", extracted.slotAt(3))
        assertTrue(extracted.folders.isEmpty())
    }
}
