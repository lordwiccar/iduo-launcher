package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real-window widget setup coverage. Discover remains attached throughout this test. */
class NativeWidgetLifecycleIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                val files = instrumentation.targetContext.filesDir
                java.io.File(files, "native-widget-timeout.png").outputStream().use { output ->
                    automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                java.io.File(files, "native-widget-timeout-hierarchy.txt").writeText(
                    automation.windows.joinToString("\n\n") { window ->
                        fun dump(node: AccessibilityNodeInfo?, depth: Int): String {
                            if (node == null) return ""
                            val line = "  ".repeat(depth) + "class=${node.className} text=${node.text} desc=${node.contentDescription} state=${node.stateDescription} " +
                                "visible=${node.isVisibleToUser} enabled=${node.isEnabled} clickable=${node.isClickable} editable=${node.isEditable}\n"
                            return line + (0 until node.childCount).joinToString("") { dump(node.getChild(it), depth + 1) }
                        }
                        dump(window.root, 0)
                    })
                java.io.File(files, "native-widget-timeout-windows.txt").writeText(shell("dumpsys window windows"))
                error("Timed out waiting for native widget UI; active=${automation.rootInActiveWindow?.packageName}")
            }
            SystemClock.sleep(40)
        }
    }

    private fun launcherModel(activity: MainActivity): LauncherModel {
        lateinit var model: LauncherModel
        instrumentation.runOnMainSync {
            model = ViewModelProvider(activity)[LauncherModel::class.java]
        }
        await { !model.state.value.loading }
        return model
    }

    private fun find(value: String): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (listOf(node.text, node.contentDescription, node.stateDescription).any { it?.toString() == value }) return node
            for (index in 0 until node.childCount) visit(node.getChild(index))?.let { return it }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { visit(it.root) }
    }

    private fun click(value: String) {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var candidate: AccessibilityNodeInfo? = null
        await {
            fun clickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (listOf(node.text, node.contentDescription, node.stateDescription).any { it?.toString() == value }) {
                    var current: AccessibilityNodeInfo? = node
                    while (current != null && !current.isClickable) current = current.parent
                    if (current?.isVisibleToUser == true && current.isEnabled && !current.isEditable) return current
                }
                for (index in 0 until node.childCount) clickable(node.getChild(index))?.let { return it }
                return null
            }
            candidate = automation.windows.firstNotNullOfOrNull { clickable(it.root) }
            candidate != null
        }
        assertTrue("Accessibility click failed for $value", candidate!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(250)
    }

    private fun clickAfterScrolling(value: String) {
        repeat(10) {
            val found = find(value)
            var clickable = found
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            if (clickable?.isVisibleToUser == true && clickable.isEnabled) {
                assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                SystemClock.sleep(250)
                return
            }
            fun contains(node: AccessibilityNodeInfo?, text: String): Boolean {
                if (node == null) return false
                if (listOf(node.text, node.contentDescription).any { it?.toString() == text }) return true
                return (0 until node.childCount).any { contains(node.getChild(it), text) }
            }
            val window = automation.windows.firstOrNull { contains(it.root, "Make it yours") || contains(it.root, "Home layout") }
            fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isVisibleToUser && node.isScrollable) return node
                for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
                return null
            }
            val scroller = scrollable(window?.root)
            val bounds = android.graphics.Rect().also { scroller?.getBoundsInScreen(it) }
            if (scroller == null || (!scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) &&
                    !scroller.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id))) {
                if (!bounds.isEmpty) androidx.test.uiautomator.UiDevice.getInstance(instrumentation).swipe(
                    bounds.centerX(), bounds.bottom - 40, bounds.centerX(), bounds.top + 40, 24)
            }
            SystemClock.sleep(180)
        }
        fail("Could not scroll to $value")
    }

    private fun tap(value: String) {
        var target: AccessibilityNodeInfo? = null
        await { find(value)?.also { target = it }?.isVisibleToUser == true }
        val bounds = android.graphics.Rect().also(requireNotNull(target)::getBoundsInScreen)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
        SystemClock.sleep(250)
    }

    private fun typeWidgetSearch(query: String) {
        var search: AccessibilityNodeInfo? = null
        await {
            var current = find("Search widgets")
            while (current != null && !current.isEditable) current = current.parent
            current?.also { search = it }?.isVisibleToUser == true
        }
        val bounds = android.graphics.Rect().also(requireNotNull(search)::getBoundsInScreen)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
        SystemClock.sleep(150)
        shell("input text ${query.replace(" ", "%s")}")
        await { find(query)?.isVisibleToUser == true }
    }

    @Test fun mandatoryConfigurationReturnsToMainPersistsAndDiscoverStillWorks() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        LiveDiscover.attachNativeFeed = true
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME").lineSequence().firstOrNull().orEmpty()
        val hadBindGrant = shell("dumpsys appwidget").lineSequence().any {
            it.contains("user=0 package=media.whitewhale.iduo")
        }
        shell("cmd role add-role-holder android.app.role.HOME media.whitewhale.iduo 0")
        val context = instrumentation.targetContext
        shell("input keyevent KEYCODE_HOME")
        var before: HomeLayout? = null
        var idsBefore = emptySet<Int>()
        var slot = -1
        var lastMain: MainActivity? = null
        try {
            await { LiveDiscover.owner.get() != null }
            var main = requireNotNull(LiveDiscover.owner.get())
            lastMain = main
            var model = launcherModel(main)
            val widgetsField = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
            var widgets = widgetsField
                .get(main) as WidgetController
            val provider = widgets.personalProviders().single {
                it.provider.packageName == "media.whitewhale.iduo.test" &&
                    it.provider.className.endsWith("RequiredConfigWidgetProvider")
            }
            instrumentation.runOnMainSync {
                before = model.state.value.layout
                idsBefore = widgets.host.appWidgetIds.toSet()
                val app = model.state.value.apps.first { it.id !in model.state.value.layout.dock }
                assertTrue(model.applyDrop(app.id, DropTarget.Home(47)))
                assertTrue("Fixture must create a second Home page", model.state.value.homePages >= 2)
                slot = model.nextWidgetSlot()
            }

            tap("Home page 2")
            shell("appwidget revokebind --package media.whitewhale.iduo --user 0")
            openNativeHomeCustomization(automation) { click("Customize launcher") }; click("Home layout")
            clickAfterScrolling("Add widget to this page")
            typeWidgetSearch(provider.loadLabel(main.packageManager).toString())
            click(provider.loadLabel(main.packageManager).toString())
            await { find("Ready to place")?.isVisibleToUser == true }
            click("Place")
            await { automation.rootInActiveWindow?.packageName == "com.android.settings" }
            await { widgets.pendingPlacement?.slot == slot }
            val bindCanceledId = requireNotNull(widgets.pendingPlacement).id
            click("Cancel")
            await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo" }
            await { widgets.pendingPlacement == null }
            assertNull("Canceling system widget consent must not place a widget", model.placement(slot))
            assertFalse("Canceling system widget consent must delete its allocated ID",
                bindCanceledId in widgets.host.appWidgetIds)

            openNativeHomeCustomization(automation) { click("Customize launcher") }; click("Home layout")
            clickAfterScrolling("Add widget to this page")
            typeWidgetSearch(provider.loadLabel(main.packageManager).toString())
            click(provider.loadLabel(main.packageManager).toString())
            await { find("Ready to place")?.isVisibleToUser == true }
            click("Place")
            await { automation.rootInActiveWindow?.packageName == "com.android.settings" }
            click("Always allow iDuo Launcher to create widgets and access their data")
            click("Create")
            SystemClock.sleep(1_000)
            java.io.File(context.filesDir, "native-widget-after-place.txt").writeText(buildString {
                appendLine("pending=${widgets.pendingPlacement}")
                appendLine("status=${widgets.setupStatus}")
                appendLine("failure=${widgets.failureMessage}")
                appendLine("placements=${model.state.value.widgetPlacements}")
                appendLine("hostIds=${widgets.host.appWidgetIds.contentToString()}")
                widgets.host.appWidgetIds.forEach { id ->
                    val info = widgets.manager.getAppWidgetInfo(id)
                    appendLine("id=$id provider=${info?.provider} configure=${info?.configure} features=${info?.widgetFeatures}")
                }
            })
            java.io.File(context.filesDir, "native-widget-after-place-activities.txt")
                .writeText(shell("dumpsys activity activities"))
            java.io.File(context.filesDir, "native-widget-after-place-logcat.txt")
                .writeText(shell("logcat -d -v threadtime -t 500"))
            await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
            await { widgets.pendingPlacement?.slot == slot }
            val canceledId = requireNotNull(widgets.pendingPlacement).id
            assertTrue(canceledId in widgets.host.appWidgetIds)

            click("Cancel fixture configuration")
            await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo" }
            main = requireNotNull(LiveDiscover.owner.get())
            lastMain = main
            model = launcherModel(main)
            widgets = widgetsField.get(main) as WidgetController
            await { widgets.pendingPlacement == null }
            assertNull(model.placement(slot))
            assertFalse(canceledId in widgets.host.appWidgetIds)

            openNativeHomeCustomization(automation) { click("Customize launcher") }; click("Home layout")
            clickAfterScrolling("Add widget to this page")
            typeWidgetSearch(provider.loadLabel(main.packageManager).toString())
            click(provider.loadLabel(main.packageManager).toString())
            await { find("Ready to place")?.isVisibleToUser == true }
            click("Place")
            await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
            await { widgets.pendingPlacement?.slot == slot }
            val committedId = requireNotNull(widgets.pendingPlacement).id
            await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
            click("Use configured widget")
            await(15_000) { model.placement(slot)?.id == committedId && widgets.pendingPlacement == null }
            await { find("Configured fixture is live · ${AppWidgetManager.getInstance(main).getAppWidgetOptions(committedId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)} × ${AppWidgetManager.getInstance(main)
                .getAppWidgetOptions(committedId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} dp") != null }
            assertEquals(provider.provider, widgets.manager.getAppWidgetInfo(committedId)?.provider)

            val activityDump = shell("dumpsys activity activities")
            assertFalse("Successful bind/configure results must not remain queued above MainActivity",
                activityDump.contains("ResultInfo{who=null, request=701") || activityDump.contains("ResultInfo{who=null, request=700"))

            click("Discover")
            await(20_000) { LiveDiscover.progress >= .99f && LiveDiscover.message.value == null }
            click("Back to home")
            await { LiveDiscover.progress == 0f }
            assertEquals(committedId, model.placement(slot)?.id)
        } finally {
            val cleanupHome = previousHome.takeIf { it.isNotEmpty() && it != "media.whitewhale.iduo" }
                ?: "com.google.android.apps.nexuslauncher"
            try {
                val cleanupMain = lastMain ?: LiveDiscover.owner.get()
                if (cleanupMain != null && before != null) instrumentation.runOnMainSync {
                    val model = ViewModelProvider(cleanupMain)[LauncherModel::class.java]
                    val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                        .get(cleanupMain) as WidgetController
                    model.restoreLayout(requireNotNull(before))
                    widgets.host.appWidgetIds.filter { it !in idsBefore }.forEach(widgets.host::deleteAppWidgetId)
                    assertEquals(idsBefore, widgets.host.appWidgetIds.toSet())
                }
            } finally {
                shell("cmd role add-role-holder android.app.role.HOME $cleanupHome 0")
                try {
                    instrumentation.runOnMainSync {
                        LiveDiscover.owner.get()?.finish()
                        LiveDiscover.host.get()?.finish()
                    }
                } finally {
                    shell("appwidget ${if (hadBindGrant) "grantbind" else "revokebind"} --package media.whitewhale.iduo --user 0")
                    if (previousHome.isNotEmpty()) shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
                    else shell("cmd role remove-role-holder android.app.role.HOME $cleanupHome 0")
                }
            }
        }
    }
}
