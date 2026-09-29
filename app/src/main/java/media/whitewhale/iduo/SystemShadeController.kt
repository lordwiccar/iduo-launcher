package media.whitewhale.iduo

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference

internal enum class ShadePanel { NOTIFICATIONS, QUICK_SETTINGS }

internal enum class ShadeOpenResult { OPENED, SERVICE_DISABLED, SERVICE_STARTING, ACTION_REJECTED }

/**
 * The platform exposes shade expansion and screen locking to third-party apps only as
 * accessibility global actions. This service deliberately observes no events and cannot inspect
 * windows or inject gestures.
 */
class SystemShadeAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        // The declaration lets Settings describe the service. Once bound, unsubscribe from
        // even our own package's events: global actions do not require event delivery.
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }

    companion object {
        private var instance = WeakReference<SystemShadeAccessibilityService>(null)
        internal fun isConnected() = instance.get() != null

        internal fun open(context: Context, panel: ShadePanel): ShadeOpenResult = perform(context, when (panel) {
            ShadePanel.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            ShadePanel.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
        })

        /** Locks the screen as the power button does, so biometric unlock stays available. */
        internal fun lockScreen(context: Context): ShadeOpenResult = perform(context, GLOBAL_ACTION_LOCK_SCREEN)

        private fun perform(context: Context, action: Int): ShadeOpenResult {
            val service = instance.get()
            if (service != null) {
                return if (service.performGlobalAction(action)) ShadeOpenResult.OPENED
                else ShadeOpenResult.ACTION_REJECTED
            }
            return if (isEnabled(context)) ShadeOpenResult.SERVICE_STARTING
            else ShadeOpenResult.SERVICE_DISABLED
        }

        internal fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, SystemShadeAccessibilityService::class.java)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any {
                val service = it.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(service.packageName, service.name) == component
            }
        }
    }
}
