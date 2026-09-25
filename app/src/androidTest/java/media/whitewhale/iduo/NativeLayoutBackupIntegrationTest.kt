package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test

/** Real DocumentsUI and ActivityResult coverage for layout save/restore lifecycle. */
class NativeLayoutBackupIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun clearFixtureDocument() {
        val marker = "duo-saf-cleanup-marker"
        shell("run-as media.whitewhale.iduo.test mkdir -p files")
        shell("run-as media.whitewhale.iduo.test touch files/$marker")
        shell("run-as media.whitewhale.iduo.test rm -f files/saf-layout.json")
        val files = shell("run-as media.whitewhale.iduo.test ls files").lineSequence().toSet()
        try {
            assertTrue("run-as verification marker was not visible: $files", marker in files)
            assertFalse("Could not clear the isolated SAF fixture document: $files", "saf-layout.json" in files)
        } finally {
            shell("run-as media.whitewhale.iduo.test rm -f files/$marker")
        }
    }
    private fun await(timeout: Long = 20_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) {
                val directory = instrumentation.targetContext.filesDir
                java.io.File(directory, "native-layout-backup-timeout.png").outputStream().use { output ->
                    automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                java.io.File(directory, "native-layout-backup-timeout-hierarchy.txt").writeText(
                    automation.windows.joinToString("\n\n") { window ->
                        fun dump(node: AccessibilityNodeInfo?, depth: Int): String {
                            if (node == null) return ""
                            return "  ".repeat(depth) + "class=${node.className} id=${node.viewIdResourceName} " +
                                "text=${node.text} desc=${node.contentDescription} visible=${node.isVisibleToUser} " +
                                "enabled=${node.isEnabled} clickable=${node.isClickable}\n" +
                                (0 until node.childCount).joinToString("") { dump(node.getChild(it), depth + 1) }
                        }
                        dump(window.root, 0)
                    })
                java.io.File(directory, "native-layout-backup-timeout-windows.txt")
                    .writeText(shell("dumpsys window windows"))
                error("Timed out; active=${automation.rootInActiveWindow?.packageName}")
            }
            SystemClock.sleep(50)
        }
    }
    private fun find(value: String): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (listOf(node.text, node.contentDescription).any { it?.toString() == value }) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { found -> return found } }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { visit(it.root) }
    }
    private fun click(value: String) {
        var target: AccessibilityNodeInfo? = null
        await {
            var node = find(value)
            while (node != null && !node.isClickable) node = node.parent
            node?.takeIf { it.isVisibleToUser && it.isEnabled }?.also { target = it } != null
        }
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(300)
    }
    private fun activeMatches(value: String): List<AccessibilityNodeInfo> {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (listOf(node.text, node.contentDescription).any { it?.toString() == value }) matches += node
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(automation.rootInActiveWindow)
        return matches
    }
    private fun actionableActive(value: String): AccessibilityNodeInfo? = activeMatches(value).mapNotNull { match ->
        var node: AccessibilityNodeInfo? = match
        while (node != null && !node.isClickable) node = node.parent
        node?.takeIf { it.isVisibleToUser && it.isEnabled }
    }.lastOrNull()
    private fun clickLastActive(value: String) {
        var target: AccessibilityNodeInfo? = null
        await {
            target = activeMatches(value).mapNotNull { match ->
                var node: AccessibilityNodeInfo? = match
                while (node != null && !node.isClickable) node = node.parent
                node?.takeIf { it.isVisibleToUser && it.isEnabled }
            }.lastOrNull()
            target != null
        }
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(300)
    }
    private fun clickDocumentSave() {
        // On the emulator DocumentsUI can render the SaveActivity while
        // UiAutomation.rootInActiveWindow transiently remains null. UIAutomator still
        // exposes the visible native SAVE control, as verified by the failure capture.
        val device = UiDevice.getInstance(instrumentation)
        val target = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)save"))), 20_000)
        assertNotNull("Visible DocumentsUI SAVE action was not exposed to UIAutomator", target)
        requireNotNull(target).click()
        SystemClock.sleep(300)
    }
    private fun openFixtureRoot() {
        await { automation.rootInActiveWindow?.packageName == "com.google.android.documentsui" }
        // ACTION_OPEN_DOCUMENT commonly reopens the last visited root. In that case
        // the fixture name is only a non-actionable breadcrumb and the saved file is
        // already available; trying to click the breadcrumb waits forever.
        if (activeMatches("duo-launcher-layout.json").any { it.isVisibleToUser }) return
        // Always open the roots drawer when it is closed. A clickable breadcrumb can
        // otherwise masquerade as a selectable root. When the drawer is already open,
        // its Show roots action is absent and the last active fixture match is its row.
        actionableActive("Show roots")?.let {
            assertTrue(it.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            SystemClock.sleep(300)
        }
        clickLastActive("Duo Backup Fixture")
    }

    @Test fun realSafSaveRestoreCancelApplyAndUndoPreserveLayoutAndBindings() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val previousAttach = LiveDiscover.attachNativeFeed
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME").lineSequence().firstOrNull().orEmpty()
        var before: LauncherState? = null
        var mutated: LauncherState? = null
        var idsBefore = emptySet<Int>()
        var lastMain: MainActivity? = null
        var lastBackups: BackupController? = null
        var primaryFailure: Throwable? = null
        try {
            LiveDiscover.attachNativeFeed = true
            shell("cmd role add-role-holder android.app.role.HOME media.whitewhale.iduo 0")
            clearFixtureDocument()
            shell("input keyevent KEYCODE_HOME")
            await { LiveDiscover.owner.get() != null }
            var main = requireNotNull(LiveDiscover.owner.get())
            lastMain = main
            // A previously interrupted fixture run can leave the durable picker recovery
            // visible and intentionally keep Discover suspended. Normalize that test-owned
            // transaction through the public controller before requiring the native host.
            instrumentation.runOnMainSync { main.backups.cancelImport() }
            await { LiveDiscover.host.get() != null }
            var model = ViewModelProvider(main)[LauncherModel::class.java]
            await { !model.state.value.loading }
            val widgets = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }.get(main) as WidgetController
            val backups = main.backups
            lastBackups = backups
            instrumentation.runOnMainSync {
                before = model.state.value
                idsBefore = widgets.host.appWidgetIds.toSet()
                backups.startExport("duo-launcher-layout.json")
            }
            await { automation.rootInActiveWindow?.packageName == "com.google.android.documentsui" }
            assertNull("Discover host must yield to DocumentsUI", LiveDiscover.host.get())
            openFixtureRoot()
            clickDocumentSave()
            await { backups.successMessage != null && LiveDiscover.host.get() != null }
            assertEquals("Layout backup saved.", backups.successMessage)
            await { find("Layout backup")?.isVisibleToUser == true && find("Layout backup saved.")?.isVisibleToUser == true }
            click("OK")
            await { backups.successMessage == null }

            instrumentation.runOnMainSync {
                val state = model.state.value
                val app = state.apps.first { it.available && it.id !in state.dock && state.folders.none { folder -> it.id in folder.appIds } }
                val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                val target = (0 until HOME_CELLS * (state.homePages + 1)).first { it !in blocked && state.homeSlots.getOrNull(it) == null }
                assertTrue(model.applyDrop(app.id, DropTarget.Home(target)))
                model.setLabels(!state.labels)
                mutated = model.state.value
                backups.startImport()
            }
            openFixtureRoot()
            click("duo-launcher-layout.json")
            await { backups.preview != null || find("Open")?.isVisibleToUser == true }
            if (backups.preview == null) click("Open")
            await { backups.preview != null }
            assertNull("Discover remains yielded while import preview owns the transaction", LiveDiscover.host.get())
            click("Cancel")
            await { LiveDiscover.host.get() != null }
            assertEquals(requireNotNull(mutated).layout, model.state.value.layout)
            assertEquals(requireNotNull(mutated).labels, model.state.value.labels)

            instrumentation.runOnMainSync { backups.startImport() }
            openFixtureRoot()
            click("duo-launcher-layout.json")
            await { backups.preview != null || find("Open")?.isVisibleToUser == true }
            if (backups.preview == null) click("Open")
            await { backups.preview != null }
            assertNull("Discover remains yielded until Restore is applied", LiveDiscover.host.get())
            click("Restore")
            await { backups.successMessage != null && LiveDiscover.host.get() != null }
            assertEquals("Layout restored. Widgets are ready to reconnect.", backups.successMessage)
            await { find("Layout backup")?.isVisibleToUser == true &&
                find("Layout restored. Widgets are ready to reconnect.")?.isVisibleToUser == true }
            click("OK")
            await { backups.successMessage == null }
            val restored = model.state.value
            val original = requireNotNull(before)
            assertEquals(original.homeSlots, restored.homeSlots)
            assertEquals(original.dock, restored.dock)
            assertEquals(original.folders, restored.folders)
            assertEquals(original.labels, restored.labels)
            assertEquals(original.widgetPlacements.map { listOf(it.slot, it.page, it.column, it.row, it.spanX, it.spanY) },
                restored.widgetPlacements.map { listOf(it.slot, it.page, it.column, it.row, it.spanX, it.spanY) })
            assertTrue("Pre-import host IDs stay retained for Undo", idsBefore.all { it in widgets.host.appWidgetIds })
            instrumentation.runOnMainSync { assertTrue(model.undoEdit()) }
            assertEquals(requireNotNull(mutated).layout, model.state.value.layout)
            assertEquals(idsBefore, widgets.host.appWidgetIds.toSet())
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            val main = LiveDiscover.owner.get() ?: lastMain
            cleanup { instrumentation.runOnMainSync {
                lastBackups?.cancelImport()
                lastBackups?.clearMessage()
            } }
            if (automation.rootInActiveWindow?.packageName == "com.google.android.documentsui") {
                cleanup { shell("input keyevent KEYCODE_BACK") }
                SystemClock.sleep(300)
            }
            if (main != null && !main.isDestroyed && before != null) cleanup { instrumentation.runOnMainSync {
                val model = ViewModelProvider(main)[LauncherModel::class.java]
                model.restoreLayout(requireNotNull(before).layout)
                model.setLabels(requireNotNull(before).labels)
                model.setGoogleSearch(requireNotNull(before).googleSearch)
                model.setVerticalStatus(requireNotNull(before).verticalStatus)
            } }
            cleanup { clearFixtureDocument() }
            val cleanupHome = previousHome.takeIf { it.isNotEmpty() && it != "media.whitewhale.iduo" }
                ?: "com.google.android.apps.nexuslauncher"
            cleanup { shell("cmd role add-role-holder android.app.role.HOME $cleanupHome 0") }
            cleanup { instrumentation.runOnMainSync { LiveDiscover.owner.get()?.finish(); LiveDiscover.host.get()?.finish() } }
            cleanup {
                if (previousHome.isNotEmpty()) shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
                else shell("cmd role remove-role-holder android.app.role.HOME $cleanupHome 0")
            }
            LiveDiscover.attachNativeFeed = previousAttach
            cleanupFailure?.let { failure -> if (primaryFailure != null) primaryFailure?.addSuppressed(failure) else throw failure }
        }
    }
}
