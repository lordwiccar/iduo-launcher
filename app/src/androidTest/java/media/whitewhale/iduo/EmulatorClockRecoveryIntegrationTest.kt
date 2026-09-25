package media.whitewhale.iduo

import android.appwidget.AppWidgetManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly invoked one-time repair for the disposable emulator fixture. */
class EmulatorClockRecoveryIntegrationTest {
    @Test fun replaceDanglingAnalogClockBinding() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("duoRecoverClock") == "true")
        assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[LauncherModel::class.java]
                val field = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                val widgets = field.get(activity) as WidgetController
                val deadline = android.os.SystemClock.uptimeMillis() + 15_000
                while (model.state.value.loading && android.os.SystemClock.uptimeMillis() < deadline)
                    android.os.SystemClock.sleep(20)
                val old = requireNotNull(model.placement(2))
                assertEquals(9, old.id)
                assertNull(widgets.manager.getAppWidgetInfo(old.id))
                val provider = widgets.personalProviders().single {
                    it.provider.packageName == "com.google.android.deskclock" &&
                        it.provider.className == "com.android.alarmclock.AnalogAppWidgetProvider"
                }
                val beforeIds = widgets.host.appWidgetIds.toSet()
                assertFalse(9 in beforeIds)
                val newId = widgets.host.allocateAppWidgetId()
                assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(newId, provider.profile, provider.provider, null))
                val replacement = old.copy(id = newId)
                model.restoreLayout(model.state.value.layout.copy(widgetPlacements =
                    model.state.value.layout.widgetPlacements.map { if (it.slot == old.slot) replacement else it }))
                assertEquals(replacement, model.placement(old.slot))
                assertEquals(provider.provider, widgets.manager.getAppWidgetInfo(newId)?.provider)
                val options = AppWidgetManager.getInstance(activity).getAppWidgetOptions(newId)
                java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                    "emulator-clock-recovery.txt").writeText(
                    "oldId=9 newId=$newId provider=${provider.provider.flattenToString()} placement=$replacement " +
                        "beforeIds=${beforeIds.sorted()} afterIds=${widgets.host.appWidgetIds.sorted()} options=$options\n")
            }
        }
    }
}
