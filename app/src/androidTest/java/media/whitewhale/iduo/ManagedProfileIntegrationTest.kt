package media.whitewhale.iduo

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Destructive only inside the explicitly named disposable DuoTest managed profile. */
class ManagedProfileIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(20_000) { !model().state.value.loading }
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun isTopResumed(userId: Int, component: ComponentName): Boolean =
        shell("dumpsys activity activities").lineSequence().any { line ->
            ("topResumedActivity" in line || "mCurrentFocus" in line) && "u$userId " in line &&
                component.flattenToShortString() in line
        }

    @Test fun personalAndDisposableWorkCopiesRemainIndependentThroughQuietModeAndUninstall() {
        val args = InstrumentationRegistry.getArguments()
        val workUserId = args.getString("duoManagedUserId")?.toIntOrNull()
        val workSerial = args.getString("duoManagedProfileSerial")?.toLongOrNull()
        val uninstallPackage = args.getString("duoUninstallPackage")
        val uninstallComponent = args.getString("duoUninstallComponent")?.let(ComponentName::unflattenFromString)
        assumeTrue("Pass the disposable managed profile user ID", workUserId != null)
        assumeTrue("Pass the disposable managed profile serial", workSerial != null)
        assumeTrue("Pass a separate launchable package installed in both profiles", uninstallPackage != null && uninstallComponent != null)
        val removablePackage = requireNotNull(uninstallPackage)
        val removableComponent = requireNotNull(uninstallComponent)
        assumeTrue("Fixture package must be safe to pass as a shell argument",
            removablePackage.matches(Regex("[A-Za-z0-9_.]+")))
        val users = shell("cmd user list")
        assumeTrue("Managed profile must have the disposable DuoTest- prefix", Regex("UserInfo\\{$workUserId:DuoTest-[^:}]+").containsMatchIn(users))

        ready()
        val launcherApps = compose.activity.getSystemService(LauncherApps::class.java)
        val userManager = compose.activity.getSystemService(UserManager::class.java)
        assertTrue("Duo must be the default Home before requesting quiet mode",
            compose.activity.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME))
        val personal = Process.myUserHandle()
        val work = requireNotNull(userManager.getUserForSerialNumber(workSerial!!))
        assertNotEquals(personal, work)
        assertTrue(isSupportedWorkProfile(launcherApps, work))
        val policyToken = "duo-${java.util.UUID.randomUUID()}"
        shell("am start --user $workUserId -a media.whitewhale.iduo.test.ENABLE_CROSS_PROFILE_WIDGETS " +
            "-n media.whitewhale.iduo.test/.ProfileFixtureActivity --es widgetPackage $removablePackage " +
            "--es resultToken $policyToken")
        var policyEvidence = ""
        compose.waitUntil(10_000) {
            policyEvidence = shell("logcat -d -s DuoProfilePolicy:I")
            policyEvidence.lineSequence().any { policyToken in it }
        }
        assertTrue("Profile-owner policy action failed: $policyEvidence",
            policyEvidence.lineSequence().any { line ->
                policyToken in line && "owner=true" in line && "requested=$removablePackage" in line &&
                    Regex("after=\\[[^]]*${Regex.escape(removablePackage)}(?:,|])").containsMatchIn(line) &&
                    "error=" !in line
            })
        val fixture = ComponentName("media.whitewhale.iduo.test", "media.whitewhale.iduo.test.ProfileFixtureActivity")
        val personalId = profileAppId(fixture.flattenToString(), userManager.getSerialNumberForUser(personal),
            userManager.getSerialNumberForUser(personal))
        val workId = profileAppId(fixture.flattenToString(), workSerial, userManager.getSerialNumberForUser(personal))
        compose.runOnIdle { model().refresh() }
        compose.waitUntil(20_000) { model().state.value.apps.any { it.id == personalId } && model().state.value.apps.any { it.id == workId } }
        val personalApp = model().state.value.apps.single { it.id == personalId }
        val workApp = model().state.value.apps.single { it.id == workId }
        assertEquals(personal, personalApp.user)
        assertEquals(work, workApp.user)
        assertFalse(personalApp.isWork)
        assertTrue(workApp.isWork)
        assertFalse("Work icon should carry Android's profile badge", personalApp.icon.sameAs(workApp.icon))
        compose.onNodeWithTag("library-page-link").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("library-app-$personalId").assertExists()
        compose.onNodeWithTag("library-app-$workId").assertDoesNotExist()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithTag("library-app-$workId").assertExists()
        compose.onNodeWithTag("library-app-$personalId").assertDoesNotExist()
        val personalPinId = profileAppId(removableComponent.flattenToString(),
            userManager.getSerialNumberForUser(personal), userManager.getSerialNumberForUser(personal))
        val workPinId = profileAppId(removableComponent.flattenToString(), workSerial,
            userManager.getSerialNumberForUser(personal))
        val personalPin = model().state.value.apps.first { it.id == personalPinId }
        val workPin = model().state.value.apps.first { it.id == workPinId }

        val controller = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
            .get(compose.activity) as WidgetController
        assertTrue(controller.providers(personal).any { it.provider.packageName == "media.whitewhale.iduo.test" })
        assertTrue("The DPC-allowlisted work widget package must be discoverable",
            controller.providers(work).any { it.provider.packageName == removablePackage })

        val before = model().state.value.layout
        val homeIndex = (0 until HOME_CELLS * (before.pageCount + 1)).first { before.slots.getOrNull(it) == null &&
            before.widgetPlacements.none { widget -> it in widget.coveredIndices() } }
        val dockIndex = before.dock.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
        try {
            compose.runOnIdle {
                if (before.dock[dockIndex] != null) model().setDock(dockIndex, null)
                assertTrue(model().applyDrop(personalPin.id, DropTarget.Home(homeIndex)))
                assertTrue(model().applyDrop(workPin.id, DropTarget.Dock(dockIndex)))
            }
            assertEquals(personalPin.id, model().state.value.homeSlots[homeIndex])
            assertEquals(workPin.id, model().state.value.dock[dockIndex])

            // Exercise the same profile-aware launch callback used by Home and the dock.
            val launch = MainActivity::class.java.getDeclaredMethod("launchApp", AppEntry::class.java, android.graphics.Rect::class.java)
                .apply { isAccessible = true }
            compose.runOnIdle { launch.invoke(compose.activity, workApp, null) }
            compose.waitUntil(10_000) { isTopResumed(workUserId!!, fixture) }
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
            compose.runOnIdle { launch.invoke(compose.activity, personalApp, null) }
            compose.waitUntil(10_000) { isTopResumed(0, fixture) }
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()

            assertTrue(userManager.requestQuietModeEnabled(true, work))
            compose.waitUntil(20_000) { userManager.isQuietModeEnabled(work) }
            compose.runOnIdle { model().refresh() }
            compose.waitUntil(20_000) { model().state.value.profiles.any { it.userSerial == workSerial && it.quiet } }
            assertEquals(workPin.id, model().state.value.dock[dockIndex])
            assertEquals("Work", model().state.value.apps.first { it.id == workId }.profileLabel)
            assertFalse(model().state.value.apps.first { it.id == workId }.available)
            compose.onNodeWithTag("library-page-link").performClick()
            compose.waitForIdle()
            // A work app can remain in the precomposed semantics tree while Personal is
            // selected, so its mere presence does not identify the active tab.
            compose.onNode(hasText("Work") and hasClickAction()).performClick()
            try {
                compose.waitUntil(5_000) {
                    runCatching { compose.onNodeWithText("Work apps are paused").assertIsDisplayed() }.isSuccess
                }
            } catch (failure: Throwable) {
                val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .takeScreenshot(java.io.File(directory, "managed-profile-paused-timeout.png"))
                java.io.File(directory, "managed-profile-paused-semantics.txt").writeText(
                    compose.onRoot(useUnmergedTree = true).printToString() +
                        "\nowner=${LiveDiscover.owner.get()} host=${LiveDiscover.host.get()}" +
                        "\nprofiles=${model().state.value.profiles}")
                throw failure
            }
            compose.onNodeWithText("Work apps are paused").assertIsDisplayed()
            compose.onNodeWithTag("turn-on-work").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !userManager.isQuietModeEnabled(work) }
            compose.runOnIdle { model().refresh() }
            compose.waitUntil(20_000) { model().state.value.apps.any { it.id == workId && it.available } }

            // Remove only the separate fixture from the disposable work user.
            assertTrue(shell("pm uninstall --user $workUserId $removablePackage").contains("Success"))
            compose.runOnIdle { model().refresh() }
            compose.waitUntil(20_000) { model().state.value.apps.none { it.id == workPin.id } }
            assertEquals(personalPin.id, model().state.value.homeSlots[homeIndex])
            assertFalse(workPin.id in model().state.value.dock)
        } finally {
            runCatching { userManager.requestQuietModeEnabled(false, work) }
            compose.runOnIdle { model().restoreLayout(before) }
        }
    }
}
