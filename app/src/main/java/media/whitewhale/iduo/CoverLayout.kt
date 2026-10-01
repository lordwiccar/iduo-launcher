package media.whitewhale.iduo

import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject

/**
 * The cover screen's own Home, made from the inner screen's: the same pages, apps, folders and
 * widgets, without the unfolded-only page. Android widgets cannot be shown in two places, so each
 * becomes a placeholder that reconnects the same provider; [restoreFor] describes it, or returns
 * null to leave that widget out.
 */
fun coverLayoutFrom(inner: HomeLayout, restoreFor: (WidgetPlacement) -> WidgetRestore?): HomeLayout {
    val placements = mutableListOf<WidgetPlacement>()
    val restores = mutableListOf<WidgetRestore>()
    inner.widgetPlacements.filter { it.page >= 0 }.forEach { placement ->
        when {
            placement.id >= 0 -> restoreFor(placement)?.let { restore ->
                placements += placement.copy(id = NEEDS_BINDING_WIDGET)
                restores += restore.copy(slot = placement.slot)
            }
            placement.id == NEEDS_BINDING_WIDGET -> inner.widgetRestore(placement.slot)?.let { restore ->
                placements += placement
                restores += restore
            }
            else -> placements += placement
        }
    }
    return inner.copy(widgetPlacements = placements, widgetRestores = restores,
        leadingSlots = List(HOME_CELLS) { null }, columns = DEFAULT_HOME_COLUMNS)
}

/** The cover's layout for saved state. The dock is shared with the inner screen and not included. */
fun encodeCoverLayout(layout: HomeLayout): JSONObject {
    val widgets = JSONArray().also { array -> layout.widgetPlacements.forEach { w -> array.put(JSONObject()
        .put("slot", w.slot).put("id", w.id).put("page", w.page).put("column", w.column).put("row", w.row)
        .put("spanX", w.spanX).put("spanY", w.spanY)) } }
    val folders = JSONArray().also { array -> layout.folders.forEach { folder -> array.put(JSONObject()
        .put("id", folder.id).put("title", folder.title).put("apps", JSONArray(folder.appIds))) } }
    val restores = JSONArray().also { array -> layout.widgetRestores.forEach { restore -> array.put(JSONObject()
        .put("slot", restore.slot).put("provider", restore.providerComponent).put("userSerial", restore.userSerial)
        .put("title", restore.title).put("profileLabel", restore.profileLabel).put("work", restore.isWork)
        .put("sourceScope", restore.sourceScope)) } }
    return JSONObject().put("homeSlots", JSONArray(layout.slots)).put("widgets", widgets).put("folders", folders)
        .put("restores", restores).put("homeRows", layout.rows).put("homeColumns", layout.columns)
}

/** Reads [encodeCoverLayout]'s form, sharing [dock]. Anything inconsistent throws. */
fun decodeCoverLayout(json: JSONObject, dock: List<String?>): HomeLayout {
    val cells = json.getJSONArray("homeSlots")
    val slots = List(cells.length()) { index -> if (cells.isNull(index)) null else cells.getString(index).takeIf { it.isNotBlank() && it != "null" } }
    val rows = json.getInt("homeRows").also { require(it in DEFAULT_HOME_ROWS..GRID_ROWS) }
    val columns = json.getInt("homeColumns").also { require(it in DEFAULT_HOME_COLUMNS..GRID_COLUMNS) }
    val folderArray = json.getJSONArray("folders")
    val folders = List(folderArray.length()) { index ->
        val item = folderArray.getJSONObject(index)
        val apps = item.getJSONArray("apps")
        FolderEntry(item.getString("id"), item.getString("title"), List(apps.length()) { apps.getString(it) })
    }
    require(folders.map(FolderEntry::id).distinct().size == folders.size)
    folders.forEach { folder ->
        require(isFolderId(folder.id) && folder.title.isNotBlank() && folder.appIds.size >= 2)
        require(folder.appIds.none { it.isBlank() || isReservedFolderId(it) })
        require(slots.count(folder.id::equals) == 1)
    }
    val shortcuts = slots.filterNotNull().filterNot(::isReservedFolderId) + folders.flatMap(FolderEntry::appIds)
    require(shortcuts.distinct().size == shortcuts.size)
    require(slots.filterNotNull().filter(::isReservedFolderId).all { id -> folders.any { it.id == id } })
    val restoreArray = json.getJSONArray("restores")
    val restores = List(restoreArray.length()) { index ->
        val item = restoreArray.getJSONObject(index)
        WidgetRestore(item.getInt("slot"), item.getString("provider"), item.getLong("userSerial"),
            item.getString("title"), item.getString("profileLabel"), item.optBoolean("work", false),
            item.optString("sourceScope").takeIf { it.isNotBlank() && it != "null" })
    }
    restores.forEach { require(ComponentName.unflattenFromString(it.providerComponent) != null && it.title.isNotBlank()) }
    var layout = HomeLayout(slots.dropLastWhile { it == null }, dock, folders = folders, widgetRestores = restores,
        leadingSlots = List(HOME_CELLS) { null }, rows = rows, columns = columns)
    require(layout.slots.indices.filter { layout.slots[it] != null }.all(layout::cellVisible))
    val widgetArray = json.getJSONArray("widgets")
    repeat(widgetArray.length()) { index ->
        val w = widgetArray.getJSONObject(index)
        val placement = WidgetPlacement(w.getInt("slot"), w.getInt("id"), w.getInt("page"), w.getInt("column"),
            w.getInt("row"), w.getInt("spanX"), w.getInt("spanY"))
        require(placement.page >= 0 && layout.placement(placement.slot) == null)
        val next = placeWidget(layout, placement)
        require(next.placement(placement.slot) == placement) { "Invalid cover widget" }
        layout = next.copy(widgetRestores = restores)
    }
    require(layout.widgetPlacements.filter { it.id == NEEDS_BINDING_WIDGET }.map { it.slot }.toSet() ==
        restores.map { it.slot }.toSet())
    return layout
}
