package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class DockSlotsTest {
    @Test fun `growing the dock appends empty positions and keeps order`() {
        val next = resizeDock(HomeLayout(listOf("a"), listOf("d", null, "e", "f")), 6)
        assertEquals(listOf("d", null, "e", "f", null, null), next.dock)
        assertEquals(listOf("a"), next.slots)
    }

    @Test fun `dock size stays within four to eight positions`() {
        val layout = HomeLayout(emptyList(), List(4) { null })
        assertEquals(MAX_DOCK_SLOTS, resizeDock(layout, 12).dock.size)
        assertEquals(MIN_DOCK_SLOTS, resizeDock(resizeDock(layout, 8), 2).dock.size)
    }

    @Test fun `shrinking fills empty kept positions before touching Home`() {
        val before = HomeLayout(listOf("a"), listOf("d", null, "e", null, "f", "g"))
        val next = resizeDock(before, 4)
        assertEquals(listOf("d", "f", "e", "g"), next.dock)
        assertEquals(listOf("a"), next.slots)
    }

    @Test fun `shrinking moves remaining apps to free Home cells around widgets`() {
        val widget = WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 1)
        val before = HomeLayout(listOf(null, null, "a"), listOf("d", "e", "f", "g", "h", "i"), listOf(widget))
        val next = resizeDock(before, 4)
        assertEquals(listOf("d", "e", "f", "g"), next.dock)
        assertEquals(listOf(null, null, "a", "h", "i"), next.slots)
    }

    @Test fun `resizing never loses or duplicates a shortcut`() {
        val before = HomeLayout(listOf("a", null, "b"), listOf("c", "d", "e", "f", "g", "h", "i", "j"))
        for (count in MIN_DOCK_SLOTS..MAX_DOCK_SLOTS) {
            val next = resizeDock(before, count)
            val all = (next.slots + next.dock).filterNotNull()
            assertEquals(all.distinct(), all)
            assertEquals((before.slots + before.dock).filterNotNull().toSet(), all.toSet())
        }
    }

    @Test fun `dock background grows by one aligned position per extra app`() {
        val p = LayoutPreset()
        val four = homeGeometry(475f, 900f, p, true)
        val six = homeGeometry(475f, 900f, p, true, dockSlots = 6)
        assertEquals(four.dockRowHeight, six.dockRowHeight, .01f)
        assertEquals(four.dockHeight + 2f * four.dockRowHeight, six.dockHeight, .01f)
    }

    @Test fun `a tall dock stays below the status rail drawn from the content top`() {
        for (height in listOf(832f, 700f)) {
            val g = homeGeometry(750f, height, LayoutPreset(), true, dockSlots = MAX_DOCK_SLOTS, statusRailHeight = 140f)
            assertTrue("$height", g.dockTop >= g.contentTop + 140f - .01f)
            assertTrue("$height", g.dockTop + g.dockHeight <= height - 124f + .01f)
        }
        val four = homeGeometry(750f, 832f, LayoutPreset(), true, statusRailHeight = 140f)
        assertEquals(homeGeometry(750f, 832f, LayoutPreset(), true).dockTop, four.dockTop, .01f)
    }

    @Test fun `a tall dock stays on screen and keeps full size touch targets`() {
        for (height in listOf(420f, 560f, 700f)) {
            val g = homeGeometry(475f, height, LayoutPreset(), true, dockSlots = MAX_DOCK_SLOTS)
            assertTrue(g.dockRowHeight >= 48f)
            assertTrue("$height", g.dockTop >= 8f)
            assertTrue("$height", g.dockTop + g.dockHeight <= height - 124f + .01f)
        }
    }
}
