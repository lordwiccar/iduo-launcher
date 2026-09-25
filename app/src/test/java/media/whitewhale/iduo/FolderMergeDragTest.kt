package media.whitewhale.iduo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class FolderMergeDragTest {
    private val cell = Rect(100f, 200f, 200f, 300f)
    private val owner = Any()

    private fun dragging(source: DragRegion, vararg targets: DragRegion) = HomeDragState().apply {
        targets.forEach { register(owner, it) }
        this.source = source; moved = true
    }

    @Test fun `only the icon centred part of a cell starts a folder`() {
        val zone = folderMergeZone(cell)
        assertTrue(zone.contains(cell.center.copy(y = cell.top + 30f)))
        assertFalse(zone.contains(Offset(cell.left + 5f, cell.top + 30f)))
        assertFalse(zone.contains(Offset(cell.right - 5f, cell.top + 30f)))
        assertFalse(zone.contains(Offset(cell.center.x, cell.bottom - 10f)))
    }

    @Test fun `a dragged app groups with another app or folder but not itself or from inside a folder`() {
        val over = Offset(150f, 230f)
        val target = DragRegion(DropTarget.Home(3), cell, "b", 0)
        val fromHome = DragRegion(DropTarget.Home(0), Rect.Zero, "a", 0)
        assertEquals(target, dragging(fromHome, target).mergeCandidate(over, setOf(0)))
        assertNull(dragging(fromHome, target).mergeCandidate(over, setOf(1)))
        assertNull(dragging(fromHome, target.copy(appId = "a")).mergeCandidate(over, setOf(0)))
        val folder = target.copy(appId = newFolderId())
        assertEquals(folder, dragging(fromHome, folder).mergeCandidate(over, setOf(0)))
        assertNull(dragging(fromHome, target.copy(appId = null)).mergeCandidate(over, setOf(0)))
        assertNull(dragging(fromHome.copy(folderId = "folder:y"), target).mergeCandidate(over, setOf(0)))
        assertNull(dragging(DragRegion(DropTarget.Widget(1), Rect.Zero, null, 0, widgetId = 5), target)
            .mergeCandidate(over, setOf(0)))
    }

    @Test fun `clearing a drag forgets the merge hover`() {
        val drag = HomeDragState().apply { mergeIndex = 3; mergeArmed = true }
        drag.clear()
        assertNull(drag.mergeIndex); assertFalse(drag.mergeArmed)
    }

    @Test fun `dropping a dock app on a Home app replaces the target with a folder of both`() {
        val layout = HomeLayout(listOf("x", "b"), listOf("a", null, null, null))
        val folder = FolderEntry(newFolderId(), "Folder", emptyList())
        val merged = createFolder(layout, folder, "a", "b", 1)
        assertEquals(folder.id, merged.slotAt(1))
        assertEquals(listOf("a", "b"), merged.folder(folder.id)!!.appIds)
        assertFalse("a" in merged.dock)
        assertEquals("x", merged.slotAt(0))
    }
}
