package media.whitewhale.iduo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class LauncherBackgroundRecoveryIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    @Test fun readyLocalPreviewRecoversWithoutSourceAndDropsStaleGrantRecord() {
        val context = compose.activity.applicationContext
        val prefs = launcherBackgroundPreferences(context)
        val committedFile = launcherBackgroundFile(context)
        val oldPrefs = prefs.all.toMap()
        val oldCommitted = committedFile.takeIf(File::isFile)?.readBytes()
        val oldStaged = stagedFiles(context)
        val oldCacheBitmap = LauncherBackgroundCache.bitmap
        val oldCacheIdentity = LauncherBackgroundCache.identity
        val operation = "ready-recovery-${System.currentTimeMillis()}"
        val stagedFile = File(committedFile.parentFile, "${committedFile.name}.$operation.fixture.tmp")
        val committedBitmap = solidBitmap(16, 10, 0xff31596d.toInt())
        try {
            val committedBytes = jpeg(committedBitmap)
            committedFile.writeBytes(committedBytes)
            stagedFile.writeBytes(jpeg(solidBitmap(30, 20, 0xffc16f42.toInt()), recycle = true))
            prefs.edit().clear().putBoolean("photoEnabled", true).putString("photoId", "committed")
                .putString("pendingOperation", operation).putString("previewPhase", "ready")
                .putString("previewFile", stagedFile.name).putString("pendingUri", "content://missing/source")
                .putString("previewGrantUri", "content://missing/source")
                .putString("previewGrantOperation", operation).commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(committedBitmap, "committed") }

            compose.activityRule.scenario.recreate()
            waitForModel()
            compose.waitUntil(10_000) {
                compose.activity.backgrounds.previewPending && compose.activity.backgrounds.previewBitmap != null
            }

            assertArrayEquals(committedBytes, committedFile.readBytes())
            assertEquals("committed", prefs.getString("photoId", null))
            assertFalse(prefs.contains("pendingUri"))
            assertFalse(prefs.contains("previewGrantUri"))
            assertFalse(prefs.contains("previewGrantOperation"))
            compose.runOnUiThread { compose.activity.backgrounds.cancelPreview() }
            assertArrayEquals(committedBytes, committedFile.readBytes())
            assertSame(committedBitmap, LauncherBackgroundCache.bitmap)
            assertFalse(committedBitmap.isRecycled)
        } finally {
            compose.runOnUiThread { compose.activity.backgrounds.cancelPreview() }
            restore(prefs, oldPrefs)
            if (oldCommitted == null) committedFile.delete() else committedFile.writeBytes(oldCommitted)
            restoreStagedFiles(context, oldStaged)
            compose.runOnUiThread { LauncherBackgroundCache.changed(oldCacheBitmap, oldCacheIdentity) }
        }
    }

    @Test fun inaccessibleDecodingRecoveryFailsWithoutChangingCommittedBackground() {
        val context = compose.activity.applicationContext
        val prefs = launcherBackgroundPreferences(context)
        val committedFile = launcherBackgroundFile(context)
        val oldPrefs = prefs.all.toMap()
        val oldCommitted = committedFile.takeIf(File::isFile)?.readBytes()
        val oldStaged = stagedFiles(context)
        val oldCacheBitmap = LauncherBackgroundCache.bitmap
        val oldCacheIdentity = LauncherBackgroundCache.identity
        val operation = "decode-recovery-${System.currentTimeMillis()}"
        val committedBitmap = solidBitmap(16, 10, 0xff31596d.toInt())
        try {
            val committedBytes = jpeg(committedBitmap)
            committedFile.writeBytes(committedBytes)
            prefs.edit().clear().putBoolean("photoEnabled", true).putString("photoId", "committed")
                .putString("pendingOperation", operation).putString("previewPhase", "decoding")
                .putString("pendingUri", "content://missing/source")
                .putString("previewGrantUri", "content://missing/source")
                .putString("previewGrantOperation", operation).commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(committedBitmap, "committed") }

            compose.activityRule.scenario.recreate()
            waitForModel()
            compose.waitUntil(10_000) {
                !compose.activity.backgrounds.loading && compose.activity.backgrounds.errorMessage != null
            }

            assertArrayEquals(committedBytes, committedFile.readBytes())
            assertEquals("committed", prefs.getString("photoId", null))
            assertTrue(compose.activity.backgrounds.photoSelected)
            assertFalse(compose.activity.backgrounds.previewPending)
            assertNull(compose.activity.backgrounds.previewBitmap)
            assertNotNull(compose.activity.backgrounds.errorMessage)
            listOf("pendingOperation", "previewPhase", "previewFile", "pendingUri",
                "previewGrantUri", "previewGrantOperation").forEach { assertFalse(prefs.contains(it)) }
            assertSame(committedBitmap, LauncherBackgroundCache.bitmap)
            assertFalse(committedBitmap.isRecycled)
        } finally {
            restore(prefs, oldPrefs)
            if (oldCommitted == null) committedFile.delete() else committedFile.writeBytes(oldCommitted)
            restoreStagedFiles(context, oldStaged)
            compose.runOnUiThread { LauncherBackgroundCache.changed(oldCacheBitmap, oldCacheIdentity) }
        }
    }

    private fun waitForModel() = compose.waitUntil(15_000) {
        !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading
    }

    private fun solidBitmap(width: Int, height: Int, color: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun jpeg(bitmap: Bitmap, recycle: Boolean = false): ByteArray = ByteArrayOutputStream().also {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
        if (recycle) bitmap.recycle()
    }.toByteArray()

    private fun stagedFiles(context: Context): Map<String, ByteArray> {
        val committed = launcherBackgroundFile(context)
        return committed.parentFile?.listFiles { file ->
            file.name.startsWith("${committed.name}.") && file.name.endsWith(".tmp")
        }?.associate { it.name to it.readBytes() }.orEmpty()
    }

    private fun restoreStagedFiles(context: Context, files: Map<String, ByteArray>) {
        val committed = launcherBackgroundFile(context)
        committed.parentFile?.listFiles { file ->
            file.name.startsWith("${committed.name}.") && file.name.endsWith(".tmp")
        }?.forEach(File::delete)
        files.forEach { (name, bytes) -> File(committed.parentFile, name).writeBytes(bytes) }
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        prefs.edit().clear().also { edit -> values.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
        } } }.commit()
    }
}
