package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Keeps instrumentation and Main alive while HOME interrupts required widget configuration. */
class NativeWidgetHomeBehaviorIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) error("Timed out; active=${automation.rootInActiveWindow?.packageName}")
            SystemClock.sleep(50)
        }
    }
    private fun find(value: String): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (listOf(node.text, node.contentDescription, node.stateDescription).any { it?.toString() == value }) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { found -> return found } }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { visit(it.root) }
    }
    private fun click(value: String) {
        var target: AccessibilityNodeInfo? = null
        await {
            fun visit(candidate: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (candidate == null) return null
                if (listOf(candidate.text, candidate.contentDescription, candidate.stateDescription)
                        .any { it?.toString() == value }) {
                    var action: AccessibilityNodeInfo = candidate
                    while (!action.isClickable && action.parent != null) action = requireNotNull(action.parent)
                    if (action.isClickable && action.isVisibleToUser && action.isEnabled && !action.isEditable) return action
                }
                repeat(candidate.childCount) { visit(candidate.getChild(it))?.let { return it } }
                return null
            }
            automation.windows.firstNotNullOfOrNull { visit(it.root) }?.also { target = it } != null
        }
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(250)
    }
    private fun clickAfterScrolling(value: String) {
        repeat(14) {
            var node = find(value)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.isVisibleToUser == true && node.isEnabled) {
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(250); return
            }
            fun contains(candidate: AccessibilityNodeInfo?, text: String): Boolean {
                if (candidate == null) return false
                if (listOf(candidate.text, candidate.contentDescription).any { it?.toString() == text }) return true
                return (0 until candidate.childCount).any { contains(candidate.getChild(it), text) }
            }
            val window = automation.windows.firstOrNull { contains(it.root, "Make it yours") || contains(it.root, "Home layout") }
            fun scrollable(candidate: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (candidate == null) return null
                if (candidate.isVisibleToUser && candidate.isScrollable) return candidate
                repeat(candidate.childCount) { scrollable(candidate.getChild(it))?.let { found -> return found } }
                return null
            }
            val scroller = scrollable(window?.root) ?: return@repeat
            if (!scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) &&
                !scroller.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)) {
                val bounds = android.graphics.Rect().also(scroller::getBoundsInScreen)
                UiDevice.getInstance(instrumentation).swipe(
                    bounds.centerX(), bounds.bottom - 40, bounds.centerX(), bounds.top + 40, 24)
            }
            SystemClock.sleep(180)
        }
        fail("Could not scroll to $value")
    }
    private fun tap(value: String) {
        var node: AccessibilityNodeInfo? = null
        await { find(value)?.also { node = it }?.isVisibleToUser == true }
        val bounds = android.graphics.Rect().also(requireNotNull(node)::getBoundsInScreen)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}"); SystemClock.sleep(250)
    }
    private fun search(query: String) {
        var field: AccessibilityNodeInfo? = null
        await {
            var node = find("Search widgets")
            while (node != null && !node.isEditable) node = node.parent
            node?.takeIf { it.isVisibleToUser }?.also { field = it } != null
        }
        val bounds = android.graphics.Rect().also(requireNotNull(field)::getBoundsInScreen)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
        shell("input text ${query.replace(" ", "%s")}")
    }

    @Test fun homeDuringRequiredConfigurationReturnsToLiveLauncherAndResolvesOneId() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val args = InstrumentationRegistry.getArguments()
        val variant = args.getString("duoWidgetHomeVariant", "unspecified")
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val previousAttach = LiveDiscover.attachNativeFeed
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME").lineSequence().firstOrNull().orEmpty()
        val hadBindGrant = shell("dumpsys appwidget").lineSequence().any {
            it.contains("user=0 package=media.whitewhale.iduo")
        }
        var baseline: HomeLayout? = null
        var idsBefore = emptySet<Int>()
        var lastMain: MainActivity? = null
        var primaryFailure: Throwable? = null
        try {
            LiveDiscover.attachNativeFeed = true
            shell("cmd role add-role-holder android.app.role.HOME media.whitewhale.iduo 0")
            shell("appwidget grantbind --package media.whitewhale.iduo --user 0")
            shell("input keyevent KEYCODE_HOME")
            await { LiveDiscover.owner.get() != null }
            val main = requireNotNull(LiveDiscover.owner.get()); lastMain = main
            val initialMainIdentity = System.identityHashCode(main)
            var model = ViewModelProvider(main)[LauncherModel::class.java]
            await { !model.state.value.loading }
            val widgetsField = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
            var widgets = widgetsField
                .get(main) as WidgetController
            val provider = widgets.personalProviders().single {
                it.provider.packageName == "media.whitewhale.iduo.test" &&
                    it.provider.className.endsWith("RequiredConfigWidgetProvider")
            }
            var slot = -1
            instrumentation.runOnMainSync {
                baseline = model.state.value.layout
                idsBefore = widgets.host.appWidgetIds.toSet()
                val app = model.state.value.apps.first { it.available && it.id !in model.state.value.layout.dock }
                assertTrue(model.applyDrop(app.id, DropTarget.Home(47)))
                slot = model.nextWidgetSlot()
            }
            tap("Home page 2")
            openNativeHomeCustomization(automation) { click("Customize launcher") }; click("Home layout")
            clickAfterScrolling("Add widget to this page")
            val label = provider.loadLabel(main.packageManager).toString()
            search(label); await { find(label)?.isVisibleToUser == true }; click(label)
            await { find("Ready to place")?.isVisibleToUser == true }; click("Place")
            await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
            await { widgets.pendingPlacement?.slot == slot }
            val assignedId = requireNotNull(widgets.pendingPlacement).id
            assertTrue(assignedId in widgets.host.appWidgetIds)
            val beforeHome = shell("dumpsys activity activities")

            shell("input keyevent KEYCODE_HOME")
            val reachedHome = runCatching {
                await(8_000) { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo" }; true
            }.getOrDefault(false)
            val afterHome = shell("dumpsys activity activities")
            val postMain = LiveDiscover.owner.get()
            File(context.filesDir, "native-widget-home-$variant.txt").writeText(buildString {
                appendLine("reachedHome=$reachedHome")
                appendLine("mainIdentityBefore=$initialMainIdentity")
                appendLine("mainIdentityAfter=${postMain?.let { System.identityHashCode(it) }}")
                appendLine("pending=${widgets.pendingPlacement}")
                appendLine("status=${widgets.setupStatus}")
                appendLine("hostIds=${widgets.host.appWidgetIds.contentToString()}")
                appendLine("--- before HOME ---"); appendLine(beforeHome)
                appendLine("--- after HOME ---"); appendLine(afterHome)
            })
            assertTrue("HOME did not visibly return to Duo; see native-widget-home-$variant.txt", reachedHome)
            assertTrue("MainActivity was not resumed after HOME; see native-widget-home-$variant.txt",
                afterHome.lineSequence().any { line ->
                    (line.contains("mResumedActivity") || line.contains("topResumedActivity")) &&
                        line.contains("media.whitewhale.iduo/.MainActivity")
                })

            if (postMain != null && postMain !== main) {
                model = ViewModelProvider(postMain)[LauncherModel::class.java]
                widgets = widgetsField.get(postMain) as WidgetController
                await { !model.state.value.loading }
            }
            await(5_000) {
                widgets.pendingPlacement == null || find("Finish setup")?.isVisibleToUser == true
            }

            if (widgets.pendingPlacement == null) {
                assertNull(model.placement(slot))
                assertFalse("Canceled configuration must delete its allocated ID", assignedId in widgets.host.appWidgetIds)
            } else {
                assertEquals("Durable setup must retain the same ID", assignedId, widgets.pendingPlacement?.id)
                assertEquals(1, widgets.host.appWidgetIds.count { it == assignedId })
                click("Finish setup")
                await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
                click("Use configured widget")
                await { model.placement(slot)?.id == assignedId && widgets.pendingPlacement == null }
                assertEquals(assignedId, model.placement(slot)?.id)
            }
            assertFalse(shell("dumpsys activity activities").contains("ResultInfo{who=null, request=701"))
            click("Discover")
            await(20_000) { LiveDiscover.progress >= .99f && LiveDiscover.message.value == null }
            click("Back to home"); await { LiveDiscover.progress == 0f }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            val main = LiveDiscover.owner.get() ?: lastMain
            if (main != null && !main.isDestroyed && baseline != null) cleanup { instrumentation.runOnMainSync {
                val model = ViewModelProvider(main)[LauncherModel::class.java]
                val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                    .get(main) as WidgetController
                widgets.cancelPendingSetup()
                model.restoreLayout(requireNotNull(baseline))
                widgets.host.appWidgetIds.filter { it !in idsBefore }.forEach(widgets.host::deleteAppWidgetId)
                assertEquals("Every baseline widget binding must survive", idsBefore, widgets.host.appWidgetIds.toSet())
            } }
            val cleanupHome = previousHome.takeIf { it.isNotEmpty() && it != "media.whitewhale.iduo" }
                ?: "com.google.android.apps.nexuslauncher"
            cleanup { shell("cmd role add-role-holder android.app.role.HOME $cleanupHome 0") }
            cleanup { instrumentation.runOnMainSync { LiveDiscover.owner.get()?.finish(); LiveDiscover.host.get()?.finish() } }
            cleanup { shell("appwidget ${if (hadBindGrant) "grantbind" else "revokebind"} --package media.whitewhale.iduo --user 0") }
            cleanup {
                if (previousHome.isNotEmpty()) shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
                else shell("cmd role remove-role-holder android.app.role.HOME $cleanupHome 0")
            }
            LiveDiscover.attachNativeFeed = previousAttach
            cleanupFailure?.let { failure -> if (primaryFailure != null) primaryFailure?.addSuppressed(failure) else throw failure }
        }
    }
}
