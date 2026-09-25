package media.whitewhale.iduo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DiscoverStartupRecoveryIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Blocks exactly the requested launches, independent of when ActivityScenario returns. */
    private class DiscoverLaunchMonitor(private val launchesToBlock: Int) : Instrumentation.ActivityMonitor() {
        private val matches = AtomicInteger()
        val blocked get() = matches.get().coerceAtMost(launchesToBlock)
        val attempts get() = matches.get()
        val recoveredAttempt get() = launchesToBlock + 1

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != LiveDiscoverActivity::class.java.name) return null
            val match = matches.incrementAndGet()
            return if (match <= launchesToBlock)
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            else null
        }
    }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for Discover startup recovery" }
            SystemClock.sleep(25)
        }
    }

    @Test fun skippedFirstLaunchRetriesWithARealHost() = verifyRecovery(blockBothAttempts = false)

    @Test fun twoSkippedLaunchesExposeRetryAndExplicitRetryRecovers() = verifyRecovery(blockBothAttempts = true)

    private fun verifyRecovery(blockBothAttempts: Boolean) {
        assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        assumeTrue(DiscoverBounds.available)
        val attachNativeFeed = LiveDiscover.attachNativeFeed
        LiveDiscover.attachNativeFeed = true
        var oldMain = LiveDiscover.owner.get()
        var oldHost = LiveDiscover.host.get()
        instrumentation.runOnMainSync { oldMain?.finish(); oldHost?.finish() }
        await { LiveDiscover.host.get() == null }

        val monitor = DiscoverLaunchMonitor(if (blockBothAttempts) 2 else 1)
        instrumentation.addMonitor(monitor)
        var monitorInstalled = true
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            var activity: MainActivity? = null
            var model: LauncherModel? = null
            scenario.onActivity {
                activity = it
                model = ViewModelProvider(it)[LauncherModel::class.java]
            }
            await { monitor.blocked >= 1 }
            await { model?.state?.value?.loading == false }
            val main = requireNotNull(activity)
            val before = main.getSharedPreferences("launcher", 0).getString("state", null)
            val widgetIds = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(main).let { it as WidgetController }.host.appWidgetIds.toSet()

            if (blockBothAttempts) {
                await { monitor.blocked >= 2 }
                await { LiveDiscover.message.value == R.string.discover_start_failed }
                instrumentation.runOnMainSync { LiveDiscover.retry() }
            }
            await { LiveDiscover.host.get()?.let { !it.isFinishing && !it.isDestroyed } == true }
            assertTrue("Recovery must launch after every deliberately blocked attempt",
                monitor.attempts >= monitor.recoveredAttempt)
            instrumentation.removeMonitor(monitor)
            monitorInstalled = false
            // A connected offscreen feed stays at position zero and is intentionally hidden.
            // Open it once so Google's real progress callback proves the recovered transport.
            instrumentation.runOnMainSync { LiveDiscover.page(1f, scrolling = false) }
            await(20_000) { LiveDiscover.message.value == null }

            assertEquals(before, main.getSharedPreferences("launcher", 0).getString("state", null))
            assertEquals(widgetIds, MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(main).let { it as WidgetController }.host.appWidgetIds.toSet())
        } finally {
            try {
                if (monitorInstalled) instrumentation.removeMonitor(monitor)
                scenario?.close()
                instrumentation.runOnMainSync { LiveDiscover.host.get()?.finish() }
                await { LiveDiscover.host.get() == null }
            } finally {
                LiveDiscover.attachNativeFeed = attachNativeFeed
            }
        }
    }
}
