package media.whitewhale.iduo

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupExperiencePersistenceIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "setup_experience_integration_test"

    @Test fun freshEntrySurvivesRecreationThenDismissalPersists() {
        context.deleteSharedPreferences(prefsName)
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        try {
            assertEquals(SetupEntryDecision.SHOW,
                SetupExperience(context, prefsName).entryDecision(hadLauncherState = false))
            // The normal launcher model has created state by the time an activity is recreated.
            assertEquals(SetupEntryDecision.SHOW,
                SetupExperience(context, prefsName).entryDecision(hadLauncherState = true))

            SetupExperience(context, prefsName).finish()
            assertEquals(SetupEntryDecision.ALREADY_FINISHED,
                SetupExperience(context, prefsName).entryDecision(hadLauncherState = true))
        } finally {
            context.deleteSharedPreferences(prefsName)
        }
    }

    @Test fun existingInstallDoesNotWriteSetupState() {
        context.deleteSharedPreferences(prefsName)
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val launcherPrefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val launcherBefore = launcherPrefs.all.toMap()
        try {
            assertEquals(SetupEntryDecision.EXISTING_INSTALL,
                SetupExperience(context, prefsName).entryDecision(hadLauncherState = true))
            assertTrue(prefs.all.isEmpty())
            assertEquals(launcherBefore, launcherPrefs.all)
        } finally {
            context.deleteSharedPreferences(prefsName)
        }
    }
}
