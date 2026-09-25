package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotSame
import org.junit.Test

/** Native Discover stays attached while Android dispatches real HOME intents. */
class HomeReturnIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Home return timed out" }
            SystemClock.sleep(30)
        }
    }

    private fun node(description: String, state: Boolean = false): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun find(candidate: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (candidate == null) return null
            if ((if (state) candidate.stateDescription else candidate.contentDescription)
                    ?.toString() == description) return candidate
            for (index in 0 until candidate.childCount) find(candidate.getChild(index))?.let { return it }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { find(it.root) }
    }

    private fun click(description: String) {
        var bounds: android.graphics.Rect? = null
        var stableSince = 0L
        await {
            val candidate = node(description)?.takeIf { it.isVisibleToUser && it.isEnabled }
                ?.let { android.graphics.Rect().also(it::getBoundsInScreen) }?.takeUnless { it.isEmpty }
            if (candidate != bounds) { bounds = candidate; stableSince = SystemClock.uptimeMillis() }
            candidate != null && SystemClock.uptimeMillis() - stableSince >= 250L
        }
        checkNotNull(bounds).let { shell("input tap ${it.centerX()} ${it.centerY()}") }
    }

    private fun waitForReturnedPage(expected: String, expectedOwner: MainActivity? = null) {
        var stableSince = 0L
        val deadline = SystemClock.uptimeMillis() + 15_000L
        while (true) {
            val returned = automation.rootInActiveWindow?.packageName?.toString() == context.packageName &&
                LiveDiscover.owner.get()?.hasWindowFocus() == true &&
                (expectedOwner == null || LiveDiscover.owner.get() === expectedOwner) &&
                node(expected, state = true)?.isVisibleToUser == true
            if (!returned) stableSince = 0L
            else if (stableSince == 0L) stableSince = SystemClock.uptimeMillis()
            if (returned && SystemClock.uptimeMillis() - stableSince >= 300L) return
            if (SystemClock.uptimeMillis() >= deadline) {
                val directory = context.filesDir
                java.io.File(directory, "home-return-timeout.png").outputStream().use { output ->
                    automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                val main = LiveDiscover.owner.get()
                val states = mutableListOf<String>()
                fun collect(candidate: AccessibilityNodeInfo?) {
                    if (candidate == null) return
                    candidate.stateDescription?.toString()?.let(states::add)
                    for (index in 0 until candidate.childCount) collect(candidate.getChild(index))
                }
                automation.windows.forEach { collect(it.root) }
                val evidence = "expected=$expected activePackage=${automation.rootInActiveWindow?.packageName} " +
                    "owner=$main hasFocus=${main?.hasWindowFocus()} lifecycle=${main?.lifecycle?.currentState} " +
                    "sameOwner=${expectedOwner == null || main === expectedOwner} " +
                    "pagerStates=${states.distinct()}"
                java.io.File(directory, "home-return-timeout.txt").writeText(evidence)
                java.io.File(directory, "home-return-timeout-windows.txt").writeText(
                    shell("dumpsys window windows")
                )
                error("Home return timed out: $evidence")
            }
            SystemClock.sleep(30)
        }
    }

    @Test fun systemHomeKeepsTheSelectedHomePageAndReturnsOtherSurfacesToIt() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) {
            "Home return instrumentation only runs on an emulator"
        }
        LiveDiscover.attachNativeFeed = true
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME")
            .lineSequence().firstOrNull().orEmpty()
        shell("cmd role add-role-holder android.app.role.HOME media.whitewhale.iduo 0")
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var before: HomeLayout? = null
        try {
            await { LiveDiscover.owner.get() != null }
            await {
                var loaded = false
                instrumentation.runOnMainSync {
                    loaded = !ViewModelProvider(LiveDiscover.owner.get()!!)[LauncherModel::class.java]
                        .state.value.loading
                }
                loaded
            }
            var pages = 0
            instrumentation.runOnMainSync {
                val model = ViewModelProvider(LiveDiscover.owner.get()!!)[LauncherModel::class.java]
                before = model.state.value.layout
                val apps = model.state.value.apps.filter { it.id !in model.state.value.dock }.take(2)
                check(apps.size == 2) { "Fixture needs two apps outside the dock" }
                model.state.value.order.toList().forEach { model.setPinned(it, false) }
                model.applyDrop(apps[0].id, DropTarget.Home(0))
                model.applyDrop(apps[1].id, DropTarget.Home(HOME_CELLS))
                pages = model.state.value.homePages
            }
            check(pages >= 2) { "Fixture did not create a second Home page" }
            val expected = "Home page 2 of $pages"
            click("Home page 2")
            await { node(expected, state = true)?.isVisibleToUser == true }
            val selectedOwner = checkNotNull(LiveDiscover.owner.get())

            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
            shell("input keyevent KEYCODE_HOME")
            waitForReturnedPage(expected, selectedOwner)

            val oldOwner = checkNotNull(LiveDiscover.owner.get())
            instrumentation.runOnMainSync { oldOwner.recreate() }
            await { LiveDiscover.owner.get()?.let { it !== oldOwner } == true }
            assertNotSame(oldOwner, LiveDiscover.owner.get())
            waitForReturnedPage(expected)

            click("All apps page")
            await { node("All apps", state = true)?.isVisibleToUser == true }
            shell("input keyevent KEYCODE_HOME")
            waitForReturnedPage(expected)

            click("Discover")
            await { node("Discover", state = true)?.isVisibleToUser == true }
            shell("input keyevent KEYCODE_HOME")
            waitForReturnedPage(expected)
        } finally {
            before?.let { layout -> instrumentation.runOnMainSync {
                LiveDiscover.owner.get()?.let { ViewModelProvider(it)[LauncherModel::class.java].restoreLayout(layout) }
            } }
            val cleanupHome = previousHome.takeIf { it.isNotEmpty() && it != "media.whitewhale.iduo" }
                ?: "com.google.android.apps.nexuslauncher"
            shell("cmd role add-role-holder android.app.role.HOME $cleanupHome 0")
            try {
                instrumentation.runOnMainSync {
                    LiveDiscover.owner.get()?.finish()
                    LiveDiscover.host.get()?.finish()
                }
            } finally {
                if (previousHome.isNotEmpty())
                    shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
                else shell("cmd role remove-role-holder android.app.role.HOME $cleanupHome 0")
            }
        }
    }
}
