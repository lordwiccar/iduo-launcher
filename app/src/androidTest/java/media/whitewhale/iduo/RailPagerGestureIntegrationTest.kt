package media.whitewhale.iduo

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Coverage for fixed controls that live outside the pager's own layout bounds. */
class RailPagerGestureIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun readyOnLastHomePage(): String {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) {
            !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading
        }
        compose.onNodeWithTag("library-page-link").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("app-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        return page().also { assertTrue(it.startsWith("Home page")) }
    }

    private fun page() = compose.onNodeWithTag("app-pager").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription]

    private fun dockIconTag(): String {
        val id = ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.dock
            .firstNotNullOfOrNull { it }
        checkNotNull(id) { "The pager gesture fixture needs one dock app" }
        return "dock-icon-$id"
    }

    private fun swipeOn(tag: String, deltaFraction: Float, reverseFraction: Float = 0f) {
        compose.onNodeWithTag(tag).performTouchInput {
            val start = center
            down(start)
            repeat(12) { step ->
                moveTo(start + Offset(width * deltaFraction * (step + 1) / 12f, 0f), 20)
            }
            repeat(12) { step ->
                moveTo(start + Offset(width * (deltaFraction + reverseFraction * (step + 1) / 12f), 0f), 20)
            }
            up()
        }
        compose.waitForIdle()
    }

    @Test fun dockAndRightRailShareHeldReversalAndOnePagePaging() {
        val home = readyOnLastHomePage()

        // A long held reversal on the fixed dock returns to its anchor.
        swipeOn("dock", deltaFraction = -.8f, reverseFraction = .72f)
        assertEquals(home, page())

        // The same gesture beginning on the bottom rail reaches only the adjacent page.
        swipeOn("search", deltaFraction = -3f)
        assertEquals("All apps", page())
    }

    @Test fun dockLongPressDragStillOwnsItsHeldPointer() {
        val home = readyOnLastHomePage()
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        val beforeLayout = model.state.value.layout
        val beforeWidgetBindings = model.state.value.widgetPlacements.map { it.slot to it.id }
        val icon = compose.onNodeWithTag(dockIconTag())
        icon.performTouchInput {
            val start = center
            down(start)
            advanceEventTime(700)
            moveTo(start + Offset(width * .35f, 0f), 30)
            moveTo(start, 30)
            up()
        }
        compose.waitForIdle()
        assertEquals(home, page())
        icon.assertExists()
        assertEquals(beforeLayout, model.state.value.layout)
        assertEquals(beforeWidgetBindings, model.state.value.widgetPlacements.map { it.slot to it.id })
    }

    @Test fun railButtonsRemainClickableAndMatchTheDockIconVisualGeometry() {
        readyOnLastHomePage()
        compose.onNodeWithTag("settings").assertDoesNotExist()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-gestures").performClick()
        compose.onNodeWithTag("google-search-switch").assertExists()
        compose.onNodeWithContentDescription("Close customization").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("google-search-switch").assertDoesNotExist()

        val dockCenter = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot.center.x
        val search = compose.onNodeWithTag("search-visual", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dockIcon = compose.onNodeWithTag(dockIconTag()).fetchSemanticsNode().boundsInRoot
        val minimumTouchPx = 48f * compose.activity.resources.displayMetrics.density

        assertTrue(abs(search.center.x - dockCenter) < 1f)
        assertTrue(abs(search.width - dockIcon.width) < 1f)
        assertTrue(compose.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot.width >= minimumTouchPx)
    }
}

class DownwardRailGestureIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun physicalDownSwipesUseTheInitialSeventyThirtySplit() {
        val opened = mutableStateListOf<ShadePanel>()
        compose.setContent {
            val pager = rememberPagerState(pageCount = { 2 })
            val limits = androidx.compose.runtime.remember(pager) { PageGestureLimits(pager) }
            Box(Modifier.fillMaxSize().onePageGestures(pager, limits, onDownwardSwipe = opened::add)
                .testTag("shade-gesture-harness")) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = false) { }
            }
        }
        fun swipeDownAt(startX: (Float) -> Float) {
            compose.onNodeWithTag("shade-gesture-harness").performTouchInput {
                val start = Offset(startX(width.toFloat()), height * .2f)
                down(start)
                moveTo(start + Offset(0f, height * .3f), 180)
                up()
            }
            compose.waitForIdle()
        }

        swipeDownAt { it * .69f }
        swipeDownAt { kotlin.math.ceil(it * .7f) + 1f }
        assertEquals(listOf(ShadePanel.NOTIFICATIONS, ShadePanel.QUICK_SETTINGS), opened.toList())
        assertEquals(ShadePanel.NOTIFICATIONS, shadePanelForStart(69.999f, 100f))
        assertEquals(ShadePanel.QUICK_SETTINGS, shadePanelForStart(70f, 100f))
    }
}
