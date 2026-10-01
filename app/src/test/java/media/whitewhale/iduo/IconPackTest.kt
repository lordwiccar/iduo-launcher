package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPackTest {
    @Test fun readsFullAndShortComponentNames() {
        assertEquals("com.android.chrome/com.google.android.apps.chrome.Main",
            appfilterComponent("ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}"))
        assertEquals("com.android.camera2/com.android.camera2.CameraLauncher",
            appfilterComponent("ComponentInfo{com.android.camera2/.CameraLauncher}"))
        assertEquals("a.b/a.b.C", appfilterComponent(" ComponentInfo{ a.b / a.b.C } "))
    }

    @Test fun rejectsMalformedEntries() {
        assertNull(appfilterComponent("ComponentInfo{}"))
        assertNull(appfilterComponent("ComponentInfo{com.example}"))
        assertNull(appfilterComponent("com.example/.Main"))
        assertNull(appfilterComponent("ComponentInfo{/.Main}"))
    }
}
