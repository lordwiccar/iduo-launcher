package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real-window coverage for both installed Jake's Good Weather providers. */
class WeatherWidgetIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) error("Timed out waiting for Weather widget state")
            SystemClock.sleep(40)
        }
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
        var target: AccessibilityNodeInfo? = null
        await {
            var node = find(value)
            while (node != null && !node.isClickable) node = node.parent
            node?.takeIf { it.isVisibleToUser && it.isEnabled && !it.isEditable }?.also { target = it } != null
        }
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(250)
    }
    private fun clickAfterScrolling(value: String) {
        for (attempt in 0 until 16) {
            var node = find(value)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.isVisibleToUser == true && node.isEnabled) {
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(250); return
            }
            fun contains(node: AccessibilityNodeInfo?, text: String): Boolean {
                if (node == null) return false
                if (listOf(node.text, node.contentDescription).any { it?.toString() == text }) return true
                return (0 until node.childCount).any { contains(node.getChild(it), text) }
            }
            val settingsWindow = automation.windows.firstOrNull { contains(it.root, "Make it yours") || contains(it.root, "Home layout") }
            val scroller = settingsWindow?.let { window ->
                fun scrollable(candidate: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (candidate == null) return null
                    if (candidate.isVisibleToUser && candidate.isScrollable) return candidate
                    for (index in 0 until candidate.childCount)
                        scrollable(candidate.getChild(index))?.let { return it }
                    return null
                }
                scrollable(window.root)
            }
            if (scroller == null) break
            if (!scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) &&
                !scroller.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)) {
                val bounds = android.graphics.Rect().also(scroller::getBoundsInScreen)
                androidx.test.uiautomator.UiDevice.getInstance(instrumentation).swipe(
                    bounds.centerX(), bounds.bottom - 40, bounds.centerX(), bounds.top + 40, 24)
            }
            SystemClock.sleep(180)
        }
        saveFailureEvidence("weather-add-widget-scroll")
        fail("Could not scroll to $value")
    }

    private fun saveFailureEvidence(prefix: String) {
        val directory = instrumentation.targetContext.filesDir
        File(directory, "$prefix.png").outputStream().use { output ->
            automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        File(directory, "$prefix-hierarchy.txt").writeText(automation.windows.joinToString("\n\n") { window ->
            fun dump(node: AccessibilityNodeInfo?, depth: Int): String {
                if (node == null) return ""
                return "  ".repeat(depth) +
                    "class=${node.className} text=${node.text} desc=${node.contentDescription} " +
                    "visible=${node.isVisibleToUser} clickable=${node.isClickable} scrollable=${node.isScrollable}\n" +
                    (0 until node.childCount).joinToString("") { dump(node.getChild(it), depth + 1) }
            }
            dump(window.root, 0)
        })
        File(directory, "$prefix-windows.txt").writeText(shell("dumpsys window windows"))
    }
    private fun tap(value: String) {
        var target: AccessibilityNodeInfo? = null
        await { find(value)?.also { target = it }?.isVisibleToUser == true }
        val bounds = android.graphics.Rect().also(requireNotNull(target)::getBoundsInScreen)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}"); SystemClock.sleep(250)
    }
    private fun typeSearch(query: String) {
        var field: AccessibilityNodeInfo? = null
        await {
            var node = find("Search widgets")
            while (node != null && !node.isEditable) node = node.parent
            node?.takeIf { it.isVisibleToUser }?.also { field = it } != null
        }
        assertTrue(requireNotNull(field).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query) }))
    }
    private fun hostView(activity: MainActivity, id: Int): AppWidgetHostView? {
        fun visit(view: View): AppWidgetHostView? {
            if (view is AppWidgetHostView && view.appWidgetId == id) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))?.let { return it }
            return null
        }
        return visit(activity.window.decorView)
    }
    private fun weatherTexts(view: View, result: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
        if (view is ViewGroup) for (index in 0 until view.childCount) weatherTexts(view.getChildAt(index), result)
        return result
    }
    private fun rendered(activity: MainActivity, id: Int): Boolean {
        var rendered = false
        instrumentation.runOnMainSync {
            rendered = hostView(activity, id)?.let { host ->
                val texts = weatherTexts(host)
                host.isAttachedToWindow && host.childCount > 0 &&
                    texts.none { it.contains("Problem loading widget", true) } &&
                    (texts.containsAll(listOf("Choose a place", "Tap to set this widget’s location.")) || texts.size >= 2)
            } == true
        }
        return rendered
    }
    private fun addFromSettings(model: LauncherModel, provider: android.appwidget.AppWidgetProviderInfo,
        expectedIndex: Int): WidgetPlacement {
        val slot = model.nextWidgetSlot()
        openNativeHomeCustomization(automation) { click("Customize launcher") }; click("Home layout"); clickAfterScrolling("Add widget to this page")
        val pm = instrumentation.targetContext.packageManager
        typeSearch(pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0)).toString())
        val label = provider.loadLabel(pm).toString()
        await { find(label)?.isVisibleToUser == true }; click(label)
        await { find("Ready to place")?.isVisibleToUser == true }; click("Place")
        await { model.placement(slot)?.id?.let { it >= 0 } == true }
        return requireNotNull(model.placement(slot)).also {
            assertEquals(expectedIndex, it.page * HOME_CELLS + it.row * GRID_COLUMNS + it.column)
        }
    }
    private fun screenshot(name: String) {
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            requireNotNull(automation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun weatherCatalogBindsBothProvidersAtTheirActualSizesAndPersists() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")); LiveDiscover.attachNativeFeed = true
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME").lineSequence().firstOrNull().orEmpty()
        shell("cmd role add-role-holder android.app.role.HOME media.whitewhale.iduo 0"); shell("input keyevent KEYCODE_HOME")
        var before: HomeLayout? = null; var idsBefore = emptySet<Int>()
        var lastActivity: MainActivity? = null
        var lastModel: LauncherModel? = null
        var lastController: WidgetController? = null
        try {
            await { LiveDiscover.owner.get() != null }
            var activity = requireNotNull(LiveDiscover.owner.get())
            var model = ViewModelProvider(activity)[LauncherModel::class.java]
            await { !model.state.value.loading }
            var controller = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(activity) as WidgetController
            lastActivity = activity; lastModel = model; lastController = controller
            val weather = controller.personalProviders().filter { it.provider.packageName == WEATHER_PACKAGE }
            val current = requireNotNull(weather.firstOrNull { it.provider.className.endsWith(".widget.CurrentWeatherReceiver") })
            val timeline = requireNotNull(weather.firstOrNull { it.provider.className.endsWith(".widget.TimelineWeatherReceiver") })
            instrumentation.runOnMainSync {
                before = model.state.value.layout; idsBefore = controller.host.appWidgetIds.toSet()
                assertTrue("Baseline must include the real Analog Clock binding", idsBefore.any { id ->
                    controller.manager.getAppWidgetInfo(id)?.provider?.flattenToString() ==
                        "com.google.android.deskclock/com.android.alarmclock.AnalogAppWidgetProvider"
                })
                val page = requireNotNull(before).pageCount
                val anchor = model.state.value.apps.first { it.id !in requireNotNull(before).dock }
                assertTrue(model.applyDrop(anchor.id, DropTarget.Home(page * HOME_CELLS + HOME_CELLS - 1)))
            }
            val page = requireNotNull(before).pageCount; val start = page * HOME_CELLS
            tap("Home page ${page + 1}")
            val currentPlacement = addFromSettings(model, current, start)
            assertEquals(2, current.targetCellWidth); assertEquals(2, current.targetCellHeight)
            assertTrue("A narrow-pane host may enlarge the 2-cell target to satisfy the provider's 150dp minimum",
                currentPlacement.spanX >= current.targetCellWidth)
            assertEquals(current.targetCellHeight, currentPlacement.spanY)
            val timelinePlacement = addFromSettings(model, timeline, start + GRID_COLUMNS * 2)
            assertEquals(4, timeline.targetCellWidth); assertEquals(2, timeline.targetCellHeight)
            assertEquals(timeline.targetCellWidth, timelinePlacement.spanX)
            assertEquals(timeline.targetCellHeight, timelinePlacement.spanY)
            java.io.File(instrumentation.targetContext.filesDir, "weather-widget-metadata.txt").writeText(buildString {
                val metrics = activity.resources.displayMetrics
                appendLine("display=${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}")
                listOf("current" to (currentPlacement to current), "timeline" to (timelinePlacement to timeline)).forEach { (name, pair) ->
                    val (placement, provider) = pair
                    val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, provider.provider, null)
                    val options = AppWidgetManager.getInstance(activity).getAppWidgetOptions(placement.id)
                    val optionWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val optionHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    appendLine("$name target=${provider.targetCellWidth}x${provider.targetCellHeight} min=${provider.minWidth / metrics.density}x${provider.minHeight / metrics.density} minResize=${provider.minResizeWidth / metrics.density}x${provider.minResizeHeight / metrics.density} padding=${padding.left + padding.right}x${padding.top + padding.bottom}px span=${placement.spanX}x${placement.spanY} options=${optionWidth}x${optionHeight}dp pitch≈${(optionWidth + 10f) / placement.spanX}x${(optionHeight + 18f) / placement.spanY}dp")
                }
            })
            listOf(currentPlacement to current, timelinePlacement to timeline).forEach { (placement, provider) ->
                assertEquals(provider.provider, controller.manager.getAppWidgetInfo(placement.id)?.provider)
                val options = AppWidgetManager.getInstance(activity).getAppWidgetOptions(placement.id)
                assertTrue(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0)
                assertTrue(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > 0)
                await { rendered(activity, placement.id) }
            }
            await { rendered(activity, currentPlacement.id) && rendered(activity, timelinePlacement.id) }
            SystemClock.sleep(500)
            await { rendered(activity, currentPlacement.id) && rendered(activity, timelinePlacement.id) }
            listOf(currentPlacement, timelinePlacement).forEach { placement ->
                instrumentation.runOnMainSync {
                    val host = requireNotNull(hostView(activity, placement.id))
                    assertEquals(0, host.paddingLeft + host.paddingRight + host.paddingTop + host.paddingBottom)
                    val density = activity.resources.displayMetrics.density
                    val options = AppWidgetManager.getInstance(activity).getAppWidgetOptions(placement.id)
                    val measuredWidthDp = host.width / density
                    val measuredHeightDp = host.height / density
                    assertEquals(measuredWidthDp, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(), 1.1f)
                    assertEquals(measuredWidthDp, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat(), 1.1f)
                    assertEquals(measuredHeightDp, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat(), 1.1f)
                    assertEquals(measuredHeightDp, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat(), 1.1f)
                    val sizes = options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    assertTrue(requireNotNull(sizes).any {
                        kotlin.math.abs(it.width - measuredWidthDp) <= 1.1f &&
                            kotlin.math.abs(it.height - measuredHeightDp) <= 1.1f
                    })
                }
            }
            screenshot("weather-widgets-bound.png")
            val oldActivity = activity
            instrumentation.runOnMainSync { oldActivity.recreate() }
            await { LiveDiscover.owner.get()?.let { it !== oldActivity } == true }
            activity = requireNotNull(LiveDiscover.owner.get()); model = ViewModelProvider(activity)[LauncherModel::class.java]
            await { !model.state.value.loading }
            controller = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(activity) as WidgetController
            lastActivity = activity; lastModel = model; lastController = controller
            tap("Home page ${page + 1}")
            assertEquals(currentPlacement, model.placement(currentPlacement.slot))
            assertEquals(timelinePlacement, model.placement(timelinePlacement.slot))
            await { rendered(activity, currentPlacement.id) && rendered(activity, timelinePlacement.id) }
            SystemClock.sleep(500)
            await { rendered(activity, currentPlacement.id) && rendered(activity, timelinePlacement.id) }
            screenshot("weather-widgets-recreated.png")
        } finally {
            if (before != null && lastModel != null && lastController != null) instrumentation.runOnMainSync {
                val cleanupModel = requireNotNull(lastModel)
                val cleanupController = requireNotNull(lastController)
                cleanupModel.restoreLayout(requireNotNull(before))
                cleanupController.host.appWidgetIds.filter { it !in idsBefore }
                    .forEach(cleanupController.host::deleteAppWidgetId)
                assertEquals(idsBefore, cleanupController.host.appWidgetIds.toSet())
            }
            val fallback = previousHome.takeIf { it.isNotEmpty() && it != "media.whitewhale.iduo" }
                ?: "com.google.android.apps.nexuslauncher"
            shell("cmd role add-role-holder android.app.role.HOME $fallback 0")
            instrumentation.runOnMainSync {
                (LiveDiscover.owner.get() ?: lastActivity)?.finish(); LiveDiscover.host.get()?.finish()
            }
            if (previousHome.isNotEmpty()) shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
            else shell("cmd role remove-role-holder android.app.role.HOME $fallback 0")
        }
    }
    private companion object { const val WEATHER_PACKAGE = "com.jakesgoodapps.weather" }
}
