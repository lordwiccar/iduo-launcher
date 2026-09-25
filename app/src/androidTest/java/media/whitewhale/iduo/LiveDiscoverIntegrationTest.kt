package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real Google window, real input dispatch, no Compose transport substitute. Emulator only. */
class LiveDiscoverIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    @org.junit.Before @org.junit.After fun releasePointer() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input touchscreen motionevent CANCEL 0 0"))
            .use { it.readBytes() }
    }
    private fun await(timeout: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) { if (SystemClock.uptimeMillis() >= deadline) {
            val context = instrumentation.targetContext
            java.io.File(context.filesDir, "native-debug.png").outputStream().use { output -> automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys window windows")).use { input -> java.io.File(context.filesDir, "native-debug-windows.txt").writeBytes(input.readBytes()) }
        }; check(SystemClock.uptimeMillis() < deadline) { "Live Discover timed out: page=${progress()} native=${LiveDiscover.nativePosition} moving=${LiveDiscover.pagerOwnsMotion}" }; SystemClock.sleep(30) }
    }
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun progress(): Float {
        var result = 0f
        instrumentation.runOnMainSync { result = LiveDiscover.progress }
        return result
    }
    private fun touch(action: Int, x: Float, y: Float) {
        val name = when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            else -> "CANCEL"
        }
        // Shell input uses logical display coordinates even when wm size letterboxes the
        // fold emulator. Raw UiAutomation coordinates target its underlying physical panel.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input touchscreen motionevent $name ${x.toInt()} ${y.toInt()}"))
            .use { it.readBytes() }
        SystemClock.sleep(40)
    }
    private fun node(description: String, state: Boolean = false): AccessibilityNodeInfo? {
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if ((if (state) node.stateDescription else node.contentDescription)?.toString() == description) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { find(it.root) }
    }
    private fun hasSearchField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isEditable) return true
        return (0 until node.childCount).any { hasSearchField(node.getChild(it)) }
    }
    private fun click(description: String) {
        var bounds: android.graphics.Rect? = null
        var stableSince = 0L
        await {
            val candidate = node(description)?.takeIf { it.isVisibleToUser && it.isEnabled }
                ?.let { android.graphics.Rect().also(it::getBoundsInScreen) }?.takeUnless { it.isEmpty }
            if (candidate != bounds) { bounds = candidate; stableSince = SystemClock.uptimeMillis() }
            candidate != null && SystemClock.uptimeMillis() - stableSince >= 250
        }
        val target = checkNotNull(bounds)
        shell("input tap ${target.centerX()} ${target.centerY()}")
    }

    @Test fun liveFeedTracksHeldEntryExitAndReversalWithoutReplacingHost() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) { "Native gesture test runs only on an emulator" }
        LiveDiscover.attachNativeFeed = true
        val previousHome = shell("cmd role get-role-holders android.app.role.HOME").lineSequence().firstOrNull().orEmpty()
        shell("cmd role add-role-holder android.app.role.HOME media.whitewhale.iduo 0")
        val context = instrumentation.targetContext
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await { LiveDiscover.owner.get() != null }
            await {
                var loaded = false
                instrumentation.runOnMainSync {
                    loaded = !ViewModelProvider(LiveDiscover.owner.get()!!)[LauncherModel::class.java].state.value.loading
                }
                loaded
            }
            // Loading may migrate and persist an older schema. Snapshot only after the raw state
            // has stopped changing so the final assertion still catches any gesture-time write.
            var persistedCandidate: String? = null
            var persistedSince = 0L
            await {
                val persisted = context.getSharedPreferences("launcher", 0).getString("state", null)
                if (persisted != persistedCandidate) {
                    persistedCandidate = persisted
                    persistedSince = SystemClock.uptimeMillis()
                }
                persisted != null && SystemClock.uptimeMillis() - persistedSince >= 300L
            }
            var width = 0f; var height = 0f; var before: String? = null
            instrumentation.runOnMainSync {
                val it = LiveDiscover.owner.get()!!
                width = it.windowManager.currentWindowMetrics.bounds.width().toFloat()
                height = it.windowManager.currentWindowMetrics.bounds.height().toFloat()
                before = it.getSharedPreferences("launcher", 0).getString("state", null)
            }
            await { LiveDiscover.host.get() != null }
            // A first open loads content; every gesture below reuses this exact connection.
            click("Discover")
            await(20000) { progress() >= .99f && LiveDiscover.nativePosition >= .99f && !LiveDiscover.pagerOwnsMotion && LiveDiscover.message.value == null }
            val host = LiveDiscover.host.get()
            // The fixed dock and rail remain in Duo's window while Google's inset feed owns
            // its content. A short, paused swipe starting there must still page back Home.
            instrumentation.runOnMainSync {
                width = LiveDiscover.fullSize.width
                height = LiveDiscover.fullSize.height
            }
            val railX = (LiveDiscover.viewport.right + width) * .5f
            val railExit = railX - 90f * instrumentation.targetContext.resources.displayMetrics.density
            val railY = height * .75f
            touch(MotionEvent.ACTION_DOWN, railX, railY)
            touch(MotionEvent.ACTION_MOVE, railX - 24f * instrumentation.targetContext.resources.displayMetrics.density, railY)
            touch(MotionEvent.ACTION_MOVE, railExit, railY)
            SystemClock.sleep(80)
            touch(MotionEvent.ACTION_UP, railExit, railY)
            await { progress() == 0f && !LiveDiscover.pagerOwnsMotion }
            assertSame("Rail exit must retain the native host", host, LiveDiscover.host.get())
            SystemClock.sleep(300)
            // Embedding and wm-size overrides can settle after ActivityScenario launches.
            // Use the laid-out Main window for input, not launch-time WindowMetrics.
            instrumentation.runOnMainSync {
                val it = LiveDiscover.owner.get()!!
                width = it.window.decorView.width.toFloat()
                height = it.window.decorView.height.toFloat()
            }
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "\nNative gesture viewport: ${width.toInt()} x ${height.toInt()}\n")
            })
            val y = height * .75f
            touch(MotionEvent.ACTION_DOWN, width * .15f, y)
            touch(MotionEvent.ACTION_MOVE, width * .20f, y)
            touch(MotionEvent.ACTION_MOVE, width * .48f, y)
            await { progress() in .2f.. .7f }
            val entry = progress()
            SystemClock.sleep(250)
            assertEquals("Entry must stay under the held finger", entry, progress(), .025f)
            touch(MotionEvent.ACTION_MOVE, width * .17f, y)
            touch(MotionEvent.ACTION_UP, width * .17f, y)
            await { progress() == 0f && !LiveDiscover.pagerOwnsMotion }
            SystemClock.sleep(300)
            click("Discover")
            await { progress() >= .99f && LiveDiscover.nativePosition >= .99f && !LiveDiscover.pagerOwnsMotion }
            SystemClock.sleep(150)
            val feedY = height * .58f
            touch(MotionEvent.ACTION_DOWN, width * .70f, feedY)
            touch(MotionEvent.ACTION_MOVE, width * .65f, feedY)
            touch(MotionEvent.ACTION_MOVE, width * .40f, feedY)
            await { progress() in .2f.. .9f }
            val exit = progress()
            SystemClock.sleep(250)
            assertEquals("Exit must not auto-commit while held", exit, progress(), .025f)
            touch(MotionEvent.ACTION_MOVE, width * .69f, feedY)
            touch(MotionEvent.ACTION_UP, width * .69f, feedY)
            await { progress() >= .99f && LiveDiscover.nativePosition >= .99f && !LiveDiscover.pagerOwnsMotion }
            SystemClock.sleep(150)
            touch(MotionEvent.ACTION_DOWN, width * .70f, feedY)
            touch(MotionEvent.ACTION_MOVE, width * .65f, feedY)
            touch(MotionEvent.ACTION_MOVE, width * .06f, feedY)
            touch(MotionEvent.ACTION_UP, width * .06f, feedY)
            await { progress() == 0f && !LiveDiscover.pagerOwnsMotion }
            SystemClock.sleep(300)
            assertSame("No task/window reconstruction between visits", host, LiveDiscover.host.get())
            // Hidden native callbacks must not drag All apps back to Home.
            repeat(3) {
                // A relaxed quarter-page swipe, including a pause before lifting, must
                // work with Google's real window still connected behind the workspace.
                touch(MotionEvent.ACTION_DOWN, width * .68f, height * .65f)
                touch(MotionEvent.ACTION_MOVE, width * .64f, height * .66f)
                touch(MotionEvent.ACTION_MOVE, width * .54f, height * .67f)
                touch(MotionEvent.ACTION_MOVE, width * .42f, height * .68f)
                SystemClock.sleep(80)
                touch(MotionEvent.ACTION_UP, width * .42f, height * .68f)
                await { node("All apps", state = true) != null }
                SystemClock.sleep(350)
                assertEquals(0f, progress(), 0f)
                touch(MotionEvent.ACTION_DOWN, width * .16f, height * .65f)
                touch(MotionEvent.ACTION_MOVE, width * .21f, height * .66f)
                touch(MotionEvent.ACTION_MOVE, width * .31f, height * .67f)
                touch(MotionEvent.ACTION_MOVE, width * .42f, height * .68f)
                SystemClock.sleep(80)
                touch(MotionEvent.ACTION_UP, width * .42f, height * .68f)
                await { node("Home page 1 of 1", state = true) != null }
                SystemClock.sleep(250)
                assertEquals("Library swipes must never open Discover", 0f, progress(), 0f)
            }
            click("Discover")
            await { progress() >= .99f && LiveDiscover.nativePosition >= .99f && !LiveDiscover.pagerOwnsMotion }
            click("Search Google")
            await { automation.rootInActiveWindow?.packageName == DiscoverClient.GOOGLE_PACKAGE && hasSearchField(automation.rootInActiveWindow) }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
            SystemClock.sleep(400)
            if (automation.rootInActiveWindow?.packageName == DiscoverClient.GOOGLE_PACKAGE && hasSearchField(automation.rootInActiveWindow))
                android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
            // Google may return to the existing Discover page or issue a HOME intent.
            // Both must restore the launcher's last selected Home page.
            await { node("Search Google")?.isVisibleToUser == true && !hasSearchField(automation.rootInActiveWindow) }
            shell("input keyevent KEYCODE_HOME")
            await { progress() == 0f && LiveDiscover.nativePosition == 0f && !LiveDiscover.pagerOwnsMotion }
            assertEquals(before, context.getSharedPreferences("launcher", 0).getString("state", null))
        } finally {
            // A Home task can be recreated while an external search activity returns.
            // Follow the live owner instead of treating it as a fixed ActivityScenario.
            val cleanupHome = previousHome.takeIf { it.isNotEmpty() && it != "media.whitewhale.iduo" }
                ?: "com.google.android.apps.nexuslauncher"
            shell("cmd role add-role-holder android.app.role.HOME $cleanupHome 0")
            try {
                instrumentation.runOnMainSync {
                    val main = LiveDiscover.owner.get(); val host = LiveDiscover.host.get()
                    main?.finish(); host?.finish()
                }
            } finally {
                if (previousHome.isNotEmpty()) shell("cmd role add-role-holder android.app.role.HOME $previousHome 0")
                else shell("cmd role remove-role-holder android.app.role.HOME $cleanupHome 0")
            }
        }
    }
}

/** Deterministic ownership handoff: a late native update must not move a new Duo drag. */
class NativeCallbackHandoffIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:org.junit.Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    @Test fun lateNativeCallbackCannotInterruptNewDuoGesture() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(15_000) { !model.state.value.loading }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        val root = compose.onNodeWithTag("launcher-root")
        val cell = compose.onNodeWithTag("home-cell-0")
        compose.mainClock.autoAdvance = false
        try {
            // Leave native ownership active at an intermediate position, then begin a new
            // Compose drag while frame observers are paused at the ownership boundary.
            compose.runOnUiThread { LiveDiscover.onNativeProgress?.invoke(.35f) }
            compose.mainClock.advanceTimeByFrame()
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            val start = Offset(rootBounds.width * .68f, rootBounds.height * .7f)
            root.performTouchInput {
                down(start)
                moveTo(start + Offset(-rootBounds.width * .18f, 0f), 80)
            }
            val heldLeft = cell.fetchSemanticsNode().boundsInRoot.left

            // This represents a delayed Google callback from the preceding native gesture.
            compose.runOnUiThread { LiveDiscover.onNativeProgress?.invoke(.55f) }
            compose.mainClock.advanceTimeByFrame()
            val afterLateNative = cell.fetchSemanticsNode().boundsInRoot.left
            assertEquals("A late native callback must not move the active Duo gesture",
                heldLeft, afterLateNative, 1f)
        } finally {
            runCatching { root.performTouchInput { cancel() } }
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { LiveDiscover.page(0f, scrolling = false) }
        }
    }
}

/** Cover retention keeps the next widget page composed while native Discover is visible. */
class DiscoverWidgetRetentionIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:org.junit.Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    @Test fun discoverKeepsTheSecondHomePagesNativeWidgetAttached() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(15_000) { !model.state.value.loading }
        val before = model.state.value.layout
        val candidateWidget = before.widgetPlacements.firstOrNull { it.id >= 0 }
        org.junit.Assume.assumeNotNull(candidateWidget)
        val widget = candidateWidget!!
        val candidateTarget = (0 until HOME_CELLS).asSequence().map { homeCellIndex(1, it) }.firstOrNull {
            widgetCandidate(before, widget.slot, it, widget.spanX, widget.spanY) != null
        }
        org.junit.Assume.assumeTrue("Second page has no free widget footprint", candidateTarget != null)
        val target = candidateTarget!!
        try {
            compose.runOnIdle { assertTrue(model.moveWidgetTo(widget.slot, target)) }
            compose.onNodeWithContentDescription("Home page 1").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("widget-slot-${widget.slot}", useUnmergedTree = true).assertExists()

            compose.onNodeWithTag("discover-page-link").performClick()
            compose.waitUntil(5_000) {
                compose.onNodeWithTag("app-pager").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "Discover"
            }
            compose.waitForIdle()
            compose.onNodeWithTag("widget-slot-${widget.slot}", useUnmergedTree = true).assertExists()
        } finally {
            compose.runOnIdle { model.restoreLayout(before) }
        }
    }
}
