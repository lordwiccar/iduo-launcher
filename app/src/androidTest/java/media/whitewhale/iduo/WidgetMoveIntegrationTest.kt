package media.whitewhale.iduo

import android.appwidget.AppWidgetManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Terminal widget-drop coverage: keep the pointer held while the preview recomposes. */
@RunWith(AndroidJUnit4::class)
class WidgetMoveIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun controller() = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        .get(compose.activity) as WidgetController
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
    private fun page() = compose.onNodeWithTag("app-pager").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription]
    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) { !model().state.value.loading }
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private data class BoundClock(val slot: Int, val id: Int, val provider: android.content.ComponentName)

    private fun allocateClock(): BoundClock {
        val widgets = controller()
        val provider = widgets.manager.installedProviders.first {
            it.provider.className.endsWith("AnalogAppWidgetProvider")
        }
        val id = widgets.host.allocateAppWidgetId()
        try {
            assertTrue("Emulator fixture requires widget binding permission",
                widgets.manager.bindAppWidgetIdIfAllowed(id, provider.provider))
        } catch (failure: Throwable) {
            widgets.host.deleteAppWidgetId(id)
            throw failure
        }
        return BoundClock(model().nextWidgetSlot(), id, provider.provider)
    }

    private fun cleanup(before: HomeLayout, idsBefore: Set<Int>, clock: BoundClock?) {
        compose.runOnIdle { model().restoreLayout(before) }
        compose.waitForIdle()
        clock?.let { if (it.id !in idsBefore && it.id in controller().host.appWidgetIds) controller().host.deleteAppWidgetId(it.id) }
        assertEquals("Every baseline widget binding, including Clock152, must survive",
            idsBefore, controller().host.appWidgetIds.toSet())
        assertEquals(before, model().state.value.layout)
    }

    @Test fun heldLivePreviewThenUpMovesThreeByThreeClockToScreenTwoRightmostAnchor() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        var clock: BoundClock? = null
        var pointerHeld = false
        var primaryFailure: Throwable? = null
        try {
            assertTrue("Fixture requires the preserved emulator baseline to occupy only Screen 1",
                before.widgetPlacements.none { it.page >= 1 })
            val bound = allocateClock()
            clock = bound
            val fixture = before.copy(
                slots = before.slots.take(HOME_CELLS),
                widgetPlacements = before.widgetPlacements + WidgetPlacement(bound.slot, bound.id, 1, 0, 0, 3, 3),
            )
            compose.runOnIdle { model().restoreLayout(fixture) }
            shell("wm size 2448x1848")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 2448 }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.waitUntil(5_000) { page().startsWith("Home page 2") }
            compose.onNodeWithTag("home-page-1").assertIsDisplayed()
            compose.waitForIdle()

            val sourceBounds = compose.onNodeWithTag("widget-slot-${bound.slot}").fetchSemanticsNode().boundsInRoot
            val grab = Offset(sourceBounds.right - 4f, sourceBounds.center.y)
            root().performTouchInput {
                down(grab); advanceEventTime(700); moveTo(grab + Offset(-4f, 0f))
            }
            pointerHeld = true
            root().performTouchInput { moveTo(center("home-cell-27"), 350) }
            compose.waitForIdle()

            // The live preview now covers the release point. This is the state that
            // previously let its Widget region shadow the Home cell underneath.
            val preview = compose.onNodeWithTag("widget-slot-${bound.slot}").fetchSemanticsNode().boundsInRoot
            assertTrue(preview.contains(center("home-cell-27")))
            root().performTouchInput { up() }
            pointerHeld = false
            compose.waitForIdle()

            assertEquals(WidgetPlacement(bound.slot, bound.id, 1, 1, 0, 3, 3), model().placement(bound.slot))
            assertEquals(bound.provider, AppWidgetManager.getInstance(compose.activity).getAppWidgetInfo(bound.id)?.provider)
            assertEquals("Home page 2 of 2", page())
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanupStep(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            if (pointerHeld) cleanupStep { root().performTouchInput { cancel() } }
            cleanupStep { shell("wm size ${originalSize ?: "reset"}") }
            cleanupStep { compose.waitForIdle(); ready() }
            cleanupStep { cleanup(before, idsBefore, clock) }
            cleanupFailure?.let { if (primaryFailure != null) primaryFailure?.addSuppressed(it) else throw it }
        }
    }

    @Test fun coverHeldEdgeTurnThenUpCommitsExactNextPageCellAndStaysThere() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        var clock: BoundClock? = null
        var pointerHeld = false
        var primaryFailure: Throwable? = null
        try {
            assertTrue("Fixture requires the preserved emulator baseline to occupy only Screen 1",
                before.widgetPlacements.none { it.page >= 1 })
            val bound = allocateClock()
            clock = bound
            val emptyApps = before.copy(slots = emptyList())
            val sourceDraft = (0 until HOME_CELLS).asSequence()
                .mapNotNull { widgetCandidate(emptyApps, bound.slot, it, 2, 2) }.first()
                .copy(id = bound.id)
            val fixture = emptyApps.copy(widgetPlacements = emptyApps.widgetPlacements + sourceDraft)
            compose.runOnIdle { model().restoreLayout(fixture) }

            shell("wm size 1248x1972")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 1248 }
            compose.waitForIdle()
            val sourceBounds = compose.onNodeWithTag("widget-slot-${bound.slot}").fetchSemanticsNode().boundsInRoot
            val grab = Offset(sourceBounds.left + sourceBounds.width * .2f, sourceBounds.top + sourceBounds.height * .2f)
            val rootBounds = root().fetchSemanticsNode().boundsInRoot
            root().performTouchInput {
                down(grab); advanceEventTime(700); moveTo(grab + Offset(4f, 0f))
                moveTo(Offset(rootBounds.right - 6f, grab.y), 300)
            }
            pointerHeld = true
            compose.waitUntil(5_000) { page() == "Home page 2 of 2" }

            val destination = center("home-cell-29")
            root().performTouchInput { moveTo(destination, 300) }
            compose.waitForIdle()
            assertTrue(compose.onNodeWithTag("widget-slot-${bound.slot}").fetchSemanticsNode().boundsInRoot.contains(destination))
            root().performTouchInput { up() }
            pointerHeld = false
            compose.waitForIdle()

            assertEquals(WidgetPlacement(bound.slot, bound.id, 1, 1, 1, 2, 2), model().placement(bound.slot))
            assertEquals("Home page 2 of 2", page())
            assertEquals(bound.provider, AppWidgetManager.getInstance(compose.activity).getAppWidgetInfo(bound.id)?.provider)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanupStep(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            if (pointerHeld) cleanupStep { root().performTouchInput { cancel() } }
            cleanupStep { shell("wm size ${originalSize ?: "reset"}") }
            cleanupStep { compose.waitForIdle(); ready() }
            cleanupStep { cleanup(before, idsBefore, clock) }
            cleanupFailure?.let { if (primaryFailure != null) primaryFailure?.addSuppressed(it) else throw it }
        }
    }
}
