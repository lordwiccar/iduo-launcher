package media.whitewhale.iduo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() { compose.waitUntil(15000) { !model().state.value.loading } }
    private fun restoreLayout(before: HomeLayout) {
        compose.runOnIdle { model().restoreLayout(before) }
    }
    private fun assertPage(value: String) = compose.onNodeWithTag("app-pager")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @Test fun catalogRefreshReusesUnchangedIconsAndReloadsAnUpdatedPackage() {
        ready()
        val before = model().state.value.apps
        var revision = model().completedRefreshes
        compose.runOnIdle { model().refresh() }
        compose.waitUntil(15000) { model().completedRefreshes > revision }
        before.forEach { old -> org.junit.Assert.assertSame(old.icon, model().state.value.apps.first { it.id == old.id }.icon) }
        val target = before.first()
        val pkg = android.content.ComponentName.unflattenFromString(target.id)!!.packageName
        revision = model().completedRefreshes
        compose.runOnIdle { model().refresh(pkg) }
        compose.waitUntil(15000) { model().completedRefreshes > revision }
        org.junit.Assert.assertNotSame(target.icon, model().state.value.apps.first { it.id == target.id }.icon)
        val other = before.first { !it.id.startsWith("$pkg/") }
        org.junit.Assert.assertSame(other.icon, model().state.value.apps.first { it.id == other.id }.icon)
    }

    @Test fun libraryQueryPinsDockAndWidgetsSurviveCoverInnerCoverResize() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) { "Resize test only runs on an emulator" }
        ready()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val prior = model().state.value
        try {
            compose.onNodeWithTag("library-page-link").performClick()
            compose.onNodeWithTag("library-search").performTextInput("Clock")
            for (size in listOf("1248x1972", "2448x1848", "1248x1972")) {
                shell("wm size $size")
                compose.waitUntil(15000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == size.substringBefore('x').toInt() }
                compose.waitForIdle()
                assertPage("All apps")
                compose.onNodeWithTag("library-search").assertTextContains("Clock")
                org.junit.Assert.assertEquals(prior.order, model().state.value.order)
                org.junit.Assert.assertEquals(prior.dock, model().state.value.dock)
                org.junit.Assert.assertEquals(prior.widgets, model().state.value.widgets)
            }
        } finally { shell("wm size ${originalSize ?: "reset"}") }
    }

    @Test fun dockRemainsFixedDuringPagingAndRecreation() {
        ready()
        val pages = homePageCount(model().state.value.order.size)
        val before = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("app-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val expected = if (pages == 1) "All apps" else "Home page 2 of $pages"
        assertPage(expected)
        org.junit.Assert.assertEquals(before, compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot)
        compose.activityRule.scenario.recreate()
        ready()
        assertPage(expected)
        if (expected == "All apps") {
            compose.onNodeWithTag("search").assertDoesNotExist()
            compose.onNodeWithTag("settings").assertDoesNotExist()
        } else compose.onNodeWithTag("search").assertIsDisplayed()
    }

    @Test fun keyboardSearchKeepsAllFourDockTargetsVisible() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) { "IME setting changes only run on an emulator" }
        ready()
        val priorImeSetting = shell("settings get secure show_ime_with_hard_keyboard").trim()
        val priorHandwritingSetting = shell("settings get secure stylus_handwriting_enabled").trim()
        val priorDock = model().state.value.dock
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            // A floating handwriting toolbar reports IME-visible without reducing app height.
            shell("settings put secure stylus_handwriting_enabled 0")
            compose.onNodeWithTag("library-page-link").performClick()
            compose.onNodeWithTag("library-search").performTouchInput { click() }
            compose.onNodeWithTag("library-search").performTextInput("Clock")
            var lastGeometry: List<Int>? = null
            var stableGeometrySamples = 0
            compose.waitUntil(10000) {
                val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                val geometry = runCatching {
                    listOf(imeBottom) + (0..3).flatMap {
                        compose.onNodeWithTag("dock-slot-$it").fetchSemanticsNode().boundsInRoot.let { bounds ->
                            listOf(bounds.top.toInt(), bounds.bottom.toInt())
                        }
                    }
                }.getOrNull()
                stableGeometrySamples = if (geometry != null && geometry == lastGeometry) stableGeometrySamples + 1 else 0
                lastGeometry = geometry
                insets?.isVisible(WindowInsetsCompat.Type.ime()) == true &&
                    imeBottom > compose.activity.window.decorView.height * .2f && stableGeometrySamples >= 3
            }
            compose.waitForIdle()
            compose.onNodeWithTag("search").assertDoesNotExist()
            compose.onNodeWithTag("settings").assertDoesNotExist()
            val dock = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
            val density = compose.activity.resources.displayMetrics.density
            val slotBounds = (0..3).associateWith {
                compose.onNodeWithTag("dock-slot-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            val root = compose.onNodeWithTag("launcher-root").fetchSemanticsNode().boundsInRoot
            val ime = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            for (index in 0..3) {
                val slot = requireNotNull(slotBounds[index])
                val evidence = "slot=$slot slots=$slotBounds dock=$dock root=$root density=$density ime=$ime"
                org.junit.Assert.assertTrue("Dock target $index is clipped; $evidence", slot.height >= 48f * density - 2f)
                org.junit.Assert.assertTrue("Dock target $index overflows; $evidence", slot.top >= dock.top && slot.bottom <= dock.bottom)
            }
            org.junit.Assert.assertEquals(priorDock, model().state.value.dock)
        } finally {
            shell(if (priorImeSetting == "null") "settings delete secure show_ime_with_hard_keyboard"
                else "settings put secure show_ime_with_hard_keyboard $priorImeSetting")
            shell(if (priorHandwritingSetting == "null") "settings delete secure stylus_handwriting_enabled"
                else "settings put secure stylus_handwriting_enabled $priorHandwritingSetting")
            compose.runOnIdle {
                WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
            }
        }
    }

    @Test fun searchOpensFinalPageAndFiltersInstalledApps() {
        ready()
        compose.onNodeWithTag("library-page-link").performClick()
        compose.waitForIdle()
        assertPage("All apps")
        compose.onNodeWithTag("library-search").performTextInput("zzzz-no-such-app-123")
        compose.onNodeWithText("No apps found").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("No apps found").assertDoesNotExist()
    }

    @Test fun normalAllAppsUsesFullWidthRowsWithoutPinsOrNumberedDockActions() {
        ready()
        val app = model().state.value.apps.first()
        compose.onNodeWithTag("library-page-link").performClick()
        compose.onNodeWithTag("library-search").performTextInput(app.label)
        compose.onNodeWithTag("pin-${app.id}").assertDoesNotExist()
        compose.runOnIdle {
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
        compose.waitForIdle()
        val row = compose.onNodeWithTag("library-app-${app.id}").assertExists()
        row.performScrollTo()
        // boundsInRoot is clipped by the LazyColumn viewport and can briefly report
        // only the visible slice while a resize or IME layout is settling.
        val density = compose.activity.resources.displayMetrics.density
        val onePixel = (1f / density).dp
        val settled = runCatching { compose.waitUntil(5000) {
            row.getUnclippedBoundsInRoot().let { it.bottom - it.top >= 60.dp - onePixel } &&
                row.fetchSemanticsNode().boundsInRoot.height >= 60f * density - 2f
        } }.isSuccess
        compose.waitForIdle()
        val measured = row.getUnclippedBoundsInRoot()
        val visible = row.fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("launcher-root").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("all-apps-list").fetchSemanticsNode().boundsInRoot
        val window = compose.activity.windowManager.currentWindowMetrics.bounds
        val decor = compose.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        val imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime())
        val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom
        org.junit.Assert.assertTrue(
            "All-app rows retain a 60dp touch target; settled=$settled, measured=$measured, " +
                "visible=$visible, list=$list, root=$root, " +
                "density=$density, " +
                "window=${window.width()}x${window.height()}, decor=${decor.width}x${decor.height}, " +
                "imeVisible=$imeVisible, imeBottom=$imeBottom",
            measured.bottom - measured.top >= 60.dp - onePixel && visible.height >= 60f * density - 2f,
        )
        row.assertIsDisplayed().performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("Add to dock").assertDoesNotExist()
        compose.onNodeWithText("App info").assertIsDisplayed()
    }

    @Test fun defaultHomeActionRemainsAvailableAfterVisitingHomeLayout() {
        compose.openHomeCustomization()
        compose.onNodeWithTag("default-home-settings").assertIsDisplayed()
        compose.onNodeWithTag("customization-home").performClick()
        compose.onNodeWithText("Choose Home apps").assertIsDisplayed()
        compose.onNodeWithText("Add widget to this page").performScrollTo()
        compose.onNodeWithTag("customization-back").performClick()
        compose.onNodeWithTag("default-home-settings").assertIsDisplayed()
    }

    @Test fun dockAssignmentSurvivesActivityRecreation() {
        ready()
        val before = model().state.value.layout
        try {
            val clock = model().state.value.apps.first { it.label == "Clock" }
            val fillers = model().state.value.apps.filter { it.id != clock.id }.take(4)
            org.junit.Assert.assertEquals("Fixture needs four non-Clock apps", 4, fillers.size)
            compose.runOnIdle { fillers.forEachIndexed { index, app -> model().setDock(index, app.id) } }
            val full = model().state.value.layout
            val preferences = compose.activity.getSharedPreferences("launcher", 0).getString("state", null)
            val revision = model().state.value.editRevision
            compose.onNodeWithTag("dock-slot-0").performTouchInput { longClick() }
            compose.onNodeWithTag("search-field").performTextInput(clock.label)
            compose.onNodeWithTag("dock-full-guidance").assertIsDisplayed()
            compose.onNodeWithTag("picker-app-${clock.id}").assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithContentDescription("Choose Clock").assertDoesNotExist()
            org.junit.Assert.assertEquals(full, model().state.value.layout)
            org.junit.Assert.assertEquals(revision, model().state.value.editRevision)
            org.junit.Assert.assertEquals(preferences, compose.activity.getSharedPreferences("launcher", 0).getString("state", null))

            compose.onNodeWithText("Leave this position empty").performClick()
            compose.onNodeWithTag("dock-full-guidance").assertDoesNotExist()
            compose.onNodeWithTag("picker-app-${clock.id}").assertIsEnabled()
            compose.onNodeWithContentDescription("Choose Clock").performClick()
            compose.onNodeWithTag("dock-slot-0").assertContentDescriptionEquals("Clock")
            compose.activityRule.scenario.recreate()
            ready()
            compose.onNodeWithTag("dock-slot-0").assertContentDescriptionEquals("Clock")
        } finally { restoreLayout(before) }
    }

    @Test fun pinEditorStaysOpenAcrossPageBoundaryAndUnpinningKeepsApp() {
        ready()
        val apps = model().state.value.apps
        org.junit.Assert.assertTrue("Google emulator needs at least 17 launchable activities", apps.size >= 17)
        val before = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                apps.take(16).forEach { model().setPinned(it.id, true) }
            }
            val target = apps[16]
            compose.openHomeCustomization()
            compose.onNodeWithTag("customization-home").performClick()
            compose.onNodeWithText("Choose Home apps").performClick()
            compose.onNodeWithTag("pin-search").performTextInput(target.label)
            compose.onNodeWithTag("pin-${target.id}").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Choose home apps").assertIsDisplayed()
            org.junit.Assert.assertEquals(2, model().state.value.homePages)
            compose.activityRule.scenario.recreate()
            ready()
            compose.onNodeWithText("Choose home apps").assertIsDisplayed()
            compose.onNodeWithTag("pin-${target.id}").performClick()
            compose.waitForIdle()
            org.junit.Assert.assertEquals(1, model().state.value.homePages)
            compose.onNodeWithTag("library-app-${target.id}").assertIsDisplayed()
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun emptyHomeAndPinChoicesSurviveRefresh() {
        ready()
        val prior = model().state.value.order
        try {
            compose.runOnIdle { prior.forEach { model().setPinned(it, false) }; model().refresh() }
            ready()
            org.junit.Assert.assertTrue(model().state.value.order.isEmpty())
            compose.activityRule.scenario.recreate()
            ready()
            compose.onNodeWithTag("add-home-apps").assertDoesNotExist()
            compose.onNodeWithTag("library-page-link").performClick()
            assertPage("All apps")
        } finally { compose.runOnIdle { prior.forEach { model().setPinned(it, true) } } }
    }

    @Test fun statusRailCanBeDisabledAndRestored() {
        ready()
        compose.onNodeWithTag("status-rail").assertIsDisplayed()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-gestures").performClick()
        compose.onNodeWithTag("status-switch").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close customization").performClick()
        compose.onNodeWithTag("status-rail").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithTag("status-rail").assertDoesNotExist()
        compose.runOnIdle { model().setVerticalStatus(true) }
        compose.onNodeWithTag("status-rail").assertIsDisplayed()
    }
}
