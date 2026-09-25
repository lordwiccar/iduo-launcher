package media.whitewhale.iduo

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the installed Google app; never submits a query or changes account data. */
class GoogleSearchIntegrationTest {
    @get:Rule val withoutFeed = WithoutNativeFeed()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).use { it.readBytes() }
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!predicate()) { check(SystemClock.uptimeMillis() < deadline) { "Google search UI timed out" }; SystemClock.sleep(100) }
    }
    @Test fun searchButtonOpensEmptyGoogleSearchAndBackRestoresHome() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        shell("input touchscreen motionevent CANCEL 0 0")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var preferences: String? = null
            scenario.onActivity { preferences = it.getSharedPreferences("launcher", 0).getString("state", null) }
            await { find(automation.rootInActiveWindow) { it.contentDescription == "Search Google" } != null }
            val search = find(automation.rootInActiveWindow) { it.contentDescription == "Search Google" }!!
            val bounds = android.graphics.Rect().also(search::getBoundsInScreen)
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            await { find(automation.rootInActiveWindow) { it.packageName == DiscoverClient.GOOGLE_PACKAGE && it.isEditable } != null }
            val field = find(automation.rootInActiveWindow) { it.packageName == DiscoverClient.GOOGLE_PACKAGE && it.isEditable }!!
            assertTrue("Google must open without a submitted query", field.text.isNullOrBlank() || field.isShowingHintText)
            shell("input keyevent KEYCODE_BACK")
            SystemClock.sleep(400)
            if (automation.rootInActiveWindow?.packageName == DiscoverClient.GOOGLE_PACKAGE) shell("input keyevent KEYCODE_BACK")
            await { find(automation.rootInActiveWindow) { it.contentDescription == "Search Google" } != null }
            scenario.onActivity { assertEquals(preferences, it.getSharedPreferences("launcher", 0).getString("state", null)) }
        }
    }
}
