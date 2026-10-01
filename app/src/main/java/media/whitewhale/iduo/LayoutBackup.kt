package media.whitewhale.iduo

import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val LAYOUT_BACKUP_VERSION = 4
const val MAX_LAYOUT_BACKUP_BYTES = 2 * 1024 * 1024
private const val MAX_BACKUP_HOME_CELLS = HOME_CELLS * 100

data class BackupWidgetDescriptor(
    val slot: Int,
    val providerComponent: String?,
    val userSerial: Long?,
    val title: String,
    val profileLabel: String,
    val builtinId: Int? = null,
    val isWork: Boolean = false,
)

/** A widget whose saved profile must be mapped by hand; shown in the restore review. */
data class ProfileIssue(val title: String, val profileLabel: String)

data class LayoutImportPreview(
    val layout: HomeLayout,
    val missingApps: List<String>,
    val profileIssues: List<ProfileIssue>,
    val appCount: Int,
    val folderCount: Int,
    val widgetCount: Int,
    val compact: LayoutPreset,
    val expanded: LayoutPreset,
    val labels: Boolean,
    val googleSearch: Boolean,
    val verticalStatus: Boolean,
    /** The cover screen's own layout, when the backup has one. */
    val cover: HomeLayout? = null,
    val separateCover: Boolean = false,
)

fun layoutBackupScope(context: Context): String {
    val prefs = context.getSharedPreferences("layout_backup_identity", Context.MODE_PRIVATE)
    return prefs.getString("scope", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("scope", it).apply() }
}

/**
 * The inner screen's layout with the shared dock and settings, plus the cover's own layout when
 * it has one. [widgetDescriptors] and [coverWidgetDescriptors] describe each layout's bound widgets.
 */
fun encodeLayoutBackup(state: LauncherState, widgetDescriptors: List<BackupWidgetDescriptor>, sourceScope: String,
    coverWidgetDescriptors: List<BackupWidgetDescriptor> = emptyList()): String {
    require(sourceScope.isNotBlank())
    val inner = state.innerLayout
    require(inner.leadingSlots.size == HOME_CELLS) { "Unfolded-only page must contain exactly $HOME_CELLS cells" }
    val apps = JSONArray().also { array -> state.apps.forEach { app -> array.put(JSONObject()
        .put("id", app.id).put("label", app.label).put("component", app.component.flattenToString())
        .put("userSerial", app.userSerial).put("profileLabel", app.profileLabel).put("work", app.isWork)) } }
    fun preset(value: LayoutPreset) = JSONObject().put("iconSize", value.iconSize).put("rowGap", value.rowGap)
        .put("dockWidth", value.dockWidth).put("dockPosition", value.dockPosition).put("dockAlignToGrid", value.dockAlignToGrid)
    val root = JSONObject().put("version", LAYOUT_BACKUP_VERSION).put("sourceScope", sourceScope).put("apps", apps)
        .put("homeSlots", JSONArray(inner.slots)).put("leadingSlots", JSONArray(inner.leadingSlots))
        .put("dock", JSONArray(inner.dock)).put("folders", encodeBackupFolders(inner))
        .put("widgets", encodeBackupWidgets(inner, widgetDescriptors, sourceScope))
        .put("labels", state.labels).put("googleSearch", state.googleSearch).put("verticalStatus", state.verticalStatus).put("homeRows", inner.rows)
        .put("compact", preset(state.compact)).put("expanded", preset(state.expanded))
    state.coverLayout?.let { cover ->
        root.put("separateCover", state.separateCover).put("cover", JSONObject()
            .put("homeSlots", JSONArray(cover.slots)).put("folders", encodeBackupFolders(cover))
            .put("widgets", encodeBackupWidgets(cover, coverWidgetDescriptors, sourceScope))
            .put("homeRows", cover.rows).put("homeColumns", cover.columns))
    }
    return root.toString(2)
}

private fun encodeBackupFolders(layout: HomeLayout) = JSONArray().also { array -> layout.folders.forEach { folder ->
    array.put(JSONObject().put("id", folder.id).put("title", folder.title).put("apps", JSONArray(folder.appIds))) } }

private fun encodeBackupWidgets(layout: HomeLayout, widgetDescriptors: List<BackupWidgetDescriptor>, sourceScope: String): JSONArray {
    val descriptorBySlot = widgetDescriptors.associateBy(BackupWidgetDescriptor::slot)
    return JSONArray().also { array -> layout.widgetPlacements.forEach { placement ->
        val saved = layout.widgetRestores.firstOrNull { it.slot == placement.slot }
        val descriptor = descriptorBySlot[placement.slot]
        val item = JSONObject().put("slot", placement.slot).put("page", placement.page)
            .put("column", placement.column).put("row", placement.row).put("spanX", placement.spanX).put("spanY", placement.spanY)
        when {
            placement.id in setOf(CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET) -> item.put("builtinId", placement.id)
            saved != null -> item.put("provider", saved.providerComponent).put("userSerial", saved.userSerial)
                .put("title", saved.title).put("profileLabel", saved.profileLabel).put("work", saved.isWork)
                .put("sourceScope", exportedWidgetScope(saved, sourceScope))
            descriptor?.providerComponent != null && descriptor.userSerial != null -> item.put("provider", descriptor.providerComponent)
                .put("userSerial", descriptor.userSerial).put("title", descriptor.title).put("profileLabel", descriptor.profileLabel)
                .put("work", descriptor.isWork).put("sourceScope", sourceScope)
            else -> error("Widget ${placement.slot} has no portable provider descriptor")
        }
        array.put(item)
    } }
}

internal fun exportedWidgetScope(restore: WidgetRestore, currentScope: String) = restore.sourceScope ?: currentScope

fun decodeLayoutBackup(raw: String, currentApps: List<AppEntry>, currentProfiles: List<AppProfile>, currentScope: String): LayoutImportPreview {
    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_LAYOUT_BACKUP_BYTES) { "Layout backup is larger than 2 MB" }
    val root = JSONObject(raw)
    val version = root.strictInt("version")
    require(version in 1..LAYOUT_BACKUP_VERSION) { "Unsupported layout backup version" }
    val sourceScope = root.getString("sourceScope").also { require(it.isNotBlank()) }
    val sameScope = sourceScope == currentScope
    val appMetadata = root.getJSONArray("apps").let { array -> List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val id = item.getString("id")
        val identity = parseProfileAppId(id) ?: error("Invalid app identity")
        require(item.getString("component") == identity.component)
        require(ComponentName.unflattenFromString(identity.component) != null)
        val serial = item.strictLong("userSerial")
        require(serial >= 0 && item.getString("label").isNotBlank() && item.getString("profileLabel").isNotBlank())
        if (item.strictBoolean("work")) require(identity.userSerial == serial) else require(identity.userSerial == null)
        id to item.getString("label")
    } }.also { entries -> require(entries.map { it.first }.distinct().size == entries.size) }.toMap()
    val available = currentApps.filter { !it.isWork || sameScope }.mapTo(mutableSetOf(), AppEntry::id)
    val missing = linkedSetOf<String>()
    fun importedApp(id: String?): String? {
        if (id == null) return null
        require(id in appMetadata) { "Layout references an app without metadata" }
        return id.takeIf { it in available } ?: run { missing += "$id (${appMetadata.getValue(id)})"; null }
    }
    fun readSlots(array: JSONArray) = List(array.length()) { index -> if (array.isNull(index)) null else array.getString(index) }
    /** A layout's folders, each referenced exactly once by [cells]. */
    fun readFolders(array: JSONArray, cells: List<String?>): List<FolderEntry> {
        val folders = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val children = item.getJSONArray("apps")
            FolderEntry(item.getString("id"), item.getString("title"), List(children.length()) { children.getString(it) })
        }
        require(folders.map(FolderEntry::id).distinct().size == folders.size)
        require(folders.flatMap(FolderEntry::appIds).distinct().size == folders.sumOf { it.appIds.size })
        folders.forEach { folder ->
            require(isFolderId(folder.id) && folder.title.isNotBlank() && folder.appIds.size >= 2)
            require(folder.appIds.none(::isReservedFolderId))
            require(cells.count(folder.id::equals) == 1)
        }
        return folders
    }
    /** Imported cells: missing apps become gaps, and a folder left with one app becomes that app. */
    fun importCells(cells: List<String?>, folderResults: Map<String, FolderEntry>) = cells.map { value -> when {
        value == null -> null
        isReservedFolderId(value) -> folderResults[value]?.let { folder -> when (folder.appIds.size) { 0 -> null; 1 -> folder.appIds.single(); else -> folder.id } }
            ?: error("Orphan folder reference")
        else -> importedApp(value)
    } }
    val slotsArray = root.getJSONArray("homeSlots")
    require(slotsArray.length() <= MAX_BACKUP_HOME_CELLS)
    // Versions 1 and 2 stored four columns of six rows per page, version 3 four columns of
    // eight rows, and version 4 the current five columns of eight rows.
    val legacyCells = version < 3
    val storedRows = if (legacyCells) LEGACY_GRID_ROWS else GRID_ROWS
    val storedSlots = readSlots(slotsArray)
    val rawSlots = if (version < 4) upgradeLegacySlots(storedSlots, storedRows) else storedSlots
    val rawLeadingSlots = if (version == 1) List(HOME_CELLS) { null } else {
        val array = root.getJSONArray("leadingSlots")
        val cells = when { version >= 4 -> HOME_CELLS; legacyCells -> LEGACY_HOME_CELLS; else -> SCHEMA9_HOME_CELLS }
        require(array.length() == cells) { "Unfolded-only page must contain exactly $cells cells" }
        val stored = readSlots(array)
        if (version >= 4) stored else upgradeLegacyLeadingSlots(stored, storedRows)
    }
    val dockArray = root.getJSONArray("dock")
    require(dockArray.length() in MIN_DOCK_SLOTS..MAX_DOCK_SLOTS)
    val rawDock = readSlots(dockArray)
    // Folders may stand on Home or in the dock.
    val importedFolders = readFolders(root.getJSONArray("folders"), rawSlots + rawLeadingSlots + rawDock)
    val surfaceApps = (rawSlots + rawLeadingSlots + rawDock).filterNotNull().filterNot(::isReservedFolderId) +
        importedFolders.flatMap(FolderEntry::appIds)
    require(surfaceApps.distinct().size == surfaceApps.size) { "An app shortcut appears more than once" }
    val folderResults = importedFolders.associate { folder -> folder.id to folder.copy(appIds = folder.appIds.mapNotNull(::importedApp)) }
    val folders = folderResults.values.filter { it.appIds.size >= 2 }
    val slots = importCells(rawSlots, folderResults)
    val leadingSlots = importCells(rawLeadingSlots, folderResults)
    val dock = importCells(rawDock, folderResults)
    val profileSerials = currentProfiles.mapTo(mutableSetOf(), AppProfile::userSerial)
    val profileIssues = linkedSetOf<ProfileIssue>()
    /** Places [widgetArray]'s widgets on [start]; Android widgets become placeholders to reconnect. */
    fun readWidgets(widgetArray: JSONArray, start: HomeLayout, columns: Int): HomeLayout {
        require(widgetArray.length() <= 500)
        var layout = start
        val widgetSlots = mutableSetOf<Int>()
        repeat(widgetArray.length()) { index ->
            val item = widgetArray.getJSONObject(index)
            val slot = item.strictInt("slot")
            require(widgetSlots.add(slot)) { "Widget slots must be unique" }
            val builtin = if (item.has("builtinId")) item.strictInt("builtinId") else null
            val provider = item.optString("provider").takeIf(String::isNotBlank)
            val id = if (builtin != null) {
                require(builtin in setOf(CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET)); builtin
            } else NEEDS_BINDING_WIDGET
            val stored = WidgetPlacement(slot, id, item.strictInt("page"), item.strictInt("column"), item.strictInt("row"),
                item.strictInt("spanX"), item.strictInt("spanY"))
            require(validBackupPlacement(stored, if (legacyCells) LEGACY_GRID_ROWS else GRID_ROWS, columns))
            val placement = if (legacyCells) upgradeLegacyPlacement(stored) else stored
            require(layout.widgetPlacements.none { backupOverlaps(it, placement) })
            require(placement.coveredIndices().none { layout.slotAt(it) != null })
            val restore = if (id == NEEDS_BINDING_WIDGET) {
                require(provider != null && ComponentName.unflattenFromString(provider) != null)
                val savedSerial = item.strictLong("userSerial"); require(savedSerial >= 0)
                val title = item.getString("title"); val profileLabel = item.getString("profileLabel")
                require(title.isNotBlank() && profileLabel.isNotBlank())
                val work = item.strictBoolean("work")
                val widgetScope = item.optString("sourceScope").takeIf { it.isNotBlank() } ?: sourceScope
                val serial = if (work) savedSerial else currentProfiles.firstOrNull { it.isPersonal }?.userSerial ?: savedSerial
                if ((work && widgetScope != currentScope) || serial !in profileSerials) profileIssues += ProfileIssue(title, profileLabel)
                WidgetRestore(slot, provider, serial, title, profileLabel, work, widgetScope)
            } else null
            layout = layout.copy(widgetPlacements = (layout.widgetPlacements + placement).sortedBy { it.slot },
                widgetRestores = layout.widgetRestores + listOfNotNull(restore))
        }
        return layout
    }
    var layout = readWidgets(root.getJSONArray("widgets"),
        HomeLayout(slots.dropLastWhile { it == null }, dock, folders = folders, leadingSlots = leadingSlots, rows = GRID_ROWS),
        DEFAULT_HOME_COLUMNS)
    fun preset(key: String): LayoutPreset {
        val item = root.getJSONObject(key)
        val loaded = LayoutPreset(item.strictFloat("iconSize"), item.strictFloat("rowGap"),
            item.strictFloat("dockWidth"), item.strictFloat("dockPosition"), item.strictBoolean("dockAlignToGrid"))
        require(loaded == loaded.sanitized()) { "Invalid layout preset" }
        return loaded
    }
    // Validate settings eagerly even though HomeLayout contains placement data only.
    val compact = preset("compact"); val expanded = preset("expanded")
    val labels = root.strictBoolean("labels"); val googleSearch = root.strictBoolean("googleSearch")
    val verticalStatus = root.strictBoolean("verticalStatus")
    val homeRows = if (legacyCells) DEFAULT_HOME_ROWS else root.strictInt("homeRows").also { require(it in DEFAULT_HOME_ROWS..GRID_ROWS) }
    layout = layout.copy(rows = maxOf(homeRows, layout.requiredRows()))
    // Version 4 may carry the cover's own layout. It shares the dock, so its apps must not repeat the dock's.
    val cover = if (version >= 4) root.optJSONObject("cover")?.let { item ->
        val cells = item.getJSONArray("homeSlots")
        require(cells.length() <= MAX_BACKUP_HOME_CELLS)
        val coverCells = readSlots(cells)
        val columns = item.strictInt("homeColumns").also { require(it in DEFAULT_HOME_COLUMNS..GRID_COLUMNS) }
        val coverRows = item.strictInt("homeRows").also { require(it in DEFAULT_HOME_ROWS..GRID_ROWS) }
        val coverFolders = readFolders(item.getJSONArray("folders"), coverCells)
        val coverApps = coverCells.filterNotNull().filterNot(::isReservedFolderId) + rawDock.filterNotNull().filterNot(::isReservedFolderId) +
            coverFolders.flatMap(FolderEntry::appIds)
        require(coverApps.distinct().size == coverApps.size) { "An app shortcut appears more than once" }
        val coverResults = coverFolders.associate { folder -> folder.id to folder.copy(appIds = folder.appIds.mapNotNull(::importedApp)) }
        val start = HomeLayout(importCells(coverCells, coverResults).dropLastWhile { it == null }, dock,
            folders = coverResults.values.filter { it.appIds.size >= 2 }, rows = GRID_ROWS, columns = columns)
        require(coverCells.indices.filter { coverCells[it] != null }.all { index -> homeCellLocal(index) % GRID_COLUMNS < columns })
        readWidgets(item.getJSONArray("widgets"), start, columns).also { read -> require(read.widgetPlacements.none { it.page < 0 }) }
            .let { it.copy(rows = maxOf(coverRows, it.requiredRows())) }
    } else null
    val separateCover = cover != null && root.optBoolean("separateCover", false)
    return LayoutImportPreview(layout, missing.toList(), profileIssues.toList(),
        appCount = (slots + leadingSlots).count { it != null && !isReservedFolderId(it) } +
            dock.count { it != null } + folders.sumOf { it.appIds.size },
        folderCount = folders.size, widgetCount = layout.widgetPlacements.size,
        compact = compact, expanded = expanded, labels = labels, googleSearch = googleSearch, verticalStatus = verticalStatus,
        cover = cover, separateCover = separateCover)
}

internal fun validBackupPlacement(value: WidgetPlacement, rows: Int = GRID_ROWS, columns: Int = DEFAULT_HOME_COLUMNS): Boolean {
    val base = value.slot in 0..10_000 && value.page in -1..99 && value.column >= 0 && value.row >= 0 &&
        value.spanX in 1..columns && value.spanY in 1..rows && value.column + value.spanX <= columns
    val inside = value.row + value.spanY <= rows
    val overflow = value.page > 0 && value.slot / 3 == value.page && value.slot % 3 == 2 && value.column == 0 &&
        value.row == rows && value.spanX == LEGACY_GRID_COLUMNS && value.spanY == 4
    return base && (inside || overflow)
}

private fun backupOverlaps(a: WidgetPlacement, b: WidgetPlacement) = a.page == b.page &&
    a.column < b.column + b.spanX && b.column < a.column + a.spanX &&
    a.row < b.row + b.spanY && b.row < a.row + a.spanY

private fun JSONObject.strictInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be an integer")
    val value = number.toDouble()
    require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
    return value.toInt()
}

private fun JSONObject.strictLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be an integer")
    val text = number.toString()
    return text.toLongOrNull()?.takeIf { it >= 0 } ?: error("$key must be a non-negative integer")
}

private fun JSONObject.strictFloat(key: String): Float {
    val number = get(key) as? Number ?: error("$key must be a number")
    return number.toFloat().takeIf(Float::isFinite) ?: error("$key must be finite")
}

private fun JSONObject.strictBoolean(key: String) = get(key) as? Boolean ?: error("$key must be a boolean")
