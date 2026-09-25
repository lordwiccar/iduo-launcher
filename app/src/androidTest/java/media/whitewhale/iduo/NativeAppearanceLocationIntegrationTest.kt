package media.whitewhale.iduo

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Exercises the real permission controller while the native Discover host is enabled. */
class NativeAppearanceLocationIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val packageName get() = context.packageName

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                File(context.filesDir, "native-location-timeout.png").outputStream().use { output ->
                    automation.takeScreenshot()?.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                File(context.filesDir, "native-location-timeout-hierarchy.txt").writeText(
                    automation.windows.joinToString("\n\n") { dump(it.root) })
                error("Timed out waiting for location UI; active=${automation.rootInActiveWindow?.packageName}")
            }
            SystemClock.sleep(50)
        }
    }

    private fun dump(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null) return ""
        return "  ".repeat(depth) + "class=${node.className} text=${node.text} desc=${node.contentDescription} " +
            "visible=${node.isVisibleToUser} enabled=${node.isEnabled} clickable=${node.isClickable}\n" +
            (0 until node.childCount).joinToString("") { dump(node.getChild(it), depth + 1) }
    }

    private fun find(value: String): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (listOf(node.text, node.contentDescription, node.stateDescription)
                    .any { it?.toString() == value }) return node
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
            node?.takeIf { it.isVisibleToUser && it.isEnabled }?.also { target = it } != null
        }
        assertTrue("Accessibility click failed for $value",
            requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(250)
    }

    private fun clickAfterScrolling(value: String) {
        repeat(12) {
            var target = find(value)
            while (target != null && !target.isClickable) target = target.parent
            if (target?.isVisibleToUser == true && target.isEnabled) {
                assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                SystemClock.sleep(250)
                return
            }
            fun contains(node: AccessibilityNodeInfo?, text: String): Boolean {
                if (node == null) return false
                if (listOf(node.text, node.contentDescription).any { it?.toString() == text }) return true
                return (0 until node.childCount).any { contains(node.getChild(it), text) }
            }
            val settingsWindow = automation.windows.firstOrNull { contains(it.root, "Make it yours") }
            fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isVisibleToUser && node.isScrollable) return node
                for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
                return null
            }
            val scroller = scrollable(settingsWindow?.root)
            if (scroller == null) return@repeat
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

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
        } }
        assertTrue(editor.commit())
    }

    private fun clearPermissionDecisionFlags() {
        shell("pm clear-permission-flags --user 0 $packageName ${Manifest.permission.ACCESS_COARSE_LOCATION} user-set user-fixed")
    }

    @Test fun explicitDeviceLocationDenialReturnsToUsableHomeWithoutChangingState() {
        assumeTrue("Native permission coverage runs only on the ephemeral emulator",
            Build.HARDWARE in listOf("ranchu", "goldfish"))
        assumeTrue("This test's guarded permission commands require the emulator primary user",
            shell("am get-current-user") == "0")

        val permission = Manifest.permission.ACCESS_COARSE_LOCATION
        val originallyGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        assumeTrue("Revoking a granted target permission would kill the instrumentation process; run with coarse location denied",
            !originallyGranted)
        val permissionDumpBefore = shell("dumpsys package $packageName")
        val originallyUserSet = Regex("$permission:.*USER_SET").containsMatchIn(permissionDumpBefore)
        val originallyUserFixed = Regex("$permission:.*USER_FIXED").containsMatchIn(permissionDumpBefore)
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME")
            .lineSequence().firstOrNull().orEmpty()
        val launcherPrefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val appearancePrefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val backgroundPrefs = context.getSharedPreferences("launcher_background", Context.MODE_PRIVATE)
        val originalLauncher = launcherPrefs.all.toMap()
        val originalAppearance = appearancePrefs.all.toMap()
        val originalBackground = backgroundPrefs.all.toMap()
        val backgroundFile = launcherBackgroundFile(context)
        val originalBackgroundBytes = backgroundFile.takeIf(File::isFile)?.readBytes()
        val originalAttachNativeFeed = LiveDiscover.attachNativeFeed

        LiveDiscover.attachNativeFeed = true
        try {
            // Start from a fresh denial state so Android presents the real runtime dialog once.
            clearPermissionDecisionFlags()
            shell("cmd role add-role-holder android.app.role.HOME $packageName 0")
            shell("input keyevent KEYCODE_HOME")
            await { LiveDiscover.owner.get() != null && automation.rootInActiveWindow?.packageName == packageName }

            // Merely starting Home must never request location permission.
            SystemClock.sleep(750)
            assertEquals(packageName, automation.rootInActiveWindow?.packageName?.toString())
            assertNull("Location permission must be user initiated", find("Don’t allow"))

            openNativeHomeCustomization(automation) { click("Customize launcher") }
            click("Wallpaper & appearance")
            clickAfterScrolling("Sunrise / sunset")
            val launcherBefore = launcherPrefs.all.toMap()
            val appearanceBefore = appearancePrefs.all.toMap()
            val backgroundBefore = backgroundPrefs.all.toMap()
            val photoBefore = backgroundFile.takeIf(File::isFile)?.readBytes()
            clickAfterScrolling("Use device location")
            await { automation.rootInActiveWindow?.packageName?.toString().orEmpty()
                .contains("permissioncontroller") && find("Don’t allow")?.isVisibleToUser == true }
            click("Don’t allow")

            await { automation.rootInActiveWindow?.packageName == packageName && LiveDiscover.owner.get() != null }
            val denied = "Location permission wasn’t granted. Using the system theme until you set a place."
            val returnedMain = requireNotNull(LiveDiscover.owner.get())
            val appearanceField = MainActivity::class.java.getDeclaredField("appearance").apply { isAccessible = true }
            await { (appearanceField.get(returnedMain) as AppearanceStore).state.locationStatus == denied }
            await { find(denied)?.isVisibleToUser == true }
            assertEquals(launcherBefore, launcherPrefs.all.toMap())
            assertEquals(appearanceBefore, appearancePrefs.all.toMap())
            assertEquals(backgroundBefore, backgroundPrefs.all.toMap())
            assertArrayEquals(photoBefore, backgroundFile.takeIf(File::isFile)?.readBytes())

            click("Close customization")
            await { find("Discover")?.isVisibleToUser == true }
            click("Discover")
            await(20_000) { LiveDiscover.progress >= .99f && LiveDiscover.message.value == null }
            click("Back to home")
            await { LiveDiscover.progress == 0f && find("Discover")?.isVisibleToUser == true }
            assertEquals(launcherBefore, launcherPrefs.all.toMap())
            assertEquals(appearanceBefore, appearancePrefs.all.toMap())
            assertEquals(backgroundBefore, backgroundPrefs.all.toMap())
            assertArrayEquals(photoBefore, backgroundFile.takeIf(File::isFile)?.readBytes())
        } finally {
            val cleanupHome = previousHome.takeIf { it.isNotBlank() && it != packageName }
                ?: "com.google.android.apps.nexuslauncher"
            shell("cmd role add-role-holder android.app.role.HOME $cleanupHome 0")
            if (automation.rootInActiveWindow?.packageName?.toString().orEmpty().contains("permissioncontroller")) {
                shell("input keyevent KEYCODE_BACK")
                SystemClock.sleep(400)
            }
            instrumentation.runOnMainSync {
                DiscoverSession.dismiss()
                LiveDiscover.owner.get()?.finish()
                LiveDiscover.host.get()?.finish()
            }
            restore(launcherPrefs, originalLauncher)
            restore(appearancePrefs, originalAppearance)
            restore(backgroundPrefs, originalBackground)
            if (originalBackgroundBytes == null) backgroundFile.delete() else backgroundFile.writeBytes(originalBackgroundBytes)
            clearPermissionDecisionFlags()
            if (originallyUserSet) shell("pm set-permission-flags --user 0 $packageName $permission user-set")
            if (originallyUserFixed) shell("pm set-permission-flags --user 0 $packageName $permission user-fixed")
            if (previousHome.isNotBlank()) shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
            else shell("cmd role remove-role-holder android.app.role.HOME $cleanupHome 0")
            LiveDiscover.attachNativeFeed = originalAttachNativeFeed
        }
    }
}
