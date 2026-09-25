package media.whitewhale.iduo

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Invokes the visible Home page's labeled options action, then chooses Customize from its menu. */
internal fun openNativeHomeCustomization(automation: UiAutomation, chooseCustomize: () -> Unit) {
    fun homeOptions(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && node.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK && it.label?.toString() == "Home options"
            }) return node
        return (0 until node.childCount).firstNotNullOfOrNull { homeOptions(node.getChild(it)) }
    }
    val node = requireNotNull(homeOptions(automation.rootInActiveWindow)) {
        "Visible Home page has no Home options accessibility action"
    }
    check(node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
        "Home options accessibility action was rejected"
    }
    SystemClock.sleep(300)
    chooseCustomize()
}
