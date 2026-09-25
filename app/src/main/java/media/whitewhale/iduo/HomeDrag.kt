package media.whitewhale.iduo

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

internal data class DragRegion(val target: DropTarget, val bounds: Rect, val appId: String?, val page: Int?,
    val widgetId: Int? = null, val folderId: String? = null, val scope: String? = null) {
    val movable get() = appId != null || (widgetId != null && widgetId != EMPTY_WIDGET)
}

@Stable
/**
 * A horizontally paged child of a Home page (the All apps grid). Home's page gesture leaves a
 * horizontal swipe that starts inside [bounds] (root coordinates) to the child while it can still
 * page that way: [canPage] takes the finger's horizontal travel, negative toward the next page.
 */
internal class ChildPagerRegion(val bounds: () -> Rect, val canPage: (Float) -> Boolean)

internal class HomeDragState {
    /** The paged child currently on screen, if any. */
    var childPager: ChildPagerRegion? = null
    val regions = mutableStateMapOf<DropTarget, DragRegion>()
    private val regionOwners = mutableMapOf<DropTarget, Any>()
    var source by mutableStateOf<DragRegion?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var origin by mutableStateOf(Offset.Zero)
    var rootBounds by mutableStateOf(Rect.Zero)
    val rootOrigin get() = rootBounds.topLeft
    var originPage = 0
    var moved by mutableStateOf(false)
    var activeSourceScope by mutableStateOf<String?>(null)
    /** Home cell whose app the dragged app is hovering over, and whether the hover has lasted long enough to merge. */
    var mergeIndex by mutableStateOf<Int?>(null)
    var mergeArmed by mutableStateOf(false)
    val active get() = source != null
    fun hit(point: Offset, pages: Set<Int>) = regions.values
        .filter { (it.page == null || it.page in pages) && it.bounds.contains(point) &&
            (source != null || it.target !is DropTarget.Folder) && it.scope == activeSourceScope }
        .maxByOrNull(::dragRegionPriority)
    fun destination(point: Offset, pages: Set<Int>): DragRegion? {
        regions[DropTarget.Remove]?.takeIf { source?.target !is DropTarget.Library && it.bounds.contains(point) }?.let { return it }
        return regions.values.filter {
            (it.page == null || it.page in pages) && it.bounds.contains(point) && when (it.target) {
                // A moving widget is rendered at its preview footprint and registers the
                // same Widget target again. Never let that preview shadow the Home cells
                // beneath it. Widgets always move through the cell grid.
                is DropTarget.Widget -> false
                is DropTarget.Home -> source?.appId != null || source?.target is DropTarget.Widget
                is DropTarget.Dock -> source?.appId != null
                is DropTarget.Folder -> source?.appId != null && source?.target !is DropTarget.Folder
                DropTarget.Remove -> source?.target !is DropTarget.Library
                is DropTarget.Library -> false
            }
        }.maxByOrNull(::dragRegionPriority)
    }
    /** A Home app or folder under the icon-centred part of [point] that the dragged app could be grouped with. */
    fun mergeCandidate(point: Offset, pages: Set<Int>): DragRegion? {
        val dragged = source ?: return null
        val draggedApp = dragged.appId?.takeUnless(::isReservedFolderId) ?: return null
        if (dragged.folderId != null || dragged.target is DropTarget.Widget) return null
        return regions.values.firstOrNull { region ->
            region.target is DropTarget.Home && (region.page == null || region.page in pages) &&
                region.scope == activeSourceScope && region.appId != null && region.appId != draggedApp &&
                folderMergeZone(region.bounds).contains(point)
        }
    }
    fun clear() { source = null; moved = false; mergeIndex = null; mergeArmed = false }

    fun register(owner: Any, region: DragRegion) {
        regionOwners[region.target] = owner
        regions[region.target] = region
    }

    fun update(owner: Any, target: DropTarget, appId: String?, page: Int?, widgetId: Int?, folderId: String?, scope: String?) {
        regions[target]?.takeIf { regionOwners[target] === owner }?.let {
            if (it.appId != appId || it.widgetId != widgetId || it.page != page || it.folderId != folderId || it.scope != scope) {
                regions[target] = it.copy(appId = appId, widgetId = widgetId, page = page, folderId = folderId, scope = scope)
            }
        }
    }

    fun unregister(owner: Any, target: DropTarget) {
        if (regionOwners[target] === owner) {
            regionOwners.remove(target)
            regions.remove(target)
        }
    }
}

/** How long a dragged app must rest on another app before dropping makes a folder. */
internal const val FOLDER_MERGE_DELAY_MS = 350L

/**
 * The icon-centred part of a Home cell. Resting here starts a folder; the cell's outer edge and label
 * area keep the ordinary insertion behaviour so apps can still be moved between neighbours.
 */
internal fun folderMergeZone(cell: Rect): Rect = Rect(
    cell.left + cell.width * .22f, cell.top + cell.height * .04f,
    cell.right - cell.width * .22f, cell.top + cell.height * .66f)

/** Page turning follows the window edges, including the space beside the fixed dock. */
internal fun dragEdgeDirection(point: Offset, window: Rect, edgeWidth: Float): Int = when {
    window.isEmpty || !window.contains(point) -> 0
    point.x < window.left + edgeWidth -> -1
    point.x > window.right - edgeWidth -> 1
    else -> 0
}

@Composable
internal fun Modifier.dropRegion(drag: HomeDragState, target: DropTarget, appId: String? = null, page: Int? = null,
    widgetId: Int? = null, folderId: String? = null, scope: String? = null): Modifier {
    val owner = remember { Any() }
    DisposableEffect(drag, target, owner) { onDispose { drag.unregister(owner, target) } }
    SideEffect { drag.update(owner, target, appId, page, widgetId, folderId, scope) }
    return onGloballyPositioned { coordinates ->
        drag.register(owner, DragRegion(target, coordinates.boundsInRoot(), appId, page, widgetId, folderId, scope))
    }
}

internal fun dragRegionPriority(region: DragRegion): Int = when (region.target) {
    is DropTarget.Folder -> 3
    is DropTarget.Widget -> 2
    is DropTarget.Library -> if (region.scope != null) 2 else 0
    else -> 1
}

/** Keeps the grabbed point over the pointer while resolving the widget's top-left cell. */
internal fun adjustedWidgetDropIndex(
    rawIndex: Int,
    placement: WidgetPlacement,
    sourceBounds: Rect,
    grabPoint: Offset,
): Int {
    val columnOffset = (((grabPoint.x - sourceBounds.left) / sourceBounds.width.coerceAtLeast(1f)) * placement.spanX)
        .toInt().coerceIn(0, placement.spanX - 1)
    val rowOffset = (((grabPoint.y - sourceBounds.top) / sourceBounds.height.coerceAtLeast(1f)) * placement.spanY)
        .toInt().coerceIn(0, placement.spanY - 1)
    val page = homeCellPage(rawIndex)
    val rawLocal = homeCellLocal(rawIndex)
    val column = (rawLocal % GRID_COLUMNS - columnOffset).coerceIn(0, GRID_COLUMNS - placement.spanX)
    val row = (rawLocal / GRID_COLUMNS - rowOffset)
        .coerceIn(0, GRID_ROWS - placement.spanY.coerceAtMost(GRID_ROWS))
    return homeCellIndex(page, row * GRID_COLUMNS + column)
}

/**
 * A native collection widget consumes its own MOVE events even while the finger has only
 * jittered. Compose's stock long-press helper treats that consumption as cancellation, so a
 * Calendar-style widget could scroll but could no longer be picked up. The root observes the
 * Initial pass and arbitrates intent from geometry instead: jitter keeps waiting, while a real
 * move, release/cancel, or second pointer yields the stream to the provider.
 */
private suspend fun AwaitPointerEventScope.awaitWidgetLongPressOrCancellation(
    down: PointerInputChange,
): PointerInputChange? {
    var current = down
    var canceled = false
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (!canceled) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            canceled = change == null || !change.pressed ||
                event.changes.any { it.id != down.id && it.pressed } ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            if (!canceled) current = change!!
        }
    }
    return current.takeUnless { canceled }
}

/** The root owns the pointer so paging cannot dispose the active gesture. */
@Composable
internal fun Modifier.homeDragInput(
    drag: HomeDragState, enabled: Boolean, page: Int, eligiblePages: Set<Int> = setOf(page),
    onStart: () -> Unit, onFinish: (Boolean) -> Unit,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentPage by rememberUpdatedState(page)
    val currentEligiblePages by rememberUpdatedState(eligiblePages)
    val start by rememberUpdatedState(onStart)
    val finish by rememberUpdatedState(onFinish)
    return onGloballyPositioned { drag.rootBounds = it.boundsInRoot() }.pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEnabled) return@awaitEachGesture
            val point = down.position + drag.rootOrigin
            val region = drag.hit(point, currentEligiblePages)?.takeIf { it.movable } ?: return@awaitEachGesture
            if (region.target is DropTarget.Widget && (region.widgetId ?: EMPTY_WIDGET) >= 0) {
                awaitWidgetLongPressOrCancellation(down)
            } else {
                awaitLongPressOrCancellation(down.id)
            } ?: return@awaitEachGesture
            drag.source = region
            drag.pointer = point
            drag.origin = point
            drag.originPage = currentPage
            drag.moved = false
            start()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.any { it.id != down.id && it.pressed }) {
                        event.changes.forEach { it.consume() }
                        finish(true)
                        break
                    }
                    drag.pointer = change.position + drag.rootOrigin
                    if ((drag.pointer - drag.origin).getDistance() > viewConfiguration.touchSlop) drag.moved = true
                    change.consume()
                    if (!change.pressed) {
                        finish(false)
                        break
                    }
                    if (!drag.active) break
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                drag.clear()
                throw cancel
            }
        }
    }
}
