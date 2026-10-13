package media.whitewhale.iduo

import java.util.UUID

private const val FOLDER_PREFIX = "folder:"

/** A folder of apps; a [large] one fills two by two cells on Home, like an App Library folder. */
data class FolderEntry(val id: String, val title: String, val appIds: List<String>, val large: Boolean = false)

fun newFolderId(): String = FOLDER_PREFIX + UUID.randomUUID()
fun isReservedFolderId(id: String) = id.startsWith(FOLDER_PREFIX)
fun isFolderId(id: String) = id.startsWith(FOLDER_PREFIX) &&
    runCatching { UUID.fromString(id.removePrefix(FOLDER_PREFIX)) }.isSuccess

/** [old]'s place on Home or in the dock, given to [new]. */
private fun HomeLayout.replacingShortcut(old: String, new: String?) = copy(
    slots = slots.map { if (it == old) new else it }.dropLastWhile { it == null },
    leadingSlots = leadingSlots.map { if (it == old) new else it },
    dock = dock.map { if (it == old) new else it },
)

private fun HomeLayout.withoutShortcut(id: String) = copy(
    slots = slots.map { it?.takeUnless(id::equals) }.dropLastWhile { it == null },
    leadingSlots = leadingSlots.map { it?.takeUnless(id::equals) },
    dock = dock.map { it?.takeUnless(id::equals) },
)

fun createFolder(
    layout: HomeLayout,
    folder: FolderEntry,
    firstAppId: String,
    secondAppId: String,
    targetIndex: Int,
): HomeLayout {
    if (!isFolderId(folder.id) || layout.indexOfShortcut(folder.id) != null || layout.folder(folder.id) != null ||
        folder.title.isBlank() || firstAppId == secondAppId || listOf(firstAppId, secondAppId).any { it.isBlank() || isReservedFolderId(it) } ||
        layout.folders.any { existing -> firstAppId in existing.appIds || secondAppId in existing.appIds } ||
        homeCellPage(targetIndex) !in -1..layout.pageCount || targetIndex in layout.unavailableCells()) return layout
    val target = layout.slotAt(targetIndex)
    if (target != null && target != firstAppId && target != secondAppId) return layout
    var next = layout.withoutShortcut(firstAppId).withoutShortcut(secondAppId)
    next = next.withSlot(targetIndex, folder.id)
    return next.copy(
        folders = next.folders + folder.copy(appIds = listOf(firstAppId, secondAppId)))
}

fun renameFolder(layout: HomeLayout, folderId: String, title: String): HomeLayout {
    if (title.isBlank() || layout.folder(folderId) == null) return layout
    return layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(title = title.trim()) else it })
}

fun addAppToFolder(layout: HomeLayout, folderId: String, appId: String, index: Int? = null): HomeLayout {
    if (appId.isBlank() || isReservedFolderId(appId)) return layout
    val sourceFolder = layout.folders.firstOrNull { appId in it.appIds }
    if (sourceFolder?.id == folderId) return layout
    if (layout.folder(folderId) == null) return layout
    val cleared = if (sourceFolder != null) removeAppFromFolder(layout, sourceFolder.id, appId, DropTarget.Remove)
        else layout.withoutShortcut(appId)
    val folder = cleared.folder(folderId) ?: return layout
    val insertion = (index ?: folder.appIds.size).coerceIn(0, folder.appIds.size)
    val members = folder.appIds.toMutableList().apply { add(insertion, appId) }
    return cleared.copy(folders = cleared.folders.map { if (it.id == folderId) it.copy(appIds = members) else it })
}

/**
 * Makes [appIds] the folder's apps, as ticked in its app list. New apps join at the end and leave
 * their place on Home, in the dock or in another folder; apps no longer ticked go to the first free
 * Home cells, from the folder's own page. A folder left with one app turns back into that app.
 */
fun setFolderApps(layout: HomeLayout, folderId: String, appIds: List<String>): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    val wanted = appIds.filterNot { it.isBlank() || isReservedFolderId(it) }.distinct()
    if (wanted.toSet() == folder.appIds.toSet()) return layout
    var next = wanted.filterNot(folder.appIds::contains).fold(layout) { current, appId -> addAppToFolder(current, folderId, appId) }
    val page = (layout.indexOfShortcut(folderId)?.let(::homeCellPage) ?: 0).coerceAtLeast(0)
    folder.appIds.filterNot(wanted::contains).forEach { appId ->
        // Once the folder has turned back into its last app, that app keeps the folder's place.
        if (next.folder(folderId)?.appIds?.contains(appId) != true) return@forEach
        next = removeAppFromFolder(next, folderId, appId, DropTarget.Remove)
        if (next.indexOfShortcut(appId) == null && appId !in next.dock) next = next.withSlot(firstFreeCell(next, page), appId)
    }
    return next
}

/** The first free, visible Home cell from [page] on. */
private fun firstFreeCell(layout: HomeLayout, page: Int): Int {
    val blocked = layout.unavailableCells()
    return generateSequence(homeCellIndex(page, 0)) { it + 1 }
        .first { it !in blocked && layout.cellVisible(it) && layout.slotAt(it) == null }
}

fun moveFolderApp(layout: HomeLayout, folderId: String, appId: String, index: Int): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    val from = folder.appIds.indexOf(appId)
    if (from < 0 || index !in folder.appIds.indices || from == index) return layout
    val members = folder.appIds.toMutableList().apply { add(index, removeAt(from)) }
    return layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(appIds = members) else it })
}

fun removeAppFromFolder(layout: HomeLayout, folderId: String, appId: String, target: DropTarget): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    if (appId !in folder.appIds || target is DropTarget.Widget || target is DropTarget.Library) return layout
    val folderCell = layout.indexOfShortcut(folderId)
    val remaining = folder.appIds.filterNot(appId::equals)
    var next = when (remaining.size) {
        0 -> layout.withoutShortcut(folderId).copy(folders = layout.folders.filterNot { it.id == folderId })
        // The last app takes the folder's place, on Home or in the dock.
        1 -> layout.replacingShortcut(folderId, remaining.single()).copy(folders = layout.folders.filterNot { it.id == folderId })
        else -> layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(appIds = remaining) else it })
    }
    if (target == DropTarget.Remove) return next
    if (target is DropTarget.Home && target.index == folderCell) return layout
    val placed = dropApp(next, appId, target)
    return if (placed == next) layout else placed
}

/**
 * Ungroups a folder where it stands: its first app takes the folder's cell and the rest fill the
 * nearest free, visible Home cells, starting on the folder's own page.
 */
fun disbandFolder(layout: HomeLayout, folderId: String): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    val cell = layout.indexOfShortcut(folderId) ?: return disbandDockFolder(layout, folder)
    val cleared = layout.withSlot(cell, null).copy(folders = layout.folders.filterNot { it.id == folderId })
    val blocked = cleared.unavailableCells()
    val page = homeCellPage(cell)
    val lastPage = cleared.pageCount + folder.appIds.size / HOME_CELLS + 1
    val pages = listOf(page) + (maxOf(0, page)..lastPage).filter { it != page }
    val free = pages.asSequence()
        .flatMap { candidate -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(candidate, it) } }
        .filter { it != cell && it !in blocked && cleared.cellVisible(it) && cleared.slotAt(it) == null }
    val targets = (sequenceOf(cell) + free).take(folder.appIds.size).toList()
    return folder.appIds.zip(targets).fold(cleared) { next, (appId, index) -> next.withSlot(index, appId) }
}

/** A folder in the dock ungroups with its first app in its dock position and the rest on Home. */
private fun disbandDockFolder(layout: HomeLayout, folder: FolderEntry): HomeLayout {
    if (folder.id !in layout.dock) return layout
    val first = folder.appIds.firstOrNull()
    var next = layout.replacingShortcut(folder.id, first).copy(folders = layout.folders.filterNot { it.id == folder.id })
    folder.appIds.drop(1).forEach { appId ->
        val blocked = next.unavailableCells()
        val cell = generateSequence(0) { it + 1 }.first { it !in blocked && next.cellVisible(it) && next.slotAt(it) == null }
        next = next.withSlot(cell, appId)
    }
    return next
}

fun reconcileFolders(layout: HomeLayout, removedAppIds: Set<String>): HomeLayout {
    var next = layout
    removedAppIds.forEach { appId ->
        next.folders.firstOrNull { appId in it.appIds }?.let { folder ->
            next = removeAppFromFolder(next, folder.id, appId, DropTarget.Remove)
        }
    }
    // Dissolving a folder can promote its final child back into the folder's cell. When
    // several children disappear in the same refresh, remove every authoritative ID once
    // more after all folder transitions so iteration order cannot resurrect a shortcut.
    return removedAppIds.fold(next) { current, appId -> current.withoutShortcut(appId) }
}

/** The cells a large folder at [anchor] fills: its own, the one to its right and the two below them. */
fun largeFolderCells(anchor: Int): Set<Int> {
    val page = homeCellPage(anchor)
    val local = homeCellLocal(anchor)
    val column = local % GRID_COLUMNS
    val row = local / GRID_COLUMNS
    return buildSet {
        for (y in 0..1) for (x in 0..1)
            if (column + x < GRID_COLUMNS && row + y < GRID_ROWS) add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x))
    }
}

/** Every cell Home's large folders fill, other than [exceptFolder]'s. */
fun HomeLayout.largeFolderFootprints(exceptFolder: String? = null): Set<Int> =
    folders.filter { it.large && it.id != exceptFolder }
        .flatMapTo(mutableSetOf()) { folder -> indexOfShortcut(folder.id)?.let(::largeFolderCells).orEmpty() }

/** Whether the folder [folderId] can fill two by two cells from [anchor]: all visible, and free of anything else. */
fun largeFolderFits(layout: HomeLayout, folderId: String, anchor: Int): Boolean {
    val local = homeCellLocal(anchor)
    if (homeCellPage(anchor) !in -1..layout.pageCount ||
        local % GRID_COLUMNS + 1 >= layout.columns || local / GRID_COLUMNS + 1 >= layout.rows) return false
    val taken = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() } + layout.largeFolderFootprints(folderId)
    return largeFolderCells(anchor).all { cell ->
        layout.cellVisible(cell) && cell !in taken && layout.slotAt(cell).let { it == null || it == folderId }
    }
}

/**
 * Makes a folder on Home large or small again. A large folder takes the nearest place on its page
 * with room; if there is none, it grows where it is, or as near as the grid allows, and the apps in
 * its way move to the first free cells from its page on. Widgets and other large folders never move.
 */
fun setFolderLarge(layout: HomeLayout, folderId: String, large: Boolean): HomeLayout {
    val folder = layout.folder(folderId) ?: return layout
    if (folder.large == large) return layout
    val resized = layout.copy(folders = layout.folders.map { if (it.id == folderId) it.copy(large = large) else it })
    if (!large) return resized
    val anchor = layout.indexOfShortcut(folderId) ?: return layout
    val page = homeCellPage(anchor)
    val local = homeCellLocal(anchor)
    val candidates = (0 until HOME_CELLS).map { homeCellIndex(page, it) }.sortedBy { candidate ->
        val other = homeCellLocal(candidate)
        kotlin.math.abs(other / GRID_COLUMNS - local / GRID_COLUMNS) + kotlin.math.abs(other % GRID_COLUMNS - local % GRID_COLUMNS)
    }
    candidates.firstOrNull { largeFolderFits(layout, folderId, it) }?.let { place ->
        return if (place == anchor) resized else resized.withSlot(anchor, null).withSlot(place, folderId)
    }
    val fixed = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() } + layout.largeFolderFootprints(folderId)
    val place = candidates.firstOrNull { candidate ->
        val cell = homeCellLocal(candidate)
        cell % GRID_COLUMNS + 1 < layout.columns && cell / GRID_COLUMNS + 1 < layout.rows &&
            largeFolderCells(candidate).all { layout.cellVisible(it) && it !in fixed }
    } ?: return layout
    var next = resized.withSlot(anchor, null)
    val displaced = largeFolderCells(place).mapNotNull(next::slotAt)
    displaced.forEach { id -> next.indexOfShortcut(id)?.let { next = next.withSlot(it, null) } }
    next = next.withSlot(place, folderId)
    displaced.forEach { id -> next = next.withSlot(firstFreeCell(next, page), id) }
    return next
}

/** Moves a large folder to [anchor] when it has room there; otherwise it stays where it is. */
fun moveLargeFolder(layout: HomeLayout, folderId: String, anchor: Int): HomeLayout {
    val from = layout.indexOfShortcut(folderId) ?: return layout
    if (from == anchor || !largeFolderFits(layout, folderId, anchor)) return layout
    return layout.withSlot(from, null).withSlot(anchor, folderId)
}

/** Large folders that no longer have room, or have left Home for the dock, become small again. */
fun fitLargeFolders(layout: HomeLayout): HomeLayout = layout.folders.filter { it.large }.fold(layout) { next, folder ->
    val anchor = next.indexOfShortcut(folder.id)
    if (anchor != null && largeFolderFits(next, folder.id, anchor)) next
    else next.copy(folders = next.folders.map { if (it.id == folder.id) it.copy(large = false) else it })
}
