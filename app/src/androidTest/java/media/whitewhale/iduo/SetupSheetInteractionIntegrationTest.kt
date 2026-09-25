@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package media.whitewhale.iduo

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetupSheetInteractionIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showSetup(prefsName: String) {
        context.deleteSharedPreferences(prefsName)
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            val finish = {
                SetupExperience(context, prefsName).finish()
                visible = false
            }
            DuoTheme {
                if (visible) ModalBottomSheet(
                    onDismissRequest = finish,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    FirstRunSetupSheet(
                        isDefaultHome = false,
                        onMakeDefault = {},
                        onAddWidget = finish,
                        onExplore = finish,
                        onSkip = finish,
                    )
                }
            }
        }
        compose.onNodeWithTag("setup-explore").assertExists()
    }

    private fun assertFinished(prefsName: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("setup-explore")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        assertEquals(SetupEntryDecision.ALREADY_FINISHED,
            SetupExperience(context, prefsName).entryDecision(hadLauncherState = false))
        context.deleteSharedPreferences(prefsName)
    }

    @Test fun closeDismissesDurably() {
        val prefsName = "setup_sheet_close_test"
        try {
            showSetup(prefsName)
            compose.onNodeWithTag("setup-close").performClick()
            assertFinished(prefsName)
        } finally { context.deleteSharedPreferences(prefsName) }
    }

    @Test fun exploreDismissesDurably() {
        val prefsName = "setup_sheet_explore_test"
        try {
            showSetup(prefsName)
            compose.onNodeWithTag("setup-explore").performScrollTo().performClick()
            assertFinished(prefsName)
        } finally { context.deleteSharedPreferences(prefsName) }
    }

    @Test fun systemBackDismissesDurably() {
        val prefsName = "setup_sheet_back_test"
        try {
            showSetup(prefsName)
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            assertFinished(prefsName)
        } finally { context.deleteSharedPreferences(prefsName) }
    }
}
