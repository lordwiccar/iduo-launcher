package media.whitewhale.iduo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CustomizationNavigationIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() {
        compose.waitUntil(15_000) { !model().state.value.loading }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
    }


    @Test fun systemBackReturnsFromSubpageToOverviewThenHome() {
        ready()
        compose.onNodeWithTag("settings").assertDoesNotExist()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-home").performClick()
        // A narrow screen shows the page alone, with a back arrow; a wide one keeps the list
        // beside it, so Back from there closes Settings at once.
        val onePage = compose.onAllNodesWithTag("customization-back").fetchSemanticsNodes().isNotEmpty()

        if (onePage) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("customization-wallpaper").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNodeWithTag("customization-wallpaper").assertIsDisplayed()
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("search").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("customization-wallpaper").assertDoesNotExist()
    }

    @Test fun emptySpaceWallpaperOpensPhotoControlsWithoutChangingLayoutOrBindings() {
        ready()
        val before = model().state.value.layout
        val idsBefore = model().state.value.widgetPlacements.map { it.slot to it.id }
        val blocked = before.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
        val empty = (0 until HOME_CELLS).first { before.slotAt(it) == null && before.cellVisible(it) && it !in blocked }

        compose.onNodeWithTag("home-cell-$empty").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("empty-space-wallpaper").performClick()
        compose.onNodeWithTag("background-choose").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-settings").assertExists()
        assertEquals(before, model().state.value.layout)
        assertEquals(idsBefore, model().state.value.widgetPlacements.map { it.slot to it.id })
    }

    @Test fun appMoveSubpageBackDoesNotChangePlacement() {
        ready()
        val before = model().state.value.layout
        val appId = (0 until HOME_CELLS).mapNotNull(before::slotAt)
            .first { id -> model().state.value.apps.any { it.id == id } }
        val index = requireNotNull(before.indexOfShortcut(appId))

        // The app's own long-press action: a touch that the emulator reads as a tap would open the app instead.
        compose.onNode(hasAnyAncestor(hasTestTag("home-app-$appId")) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("Move on Home").performClick()
        compose.onNodeWithText("Move to first position").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Move on Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close app options").performClick()
        assertEquals(before, model().state.value.layout)
    }

    @Test fun helpIsReachableFromPackedPageEntryAndBackReturnsToCustomization() {
        ready()
        val before = model().state.value.layout
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-support").performScrollTo().performClick()
        compose.onNodeWithTag("customization-help").performClick()
        compose.onNodeWithText("Customize any page").assertIsDisplayed()
        compose.onNodeWithTag("help-home-settings").assertExists()
        compose.onNodeWithTag("help-add-widget").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-shade-setup").performScrollTo().assertIsDisplayed()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("customization-help")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertEquals(before, model().state.value.layout)
    }
}
