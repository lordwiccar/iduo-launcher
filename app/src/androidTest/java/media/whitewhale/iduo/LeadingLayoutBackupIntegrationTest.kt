package media.whitewhale.iduo

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeadingLayoutBackupIntegrationTest {
    private val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val profiles = listOf(
        AppProfile(0, "Personal", true, false, false, true, true),
        AppProfile(42, "Work", false, true, false, true, true),
    )

    private fun app(number: Int, work: Boolean = false): AppEntry {
        val component = "com.example.app$number/com.example.app$number.Main"
        val serial = if (work) 42L else 0L
        return AppEntry(profileAppId(component, serial, 0), "App $number", icon,
            ComponentName.unflattenFromString(component)!!, userSerial = serial,
            profileLabel = if (work) "Work" else "Personal", isWork = work)
    }

    private fun mixedState(): LauncherState {
        val apps = listOf(app(1), app(2), app(3, work = true), app(4))
        val folder = FolderEntry("folder:00000000-0000-0000-0000-000000000001", "Mixed",
            listOf(apps[1].id, apps[2].id))
        val leading = MutableList<String?>(HOME_CELLS) { null }.apply {
            this[8] = apps[0].id
            this[20] = folder.id
        }
        val placements = listOf(
            WidgetPlacement(20, NEEDS_BINDING_WIDGET, -1, 0, 0, 2, 2),
            WidgetPlacement(21, NEEDS_BINDING_WIDGET, -1, 2, 2, 2, 2),
            WidgetPlacement(22, CLOCK_WIDGET, 0, 0, 0, 2, 2),
        )
        val restores = listOf(
            WidgetRestore(20, "com.example.app1/com.example.app1.Widget", 0, "Personal widget", "Personal",
                sourceScope = "same-scope"),
            WidgetRestore(21, "com.example.app3/com.example.app3.Widget", 42, "Work widget", "Work", isWork = true,
                sourceScope = "same-scope"),
        )
        return LauncherState(apps = apps, profiles = profiles, folders = listOf(folder),
            homeSlots = listOf(null, null, apps[3].id), leadingSlots = leading,
            widgetPlacements = placements, widgetRestores = restores, loading = false)
    }

    @Test fun currentVersionRoundTripsMixedLeadingAppsFoldersWidgetsAndProfiles() {
        val state = mixedState()
        val raw = encodeLayoutBackup(state, emptyList(), "same-scope")
        val json = JSONObject(raw)
        assertEquals(LAYOUT_BACKUP_VERSION, json.getInt("version"))
        assertEquals(HOME_CELLS, json.getJSONArray("leadingSlots").length())

        val preview = decodeLayoutBackup(raw, state.apps, profiles, "same-scope")
        assertEquals(state.leadingSlots, preview.layout.leadingSlots)
        assertEquals(state.homeSlots, preview.layout.slots)
        assertEquals(state.folders, preview.layout.folders)
        assertEquals(state.widgetPlacements, preview.layout.widgetPlacements)
        assertEquals(state.widgetRestores, preview.layout.widgetRestores)
        assertTrue(preview.profileIssues.isEmpty())
        assertEquals(4, preview.appCount)
    }

    @Test fun version1ImportsWithCanonicalEmptyLeadingPageAndVersion2RequiresIt() {
        val state = mixedState()
        val old = JSONObject(encodeLayoutBackup(state.copy(leadingSlots = List(HOME_CELLS) { null },
            folders = emptyList()), emptyList(), "scope"))
            .put("version", 1).apply { remove("leadingSlots") }
        val imported = decodeLayoutBackup(old.toString(), state.apps, profiles, "scope")
        assertEquals(List<String?>(HOME_CELLS) { null }, imported.layout.leadingSlots)

        val missing = JSONObject(old.toString()).put("version", 2)
        assertThrows(Exception::class.java) {
            decodeLayoutBackup(missing.toString(), state.apps, profiles, "scope")
        }
        val short = JSONObject(encodeLayoutBackup(state, emptyList(), "scope"))
            .put("leadingSlots", JSONArray().put(JSONObject.NULL))
        assertThrows(Exception::class.java) {
            decodeLayoutBackup(short.toString(), state.apps, profiles, "scope")
        }
    }

    @Test fun rejectsDuplicateOrOrphanLeadingShortcutWidgetOverlapAndInvalidNegativePage() {
        val state = mixedState()
        fun base() = JSONObject(encodeLayoutBackup(state, emptyList(), "scope"))

        val duplicate = base().also {
            it.getJSONArray("leadingSlots").put(9, state.leadingSlots[8])
        }
        assertThrows(Exception::class.java) { decodeLayoutBackup(duplicate.toString(), state.apps, profiles, "scope") }

        val orphan = base().also {
            it.getJSONArray("leadingSlots").put(8, "folder:00000000-0000-0000-0000-000000000099")
        }
        assertThrows(Exception::class.java) { decodeLayoutBackup(orphan.toString(), state.apps, profiles, "scope") }

        val overlap = base().also {
            it.getJSONArray("leadingSlots").put(0, state.apps[0].id).put(8, JSONObject.NULL)
        }
        assertThrows(Exception::class.java) { decodeLayoutBackup(overlap.toString(), state.apps, profiles, "scope") }

        val negativePage = base().also { it.getJSONArray("widgets").getJSONObject(0).put("page", -2) }
        assertThrows(Exception::class.java) { decodeLayoutBackup(negativePage.toString(), state.apps, profiles, "scope") }
    }

    @Test fun fullLeadingPageRoundTripsWithoutCreatingCoverPageSlots() {
        val apps = (100 until 100 + HOME_CELLS).map(::app)
        val state = LauncherState(apps = apps, profiles = profiles, homeSlots = emptyList(),
            leadingSlots = apps.map(AppEntry::id), widgetPlacements = emptyList(), loading = false)
        val preview = decodeLayoutBackup(encodeLayoutBackup(state, emptyList(), "scope"), apps, profiles, "scope")
        assertEquals(apps.map(AppEntry::id), preview.layout.leadingSlots)
        assertTrue(preview.layout.slots.isEmpty())
        assertEquals(HOME_CELLS, preview.appCount)
        assertNull(preview.layout.slotAt(0))
    }
}
