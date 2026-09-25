package media.whitewhale.iduo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The same pager must support partial, reversible Discover gestures without a native service. */
class DiscoverIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() { compose.waitUntil(15000) { !model().state.value.loading } }
    private fun assertPage(value: String) = compose.onNodeWithTag("app-pager")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

    @Test fun discoverSharesDockAndHomePreservesLayout() {
        ready()
        val before = model().state.value
        val homeDock = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithTag("discover-page-link").performClick()
        assertPage("Discover")
        assertEquals(homeDock, compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInWindow)
        compose.onNodeWithTag("discover-home").performClick()
        assertPage("Home page 1 of 1")
        assertEquals(before.homeSlots, model().state.value.homeSlots)
        assertEquals(before.dock, model().state.value.dock)
        assertEquals(before.widgetPlacements, model().state.value.widgetPlacements)
    }

    @Test fun discoverSearchUsesLibraryAndKeepsDock() {
        ready()
        compose.onNodeWithTag("app-pager").performTouchInput { swipeRight() }
        assertPage("Discover")
        compose.onNodeWithTag("library-page-link").performClick()
        compose.onNodeWithTag("library-search").assertIsDisplayed()
        for (slot in 0..3) compose.onNodeWithTag("dock-slot-$slot").assertIsDisplayed()
    }

    @Test fun heldEntryShowsBothPagesAndReversalCancels() {
        ready()
        val priorMessage = LiveDiscover.message.value
        try {
            compose.runOnIdle { LiveDiscover.message.value = null }
            val pager = compose.onNodeWithTag("app-pager")
            pager.performTouchInput {
                down(Offset(width * .15f, height * .78f))
                moveTo(Offset(width * .48f, height * .78f), 300)
            }
            compose.onNodeWithTag("discover-page").assertIsDisplayed()
            compose.onNodeWithTag("discover-recovery-surface").assertDoesNotExist()
            compose.onNodeWithTag("home-cell-0").assertIsDisplayed()
            pager.performTouchInput {
                moveTo(Offset(width * .17f, height * .78f), 300)
                up()
            }
            assertPage("Home page 1 of 1")
        } finally {
            compose.runOnIdle { LiveDiscover.message.value = priorMessage }
        }
    }
}
