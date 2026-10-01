package media.whitewhale.iduo

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeparateCoverIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(15_000) { !model().state.value.loading }

    @Test fun theCoverGetsItsOwnCopyAndEachScreenKeepsItsChanges() {
        ready()
        val onCover = compose.activity.resources.configuration.screenWidthDp < 650
        val before = model().state.value
        val host = AppWidgetHost(compose.activity, 1024)
        val manager = AppWidgetManager.getInstance(compose.activity)
        var id = -1
        var slot = -1
        try {
            compose.runOnIdle { model().setSeparateCover(false) }
            val provider = manager.installedProviders.first { it.provider.className.endsWith("AnalogAppWidgetProvider") }
            id = host.allocateAppWidgetId()
            assertTrue("Emulator fixture requires widget binding permission", manager.bindAppWidgetIdIfAllowed(id, provider.provider))
            slot = model().nextWidgetSlot()
            val layout = model().state.value.layout
            val draft = (0 until HOME_CELLS * (layout.pageCount + 1)).firstNotNullOf { widgetCandidate(layout, slot, it, 2, 2) }
            compose.runOnIdle { assertTrue(model().placeWidget(draft.copy(id = id))) }
            val inner = model().state.value.innerLayout
            val hadCover = model().state.value.coverLayout != null

            compose.runOnIdle { model().setSeparateCover(true) }
            val cover = requireNotNull(model().state.value.coverLayout)
            if (!hadCover) {
                assertEquals(inner.slots, cover.slots)
                // The Android widget stays bound on the inner screen and waits to reconnect on the cover.
                assertEquals(NEEDS_BINDING_WIDGET, cover.placement(slot)?.id)
                assertEquals(provider.provider.flattenToString(), cover.widgetRestore(slot)?.providerComponent)
            }
            assertEquals(id, model().state.value.innerLayout.placement(slot)?.id)
            assertTrue(id in model().retainedWidgetIds)

            // Five columns need a dock that is not always shown.
            compose.runOnIdle { model().setCoverColumns(5) }
            if (model().state.value.dockMode == DockMode.SHOWN) assertEquals(DEFAULT_HOME_COLUMNS, model().state.value.coverLayout!!.columns)
            compose.runOnIdle { model().setDockMode(DockMode.HIDDEN); model().setCoverColumns(5) }
            assertEquals(5, model().state.value.coverLayout!!.columns)
            assertEquals(DEFAULT_HOME_COLUMNS, model().state.value.innerLayout.columns)

            // An app moved into the cover's fifth column leaves the inner screen as it was.
            compose.runOnIdle { model().showDisplay(true) }
            assertTrue(model().state.value.showingCover)
            val shown = model().state.value.layout
            val appId = shown.slots.filterNotNull().first { !isReservedFolderId(it) }
            val fifth = (0 until HOME_CELLS).map { homeCellIndex(0, it) }.first { index ->
                homeCellLocal(index) % GRID_COLUMNS == 4 && shown.cellVisible(index) && shown.slotAt(index) == null &&
                    index !in shown.unavailableCells()
            }
            compose.runOnIdle { assertTrue(model().applyDrop(appId, DropTarget.Home(fifth))) }
            assertEquals(appId, model().state.value.coverLayout!!.slotAt(fifth))
            assertEquals(model().state.value.innerLayout.slots.filterNotNull().toSet(), inner.slots.filterNotNull().toSet())
            assertEquals(inner.indexOfShortcut(appId), model().state.value.innerLayout.indexOfShortcut(appId))
            compose.runOnIdle { model().showDisplay(onCover) }

            // Saved state holds the inner layout at the top level and the cover's own beside it.
            val saved = JSONObject(compose.activity.getSharedPreferences("launcher", 0).getString("state", null)!!)
            assertTrue(saved.getBoolean("separateCover"))
            assertEquals(model().state.value.coverLayout, decodeCoverLayout(saved.getJSONObject("cover"), model().state.value.dock))
            assertEquals(inner.indexOfShortcut(appId), saved.getJSONArray("homeSlots").let { cells ->
                (0 until cells.length()).firstOrNull { !cells.isNull(it) && cells.getString(it) == appId } })

            // An always-shown dock takes the cover back to four columns without losing the moved app.
            compose.runOnIdle { model().setDockMode(DockMode.SHOWN) }
            val four = model().state.value.coverLayout!!
            assertEquals(DEFAULT_HOME_COLUMNS, four.columns)
            assertNotNull(four.indexOfShortcut(appId))
            assertTrue(four.slots.indices.filter { four.slots[it] != null }.all(four::cellVisible))

            // Mirroring shows the inner layout on both screens and keeps the cover's for later.
            compose.runOnIdle { model().setSeparateCover(false) }
            assertFalse(model().state.value.showingCover)
            assertEquals(inner.indexOfShortcut(appId), model().state.value.layout.indexOfShortcut(appId))
            assertEquals(four, model().state.value.coverLayout)
            compose.runOnIdle { model().setSeparateCover(true) }
            assertEquals(four.slots, model().state.value.coverLayout!!.slots)
        } finally {
            compose.runOnIdle {
                model().setSeparateCover(false)
                model().setDockMode(before.dockMode)
                if (slot >= 0) model().removePlacement(DropTarget.Widget(slot))
            }
            if (id >= 0) host.deleteAppWidgetId(id)
        }
    }
}
