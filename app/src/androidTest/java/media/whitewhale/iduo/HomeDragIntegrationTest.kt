package media.whitewhale.iduo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDragIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() { compose.waitUntil(15000) { !model().state.value.loading } }
    private fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun waitForDockApps(apps: List<AppEntry>) {
        compose.waitUntil(4000) {
            apps.mapIndexed { index, app -> index to app }.all { (index, app) ->
                runCatching {
                    compose.onNodeWithTag("dock-slot-$index").fetchSemanticsNode()
                        .config[SemanticsProperties.ContentDescription] == listOf(app.label)
                }.getOrDefault(false)
            }
        }
    }
    private fun drag(from: String, to: String) {
        val start = center(from); val end = center(to)
        root().performTouchInput {
            down(start); advanceEventTime(700)
            moveTo(start + Offset(3f, 0f)); moveTo(end, 300); up()
        }
        compose.waitForIdle()
    }
    private fun undo() {
        compose.onNodeWithText("Layout updated").assertDoesNotExist()
        compose.openHomeCustomization()
        compose.onNodeWithText("Undo last layout change").performClick()
        compose.waitForIdle()
    }
    private fun restore(before: HomeLayout) {
        compose.runOnIdle { model().restoreLayout(before) }
    }

    @Test fun homeDragInsertsAtOccupiedCellAndUndoRestoresBothSurfaces() {
        ready()
        val before = model().state.value.layout
        try {
            val occupied = before.slots.indices.filter { before.slots[it] != null }
            assertTrue("Fixture needs three Home apps", occupied.size >= 3)
            val from = occupied[0]; val to = occupied[2]
            val expected = dropApp(before, before.slots[from]!!, DropTarget.Home(to))
            assertNotEquals("Fixture must exercise an occupied-cell insertion", before, expected)
            drag("home-cell-$from", "home-cell-$to")
            assertEquals(expected, model().state.value.layout)
            assertEquals(before.dock, model().state.value.dock)
            undo()
            assertEquals(before, model().state.value.layout)
        } finally { restore(before) }
    }

    @Test fun fullDockReordersAndMovingOneOutCreatesAVacancyForAHomeTransfer() {
        ready()
        val before = model().state.value.layout
        try {
            val apps = model().state.value.apps
            assertTrue("Fixture needs ten apps", apps.size >= 10)
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                apps.take(6).forEach { model().setPinned(it.id, true) }
                apps.drop(6).take(4).forEachIndexed { index, app -> model().setDock(index, app.id) }
            }
            waitForDockApps(apps.drop(6).take(4))
            val arranged = model().state.value.layout
            val placementIds = (arranged.slots.filterNotNull() + arranged.dock.filterNotNull()).toSet()
            val expectedDockMove = dropApp(arranged, arranged.dock[0]!!, DropTarget.Dock(1))
            drag("dock-slot-0", "dock-slot-1")
            assertEquals(expectedDockMove, model().state.value.layout)
            val movedOut = expectedDockMove.dock[0]!!
            val homeTarget = expectedDockMove.slots.indices.first { it !in expectedDockMove.widgetPlacements.flatMap { w -> w.coveredIndices() } }
            val expectedVacancy = dropApp(expectedDockMove, movedOut, DropTarget.Home(homeTarget))
            drag("dock-slot-0", "home-cell-$homeTarget")
            assertEquals(expectedVacancy, model().state.value.layout)
            assertNull(model().state.value.dock[0])
            val movedInIndex = expectedVacancy.slots.indexOfFirst { it != null }
            val movedIn = expectedVacancy.slots[movedInIndex]!!
            val expectedTransfer = dropApp(expectedVacancy, movedIn, DropTarget.Dock(0))
            drag("home-cell-$movedInIndex", "dock-slot-0")
            assertEquals(expectedTransfer, model().state.value.layout)
            assertFalse(movedIn in model().state.value.homeSlots)
            assertEquals(movedIn, model().state.value.dock[0])
            assertEquals(placementIds,
                (model().state.value.homeSlots.filterNotNull() + model().state.value.dock.filterNotNull()).toSet())
        } finally { restore(before) }
    }

    @Test fun occupiedHomeHoverPreviewsInsertionAcrossARowWithoutSavingAndCancelRestoresIt() {
        ready()
        val before = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                model().state.value.apps.take(6).forEach { model().setPinned(it.id, true) }
            }
            val arranged = model().state.value.layout
            val occupied = arranged.slots.indices.filter { arranged.slots[it] != null }
            val source = occupied.first(); val target = occupied[3]
            val moving = arranged.slots[source]!!
            val nudged = arranged.slots[target]!!
            val start = center("home-cell-$source")
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(3f, 0f)) }
            root().performTouchInput { moveTo(center("home-cell-$target"), 300) }
            compose.waitForIdle()

            assertEquals("Hover must not persist", arranged, model().state.value.layout)
            compose.onNodeWithTag("drag-gap-home-$target", useUnmergedTree = true).assertIsDisplayed()
            val preview = center("home-app-$nudged")
            val expected = center("home-cell-${occupied[2]}")
            assertEquals(expected.x, preview.x, 2f)
            assertEquals(expected.y, preview.y, 2f)

            root().performTouchInput { moveTo(Offset(2f, 2f), 200); up() }
            compose.waitForIdle()
            assertEquals(arranged, model().state.value.layout)
            compose.onNodeWithTag("drag-gap-home-$target").assertDoesNotExist()
            val restored = center("home-app-$nudged")
            val original = center("home-cell-$target")
            assertEquals(original.x, restored.x, 2f)
            assertEquals(original.y, restored.y, 2f)
            assertEquals(moving, model().state.value.homeSlots[source])
        } finally { restore(before) }
    }

    @Test fun homeDropOnFullDockShowsGuidanceAndDoesNotChangeLayoutOrUndoState() {
        ready()
        val before = model().state.value.layout
        try {
            val apps = model().state.value.apps
            assertTrue("Fixture needs ten apps", apps.size >= 10)
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                apps.take(6).forEach { model().setPinned(it.id, true) }
                apps.drop(6).take(4).forEachIndexed { index, app -> model().setDock(index, app.id) }
            }
            waitForDockApps(apps.drop(6).take(4))
            val arranged = model().state.value.layout
            val source = arranged.slots.indexOfFirst { it != null }
            val moving = arranged.slots[source]!!
            val nudged = arranged.dock[1]!!
            val revision = model().state.value.editRevision
            val canUndo = model().state.value.canUndoEdit
            val preferences = compose.activity.getSharedPreferences("launcher", 0).getString("state", null)
            val start = center("home-cell-$source")
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(3f, 0f)) }
            root().performTouchInput { moveTo(center("dock-slot-1"), 300) }
            compose.waitForIdle()

            assertEquals("Hover must not persist", arranged, model().state.value.layout)
            compose.onNodeWithText("Dock full • Move an app out first").assertIsDisplayed()
            compose.onNodeWithTag("drag-gap-dock-1").assertDoesNotExist()
            val preview = center("dock-app-$nudged")
            val expected = center("dock-slot-1")
            assertEquals(expected.y, preview.y, 2f)

            root().performTouchInput { moveTo(center("dock-slot-1"), 100); up() }
            compose.waitForIdle()
            assertEquals(arranged, model().state.value.layout)
            assertEquals(moving, model().state.value.homeSlots[source])
            assertEquals(revision, model().state.value.editRevision)
            assertEquals(canUndo, model().state.value.canUndoEdit)
            assertEquals(preferences, compose.activity.getSharedPreferences("launcher", 0).getString("state", null))
        } finally { restore(before) }
    }

    @Test fun heldDragTurnsPageAndPersistsSparseNewPage() {
        ready()
        val before = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                model().state.value.apps.take(4).forEach { model().setPinned(it.id, true) }
            }
            val source = model().state.value.homeSlots.indexOfFirst { it != null }
            val id = model().state.value.homeSlots[source]
            val start = center("home-cell-$source")
            val bounds = root().fetchSemanticsNode().boundsInRoot
            val edge = Offset(bounds.right - 8f, start.y)
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(3f, 0f)); moveTo(edge, 300) }
            compose.waitUntil(5000) {
                compose.onNodeWithTag("app-pager").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Home page 2 of 2"
            }
            compose.waitForIdle()
            val destination = center("home-cell-47")
            root().performTouchInput { moveTo(destination, 300); up() }
            compose.waitForIdle()
            assertNull("Result: ${model().state.value.homeSlots}", model().state.value.homeSlots[source])
            assertEquals(id, model().state.value.homeSlots[47])
            val saved = org.json.JSONObject(compose.activity.getSharedPreferences("launcher", 0).getString("state", "{}")!!)
            assertEquals(id, saved.getJSONArray("homeSlots").getString(47))
            assertTrue(saved.getJSONArray("homeSlots").isNull(source))
            compose.activityRule.scenario.recreate()
            ready()
            assertEquals(id, model().state.value.homeSlots[47])
            compose.onNodeWithTag("app-pager").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Home page 2 of 2"))
        } finally { restore(before) }
    }

    @Test fun droppingOutsideTargetsLeavesLayoutUnchanged() {
        ready()
        val before = model().state.value.layout
        val source = before.slots.indexOfFirst { it != null }
        val start = center("home-cell-$source")
        root().performTouchInput { down(start); advanceEventTime(700); moveTo(Offset(8f, 8f), 300); up() }
        compose.waitForIdle()
        assertEquals(before, model().state.value.layout)
        compose.onNodeWithTag("drag-ghost").assertDoesNotExist()
    }
}
