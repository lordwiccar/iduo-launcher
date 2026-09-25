package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderGridShapeTest {
    @Test fun `small folders grow close to square and wider rather than taller`() {
        assertEquals(FolderGridShape(2, 1, 1), folderGridShape(2, 6, 6))
        assertEquals(FolderGridShape(2, 2, 1), folderGridShape(3, 6, 6))
        assertEquals(FolderGridShape(3, 2, 1), folderGridShape(5, 6, 6))
        assertEquals(FolderGridShape(3, 3, 1), folderGridShape(9, 6, 6))
        assertEquals(FolderGridShape(4, 3, 1), folderGridShape(10, 6, 6))
        assertEquals(FolderGridShape(6, 6, 1), folderGridShape(36, 6, 6))
    }

    @Test fun `apps beyond six by six continue on another page`() {
        assertEquals(FolderGridShape(6, 6, 2), folderGridShape(37, 6, 6))
        assertEquals(FolderGridShape(6, 6, 3), folderGridShape(73, 6, 6))
        assertEquals(FolderGridShape(6, 6, 2), folderGridShape(40, 9, 12))
    }

    @Test fun `a smaller screen limits columns and rows before paging`() {
        assertEquals(FolderGridShape(4, 3, 1), folderGridShape(12, 4, 7))
        assertEquals(FolderGridShape(4, 2, 1), folderGridShape(7, 4, 2))
        assertEquals(FolderGridShape(3, 2, 2), folderGridShape(7, 3, 2))
        assertEquals(FolderGridShape(1, 1, 3), folderGridShape(3, 0, 0))
    }
}
