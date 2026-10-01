package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DockFoldersTest {
    private val folder = FolderEntry("folder:00000000-0000-0000-0000-000000000001", "Pair", listOf("a", "b"))
    private val home = HomeLayout(fourColumns(null, null, null, null, null, null, null, null, folder.id, "c"),
        listOf("d", null, null, null), folders = listOf(folder))

    @Test fun `a folder moves into one dock position and back to Home`() {
        val docked = dropApp(home, folder.id, DropTarget.Dock(1))
        assertEquals(listOf("d", folder.id, null, null), docked.dock)
        assertNull(docked.indexOfShortcut(folder.id))
        assertEquals(listOf(folder), docked.folders)
        val back = dropApp(docked, folder.id, DropTarget.Home(cell4(8)))
        assertEquals(folder.id, back.slotAt(cell4(8)))
        assertEquals(listOf("d", null, null, null), back.dock)
    }

    @Test fun `a folder does not replace a full dock`() {
        val full = home.copy(dock = listOf("d", "e", "f", "g"))
        assertSame(full, dropApp(full, folder.id, DropTarget.Dock(0)))
    }

    @Test fun `the last app left in a dock folder takes its dock position`() {
        val docked = dropApp(home, folder.id, DropTarget.Dock(1))
        val after = removeAppFromFolder(docked, folder.id, "a", DropTarget.Remove)
        assertEquals(listOf("d", "b", null, null), after.dock)
        assertNull(after.folder(folder.id))
    }

    @Test fun `ungrouping a dock folder keeps its first app in the dock and puts the rest on Home`() {
        val three = folder.copy(appIds = listOf("a", "b", "x"))
        val docked = dropApp(home.copy(folders = listOf(three)), folder.id, DropTarget.Dock(1))
        val after = disbandFolder(docked, folder.id)
        assertEquals(listOf("d", "a", null, null), after.dock)
        assertEquals(setOf("b", "x"), after.slots.filterNotNull().filter { it in setOf("b", "x") }.toSet())
        assertEquals(emptyList<FolderEntry>(), after.folders)
    }

    @Test fun `removing a dock folder's position is refused so its apps are never lost`() {
        val docked = dropApp(home, folder.id, DropTarget.Dock(1))
        assertSame(docked, removePlacement(docked, DropTarget.Dock(1)))
    }
}
