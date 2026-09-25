package media.whitewhale.iduo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun controller() = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        .get(compose.activity) as WidgetController
    private fun ready() = compose.waitUntil(15_000) { !model().state.value.loading }

    @Test fun createRenameExtractRefuseFullDockUndoAndRecreate() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val folderChildren = before.folders.flatMapTo(mutableSetOf(), FolderEntry::appIds)
        val apps = model().state.value.apps.filter { it.available && it.id !in folderChildren }.take(8)
        assumeTrue("Folder test needs at least six apps", apps.size >= 6)
        try {
            val first = apps[0]; val second = apps[1]
            val blocked = before.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
            val existingFolderPins = before.slots.mapIndexedNotNull { index, id ->
                id?.takeIf(::isFolderId)?.let { index to it }
            }.toMap()
            val appCells = (0 until HOME_CELLS)
                .filter { it !in blocked && it !in existingFolderPins }.take(2)
            assumeTrue("Folder test needs two widget-free Home cells", appCells.size == 2)
            val controlledSize = maxOf(appCells.last() + 1, (existingFolderPins.keys.maxOrNull() ?: -1) + 1)
            val fixture = before.copy(
                // Keep every widget placement/restore descriptor while controlling app shortcuts.
                slots = List(controlledSize) { index -> when (index) {
                    appCells[0] -> first.id
                    appCells[1] -> second.id
                    else -> existingFolderPins[index]
                } },
                dock = List(4) { null },
            )
            compose.runOnIdle { model().restoreLayout(fixture) }
            compose.waitUntil(5_000) { model().state.value.layout == fixture }
            assertEquals("Folder fixture must not prune any widget binding",
                idsBefore, controller().host.appWidgetIds.toSet())
            compose.onNode(hasAnyAncestor(hasTestTag("home-app-${first.id}")) and hasContentDescription(first.label),
                useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.OnLongClick)
            compose.onNodeWithText("Create folder").performClick()
            compose.onNodeWithTag("folder-app-${second.id}").performScrollTo().performClick()
            compose.waitUntil(5_000) { model().state.value.folders.size == fixture.folders.size + 1 }
            val folder = model().state.value.folders.first { it.id !in before.folders.map(FolderEntry::id) }
            compose.onNodeWithTag("home-folder-${folder.id}").performClick()
            compose.onNodeWithTag("folder-rename").performClick()
            compose.onNodeWithTag("folder-name-field").performTextReplacement("Travel")
            compose.activityRule.scenario.recreate(); ready()
            compose.onNodeWithTag("folder-name-field").assertTextContains("Travel")
            compose.onNodeWithTag("folder-name-save").performClick()
            compose.waitUntil(5_000) { model().folder(folder.id)?.title == "Travel" }

            compose.activityRule.scenario.recreate(); ready()
            compose.onNodeWithTag("home-folder-${folder.id}").assertIsDisplayed().performClick()
            compose.onNodeWithTag("folder-name").assertTextContains("Travel")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val launchedActivity = android.app.Instrumentation.ActivityMonitor(
                null as android.content.IntentFilter?, null, false,
            ).also(instrumentation::addMonitor)
            try {
                compose.onNodeWithTag("folder-panel").performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, 2f)) }
                compose.onNodeWithTag("folder-panel").assertDoesNotExist()
                assertNull("Outside folder tap must not launch the covered Home app", instrumentation.waitForMonitorWithTimeout(launchedActivity, 750))
            } finally {
                instrumentation.removeMonitor(launchedActivity)
            }
            compose.onNodeWithTag("home-folder-${folder.id}").performClick()
            compose.onNodeWithTag("folder-options").performClick()
            compose.onNodeWithTag("folder-disband").performClick()
            compose.waitUntil(5_000) { model().folder(folder.id) == null }
            assertTrue(listOf(first.id, second.id).all { model().state.value.layout.indexOfShortcut(it) != null })
            compose.runOnIdle { model().undoEdit() }
            compose.waitUntil(5_000) { model().folder(folder.id)?.appIds?.containsAll(listOf(first.id, second.id)) == true }

            compose.runOnIdle {
                apps.drop(2).take(4).forEachIndexed { index, app -> model().setDock(index, app.id) }
            }
            compose.waitUntil(5_000) { model().state.value.dock.none { it == null } }
            val fullDockLayout = model().state.value.layout
            compose.runOnIdle { assertFalse(model().removeAppFromFolder(folder.id, first.id, DropTarget.Dock(0))) }
            assertEquals(fullDockLayout, model().state.value.layout)
        } finally {
            compose.runOnIdle { model().restoreLayout(before) }
            compose.waitUntil(5_000) { model().state.value.layout == before }
            assertEquals(before.widgetPlacements.map { it.id }, model().state.value.widgetPlacements.map { it.id })
            assertEquals("Folder cleanup must preserve the complete original host ID set",
                idsBefore, controller().host.appWidgetIds.toSet())
        }
    }
}
