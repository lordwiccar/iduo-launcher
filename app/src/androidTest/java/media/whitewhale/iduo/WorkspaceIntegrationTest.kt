package media.whitewhale.iduo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() { compose.waitUntil(15000) { !model().state.value.loading } }
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
    private fun page() = compose.onNodeWithTag("app-pager").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    private fun waitForDock(layout: HomeLayout) {
        val labels = model().state.value.apps.associate { it.id to it.label }
        compose.waitUntil(4000) {
            layout.dock.indices.all { index ->
                val expected = layout.dock[index]?.let(labels::get) ?: "Choose dock app ${index + 1}"
                runCatching {
                    compose.onNodeWithTag("dock-slot-$index").fetchSemanticsNode()
                        .config[SemanticsProperties.ContentDescription] == listOf(expected)
                }.getOrDefault(false)
            }
        }
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
    private fun begin(tag: String) {
        val start = center(tag)
        root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(4f, 0f)) }
        compose.waitForIdle()
    }
    private fun drop(tag: String) {
        val end = center(tag)
        root().performTouchInput { moveTo(end, 300); up() }
        compose.waitForIdle()
    }

    @Test fun libraryDragAddsToHomeAndCanCreateANewPage() {
        ready(); val before = model().state.value.layout
        try {
            val app = model().state.value.apps.first { it.id !in model().state.value.order }
            compose.onNodeWithTag("library-page-link").performClick()
            compose.onNodeWithTag("library-search").performTextInput(app.label)
            begin("library-app-${app.id}")
            compose.waitUntil(4000) { page().startsWith("Home page") }
            compose.onNodeWithTag("drag-ghost").assertIsDisplayed()
            val bounds = root().fetchSemanticsNode().boundsInRoot
            root().performTouchInput { moveTo(Offset(bounds.right - 6, bounds.center.y), 250) }
            compose.waitUntil(5000) { page() == "Home page 2 of 2" }
            compose.onNodeWithTag("home-cell-$HOME_CELLS").assertIsDisplayed()
            drop("home-cell-$HOME_CELLS")
            assertEquals(app.id, model().state.value.homeSlots[HOME_CELLS])
            assertEquals(2, model().state.value.homePages)
            assertTrue(model().state.value.apps.any { it.id == app.id })
            undo()
            assertEquals(before, model().state.value.layout)
        } finally { restore(before) }
    }

    @Test fun draggingAFilteredAppDismissesTheKeyboardBeforePlacement() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        ready(); val before = model().state.value.layout
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText().trim() }
        fun imeVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        val ime = shell("settings get secure show_ime_with_hard_keyboard")
        val handwriting = shell("settings get secure stylus_handwriting_enabled")
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("settings put secure stylus_handwriting_enabled 0")
            val app = model().state.value.apps.first { it.id !in model().state.value.order }
            compose.onNodeWithTag("library-page-link").performClick()
            compose.onNodeWithTag("library-search").performTouchInput { click() }
            compose.onNodeWithTag("library-search").performTextInput(app.label)
            compose.waitUntil(10000) { imeVisible() }
            begin("library-app-${app.id}")
            compose.waitUntil(10000) { !imeVisible() && page().startsWith("Home page") }
            drop("home-cell-8")
            assertEquals(app.id, model().state.value.homeSlots[8])
        } finally {
            restore(before)
            for ((key, value) in listOf("show_ime_with_hard_keyboard" to ime, "stylus_handwriting_enabled" to handwriting))
                shell(if (value == "null") "settings delete secure $key" else "settings put secure $key $value")
        }
    }

    @Test fun dockCanBeRearrangedFromAllAppsWithoutOpeningItsPicker() {
        ready(); val before = model().state.value.layout
        try {
            compose.onNodeWithTag("library-page-link").performClick()
            val expected = dropApp(before, before.dock[0]!!, DropTarget.Dock(1))
            begin("dock-slot-0"); drop("dock-slot-1")
            assertEquals(expected, model().state.value.layout)
            compose.onNodeWithTag("search-field").assertDoesNotExist()
            undo()
            assertEquals(before, model().state.value.layout)
        } finally { restore(before) }
    }

    @Test fun libraryDragUsesADockVacancyAndCanceledDragPreservesInstalledApps() {
        ready(); val before = model().state.value.layout
        try {
            compose.runOnIdle { model().setDock(3, null) }
            val arranged = model().state.value.layout
            waitForDock(arranged)
            val installed = model().state.value.apps.map { it.id }
            val app = model().state.value.apps.first { it.id !in arranged.dock }
            compose.onNodeWithTag("library-page-link").performClick()
            compose.onNodeWithTag("library-search").performTextInput(app.label)
            begin("library-app-${app.id}")
            compose.waitUntil(4000) { page().startsWith("Home page") }
            compose.onNodeWithContentDescription("Moving ${app.label}").assertIsDisplayed()
            val expected = dropApp(arranged, app.id, DropTarget.Dock(1))
            drop("dock-slot-1")
            assertEquals(expected, model().state.value.layout)
            assertEquals(installed, model().state.value.apps.map { it.id })
            undo()
            assertEquals(arranged, model().state.value.layout)
            compose.onNodeWithTag("library-page-link").performClick()
            begin("library-app-${app.id}")
            root().performTouchInput { moveTo(Offset(2f, 2f), 250); up() }
            compose.waitForIdle()
            assertEquals("All apps", page())
            assertEquals(arranged, model().state.value.layout)
            assertEquals(installed, model().state.value.apps.map { it.id })
        } finally { restore(before) }
    }

    @Test fun libraryDropOnFullDockShowsGuidanceAndReturnsToAllAppsWithoutSaving() {
        ready(); val before = model().state.value.layout
        try {
            val apps = model().state.value.apps
            assertTrue("Fixture needs five apps", apps.size >= 5)
            compose.runOnIdle { apps.take(4).forEachIndexed { index, app -> model().setDock(index, app.id) } }
            val arranged = model().state.value.layout
            waitForDock(arranged)
            val app = apps.first { it.id !in arranged.dock }
            val revision = model().state.value.editRevision
            val canUndo = model().state.value.canUndoEdit
            val preferences = compose.activity.getSharedPreferences("launcher", 0).getString("state", null)
            compose.onNodeWithTag("library-page-link").performClick()
            compose.onNodeWithTag("library-search").performTextInput(app.label)
            begin("library-app-${app.id}")
            compose.waitUntil(4000) { page().startsWith("Home page") }
            compose.onNodeWithContentDescription("Moving ${app.label}").assertIsDisplayed()
            root().performTouchInput { moveTo(center("dock-slot-1"), 300) }
            compose.waitForIdle()
            compose.onNodeWithText("Dock full • Move an app out first").assertIsDisplayed()
            compose.onNodeWithTag("drag-gap-dock-1").assertDoesNotExist()
            assertEquals(arranged, model().state.value.layout)
            root().performTouchInput { moveTo(center("dock-slot-1"), 100); up() }
            compose.waitForIdle()
            assertEquals("All apps", page())
            assertEquals(arranged, model().state.value.layout)
            assertEquals(revision, model().state.value.editRevision)
            assertEquals(canUndo, model().state.value.canUndoEdit)
            assertEquals(preferences, compose.activity.getSharedPreferences("launcher", 0).getString("state", null))
        } finally { restore(before) }
    }

    @Test fun widgetCollisionIsANoOpAndMovingToABlankPageKeepsItsStableSlot() {
        ready(); val before = model().state.value.layout
        try {
            begin("widget-slot-0"); drop("widget-slot-1")
            assertEquals(before, model().state.value.layout)
            begin("widget-slot-0")
            val bounds = root().fetchSemanticsNode().boundsInRoot
            root().performTouchInput { moveTo(Offset(bounds.right - 6, bounds.center.y), 250) }
            compose.waitUntil(5000) { page() == "Home page 2 of 2" }
            drop("home-cell-$HOME_CELLS")
            assertEquals(before.placement(0)?.copy(page = 1, column = 0, row = 0), model().placement(0))
            assertEquals(2, model().state.value.homePages)
            assertEquals(before.slots, model().state.value.homeSlots)
            compose.activityRule.scenario.recreate(); ready()
            assertEquals(before.placement(0)?.copy(page = 1, column = 0, row = 0), model().placement(0))
            assertEquals(2, model().state.value.homePages)
            compose.runOnIdle { model().removePlacement(DropTarget.Widget(0)) }
            compose.waitForIdle()
            assertEquals("Home page 1 of 1", page())
        } finally { restore(before) }
    }

    @Test fun androidWidgetKeepsItsBindingWhenDraggedRemovedAndUndone() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        ready(); val before = model().state.value.layout
        val host = android.appwidget.AppWidgetHost(compose.activity, 1024)
        val manager = android.appwidget.AppWidgetManager.getInstance(compose.activity)
        val provider = manager.installedProviders.first { it.provider.className.endsWith("AnalogAppWidgetProvider") }
        val id = host.allocateAppWidgetId()
        try {
            assertTrue("Emulator fixture requires widget binding permission", manager.bindAppWidgetIdIfAllowed(id, provider.provider))
            val slot = model().nextWidgetSlot()
            compose.runOnIdle { model().placeWidget(WidgetPlacement(slot, id, 1, 0, 0, 2, 2)) }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.waitForIdle()
            begin("widget-slot-$slot"); drop("home-cell-27")
            assertEquals(WidgetPlacement(slot, id, 1, 2, 0, 2, 2), model().placement(slot))
            assertEquals(provider.provider, manager.getAppWidgetInfo(id).provider)
            compose.activityRule.scenario.recreate(); ready()
            assertEquals(WidgetPlacement(slot, id, 1, 2, 0, 2, 2), model().placement(slot))
            begin("widget-slot-$slot")
            root().performTouchInput { moveBy(Offset(50f, 40f), 200) }
            compose.waitForIdle(); drop("remove-drop-target")
            assertNull(model().placement(slot))
            undo()
            assertEquals(id, model().placement(slot)?.id)
            assertEquals(provider.provider, manager.getAppWidgetInfo(id).provider)
        } finally { restore(before); host.deleteAppWidgetId(id) }
    }

    @Test fun removeTargetRemovesOnlyThePlacementAndUndoRestoresIt() {
        ready(); val before = model().state.value.layout
        try {
            val source = before.slots.indexOfFirst { it != null }
            begin("home-cell-$source")
            root().performTouchInput { moveBy(Offset(80f, 20f), 200) }
            compose.waitForIdle()
            drop("remove-drop-target")
            assertFalse(model().state.value.order.contains(before.slots[source]))
            assertEquals(before.dock, model().state.value.dock)
            assertTrue(model().state.value.apps.any { it.id == before.slots[source] })
            undo()
            assertEquals(before, model().state.value.layout)
        } finally { restore(before) }
    }

    @Test fun longSwipeCannotSkipPagesAndLibraryReturnsWithADiagonalSwipe() {
        ready(); val before = model().state.value.layout
        try {
            val occupied = before.slots.indices.filter { before.slots[it] != null }
            compose.runOnIdle {
                model().applyDrop(before.slots[occupied[0]]!!, DropTarget.Home(HOME_CELLS))
                model().applyDrop(before.slots[occupied[1]]!!, DropTarget.Home(HOME_CELLS * 2))
                // Clear the fixture-only undo record before exercising navigation.
                model().setDock(0, before.dock[0])
            }
            val pager = compose.onNodeWithTag("app-pager")
            pager.performTouchInput {
                down(Offset(width * .95f, height * .8f))
                moveTo(Offset(width * .015f, height * .8f), 120); up()
            }
            assertEquals("Home page 2 of 3", page())
            compose.waitForIdle()
            repeat(3) {
                compose.onNodeWithTag("library-page-link").performClick()
                compose.waitForIdle()
                compose.waitUntil(5000) { page() == "All apps" }
                assertEquals("All apps", page())
                val list = compose.onNodeWithTag("all-apps-list")
                list.performTouchInput { swipeUp() }
                compose.waitForIdle()
                assertTrue("Vertical app scrolling stays available", list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                pager.performTouchInput {
                    down(Offset(width * .2f, height * .65f))
                    moveTo(Offset(width * .9f, height * .70f), 300); up()
                }
                compose.waitForIdle()
                assertEquals("Home page 3 of 3", page())
            }
        } finally { restore(before) }
    }

    @Test fun dockHoverDoesNotTurnPagesAndLeavingAnEdgeCancelsItsTimer() {
        ready(); val before = model().state.value.layout
        try {
            val source = before.slots.indexOfFirst { it != null }
            begin("home-cell-$source")
            val dock = center("dock-slot-1")
            root().performTouchInput { moveTo(dock, 250) }
            android.os.SystemClock.sleep(1000)
            compose.waitForIdle()
            assertEquals("Home page 1 of 2", page())
            val bounds = root().fetchSemanticsNode().boundsInRoot
            root().performTouchInput { moveTo(Offset(bounds.right - 6f, dock.y), 100) }
            // Moving away before the hold completes must not schedule a delayed turn.
            root().performTouchInput { moveTo(dock, 100) }
            android.os.SystemClock.sleep(1000)
            compose.waitForIdle()
            assertEquals("Home page 1 of 2", page())
            root().performTouchInput { moveTo(Offset(4f, 4f), 100); up() }
            compose.waitForIdle()
            assertEquals(before, model().state.value.layout)
            assertEquals("Home page 1 of 1", page())
        } finally { restore(before) }
    }

    @Test fun temporaryPageCanBeVisitedAndLeftWithoutSavingIt() {
        ready(); val before = model().state.value.layout
        try {
            val source = before.slots.indexOfFirst { it != null }
            begin("home-cell-$source")
            val bounds = root().fetchSemanticsNode().boundsInRoot
            root().performTouchInput { moveTo(Offset(bounds.right - 6f, bounds.center.y), 250) }
            compose.waitUntil(5000) { page() == "Home page 2 of 2" }
            compose.waitForIdle()
            compose.onNodeWithTag("home-cell-$HOME_CELLS").assertIsDisplayed()
            root().performTouchInput { moveTo(Offset(bounds.left + 6f, bounds.center.y), 250) }
            compose.waitUntil(5000) { page() == "Home page 1 of 2" }
            root().performTouchInput { moveTo(Offset(4f, 4f), 100); up() }
            compose.waitForIdle()
            assertEquals(before, model().state.value.layout)
            assertEquals("Home page 1 of 1", page())
        } finally { restore(before) }
    }
}
