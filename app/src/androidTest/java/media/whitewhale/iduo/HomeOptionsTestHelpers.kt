package media.whitewhale.iduo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput

/** Opens customization through the same narrow background margin used on a packed Home page. */
internal fun ComposeTestRule.openHomeCustomization(page: Int? = null) {
    val margin = page?.let { onNodeWithTag("home-options-margin-$it") }
        ?: (-1..20).firstNotNullOfOrNull { candidate ->
            onNodeWithTag("home-options-margin-$candidate").takeIf {
                runCatching { it.assertIsDisplayed() }.isSuccess
            }
        }
        ?: error("No visible Home options margin")
    margin.performTouchInput {
        down(Offset(center.x, center.y))
        advanceEventTime(750)
        up()
    }
    waitForIdle()
    onNodeWithTag("empty-space-customize").performClick()
}
