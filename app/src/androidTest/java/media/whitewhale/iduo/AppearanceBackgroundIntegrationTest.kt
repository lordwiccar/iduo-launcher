package media.whitewhale.iduo

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import java.io.FileOutputStream
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AppearanceBackgroundIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(15_000) { !model().state.value.loading }
    private fun settings() {
        if (compose.onAllNodesWithTag("background-choose")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) {
            if (compose.onAllNodesWithTag("customization-wallpaper")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) {
                compose.openHomeCustomization()
            }
            compose.onNodeWithTag("customization-wallpaper").performScrollTo().performClick()
        }
        compose.onNodeWithTag("background-choose").assertExists()
    }

    @Test fun appearanceAndBackgroundPersistRecoverAndLeaveLayoutUntouched() {
        ready()
        val layout = model().state.value.layout
        val appearancePrefs = compose.activity.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val backgroundPrefs = compose.activity.getSharedPreferences("launcher_background", Context.MODE_PRIVATE)
        val oldAppearance = appearancePrefs.all.toMap()
        val oldBackground = backgroundPrefs.all.toMap()
        val file = launcherBackgroundFile(compose.activity)
        val oldPhoto = file.takeIf { it.isFile }?.readBytes()
        val oldStagedPhotos = stagedPhotoFiles(compose.activity)
        try {
            appearancePrefs.edit().clear().commit()
            backgroundPrefs.edit().clear().commit(); file.delete()
            compose.runOnUiThread { LauncherBackgroundCache.changed(null) }
            compose.activityRule.scenario.recreate(); ready(); settings()
            compose.onNodeWithTag("appearance-light").assertIsSelected()

            compose.onNodeWithTag("appearance-dark").performScrollTo().performClick()
            assertEquals("DARK", appearancePrefs.getString("mode", null))
            compose.onNodeWithTag("appearance-sunrise_sunset").performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("appearance-latitude")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNodeWithTag("appearance-latitude").performScrollTo().performTextInput("99")
            compose.onNodeWithTag("appearance-longitude").performScrollTo().performTextInput("0")
            compose.onNodeWithTag("appearance-save-place").performScrollTo().assertIsDisplayed().performClick()
            val coordinateError = hasText("Enter a latitude from −90 to 90 and longitude from −180 to 180.")
            try {
                compose.waitUntil(5_000) {
                    runCatching { compose.onNode(coordinateError).assertIsDisplayed() }.isSuccess
                }
            } catch (failure: Throwable) {
                writeFailureArtifacts("manual-feedback", appearancePrefs, file)
                throw failure
            }
            compose.onNodeWithTag("appearance-place").performScrollTo().performTextInput("Equator")
            compose.onNodeWithTag("appearance-latitude").performScrollTo().performTextReplacement("0")
            compose.onNodeWithTag("appearance-longitude").performScrollTo().performTextReplacement("0")
            compose.onNodeWithTag("appearance-save-place").performScrollTo().assertIsDisplayed().performClick()
            try {
                compose.waitUntil(5_000) { appearancePrefs.getString("lat", null) == "0.0" }
            } catch (failure: Throwable) {
                writeFailureArtifacts("manual-valid-save", appearancePrefs, file)
                throw failure
            }
            assertEquals("SUNRISE_SUNSET", appearancePrefs.getString("mode", null))
            assertEquals("0.0", appearancePrefs.getString("lat", null))
            compose.activityRule.scenario.recreate(); ready(); settings()
            compose.onNodeWithTag("appearance-sunrise_sunset").assertIsSelected()

            val bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff31596d.toInt()) }
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            file.writeBytes(bytes); backgroundPrefs.edit().putBoolean("photoEnabled", true).commit()
            compose.activityRule.scenario.recreate(); ready(); settings()
            // A photo iDuo did not set as Android's wallpaper cannot stand in for it.
            compose.waitUntil(5_000) { !file.exists() }; assertFalse(backgroundPrefs.getBoolean("photoEnabled", true))

            backgroundPrefs.edit().putBoolean("pickerPending", true).commit()
            compose.activityRule.scenario.recreate(); ready()
            compose.onNodeWithTag("background-picker-cancel").assertIsDisplayed().performClick()
            assertFalse(backgroundPrefs.contains("pickerPending"))
            assertEquals(layout, model().state.value.layout)
        } finally {
            restore(appearancePrefs, oldAppearance); restore(backgroundPrefs, oldBackground)
            if (oldPhoto == null) file.delete() else file.writeBytes(oldPhoto)
            restoreStagedPhotoFiles(compose.activity, oldStagedPhotos)
            compose.runOnUiThread { LauncherBackgroundCache.changed(null) }
            assertEquals(layout, model().state.value.layout)
        }
    }

    @Test fun realPhotoPickerSelectsTheExactOwnedMediaItem() {
        ready()
        val layout = model().state.value.layout
        val appContext = compose.activity.applicationContext
        val backgroundPrefs = appContext.getSharedPreferences("launcher_background", Context.MODE_PRIVATE)
        val oldBackground = backgroundPrefs.all.toMap()
        val privatePhoto = launcherBackgroundFile(appContext)
        val oldPhoto = privatePhoto.takeIf(File::isFile)?.readBytes()
        val oldStagedPhotos = stagedPhotoFiles(appContext)
        val resolver = appContext.contentResolver
        val displayName = "duo-background-${System.currentTimeMillis()}.jpg"
        val takenAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DuoLauncherTests")
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.DATE_ADDED, takenAt / 1000)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val media = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            val priorId = "prior-${System.currentTimeMillis()}"
            val priorBitmap = Bitmap.createBitmap(18, 12, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xff31596d.toInt())
            }
            val priorBytes = ByteArrayOutputStream().also {
                assertTrue(priorBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
            }.toByteArray()
            priorBitmap.recycle()
            privatePhoto.writeBytes(priorBytes)
            backgroundPrefs.edit().putBoolean("photoEnabled", true).putString("photoId", priorId)
                .remove("pickerPending").remove("pendingUri").remove("pendingOperation")
                .remove("previewPhase").remove("previewFile").commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(null) }
            compose.activityRule.scenario.recreate(); ready()

            val fixture = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xffc16f42.toInt())
            }
            resolver.openOutputStream(media, "w")!!.use { output ->
                assertTrue(fixture.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
            fixture.recycle()
            resolver.update(media, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val publishedTakenAt = resolver.query(media,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED),
                null, null, null)!!.use { cursor ->
                assertTrue("Inserted photo must remain queryable", cursor.moveToFirst())
                cursor.getLong(0).takeIf { it > 0L } ?: (cursor.getLong(1) * 1000L)
            }
            val pickerDescription = "Photo taken on " +
                SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(publishedTakenAt))

            settings()
            compose.onNodeWithTag("background-choose").performScrollTo().performClick()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val item = device.wait(Until.findObject(By.desc(pickerDescription)), 10_000)
            if (item == null) {
                val hierarchy = File(instrumentation.targetContext.cacheDir, "photo-picker-$displayName.xml")
                device.dumpWindowHierarchy(hierarchy)
                fail("Photo Picker did not expose $pickerDescription; hierarchy saved to ${hierarchy.absolutePath}")
            }
            requireNotNull(item).click()
            val done = device.wait(Until.findObject(By.text("Done")), 5_000)
            assertNotNull("Photo Picker must expose its confirmation action after selection", done)
            requireNotNull(done).click()
            try {
                compose.waitUntil(15_000) {
                    compose.activity.backgrounds.previewPending &&
                        compose.activity.backgrounds.previewBitmap != null &&
                        !compose.activity.backgrounds.pickerPending
                }
            } catch (failure: Throwable) {
                writeFailureArtifacts("photo-result", backgroundPrefs, privatePhoto)
                throw failure
            }
            assertArrayEquals("Selecting must leave the committed photo untouched", priorBytes, privatePhoto.readBytes())
            assertEquals(priorId, backgroundPrefs.getString("photoId", null))
            assertFalse(backgroundPrefs.contains("pickerPending"))
            assertEquals("ready", backgroundPrefs.getString("previewPhase", null))

            compose.activityRule.scenario.recreate(); ready()
            compose.waitUntil(10_000) {
                compose.activity.backgrounds.previewPending && compose.activity.backgrounds.previewBitmap != null
            }
            assertArrayEquals("Recreation must not commit the preview", priorBytes, privatePhoto.readBytes())
            assertEquals(priorId, backgroundPrefs.getString("photoId", null))
            compose.runOnUiThread { compose.activity.backgrounds.run { requestPhotoApply(); applyTo(WallpaperTarget.HOME) } }
            compose.waitUntil(5_000) {
                !compose.activity.backgrounds.previewPending &&
                    backgroundPrefs.getString("photoId", null) != priorId
            }
            val selected = requireNotNull(BitmapFactory.decodeFile(privatePhoto.absolutePath))
            assertEquals(40, selected.width)
            assertEquals(24, selected.height)
            val pixel = selected.getPixel(selected.width / 2, selected.height / 2)
            assertTrue(abs(android.graphics.Color.red(pixel) - 0xc1) <= 16)
            assertTrue(abs(android.graphics.Color.green(pixel) - 0x6f) <= 16)
            assertTrue(abs(android.graphics.Color.blue(pixel) - 0x42) <= 16)
            selected.recycle()
            LiveDiscover.owner.get()?.let { owner ->
                assertEquals(layout, ViewModelProvider(owner)[LauncherModel::class.java].state.value.layout)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LiveDiscover.owner.get()?.backgrounds?.cancelPreview()
                LiveDiscover.owner.get()?.backgrounds?.cancelPendingSelection()
            }
            resolver.delete(media, null, null)
            restore(backgroundPrefs, oldBackground)
            if (oldPhoto == null) privatePhoto.delete() else privatePhoto.writeBytes(oldPhoto)
            restoreStagedPhotoFiles(appContext, oldStagedPhotos)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { LauncherBackgroundCache.changed(null) }
            LiveDiscover.owner.get()?.let { owner ->
                assertEquals(layout, ViewModelProvider(owner)[LauncherModel::class.java].state.value.layout)
            }
        }
    }

    @Test fun cancelingRestoredPreviewKeepsCommittedPhotoAndCache() {
        ready()
        val appContext = compose.activity.applicationContext
        val prefs = appContext.getSharedPreferences("launcher_background", Context.MODE_PRIVATE)
        val oldPrefs = prefs.all.toMap()
        val committedFile = launcherBackgroundFile(appContext)
        val oldPhoto = committedFile.takeIf(File::isFile)?.readBytes()
        val oldStagedPhotos = stagedPhotoFiles(appContext)
        val operation = "cancel-preview-${System.currentTimeMillis()}"
        val stagedFile = File(committedFile.parentFile, "${committedFile.name}.$operation.fixture.tmp")
        val committedBitmap = Bitmap.createBitmap(16, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff31596d.toInt())
        }
        try {
            FileOutputStream(committedFile).use {
                assertTrue(committedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
            }
            Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888).also { staged ->
                staged.eraseColor(0xffc16f42.toInt())
                FileOutputStream(stagedFile).use {
                    assertTrue(staged.compress(Bitmap.CompressFormat.JPEG, 95, it))
                }
                staged.recycle()
            }
            prefs.edit().putBoolean("photoEnabled", true).putString("photoId", "committed")
                .putString("pendingOperation", operation).putString("previewPhase", "ready")
                .putString("previewFile", stagedFile.name).remove("pickerPending").commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(committedBitmap, "committed") }

            compose.activityRule.scenario.recreate(); ready()
            compose.waitUntil(10_000) {
                compose.activity.backgrounds.previewPending && compose.activity.backgrounds.previewBitmap != null
            }
            val displayedPreview = requireNotNull(compose.activity.backgrounds.previewBitmap)
            val committedBytes = committedFile.readBytes()
            compose.runOnUiThread { compose.activity.backgrounds.cancelPreview() }

            assertFalse(compose.activity.backgrounds.previewPending)
            assertNull(compose.activity.backgrounds.previewBitmap)
            assertArrayEquals(committedBytes, committedFile.readBytes())
            assertEquals("committed", prefs.getString("photoId", null))
            assertFalse(stagedFile.exists())
            assertFalse(prefs.contains("previewPhase"))
            assertFalse("Cancel must not recycle a bitmap already published to Compose", displayedPreview.isRecycled)
            assertSame(committedBitmap, LauncherBackgroundCache.bitmap)
            assertFalse("Cancel must not recycle the bitmap currently used by launcher canvases", committedBitmap.isRecycled)
        } finally {
            compose.runOnUiThread {
                compose.activity.backgrounds.cancelPreview()
                LauncherBackgroundCache.changed(null)
            }
            restore(prefs, oldPrefs)
            if (oldPhoto == null) committedFile.delete() else committedFile.writeBytes(oldPhoto)
            stagedFile.delete()
            restoreStagedPhotoFiles(appContext, oldStagedPhotos)
            if (!committedBitmap.isRecycled) committedBitmap.recycle()
        }
    }

    private fun stagedPhotoFiles(context: Context): Map<String, ByteArray> {
        val committed = launcherBackgroundFile(context)
        return committed.parentFile?.listFiles { file ->
            file.name.startsWith("${committed.name}.") && file.name.endsWith(".tmp")
        }?.associate { it.name to it.readBytes() }.orEmpty()
    }

    private fun restoreStagedPhotoFiles(context: Context, files: Map<String, ByteArray>) {
        val committed = launcherBackgroundFile(context)
        committed.parentFile?.listFiles { file ->
            file.name.startsWith("${committed.name}.") && file.name.endsWith(".tmp")
        }?.forEach(File::delete)
        files.forEach { (name, bytes) -> File(committed.parentFile, name).writeBytes(bytes) }
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        prefs.edit().clear().also { edit -> values.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value); is Float -> edit.putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
        } } }.commit()
    }

    private fun writeFailureArtifacts(name: String, prefs: SharedPreferences, privateFile: File) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val directory = instrumentation.targetContext.cacheDir
        val owner = LiveDiscover.owner.get()
        val controller = owner?.backgrounds
        File(directory, "$name-state.txt").writeText(buildString {
            appendLine("package=${device.currentPackageName}")
            appendLine("owner=${owner?.javaClass?.name}")
            appendLine("ownerLifecycle=${owner?.lifecycle?.currentState}")
            appendLine("loading=${controller?.loading}")
            appendLine("pickerPending=${controller?.pickerPending}")
            appendLine("previewPending=${controller?.previewPending}")
            appendLine("previewReady=${controller?.previewBitmap != null}")
            appendLine("photoSelected=${controller?.photoSelected}")
            appendLine("error=${controller?.errorMessage}")
            appendLine("success=${controller?.successMessage}")
            appendLine("prefs=${prefs.all}")
            appendLine("fileExists=${privateFile.exists()}")
            appendLine("fileBytes=${privateFile.takeIf(File::exists)?.length() ?: 0L}")
            appendLine("activities=${runCatching { device.executeShellCommand("dumpsys activity activities") }.getOrElse { it.toString() }}")
        })
        device.dumpWindowHierarchy(File(directory, "$name-hierarchy.xml"))
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(directory, "$name.png")).use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
