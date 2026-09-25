package media.whitewhale.iduo

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Expanded-only coverage for the overlapping, uniquely composed Home panes. */
@RunWith(AndroidJUnit4::class)
class ExpandedWorkspaceIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun pager() = compose.onNodeWithTag("app-pager")
    private fun page() = pager().fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    private fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
    private fun preferences() = compose.activity.getSharedPreferences("launcher", 0).getString("state", null)

    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) { "Expanded workspace tests only run on an emulator" }
        compose.waitUntil(15000) { !model().state.value.loading }
        val density = compose.activity.resources.displayMetrics.density
        check(compose.activity.windowManager.currentWindowMetrics.bounds.width() / density >= 650f) {
            "ExpandedWorkspaceIntegrationTest requires the inner-display size"
        }
    }

    private fun waitForPage(expected: String) = compose.waitUntil(5000) { page() == expected }

    private fun assertDisplayedCount(tag: String, expected: Int) {
        val viewport = pager().fetchSemanticsNode().boundsInRoot
        val visible = compose.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .count { node ->
                val bounds = node.boundsInRoot
                bounds.width > 1f && bounds.height > 1f && bounds.overlaps(viewport)
            }
        assertEquals("Displayed instances of $tag", expected, visible)
    }

    private fun selectHome(index: Int) {
        compose.onNodeWithContentDescription("Home page ${index + 1}").performClick()
        waitForPage("Home page ${index + 1} of 3")
        // currentPage changes halfway through animateScrollToPage. Drag hit testing uses
        // settledPage, so wait for both visible panes to reach their final positions.
        compose.waitForIdle()
        assertDisplayedCount("home-page-$index", 1)
        if (index > 0) assertDisplayedCount("home-page-${index - 1}", 1)
    }

    private data class Fixture(val layout: HomeLayout, val ids: List<String>)

    /** Keep all existing widget IDs assigned while the activity may be recreated. */
    private fun arrangeThreePages(): Fixture {
        val state = model().state.value
        val apps = state.apps.filter { it.id !in state.dock }.take(3)
        assertEquals("Fixture needs three apps outside the dock", 3, apps.size)
        compose.runOnIdle {
            model().state.value.order.toList().forEach { model().setPinned(it, false) }
            model().applyDrop(apps[0].id, DropTarget.Home(8))
            model().applyDrop(apps[1].id, DropTarget.Home(HOME_CELLS))
            model().applyDrop(apps[2].id, DropTarget.Home(HOME_CELLS * 2))
            // Fixture construction should not become the operation tested by Undo.
            model().setDock(0, model().state.value.dock[0])
        }
        compose.waitUntil(5000) { model().state.value.homePages == 3 }
        selectHome(0)
        return Fixture(model().state.value.layout, apps.map { it.id })
    }

    private fun restore(before: HomeLayout) {
        compose.runOnIdle { model().restoreLayout(before) }
    }

    private fun longSwipe(left: Boolean) {
        pager().performTouchInput {
            val y = height * .78f
            val start = Offset(width * if (left) .94f else .06f, y)
            val end = Offset(width * if (left) .02f else .98f, y)
            down(start); moveTo(end, 180); up()
        }
        compose.waitForIdle()
    }

    private fun beginDrag(tag: String, expectedAppId: String) {
        val app = model().state.value.apps.first { it.id == expectedAppId }
        val sourceIndex = tag.substringAfterLast('-').toInt()
        assertEquals("App registered in $tag", expectedAppId, model().state.value.homeSlots[sourceIndex])
        compose.onNodeWithTag("home-app-$expectedAppId").assertIsDisplayed()
        val start = center(tag)
        root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(4f, 0f)) }
        compose.onNodeWithContentDescription("Moving ${app.label}").assertIsDisplayed()
        assertEquals("Long press must not mutate $tag before drop", expectedAppId,
            model().state.value.homeSlots[sourceIndex])
    }

    private fun dropOn(tag: String) {
        val end = center(tag)
        root().performTouchInput { moveTo(end, 300) }
        root().performTouchInput { up() }
        compose.waitForIdle()
    }

    private fun undo() {
        compose.openHomeCustomization()
        compose.onNodeWithText("Undo last layout change").performClick()
        compose.waitForIdle()
    }

    @Test fun expandedPagerShowsUniqueOverlappingPairsAndLongSwipesMoveOnePage() {
        ready(); val before = model().state.value.layout
        try {
            arrangeThreePages()
            compose.onAllNodesWithTag("expanded-leading-home", useUnmergedTree = true).assertCountEquals(1)
            assertDisplayedCount("expanded-leading-home", 1)
            assertDisplayedCount("home-page--1", 1)
            assertDisplayedCount("home-cell-${homeCellIndex(-1, 0)}", 1)
            assertDisplayedCount("home-cell-${homeCellIndex(-1, HOME_CELLS - 1)}", 1)
            assertDisplayedCount("home-page-0", 1)
            assertDisplayedCount("home-cell-0", 1)
            assertDisplayedCount("home-page-1", 0)
            val initialRightPane = compose.onNodeWithTag("home-page-0").fetchSemanticsNode().boundsInRoot

            longSwipe(left = true)
            assertEquals("Home page 2 of 3", page())
            assertDisplayedCount("expanded-leading-home", 0)
            assertDisplayedCount("home-page--1", 0)
            assertDisplayedCount("home-page-0", 1)
            assertDisplayedCount("home-page-1", 1)
            assertDisplayedCount("home-cell-0", 1)
            assertDisplayedCount("home-cell-$HOME_CELLS", 1)
            assertTrue(center("home-page-0").x < center("home-page-1").x)
            // home-page includes 16 dp of leading padding; measure the grid itself so this
            // stays an assertion about the visible icons/cells rather than its wrapper.
            val leftGridLeft = compose.onNodeWithTag("home-cell-0").fetchSemanticsNode().boundsInRoot.left
            val leftGridRight = compose.onNodeWithTag("home-cell-3").fetchSemanticsNode().boundsInRoot.right
            val leftGridCenter = (leftGridLeft + leftGridRight) / 2f
            val physicalBounds = root().fetchSemanticsNode().boundsInRoot
            val physicalLeftHalfCenter = physicalBounds.left + physicalBounds.width / 4f
            assertEquals("The left Home grid is centered in the physical left half",
                physicalLeftHalfCenter, leftGridCenter, 2f)
            val nextRightPane = compose.onNodeWithTag("home-page-1").fetchSemanticsNode().boundsInRoot
            assertEquals("The incoming page settles at the original right-pane position",
                initialRightPane.left, nextRightPane.left, 1f)
            assertEquals("The settled right pane keeps its width",
                initialRightPane.right, nextRightPane.right, 1f)

            longSwipe(left = true)
            assertEquals("Home page 3 of 3", page())
            assertDisplayedCount("home-page-1", 1)
            assertDisplayedCount("home-page-2", 1)
            assertDisplayedCount("home-cell-$HOME_CELLS", 1)
            assertDisplayedCount("home-cell-${2 * HOME_CELLS}", 1)
            longSwipe(left = false)
            assertEquals("A long reversal moves only one page", "Home page 2 of 3", page())
            val restoredRightPane = compose.onNodeWithTag("home-page-1").fetchSemanticsNode().boundsInRoot
            assertEquals("Reversing restores the right pane exactly",
                initialRightPane.left, restoredRightPane.left, 1f)
            longSwipe(left = false)
            assertEquals("Home page 1 of 3", page())
            val restoredInitialPane = compose.onNodeWithTag("home-page-0").fetchSemanticsNode().boundsInRoot
            assertEquals("Returning to the first pair restores its original position",
                initialRightPane.left, restoredInitialPane.left, 1f)
            assertEquals("Returning to the first pair restores its original width",
                initialRightPane.right, restoredInitialPane.right, 1f)
        } finally { restore(before) }
    }

    @Test fun heldSwipeTracksTheFingerAndAReversalLeavesStateUntouched() {
        ready(); val before = model().state.value.layout
        try {
            val arranged = arrangeThreePages().layout
            val saved = preferences()
            val startX = compose.onNodeWithTag("home-cell-0").fetchSemanticsNode().boundsInRoot.left
            pager().performTouchInput {
                val start = Offset(width * .82f, height * .78f)
                down(start); moveTo(start + Offset(-48f, 0f), 120)
            }
            val afterSlop = compose.onNodeWithTag("home-cell-0").fetchSemanticsNode().boundsInRoot.left
            pager().performTouchInput { moveBy(Offset(-84f, 0f), 120) }
            val afterSecondSample = compose.onNodeWithTag("home-cell-0").fetchSemanticsNode().boundsInRoot.left
            assertEquals("Each post-slop pixel moves the Home pane one pixel", -84f, afterSecondSample - afterSlop, 4f)
            assertTrue("The held page visibly follows the pointer", afterSecondSample < startX)
            pager().performTouchInput { moveBy(Offset(132f, 0f), 200); up() }
            compose.waitForIdle()
            assertEquals("Home page 1 of 3", page())
            assertEquals(arranged, model().state.value.layout)
            assertEquals(saved, preferences())
        } finally { restore(before) }
    }

    @Test fun appsCanMoveBetweenBothVisiblePagesWithoutChangingThePair() {
        ready(); val before = model().state.value.layout
        try {
            val fixture = arrangeThreePages()
            selectHome(1)
            assertEquals("Left pane fixture app", fixture.ids[0], model().state.value.homeSlots[8])
            assertEquals("Right pane fixture app", fixture.ids[1], model().state.value.homeSlots[HOME_CELLS])
            val leftToRight = dropApp(fixture.layout, fixture.ids[0], DropTarget.Home(HOME_CELLS))
            beginDrag("home-cell-8", fixture.ids[0]); dropOn("home-cell-$HOME_CELLS")
            assertEquals("left=${center("home-cell-8")} right=${center("home-cell-$HOME_CELLS")} pager=${page()}",
                leftToRight, model().state.value.layout)
            assertEquals("Home page 2 of 3", page())
            undo()
            assertEquals(fixture.layout, model().state.value.layout)
            assertEquals("Home page 2 of 3", page())

            val rightToLeft = dropApp(fixture.layout, fixture.ids[1], DropTarget.Home(8))
            beginDrag("home-cell-$HOME_CELLS", fixture.ids[1]); dropOn("home-cell-8")
            assertEquals(rightToLeft, model().state.value.layout)
            assertEquals("A drop onto the visible left pane keeps the pair selected", "Home page 2 of 3", page())
            undo()

            beginDrag("home-cell-8", fixture.ids[0])
            val bounds = root().fetchSemanticsNode().boundsInRoot
            root().performTouchInput { moveTo(Offset(bounds.center.x, 2f), 200); up() }
            compose.waitForIdle()
            assertEquals(fixture.layout, model().state.value.layout)
            assertEquals("Home page 2 of 3", page())
        } finally { restore(before) }
    }

    @Test fun laterExpandedWidgetKeepsItsBindingAndSlotAcrossRecreation() {
        ready(); val before = model().state.value.layout
        val host = AppWidgetHost(compose.activity, 1024)
        var id = -1
        try {
            arrangeThreePages()
            val manager = AppWidgetManager.getInstance(compose.activity)
            val retainedProviders = before.widgetPlacements.map { it.id }.filter { it >= 0 }
                .associateWith { manager.getAppWidgetInfo(it)?.provider }
            val provider = manager.installedProviders.firstOrNull { it.provider.className.endsWith("AnalogAppWidgetProvider") }
            assertNotNull("Emulator fixture needs the Clock analog widget", provider)
            id = host.allocateAppWidgetId()
            assertTrue("Emulator fixture requires widget binding permission", manager.bindAppWidgetIdIfAllowed(id, provider!!.provider))
            val slot = model().nextWidgetSlot()
            compose.runOnIdle { model().placeWidget(WidgetPlacement(slot, id, 1, 2, 0, 2, 2)) }
            val expectedPlacements = model().state.value.widgetPlacements
            compose.onAllNodesWithTag("widget-slot-2", useUnmergedTree = true).assertCountEquals(1)
            selectHome(1)
            compose.onNodeWithTag("widget-slot-$slot").assertIsDisplayed()
            compose.onAllNodesWithTag("widget-slot-$slot", useUnmergedTree = true).assertCountEquals(1)
            assertTrue("The initial panel is never composed twice",
                compose.onAllNodesWithTag("widget-slot-2", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size <= 1)
            val savedWidgets = JSONObject(preferences()!!).getJSONArray("widgets")
            assertTrue((0 until savedWidgets.length()).any { savedWidgets.getJSONObject(it).let { w ->
                w.getInt("slot") == slot && w.getInt("id") == id && w.getInt("page") == 1 && w.getInt("column") == 2
            } })

            compose.activityRule.scenario.recreate(); ready()
            waitForPage("Home page 2 of 3")
            compose.onNodeWithTag("widget-slot-$slot").assertIsDisplayed()
            assertEquals(expectedPlacements, model().state.value.widgetPlacements)
            assertEquals(provider.provider, manager.getAppWidgetInfo(id).provider)
            retainedProviders.forEach { (retainedId, retainedProvider) ->
                assertEquals("Existing widget $retainedId keeps its binding", retainedProvider,
                    manager.getAppWidgetInfo(retainedId)?.provider)
            }
        } finally {
            restore(before)
            if (id >= 0) host.deleteAppWidgetId(id)
        }
    }

    @Test fun coverRoundTripKeepsTheRightPagePreferencesDockAndFullLibrary() {
        ready(); val before = model().state.value.layout
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        try {
            arrangeThreePages(); selectHome(2)
            val saved = preferences()
            val innerDock = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
            shell("wm size 1248x1972")
            compose.waitUntil(15000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 1248 }
            compose.waitForIdle()
            assertEquals("Home page 3 of 3", page())
            assertEquals(saved, preferences())

            shell("wm size 2448x1848")
            compose.waitUntil(15000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 2448 }
            compose.waitForIdle()
            assertEquals("Home page 3 of 3", page())
            compose.onNodeWithTag("home-page-1").assertIsDisplayed()
            compose.onNodeWithTag("home-page-2").assertIsDisplayed()
            assertEquals(saved, preferences())
            assertEquals(innerDock, compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot)

            compose.onNodeWithTag("library-page-link").performClick()
            waitForPage("All apps")
            val library = compose.onNodeWithTag("library-page").fetchSemanticsNode().boundsInRoot
            val viewport = pager().fetchSemanticsNode().boundsInRoot
            assertEquals("All apps reaches the pager's trailing edge", viewport.right, library.right, 2f)
            assertTrue("All apps uses the full expanded pager apart from its content padding",
                library.width > viewport.width * .9f)
        } finally {
            shell("wm size ${originalSize ?: "reset"}")
            restore(before)
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
}
