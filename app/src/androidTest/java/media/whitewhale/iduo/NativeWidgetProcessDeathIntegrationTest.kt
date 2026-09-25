package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run the two methods separately using docs/WIDGET-PROCESS-DEATH-TEST.md. */
class NativeWidgetProcessDeathIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val record get() = instrumentation.targetContext.getSharedPreferences("widget_process_death_stage", 0)
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun await(timeout: Long = 20_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) error("Timed out; active=${automation.rootInActiveWindow?.packageName}")
            SystemClock.sleep(50)
        }
    }
    private fun main(): MainActivity {
        shell("input keyevent KEYCODE_HOME")
        await { LiveDiscover.owner.get() != null }
        return requireNotNull(LiveDiscover.owner.get())
    }
    private fun requireSafeHarness() {
        assumeTrue("Run only through the explicit process-death harness",
            InstrumentationRegistry.getArguments().getString("duoWidgetProcessDeath") == "true")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertTrue("Harness must make Duo the default Home",
            instrumentation.targetContext.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME))
    }
    private fun click(value: String) {
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        var target: AccessibilityNodeInfo? = null
        await {
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (listOf(node.text, node.contentDescription).any { it?.toString() == value }) {
                    var current: AccessibilityNodeInfo? = node
                    while (current != null && !current.isClickable) current = current.parent
                    if (current?.isVisibleToUser == true && current.isEnabled) return current
                }
                repeat(node.childCount) { find(node.getChild(it))?.let { found -> return found } }
                return null
            }
            automation.windows.firstNotNullOfOrNull { find(it.root) }?.also { target = it } != null
        }
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun stage1LeaveRequiredConfigurationPending() {
        requireSafeHarness()
        record.edit().clear().commit()
        LiveDiscover.attachNativeFeed = true
        val main = main()
        val model = ViewModelProvider(main)[LauncherModel::class.java]
        await { !model.state.value.loading }
        val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }.get(main) as WidgetController
        val provider = widgets.personalProviders().single { it.provider.packageName == "media.whitewhale.iduo.test" &&
            it.provider.className.endsWith("RequiredConfigWidgetProvider") }
        val beforeRaw = main.getSharedPreferences("launcher", 0).getString("state", null)
        val beforeIds = widgets.host.appWidgetIds.toSet()
        val layout = model.state.value.layout
        val slot = model.nextWidgetSlot()
        val draft = (0 until HOME_CELLS * (layout.pageCount + 1)).firstNotNullOfOrNull { index ->
            widgetCandidate(layout, slot, index, 1, 1)
        } ?: error("Fixture needs one widget cell")
        assertTrue(record.edit().putString("layout", beforeRaw).putString("ids", beforeIds.sorted().joinToString(","))
            .putInt("pendingId", -1).putInt("slot", slot).putInt("producerPid", android.os.Process.myPid())
            .putString("producerStage", "new-widget").commit())
        instrumentation.runOnMainSync { widgets.add(draft, provider) }
        val assignedId = requireNotNull(widgets.pendingPlacement).id
        assertTrue(record.edit().putInt("pendingId", assignedId).commit())
        await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" && widgets.pendingPlacement?.slot == slot }
        val pending = requireNotNull(widgets.pendingPlacement)
        assertTrue(pending.id in widgets.host.appWidgetIds)
        assertEquals(beforeIds + pending.id, widgets.host.appWidgetIds.toSet())
        // SharedPreferences.apply() in WidgetController must reach disk before the harness kills the target.
        SystemClock.sleep(1_000)
    }

    @Test fun stage2RestoreResumeCommitThenUndoWithoutDuplicateBinding() {
        requireSafeHarness()
        val pendingId = record.getInt("pendingId", -1)
        val slot = record.getInt("slot", -1)
        val beforeRaw = record.getString("layout", null)
        val beforeIds = record.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
        assertTrue("Run stage1 first", pendingId >= 0 && slot >= 0 && beforeRaw != null)
        val producerPid = record.getInt("producerPid", -1)
        assertTrue("Stage1 producer marker is missing", producerPid > 0 && record.getString("producerStage", null) == "new-widget")
        assertNotEquals("Consumer must run in a new target process", producerPid, android.os.Process.myPid())
        LiveDiscover.attachNativeFeed = true
        val main = main()
        val model = ViewModelProvider(main)[LauncherModel::class.java]
        await { !model.state.value.loading }
        val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }.get(main) as WidgetController
        await { widgets.pendingPlacement?.id == pendingId && widgets.setupStatus != null }
        assertEquals(beforeIds + pendingId, widgets.host.appWidgetIds.toSet())
        assertEquals(1, widgets.host.appWidgetIds.count { it == pendingId })
        assertNull(model.placement(slot))
        instrumentation.runOnMainSync { widgets.finishPendingSetup() }
        await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
        click("Use configured widget")
        await { model.placement(slot)?.id == pendingId && widgets.pendingPlacement == null }
        assertEquals(beforeIds + pendingId, widgets.host.appWidgetIds.toSet())
        instrumentation.runOnMainSync { assertTrue(model.undoEdit()) }
        await { pendingId !in widgets.host.appWidgetIds }
        assertEquals(beforeIds, widgets.host.appWidgetIds.toSet())
        assertEquals(beforeRaw, main.getSharedPreferences("launcher", 0).getString("state", null))
        if (9 in beforeIds) assertTrue(9 in widgets.host.appWidgetIds)
        record.edit().putString("completed", "new-widget").commit()
    }

    @Test fun stage3LeaveExistingWidgetReconfigurePending() {
        requireSafeHarness()
        record.edit().clear().commit()
        val main = main()
        val model = ViewModelProvider(main)[LauncherModel::class.java]
        await { !model.state.value.loading }
        val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }.get(main) as WidgetController
        val provider = widgets.personalProviders().single { it.provider.packageName == "media.whitewhale.iduo.test" &&
            it.provider.className.endsWith("OptionalConfigWidgetProvider") }
        val beforeRaw = main.getSharedPreferences("launcher", 0).getString("state", null)
        val beforeIds = widgets.host.appWidgetIds.toSet()
        val layout = model.state.value.layout
        val slot = model.nextWidgetSlot()
        val draft = (0 until HOME_CELLS * (layout.pageCount + 1)).firstNotNullOfOrNull { index ->
            widgetCandidate(layout, slot, index, 1, 1)
        } ?: error("Fixture needs one widget cell")
        assertTrue(record.edit().putString("layout", beforeRaw).putString("ids", beforeIds.sorted().joinToString(","))
            .putInt("pendingId", -1).putInt("slot", slot).putInt("producerPid", android.os.Process.myPid())
            .putString("producerStage", "reconfigure").commit())
        instrumentation.runOnMainSync { widgets.add(draft, provider) }
        await { model.placement(slot)?.id?.let { it >= 0 } == true }
        val id = requireNotNull(model.placement(slot)).id
        assertTrue(record.edit().putInt("pendingId", id).commit())
        assertTrue(widgets.canReconfigure(id))
        instrumentation.runOnMainSync { assertTrue(widgets.reconfigure(id)) }
        await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" && widgets.reconfigureWidgetId == id }
        SystemClock.sleep(1_000)
    }

    @Test fun stage4RestoreAndCancelReconfigureWithoutChangingBinding() {
        requireSafeHarness()
        val id = record.getInt("pendingId", -1)
        val slot = record.getInt("slot", -1)
        val beforeRaw = record.getString("layout", null)
        val beforeIds = record.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
        assertTrue("Run stage3 first", id >= 0 && slot >= 0 && beforeRaw != null)
        val producerPid = record.getInt("producerPid", -1)
        assertTrue("Stage3 producer marker is missing", producerPid > 0 && record.getString("producerStage", null) == "reconfigure")
        assertNotEquals("Consumer must run in a new target process", producerPid, android.os.Process.myPid())
        val main = main()
        val model = ViewModelProvider(main)[LauncherModel::class.java]
        await { !model.state.value.loading }
        val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }.get(main) as WidgetController
        await { widgets.reconfigureWidgetId == id }
        val placement = requireNotNull(model.placement(slot))
        assertEquals(id, placement.id)
        assertEquals(beforeIds + id, widgets.host.appWidgetIds.toSet())
        instrumentation.runOnMainSync { assertTrue(widgets.finishPendingReconfigure()) }
        await { automation.rootInActiveWindow?.packageName == "media.whitewhale.iduo.test" }
        click("Cancel fixture configuration")
        await { widgets.reconfigureWidgetId == null }
        assertEquals(placement, model.placement(slot))
        assertEquals(beforeIds + id, widgets.host.appWidgetIds.toSet())
        // The add transaction's Undo snapshot was intentionally lost with stage 3's process.
        // Remove only this fixture, then clear that new in-memory Undo so reconciliation may
        // delete its binding. The resulting layout is the recorded pre-stage baseline.
        instrumentation.runOnMainSync {
            widgets.remove(slot)
            model.restoreLayout(model.state.value.layout)
        }
        await { id !in widgets.host.appWidgetIds }
        assertEquals(beforeIds, widgets.host.appWidgetIds.toSet())
        assertEquals(beforeRaw, main.getSharedPreferences("launcher", 0).getString("state", null))
        record.edit().putString("completed", "reconfigure").commit()
    }

    @Test fun cleanupRecordedFixtureBeforeOuterPreferenceRestore() {
        requireSafeHarness()
        assertNotNull("No recorded baseline; refusing an unbounded cleanup", record.getString("layout", null))
        val id = record.getInt("pendingId", -1)
        val originalIds = record.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
        val main = main()
        val model = ViewModelProvider(main)[LauncherModel::class.java]
        await { !model.state.value.loading }
        val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }.get(main) as WidgetController
        val newIds = widgets.host.appWidgetIds.toSet() - originalIds
        instrumentation.runOnMainSync {
            if (widgets.reconfigureWidgetId?.let { it in newIds } == true) widgets.cancelPendingReconfigure()
            if (widgets.pendingPlacement?.id?.let { it in newIds } == true) widgets.cancelPendingSetup()
            model.state.value.widgetPlacements.filter { it.id in newIds }.forEach { placement ->
                widgets.remove(placement.slot)
            }
            if (newIds.isNotEmpty()) {
                model.restoreLayout(model.state.value.layout)
            }
            newIds.filter { it in widgets.host.appWidgetIds && it !in model.retainedWidgetIds }
                .forEach(widgets.host::deleteAppWidgetId)
        }
        assertTrue(newIds.none { it in widgets.host.appWidgetIds })
        if (id >= 0 && id !in originalIds) assertFalse(id in widgets.host.appWidgetIds)
        record.edit().clear().commit()
    }
}
