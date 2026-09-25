package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Emulator-only coverage of the real Settings-controlled accessibility service and SystemUI. */
@RunWith(AndroidJUnit4::class)
class SystemShadeIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // UiAutomation suppresses accessibility services unless this flag is used.
    private val automation = instrumentation.getUiAutomation(
        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
    )
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private val component = "media.whitewhale.iduo/media.whitewhale.iduo.SystemShadeAccessibilityService"

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun await(timeout: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) error("Timed out; active=${automation.rootInActiveWindow?.packageName}")
            SystemClock.sleep(50)
        }
    }

    private fun findText(fragment: String, packageName: String? = null): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && (packageName == null || node.packageName?.toString() == packageName) &&
                listOf(node.text, node.contentDescription, node.stateDescription)
                    .any { it?.toString()?.contains(fragment, ignoreCase = true) == true }) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { found -> return found } }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { visit(it.root) }
    }

    private fun clickSystemText(text: String) {
        var node: AccessibilityNodeInfo? = null
        await { findText(text)?.also { node = it } != null }
        var clickable = requireNotNull(node)
        while (!clickable.isClickable && clickable.parent != null) clickable = requireNotNull(clickable.parent)
        assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private data class AccessibilityBaseline(val services: String?, val enabled: String?)
    private fun baseline() = AccessibilityBaseline(
        shell("settings get secure enabled_accessibility_services").takeUnless { it == "null" },
        shell("settings get secure accessibility_enabled").takeUnless { it == "null" },
    )
    private fun put(key: String, value: String?) {
        if (value == null) shell("settings delete secure $key")
        else {
            require(value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in "._/:-" || it.code == 36 }) {
                "Unexpected character in secure accessibility setting"
            }
            // executeShellCommand passes this command to Android's shell service; quote marks
            // here would be stored literally rather than stripped by a host shell.
            shell("settings put secure $key $value")
        }
    }
    private fun withoutDuo(value: String?) = value.orEmpty().split(':')
        .filter { it.isNotBlank() && it != component }.joinToString(":").ifEmpty { null }
    private fun restore(saved: AccessibilityBaseline) {
        put("enabled_accessibility_services", saved.services)
        put("accessibility_enabled", saved.enabled)
    }

    private fun recordFailure(label: String, actionResult: ShadeOpenResult?, failure: Throwable) {
        val dir = instrumentation.targetContext.filesDir
        fun StringBuilder.dump(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null || depth > 12) return
            appendLine("  ".repeat(depth) +
                "pkg=${node.packageName} visible=${node.isVisibleToUser} text=${node.text} desc=${node.contentDescription}")
            repeat(node.childCount) { dump(node.getChild(it), depth + 1) }
        }
        File(dir, "shade-native-failure.txt").writeText(buildString {
            appendLine("stage=$label")
            appendLine("serviceConnected=${SystemShadeAccessibilityService.isConnected()}")
            appendLine("actionResult=$actionResult")
            appendLine("active=${automation.rootInActiveWindow?.packageName}")
            appendLine("failure=$failure")
            appendLine("windows:")
            automation.windows.forEach { window ->
                appendLine("  type=${window.type} active=${window.isActive} focused=${window.isFocused} root=${window.root?.packageName}")
                dump(window.root)
            }
            appendLine("dumpsys window:")
            appendLine(runCatching { shell("dumpsys window windows") }.getOrElse { it.toString() })
        })
        runCatching {
            FileOutputStream(File(dir, "shade-native-failure.png")).use { output ->
                automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    @Test fun disabledGestureExplainsSetupOnlyWhenInvokedAndCancelPreservesHome() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val saved = baseline()
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(15_000) { !model.state.value.loading }
        val layout = model.state.value.layout
        try {
            put("enabled_accessibility_services", withoutDuo(saved.services))
            put("accessibility_enabled", if (withoutDuo(saved.services) == null) "0" else "1")
            await { !SystemShadeAccessibilityService.isConnected() }

            assertTrue("No setup prompt may appear at startup", findText("Turn on shade gestures") == null)
            compose.onNodeWithTag("launcher-root").performTouchInput {
                swipe(
                    start = Offset(width * .5f, height * .2f),
                    end = Offset(width * .5f, height * .55f),
                    durationMillis = 300,
                )
            }
            await { findText("Turn on shade gestures") != null }
            assertEquals("Opening setup must not edit Home", layout, model.state.value.layout)
            assertTrue("The native setup dialog must own Discover before Settings is opened",
                LiveDiscover.hasExternalResultPending("main", "shade-service-setup"))

            compose.activityRule.scenario.recreate()
            await { findText("Turn on shade gestures") != null }
            assertTrue("Recreated setup must retain Discover ownership",
                LiveDiscover.hasExternalResultPending("main", "shade-service-setup"))

            clickSystemText("Not now")
            await { findText("Turn on shade gestures") == null }
            await { !LiveDiscover.hasExternalResultPending("main", "shade-service-setup") }
            val recreatedModel = ViewModelProvider(compose.activity)[LauncherModel::class.java]
            compose.waitUntil(5_000) { !recreatedModel.state.value.loading }
            assertEquals("Cancel must preserve every placement", layout, recreatedModel.state.value.layout)
            compose.onNodeWithTag("discover-page-link").assertIsDisplayed().assertHasClickAction()
        } finally {
            restore(saved)
        }
    }

    @Test fun enabledServiceOpensNotificationsAndExpandedQuickSettings() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val saved = baseline()
        var stage = "enable"
        var actionResult: ShadeOpenResult? = null
        try {
            val enabled = (withoutDuo(saved.services)?.let { "$it:" } ?: "") + component
            put("enabled_accessibility_services", enabled)
            put("accessibility_enabled", "1")
            await { SystemShadeAccessibilityService.isConnected() }

            stage = "notifications"
            compose.runOnIdle { actionResult = SystemShadeAccessibilityService.open(compose.activity, ShadePanel.NOTIFICATIONS) }
            assertEquals(ShadeOpenResult.OPENED, actionResult)
            await { findText("notification", "com.android.systemui") != null }
            assertNotNull("Notifications must expose visible SystemUI notification content",
                findText("notification", "com.android.systemui"))
            shell("cmd statusbar collapse")
            stage = "collapse"
            await {
                automation.rootInActiveWindow?.packageName?.toString() == "media.whitewhale.iduo" &&
                    findText("notification", "com.android.systemui") == null &&
                    findText("brightness", "com.android.systemui") == null
            }
            // Require the collapsed state to remain settled beyond the last animation frame.
            SystemClock.sleep(300)
            assertEquals("media.whitewhale.iduo", automation.rootInActiveWindow?.packageName?.toString())
            assertTrue(findText("notification", "com.android.systemui") == null)
            assertTrue(findText("brightness", "com.android.systemui") == null)

            stage = "quick-settings"
            compose.runOnIdle { actionResult = SystemShadeAccessibilityService.open(compose.activity, ShadePanel.QUICK_SETTINGS) }
            assertEquals(ShadeOpenResult.OPENED, actionResult)
            await { findText("brightness", "com.android.systemui") != null }
            assertNotNull("Expanded Quick Settings must expose the real brightness control",
                findText("brightness", "com.android.systemui"))
        } catch (failure: Throwable) {
            recordFailure(stage, actionResult, failure)
            throw failure
        } finally {
            shell("cmd statusbar collapse")
            restore(saved)
        }
    }
}
