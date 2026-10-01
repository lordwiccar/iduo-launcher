package media.whitewhale.iduo

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * Returns to Home after every test that left another app in front, such as an app a failed long
 * press opened or its Google sign-in screen, so it cannot cover the tests that follow.
 */
class ForeignAppGuard : RunListener() {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun foregroundPackage(): String? = Regex("""topResumedActivity=ActivityRecord\{\S+ \S+ ([^/\s]+)/""")
        .find(shell("dumpsys activity activities"))?.groupValues?.get(1)

    override fun testFinished(description: Description) {
        val home = instrumentation.targetContext.packageName
        val allowed = setOf(home, instrumentation.context.packageName)
        repeat(3) {
            val foreground = foregroundPackage() ?: return
            if (foreground in allowed) return
            shell("input keyevent KEYCODE_HOME")
            SystemClock.sleep(500)
        }
    }
}
