package media.whitewhale.iduo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LeadingWorkspaceIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(15_000) { !model.state.value.loading }
        val density = compose.activity.resources.displayMetrics.density
        check(compose.activity.windowManager.currentWindowMetrics.bounds.width() / density >= 650f) {
            "LeadingWorkspaceIntegrationTest requires the inner display"
        }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
    }

    @Test fun unfoldedLeadingPageIsAFullGridCenteredInThePhysicalLeftHalf() {
        ready()
        compose.onNodeWithTag("expanded-leading-home").assertIsDisplayed()
        repeat(HOME_CELLS) { local ->
            compose.onNodeWithTag("home-cell-${homeCellIndex(-1, local)}").assertExists()
        }

        val root = compose.onNodeWithTag("launcher-root").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("home-cell-${homeCellIndex(-1, 0)}").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("home-cell-${homeCellIndex(-1, 3)}").fetchSemanticsNode().boundsInRoot
        val gridCenter = (first.center.x + last.center.x) / 2f
        assertTrue("leading grid center=$gridCenter left-half center=${root.left + root.width / 4f}",
            abs(gridCenter - (root.left + root.width / 4f)) < 1.1f)

        val firstNormal = compose.onNodeWithTag("home-cell-0").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, firstNormal.width, 1f)
        assertEquals(first.height, firstNormal.height, 1f)
    }
}
