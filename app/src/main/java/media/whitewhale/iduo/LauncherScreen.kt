@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package media.whitewhale.iduo

import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

private fun findFreeWidgetIndex(layout: HomeLayout, page: Int, spanX: Int, spanY: Int): Int? {
    val blocked = layout.unavailableCells()
    for (row in 0..layout.rows - spanY) for (column in 0..GRID_COLUMNS - spanX) {
        val cells = buildList {
            repeat(spanY) { y -> repeat(spanX) { x -> add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x)) } }
        }
        if (cells.none { it in blocked || layout.slotAt(it) != null }) return cells.first()
    }
    return null
}

@Composable
fun DuoTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (dark) DarkDuoPalette else LightDuoPalette
    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink), content = content)
    }
}


@Composable
fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onStatusMode: (Boolean) -> Unit, onWallpaperSettings: () -> Unit,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf("") }
    var dockSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetTargetIndex by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var widgetExactTarget by rememberSaveable { mutableStateOf(false) }
    var widgetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetProfileSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetSession by remember { mutableStateOf<WidgetPickerSession?>(null) }
    var widgetPlacementMessage by remember { mutableStateOf<String?>(null) }
    var emptyCellIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var resizeSlot by remember { mutableStateOf<Int?>(null) }
    var resizeWidth by rememberSaveable { mutableIntStateOf(1) }
    var resizeHeight by rememberSaveable { mutableIntStateOf(1) }
    var resizeConstraints by remember { mutableStateOf<WidgetSpanConstraints?>(null) }
    var resizePitchX by remember { mutableFloatStateOf(1f) }
    var resizePitchY by remember { mutableFloatStateOf(1f) }
    var resizeTopPitch by remember { mutableFloatStateOf(1f) }
    var resizeAppPitch by remember { mutableFloatStateOf(1f) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var appMoveMenu by rememberSaveable { mutableStateOf(false) }
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(selectedId) { if (selectedId == null) appMoveMenu = false }
    LaunchedEffect(sheet) { if (sheet.isEmpty()) customizationPage = CustomizationPage.OVERVIEW }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var lastHomePage by rememberSaveable { mutableIntStateOf(0) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var pinQuery by rememberSaveable { mutableStateOf("") }
    val launcherActivity = androidx.activity.compose.LocalActivity.current as MainActivity
    val launcherRootView = LocalView.current.rootView
    DisposableEffect(sheet == "widgets") {
        val active = sheet == "widgets"
        if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", true)
        onDispose { if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", false) }
    }
    val appsById = remember(state.apps) { state.apps.associateBy { it.id } }
    val drag = remember { HomeDragState() }
    val folderOwnsInput = openFolderId != null || drag.source?.folderId != null
    DisposableEffect(folderOwnsInput) {
        if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", true)
        onDispose { if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", false) }
    }
    val haptic = LocalHapticFeedback.current
    val homePages = state.homePages
    val pendingNewPage = widgets.pendingPlacement?.page == homePages
    val visibleHomePages = homePages + if (drag.active || widgetSession != null || pendingNewPage) 1 else 0
    var expandedWorkspace by remember { mutableStateOf(false) }
    // The RSS reader is an ordinary page; Discover has a page only where Google's feed can be embedded.
    val rssLeft = state.leftPage == LeftPage.RSS
    val firstHome = if (rssLeft || DiscoverBounds.available) 1 else 0
    val pageCount = visibleHomePages + 1
    val nativePager = rememberPagerState(initialPage = savedPage.coerceIn(-firstHome, pageCount - 1) + firstHome, pageCount = { pageCount + firstHome })
    val pager = remember(nativePager, firstHome) { LauncherPager(nativePager, firstHome) }
    // Adding or removing the left page shifts every physical page; keep showing the same one.
    val shownFirstHome = remember { intArrayOf(firstHome) }
    LaunchedEffect(firstHome) {
        if (shownFirstHome[0] != firstHome) {
            val logical = nativePager.currentPage - shownFirstHome[0]
            shownFirstHome[0] = firstHome
            nativePager.scrollToPage((logical + firstHome).coerceIn(0, pageCount + firstHome - 1))
        }
    }
    // Google's feed host must not run, or hold input focus, while the RSS reader has the page.
    DisposableEffect(rssLeft) {
        if (rssLeft) LiveDiscover.setExternalResultPending(launcherActivity, "main", "left-page-rss", true)
        onDispose { if (rssLeft) LiveDiscover.setExternalResultPending(launcherActivity, "main", "left-page-rss", false) }
    }
    fun leaveTemporaryWidgetPage() {
        val persistedPages = model.state.value.homePages
        if (pager.currentPage >= persistedPages)
            pager.requestScrollToPage((persistedPages - 1).coerceAtLeast(0))
    }
    var priorPendingPlacement by remember { mutableStateOf<WidgetPlacement?>(null) }
    LaunchedEffect(widgets.pendingPlacement, state.layout) {
        val pending = widgets.pendingPlacement
        if (pending != null) priorPendingPlacement = pending
        else priorPendingPlacement?.let { prior ->
            if (model.placement(prior.slot) == null && prior.page >= homePages) leaveTemporaryWidgetPage()
            priorPendingPlacement = null
        }
    }
    val pageGestures = remember(nativePager) { PageGestureLimits(nativePager) }
    SideEffect { pageGestures.editing = drag.active || widgetSession != null || resizeSlot != null; LiveDiscover.allowNativeOpen = pager.currentPage == 0 && !drag.active && widgetSession == null && resizeSlot == null }
    val pageFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(nativePager, pagerSnapDistance = pageGestures)
    var nativeMotion by remember { mutableStateOf(false) }
    DisposableEffect(nativePager) {
        val callback: (Float) -> Unit = { progress ->
            val scrolling = nativePager.isScrollInProgress
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_received",
                "progress=$progress scrolling=$scrolling nativeMotion=$nativeMotion current=${nativePager.currentPage} offset=${nativePager.currentPageOffsetFraction}")
            if (!scrolling || nativeMotion) {
                val priorNativeMotion = nativeMotion
                nativeMotion = progress > 0f && progress < 1f
                val position = 1f - progress
                val page = position.roundToInt()
                if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_accepted",
                    "progress=$progress nativeMotion=$priorNativeMotion->$nativeMotion requestPage=$page requestOffset=${position - page}")
                nativePager.requestScrollToPage(page, position - page)
            } else if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_rejected",
                "progress=$progress reason=compose_scrolling nativeMotion=$nativeMotion")
        }
        LiveDiscover.onNativeProgress = callback
        onDispose { if (LiveDiscover.onNativeProgress === callback) LiveDiscover.onNativeProgress = null }
    }
    LaunchedEffect(nativePager) {
        snapshotFlow { Triple((1f - nativePager.currentPage - nativePager.currentPageOffsetFraction).coerceIn(0f, 1f), nativePager.isScrollInProgress, nativeMotion) to (nativePager.targetPage < firstHome) }
            .collect { (motion, towardFeed) ->
                val (progress, scrolling, native) = motion
                if (firstHome > 0) {
                    if (DuoMotionTrace.enabled) DuoMotionTrace.event("pager_observer",
                        "progress=$progress scrolling=$scrolling nativeMotion=$native towardFeed=$towardFeed")
                    if (scrolling) {
                        if (nativeMotion && DuoMotionTrace.enabled) DuoMotionTrace.event("native_owner_cleared",
                            "reason=compose_scrolling progress=$progress")
                        nativeMotion = false
                        LiveDiscover.page(progress, true, towardFeed)
                    } else if (!native) LiveDiscover.page(progress, false)
                }
            }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(pager) {
        val callback = { scope.launch { pager.animateScrollToPage(0) }; Unit }
        LiveDiscover.onHomeRequest = callback
        onDispose { if (LiveDiscover.onHomeRequest === callback) LiveDiscover.onHomeRequest = null }
    }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    var previousEditRevision by remember { mutableIntStateOf(state.editRevision) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager, homePages) {
        snapshotFlow { pager.settledPage to drag.active }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving) { savedPage = page; if (page in 0 until homePages) lastHomePage = page }
        }
    }
    LaunchedEffect(homePages, state.editRevision) {
        if (homePages != previousHomePages && !drag.active) {
            // Pin edits in the library keep the library selected; a completed drop stays on home.
            if (state.editRevision == previousEditRevision) {
                if (pager.currentPage == previousHomePages) pager.scrollToPage(homePages)
                else if (pager.currentPage >= pageCount) pager.scrollToPage(homePages - 1)
            } else if (pager.currentPage >= homePages) pager.scrollToPage(homePages - 1)
        }
        previousHomePages = homePages
        previousEditRevision = state.editRevision
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != homePages) focus.clearFocus() }
    LaunchedEffect(state.verticalStatus) { onStatusMode(state.verticalStatus) }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) {
        // An app can pause Home after the destination is visible but before its settle completes.
        val page = pager.currentPage.takeIf { it in 0 until homePages }
            ?: lastHomePage.coerceIn(0, homePages - 1)
        drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null
        widgetExactTarget = false; widgetPlacementMessage = null; selectedId = null; appMoveMenu = false
        openFolderId = null; createFolderFirstId = null; emptyCellIndex = null
        focus.clearFocus(); keyboard?.hide()
        pager.animateScrollToPage(page)
    } }
    LaunchedEffect(searchRequests) { if (searchRequests > 0) { drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null; widgetExactTarget = false; selectedId = null
        if (!state.googleSearch || !onGoogleSearch(null)) pager.animateScrollToPage(homePages)
    } }
    val widgetPickerBack = {
        if (widgetSession != null) {
            leaveTemporaryWidgetPage(); widgetSession = null; widgetPlacementMessage = null
        } else {
            sheet = ""; widgetPackage = null; widgetExactTarget = false; widgetPlacementMessage = null
        }
    }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    BackHandler(enabled = sheet.isEmpty()) { if (resizeSlot != null) resizeSlot = null else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else if (selectedId != null) selectedId = null else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val openDiscover = { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else onDiscover(); Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0 until visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    val rawTarget = if (drag.active) drag.destination(drag.pointer, eligibleDragPages)?.target else null
    val target = if (rawTarget is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
        val slot = (drag.source!!.target as DropTarget.Widget).index
        model.placement(slot)?.let {
            DropTarget.Home(adjustedWidgetDropIndex(rawTarget.index, it, drag.source!!.bounds, drag.origin))
        } ?: rawTarget
    } else rawTarget
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    // Resting on another app's icon groups the two into a folder instead of shifting the grid.
    val mergeRegion = if (drag.active && drag.moved) drag.mergeCandidate(drag.pointer, eligibleDragPages) else null
    val mergeIndex = (mergeRegion?.target as? DropTarget.Home)?.index
    val mergeIntoFolder = mergeRegion?.appId?.let(::isReservedFolderId) == true
    LaunchedEffect(mergeIndex) {
        drag.mergeArmed = false
        drag.mergeIndex = mergeIndex
        if (mergeIndex != null) {
            // An existing folder accepts the app at once; two apps need a deliberate pause.
            if (!mergeIntoFolder) delay(FOLDER_MERGE_DELAY_MS)
            drag.mergeArmed = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val insertionTarget = target.takeIf { drag.moved && !blockedDock && mergeIndex == null }
    val widgetRawTarget = widgetSession?.let { session -> drag.regions.values.firstOrNull {
        it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(session.pointer)
    }?.target as? DropTarget.Home }
    val widgetDraft = widgetSession?.let { session -> session.candidate ?: widgetRawTarget?.let { cell ->
        widgetCandidate(state.layout, session.slot, session.targetIndex ?: cell.index, session.span.width, session.span.height)
    } ?: session.targetIndex?.let { widgetCandidate(state.layout, session.slot, it, session.span.width, session.span.height) } }
    val dropHomePage = if (pager.currentPage >= visibleHomePages)
        lastHomePage.coerceIn(0, homePages - 1) else pager.currentPage.coerceIn(0, homePages)
    val previewLayout = remember(state.layout, drag.source, insertionTarget, drag.moved) {
        val id = drag.source?.appId
        when {
            id != null && insertionTarget is DropTarget.Home -> dropApp(state.layout, id, insertionTarget)
            id != null && insertionTarget is DropTarget.Dock -> dropApp(state.layout, id, insertionTarget)
            drag.source?.target is DropTarget.Widget && insertionTarget is DropTarget.Home ->
                moveWidget(state.layout, (drag.source!!.target as DropTarget.Widget).index, insertionTarget.index)
            else -> state.layout
        }
    }
    val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
    val edgePointer = widgetSession?.takeIf { it.dragging }?.pointer ?: drag.pointer
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true
    val edge = if (!edgeActive) 0 else dragEdgeDirection(edgePointer, drag.rootBounds, edgeWidth)
    LaunchedEffect(edgeActive, edge) {
        if (edge != 0) while (drag.active || widgetSession?.dragging == true) {
            delay(650)
            val next = (pager.currentPage + edge).coerceIn(0, homePages)
            if ((!drag.active && widgetSession?.dragging != true) || next == pager.currentPage) break
            // Do not key this effect on currentPage: it changes halfway through the
            // animation and would cancel the turn before the inner grid is visible.
            // Once the hold commits a turn, finish its animation while the finger moves
            // into the incoming page. Leaving the edge cancels only the next hold timer.
            scope.launch { pager.animateScrollToPage(next) }.join()
        }
    }
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        val merge = drag.mergeCandidate(drag.pointer, eligibleDragPages)
            ?.takeIf { moved && !cancelled && drag.mergeArmed && (it.target as? DropTarget.Home)?.index == drag.mergeIndex }
        val changed = when {
            merge != null && source.appId != null && isReservedFolderId(merge.appId!!) ->
                model.addAppToFolder(merge.appId, source.appId)
            merge != null && source.appId != null ->
                model.createFolder(merge.appId!!, source.appId, (merge.target as DropTarget.Home).index) != null
            source.folderId != null && destination is DropTarget.Folder ->
                model.addAppToFolder(destination.id, source.appId ?: "")
            source.folderId != null && destination != null && source.appId != null ->
                model.removeAppFromFolder(source.folderId, source.appId, destination)
            destination == DropTarget.Remove -> model.removePlacement(source.target)
            destination is DropTarget.Home && source.target is DropTarget.Widget -> model.moveWidgetTo(source.target.index, destination.index)
            destination != null && source.appId != null -> model.applyDrop(source.appId, destination)
            else -> false
        }
        val returnToLibrary = source.target is DropTarget.Library && source.folderId == null && !changed
        val destinationHomePage = (destination as? DropTarget.Home)?.index?.let(::homeCellPage)
        val currentWindow = pager.settledPage.coerceIn(0, visibleHomePages - 1)
        val page = when (destination) {
            is DropTarget.Home -> if (expandedWorkspace && homeCellPage(destination.index) in eligibleDragPages) currentWindow else destinationHomePage!!
            is DropTarget.Dock -> dropHomePage
            is DropTarget.Widget -> 0
            else -> if (source.target is DropTarget.Library) pager.currentPage else drag.originPage
        }
        scope.launch {
            // Let a new home page compose before removing the temporary drop page.
            withFrameNanos { }
            drag.clear()
            withFrameNanos { }
            pager.scrollToPage(if (returnToLibrary) model.state.value.homePages else page.coerceIn(0, model.state.value.homePages - 1))
            if (!moved && !cancelled) {
                if (source.target is DropTarget.Dock) { dockSlot = source.target.index; sheet = "dock" }
                else if (source.target is DropTarget.Widget) { widgetSlot = source.target.index; sheet = "widgetActions" }
                else if (source.appId?.let(::isFolderId) == true) openFolderId = source.appId
                else if (source.folderId == null) selectedId = source.appId
            }
        }
    }

    // Soften Home behind an open folder so the folder reads as the one surface in focus. No
    // layer is added while closed, keeping the retained Home layer that Discover draws untouched.
    val folderBlur by animateDpAsState(if (openFolderId != null) FOLDER_BACKDROP_BLUR else 0.dp, label = "folder blur")
    val behindFolder = if (folderBlur > 0.dp) Modifier.blur(folderBlur) else Modifier
    // Android's wallpaper lies behind this window, so the window blurs it rather than Compose.
    val wallpaperBlur = with(LocalDensity.current) { FOLDER_BACKDROP_BLUR.roundToPx() }
    DisposableEffect(openFolderId != null) {
        launcherActivity.window.setWallpaperBlur(if (openFolderId != null) wallpaperBlur else 0)
        onDispose { launcherActivity.window.setWallpaperBlur(0) }
    }
    // Let a wallpaper wider than the screen scroll with the Home pages.
    LaunchedEffect(nativePager, homePages, firstHome) {
        val wallpapers = android.app.WallpaperManager.getInstance(launcherActivity)
        val span = (homePages - 1).coerceAtLeast(1).toFloat()
        wallpapers.setWallpaperOffsetSteps(1f / span, 1f)
        snapshotFlow { nativePager.currentPage + nativePager.currentPageOffsetFraction - firstHome }.collect { position ->
            val token = launcherActivity.window.decorView.windowToken ?: return@collect
            wallpapers.setWallpaperOffsets(token, if (homePages > 1) (position / span).coerceIn(0f, 1f) else .5f, .5f)
        }
    }
    val homeLayer = rememberGraphicsLayer()
    DisposableEffect(homeLayer) {
        homeLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        LiveDiscover.homeLayer = homeLayer
        onDispose { if (LiveDiscover.homeLayer === homeLayer) LiveDiscover.homeLayer = null }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        // The feed frame reuses the pager's render nodes in another window. Give Main
        // a complete render target so cross-window damage cannot erase stationary controls.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.onSizeChanged { LiveDiscover.fullSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.testTag("launcher-root").homeDragInput(drag,
        enabled = sheet.isEmpty() && !showFirstRun && selectedId == null && resizeSlot == null && pager.currentPage >= 0,
        page = pager.currentPage, eligiblePages = eligibleDragPages, onStart = {
            focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (drag.source?.folderId != null) openFolderId = null
            if (drag.source?.target is DropTarget.Library) scope.launch {
                withFrameNanos { }
                pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1))
            }
        },
        onFinish = { cancelled -> finishDrag(cancelled) })) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth.value >= 650f
            val preset = if (wide) state.expanded else state.compact
            val density = LocalDensity.current
            val inLibrary = pager.currentPage == visibleHomePages
            var statusHeight by remember { mutableFloatStateOf(0f) }
            val geometry = homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                statusRailHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { 14.sp.toDp().value } + 6f, inLibrary = inLibrary,
                homeBottomSpace = if (isDefaultHome) 44f else 88f, dockSlots = state.dock.size, homeRows = state.homeRows)
            // The most Home rows this screen can show in full, measured like the page itself.
            val maxRowsFit = (GRID_ROWS downTo DEFAULT_HOME_ROWS + 1).firstOrNull { rows ->
                homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                    labelHeight = with(density) { 14.sp.toDp().value } + 6f,
                    homeBottomSpace = if (isDefaultHome) 44f else 88f, homeRows = rows).gridFits
            } ?: DEFAULT_HOME_ROWS
            SideEffect {
                resizePitchX = with(density) { (geometry.gridWidth / GRID_COLUMNS).dp.toPx() }
                resizePitchY = with(density) { minOf((geometry.widgetHeight + 18f) / 2f, geometry.rowHeight).dp.toPx() }
                resizeTopPitch = with(density) { ((geometry.widgetHeight + 18f) / 2f).dp.toPx() }
                resizeAppPitch = with(density) { geometry.rowHeight.dp.toPx() }
            }
            LaunchedEffect(geometry.gridWidth, geometry.widgetHeight, geometry.rowHeight) { resizeSlot = null }
            SideEffect { expandedWorkspace = geometry.expanded }
            LaunchedEffect(geometry.expanded) {
                if (!geometry.expanded) {
                    val sessionTargetsLeading = widgetSession?.let { session ->
                        session.candidate?.page == -1 || session.targetIndex?.let(::homeCellPage) == -1
                    } == true
                    val savedTargetLeading = widgetTargetIndex != Int.MIN_VALUE && homeCellPage(widgetTargetIndex) == -1
                    if (sessionTargetsLeading || savedTargetLeading) {
                        widgetSession = null
                        widgetTargetIndex = Int.MIN_VALUE
                        widgetExactTarget = false
                        widgetPackage = null
                        widgetProfileSerial = null
                        widgetPlacementMessage = null
                        sheet = ""
                    }
                    val dragTouchesLeading = drag.source?.page == -1 ||
                        ((target as? DropTarget.Home)?.index?.let(::homeCellPage) == -1)
                    if (dragTouchesLeading) {
                        drag.clear()
                    }
                }
            }
            val contentHeight = maxHeight
            val panelWidth = maxWidth - geometry.homeWidth.dp
            val pagerWidth = maxWidth - preset.dockWidth.dp - 28.dp
            val leftColumnOrigin = (maxWidth / 2f - geometry.gridWidth.dp) / 2f - 16.dp
            val homeStride = panelWidth - leftColumnOrigin
            val bottomSpace = if (isDefaultHome) 44.dp else 88.dp
            val workspaceMotion = if (geometry.expanded) remember(firstHome, visibleHomePages, pagerWidth, homeStride, density) {
                WorkspacePageMotion(firstHome, visibleHomePages, with(density) { pagerWidth.toPx() }, with(density) { homeStride.toPx() })
            } else null
            val dockScroll = rememberScrollState()
            var gestureOriginInRoot by remember { mutableStateOf(Offset.Zero) }
            var gestureOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            val pagerInputEnabled = pager.currentPage in -firstHome..visibleHomePages && !drag.active &&
                widgetSession == null && resizeSlot == null && sheet.isEmpty() && !showFirstRun && selectedId == null &&
                openFolderId == null && emptyCellIndex == null && createFolderFirstId == null &&
                launcherActivity.backups.preview == null && !launcherActivity.backups.pickerPending &&
                !launcherActivity.backgrounds.pickerPending && widgets.setupStatus == null &&
                widgets.reconfigureWidgetId == null
            val openHomeOptionsAt by rememberUpdatedState { point: Offset, width: Int ->
                val page = pager.currentPage
                val targetPage = if (geometry.expanded && point.x < width / 2f) page - 1 else page
                val overItem = drag.hit(point + gestureOriginInRoot, setOf(page - 1, page))?.movable == true
                if (pagerInputEnabled && page in 0 until visibleHomePages && !overItem) {
                    emptyCellIndex = firstEmptyHomeCell(state, targetPage)
                }
            }
            Box(Modifier.fillMaxSize().then(behindFolder).onGloballyPositioned {
                gestureOriginInRoot = it.boundsInRoot().topLeft
                gestureOriginInWindow = it.boundsInWindow().topLeft
            }.onePageGestures(
                nativePager,
                pageGestures,
                motion = workspaceMotion,
                enabled = pagerInputEnabled,
                // Positive IDs are provider-owned Android views. Leave their vertical
                // stream untouched so scrollable widgets retain native gesture handling.
                // A dock that is already scrolled also gets first use of a downward drag.
                canStartDownwardSwipe = { point ->
                    if (pager.currentPage !in 0 until visibleHomePages) false else {
                        val region = drag.hit(point + gestureOriginInRoot, eligibleDragPages)
                        val rootOnScreen = IntArray(2).also(launcherRootView::getLocationOnScreen)
                        val screenPoint = point + gestureOriginInWindow +
                            Offset(rootOnScreen[0].toFloat(), rootOnScreen[1].toFloat())
                        !(region?.target is DropTarget.Dock && dockScroll.value > 0) &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint)
                    }
                },
                onDownwardSwipe = launcherActivity::openSystemShade,
                onLeadingOverscroll = if (firstHome == 0) onDiscover else null,
                childPagesHorizontally = { point, travel ->
                    drag.childPager?.let { child -> child.bounds().contains(point + gestureOriginInRoot) && child.canPage(travel) } == true
                },
            ).pointerInput(Unit) {
                // Bare wallpaper anywhere on Home opens Home options. Icons, cells, the dock and
                // controls consume their own presses first, and a page swipe cancels this one.
                detectTapGestures(onLongPress = { openHomeOptionsAt(it, size.width) })
            }) {
            val pagerModifier = Modifier.fillMaxHeight().width(pagerWidth)
                .drawWithContent {
                    homeLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(homeLayer)
                    LiveDiscover.host.get()?.invalidateFrame()
                }.testTag("app-pager")
                .discoverSwipe(firstHome == 0 && pager.currentPage == 0 && !drag.active && sheet.isEmpty() &&
                    !showFirstRun && selectedId == null, onDiscover)
                .onGloballyPositioned {
                    if (firstHome > 0 && !rssLeft) {
                        val bounds = it.boundsInWindow()
                        LiveDiscover.pagerOrigin = bounds.topLeft
                        val padding = 32 * density.density
                        LiveDiscover.prepare(launcherActivity,
                            android.graphics.Rect((bounds.left + padding).toInt(), (bounds.top + padding).toInt(),
                                (bounds.right - 16 * density.density).toInt(), (bounds.bottom - padding).toInt()), bounds.width)
                    }
                }
                .semantics { stateDescription = if (pager.currentPage == -1) launcherActivity.getString(if (rssLeft) R.string.rss_title else R.string.discover)
                    else if (pager.currentPage == visibleHomePages) launcherActivity.getString(R.string.all_apps)
                    else launcherActivity.getString(R.string.home_page_of, pager.currentPage + 1, visibleHomePages) }
            if (geometry.expanded) {
                Box(pagerModifier) {
                    // PagerState remains the source of truth for native Discover progress,
                    // snapping, accessibility state, and programmatic page requests.
                    HorizontalPager(nativePager, Modifier.fillMaxSize(), userScrollEnabled = false,
                        key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { }
                    ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        libraryQuery = libraryQuery, onLibraryQuery = { libraryQuery = it },
                        onLaunch = onLaunch, onLaunchFrom = onLaunchFrom, onPinned = model::setPinned,
                        onTurnOnWork = { model.turnOnWork(it) },
                        onActions = { selectedId = it.id }, onWidget = { widgetSlot = it; sheet = "widgetActions" },
                        onFolder = { openFolderId = it },
                        onEmptyWidget = { emptyCellIndex = it },
                        onRefresh = model::refresh,
                    )
                }
            } else {
                HorizontalPager(nativePager, pagerModifier,
                    // Keep adjacent Home panes attached so ordinary back-and-forth paging does
                    // not synchronously inflate provider RemoteViews inside the gesture frame.
                    // Discover is two physical positions before Home 2. Retain both Home
                    // neighbors to avoid reinflating Home 2's RemoteViews during native exit.
                    beyondViewportPageCount = if (firstHome > 0) 2 else 1,
                    userScrollEnabled = !drag.active && resizeSlot == null, flingBehavior = pageFling,
                    key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { physicalPage ->
                    val page = physicalPage - firstHome
                    if (page == -1) {
                        if (rssLeft) RssPage(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace),
                            active = pager.currentPage == -1)
                        else DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
                    } else if (page == visibleHomePages) {
                        AppLibrary(state, libraryQuery, { libraryQuery = it }, onLaunch, model::setPinned,
                            onActions = { selectedId = it.id }, modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace).testTag("library-page"),
                            drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = { model.turnOnWork(it) })
                    } else {
                        Row(Modifier.fillMaxSize().testTag("home-surface")) {
                            HomePagePane(page, state, previewLayout.slots, previewLayout.leadingSlots, previewLayout.widgetPlacements, appsById, geometry, contentHeight,
                                bottomSpace, widgets, drag, target, insertionTarget, showLargeWidget = false,
                                onLaunch = onLaunchFrom, onActions = { selectedId = it.id },
                                onWidget = { widgetSlot = it; sheet = "widgetActions" },
                                onFolder = { openFolderId = it },
                                onEmptyWidget = { emptyCellIndex = it },
                                onRefresh = model::refresh)
                        }
                    }
                }
            }
            if (state.verticalStatus) StatusRail(deviceStatus,
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = geometry.contentTop.dp)
                    .width(preset.dockWidth.dp).onSizeChanged {
                        // The normal rail's 20dp location slot and 3dp gap do not move the dock.
                        statusHeight = (with(density) { it.height.toDp().value } -
                            if (contentHeight < 500.dp) 0f else 23f).coerceAtLeast(0f)
                    },
                compact = contentHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp)
            Surface(Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = geometry.dockTop.dp)
                .width(preset.dockWidth.dp).height(geometry.dockHeight.dp).graphicsLayer {
                    // Composite the stationary dock independently of the shared pager layer.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.testTag("dock"),
                shape = RoundedCornerShape(30.dp), color = Glass.copy(alpha = .32f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .3f))) {
                Column(Modifier.padding(vertical = 8.dp).verticalScroll(dockScroll)) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        dockIconSize(geometry.iconSize), drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" })
                }
            }
            Column(Modifier.align(Alignment.BottomStart).width(pagerWidth).padding(start = 16.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome) FilledTonalButton(onClick = { sheet = ""; onMakeDefault() }, Modifier.heightIn(min = 48.dp).testTag("home-setup")) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.set_as_home_app))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (!drag.active) IconButton(onClick = openDiscover, Modifier.size(32.dp).testTag("discover-page-link")) {
                        Icon(if (rssLeft) Icons.Rounded.RssFeed else Icons.Rounded.Explore,
                            stringResource(if (rssLeft) R.string.rss_title else R.string.discover),
                            tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(17.dp))
                    }
                    if (visibleHomePages <= 6) repeat(visibleHomePages) { index ->
                        Box(Modifier.size(28.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(index) } }
                            .semantics { contentDescription = if (index == homePages) launcherActivity.getString(R.string.new_home_page)
                                else launcherActivity.getString(R.string.home_page, index + 1) }, contentAlignment = Alignment.Center) {
                            if (index == homePages) Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            else Box(Modifier.size(if (index == pager.currentPage) 6.dp else 4.dp).background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else .4f), CircleShape))
                        }
                    } else Text("${minOf(pager.currentPage + 1, homePages)} / $homePages", color = Color.White, fontSize = 12.sp)
                    IconButton(onClick = openLibrary, Modifier.size(32.dp).testTag("library-page-link")) {
                        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, stringResource(R.string.all_apps_page), tint = Color.White.copy(alpha = if (pager.currentPage == homePages) 1f else .6f), modifier = Modifier.size(17.dp))
                    }
                }
            }
            if (!inLibrary && !drag.active) Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 6.dp)
                .width(preset.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val controlSize = dockIconSize(geometry.iconSize).dp
                if (pager.currentPage == -1) CircleControl(Icons.Rounded.ArrowForward, stringResource(R.string.back_to_home), "discover-home", controlSize) { scope.launch { pager.animateScrollToPage(0) } }
                val searchBounds = remember { android.graphics.Rect() }
                Box(Modifier.onGloballyPositioned { searchBounds.set(it.boundsInWindow().toAndroidBounds()) }) {
                    CircleControl(Icons.Rounded.Search, stringResource(if (state.googleSearch) R.string.search_google else R.string.search_apps), "search", controlSize) {
                        if (!state.googleSearch || !onGoogleSearch(searchBounds)) openLibrary()
                    }
                }
            }
            if (sheet.isNotEmpty() && sheet != "widgets") {
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.WALLPAPER else customizationPage
                ModalBottomSheet(onDismissRequest = {
                    customizationPage = CustomizationPage.OVERVIEW
                    sheet = ""; widgetPackage = null; widgetExactTarget = false
                }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
                    containerColor = MaterialTheme.colorScheme.surface) {
                    ModalDialogBackHandler {
                        if ((sheet == "settings" || sheet == "settings:wallpaper") &&
                            activeCustomizationPage != CustomizationPage.OVERVIEW) {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = "settings"
                        } else {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(state.apps, dockSlot,
                            onSelect = {
                                if (canPlaceInDock(state.layout, it.id)) {
                                    model.applyDrop(it.id, DropTarget.Dock(dockSlot)); sheet = ""
                                }
                            },
                            onClear = { model.removePlacement(DropTarget.Dock(dockSlot)) },
                            onLongClick = { selectedId = it.id; sheet = "" },
                            canSelect = { canPlaceInDock(state.layout, it.id) },
                            blockedHint = if (state.dock.none { it == null }) stringResource(R.string.dock_full) else null)
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text(stringResource(R.string.done)) }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = { selectedId = it.id; sheet = "" }, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> CustomizationSheet(state, wide, model, isDefaultHome, maxRowsFit,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { customizationPage = CustomizationPage.OVERVIEW; sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperSettings = { sheet = ""; onWallpaperSettings() }, homePage = pager.currentPage.coerceIn(0, homePages - 1))
                        "widgetActions" -> model.placement(widgetSlot)?.let { placement ->
                            val topPitch = (geometry.widgetHeight + 18f) / 2f
                            val gridSizing = WidgetGridSizing(GRID_COLUMNS, state.homeRows, geometry.gridWidth / GRID_COLUMNS,
                                minOf(topPitch, geometry.rowHeight), maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                                topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight)
                            val constraints = widgets.manager.getAppWidgetInfo(placement.id)?.let { widgets.sizing(it, gridSizing) }
                            WidgetActions(placement, constraints, rows = state.homeRows,
                                canConfigure = widgets.canReconfigure(placement.id),
                                onConfigure = { widgets.reconfigure(placement.id); sheet = "" },
                                isValid = { x, y -> (x == placement.spanX && y == placement.spanY) || resizeWidget(state.layout, widgetSlot, x, y) != state.layout },
                                onResize = { x, y -> model.resizeWidget(widgetSlot, x, y) },
                                onStartResize = { x, y ->
                                    resizeSlot = widgetSlot; resizeWidth = x; resizeHeight = y
                                    resizeConstraints = constraints; sheet = ""
                                },
                                onMoveToPage = { page ->
                                    (0 until HOME_CELLS).firstOrNull { local ->
                                        widgetCandidate(state.layout, placement.slot, page * HOME_CELLS + local,
                                            placement.spanX, placement.spanY) != null
                                    }?.let { model.moveWidgetTo(placement.slot, page * HOME_CELLS + it) } == true
                                }, homePages = homePages,
                                onReplace = {
                                    widgetPackage = null
                                    widgetProfileSerial = widgets.manager.getAppWidgetInfo(placement.id)?.profile?.let {
                                        launcherActivity.getSystemService(UserManager::class.java).getSerialNumberForUser(it)
                                    }?.takeIf { it >= 0 }
                                    widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemove = { widgets.remove(widgetSlot); sheet = "" },
                                onClose = { sheet = "" })
                        }
                    }
                }
            }
            if (showFirstRun) {
                ModalBottomSheet(
                    onDismissRequest = onFinishFirstRun,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("first-run-setup"),
                ) {
                    FirstRunSetupSheet(
                        isDefaultHome = isDefaultHome,
                        onMakeDefault = onMakeDefault,
                        onAddWidget = {
                            onFinishFirstRun()
                            widgetSlot = model.nextWidgetSlot()
                            widgetTargetIndex = pager.currentPage.coerceIn(0, homePages - 1) * HOME_CELLS
                            widgetPackage = null
                            widgetProfileSerial = null
                            widgetExactTarget = false
                            sheet = "widgets"
                        },
                        onExplore = onFinishFirstRun,
                        onSkip = onFinishFirstRun,
                    )
                }
            }
            if (sheet == "widgets") {
                val catalogProfiles = remember(state.profiles) { state.profiles.filter { it.isPersonal || it.isWork } }
                val selectedProfile = catalogProfiles.firstOrNull { it.userSerial == widgetProfileSerial }
                    ?: catalogProfiles.firstOrNull { it.isPersonal } ?: AppProfile(0, "Personal", true, false, false, true, true)
                val userManager = remember(launcherActivity) { launcherActivity.getSystemService(UserManager::class.java) }
                val providers = remember(widgetPackage, selectedProfile, sheet, state.apps) {
                    val user = userManager.getUserForSerialNumber(selectedProfile.userSerial)
                    if (user == null || !selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) emptyList()
                    else runCatching { widgetPackage?.let { widgets.providersForPackage(it, user) }
                        ?: widgets.providers(user) }.getOrDefault(emptyList()).filter { provider ->
                        provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                    }
                }
                val catalog by produceState<List<WidgetCatalogEntry>?>(null, providers, selectedProfile.userSerial, sheet) {
                    value = withContext(Dispatchers.IO) { widgetCatalog(launcherActivity, providers, selectedProfile) }
                }
                val topPitch = (geometry.widgetHeight + 18f) / 2f
                val pickerSizing = remember(geometry, state.homeRows) { WidgetGridSizing(GRID_COLUMNS, state.homeRows,
                    geometry.gridWidth / GRID_COLUMNS, minOf(topPitch, geometry.rowHeight),
                    maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                    topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight) }
                val footprint: (AppWidgetProviderInfo) -> WidgetSpan? = { provider ->
                    widgets.sizing(provider, pickerSizing)?.takeIf { it.minimumFitsGrid }?.preferred
                }
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile,
                    onSelectProfile = { widgetProfileSerial = it.userSerial; widgetPlacementMessage = null },
                    onTurnOnWork = { model.turnOnWork(it) }, hiddenForDrag = widgetSession != null,
                    footprint = footprint,
                    onBack = widgetPickerBack,
                    onTap = { provider ->
                        footprint(provider)?.let { preferredSpan ->
                            val existing = model.placement(widgetSlot)
                            val constraints = widgets.sizing(provider, pickerSizing)
                            val span = existing?.let { placement ->
                                WidgetSpan(placement.spanX, placement.spanY).takeIf {
                                    constraints != null && it.width in constraints.minimum.width..constraints.maximum.width &&
                                        it.height in constraints.minimum.height..constraints.maximum.height
                                }
                            } ?: preferredSpan
                            val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                            if (special != null) {
                                widgetSession = WidgetPickerSession(provider, widgetSlot,
                                    WidgetSpan(special.spanX, special.spanY), Offset.Zero,
                                    dragging = false, candidate = special)
                                widgetPlacementMessage = null
                                scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                                return@let
                            }
                            val requestedIndex = existing?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                                ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                            val requestedPage = homeCellPage(requestedIndex).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                            val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                            val autoPages = (listOf(requestedPage) + availablePages.filter { it != requestedPage })
                            val freeIndex = if (existing != null || widgetExactTarget) requestedIndex.takeIf {
                                widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                            } else autoPages.asSequence().flatMap { page ->
                                (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) }
                            }.firstOrNull { widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null }
                            val targetIndex = freeIndex ?: requestedIndex
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, Offset.Zero,
                                dragging = false, targetIndex = targetIndex)
                            widgetPlacementMessage = if (freeIndex == null)
                                launcherActivity.getString(R.string.widget_no_room_size) else null
                            scope.launch { pager.scrollToPage(homeCellPage(targetIndex).coerceIn(0, homePages)) }
                        }
                    },
                    onBuiltin = builtin@{ builtinId ->
                        val existing = model.placement(widgetSlot)
                        val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                        val span = existing?.let { WidgetSpan(it.spanX, it.spanY) } ?: WidgetSpan(2, 2)
                        if (special != null) {
                            widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                                dragging = false, candidate = special, builtinId = builtinId)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                            return@builtin
                        }
                        val requested = existing?.let {
                            homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column)
                        } ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                        val requestedPage = homeCellPage(requested).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                        val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                        val candidates = if (model.placement(widgetSlot) != null || widgetExactTarget) sequenceOf(requested)
                            else (listOf(requestedPage) + availablePages.filter { it != requestedPage }).asSequence()
                                .flatMap { page -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) } }
                        val free = candidates.firstOrNull {
                            widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                        }
                        widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                            dragging = false, targetIndex = free ?: requested, builtinId = builtinId)
                        widgetPlacementMessage = if (free == null)
                            launcherActivity.getString(R.string.widget_no_room_card) else null
                        scope.launch { pager.scrollToPage(homeCellPage(free ?: requested).coerceIn(0, homePages)) }
                    },
                    onDragStart = { provider, point ->
                        footprint(provider)?.let { span ->
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, point, dragging = true)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                        }
                    },
                    onDrag = { point -> widgetSession = widgetSession?.copy(pointer = point) },
                    onDrop = {
                        val session = widgetSession
                        if (session != null && widgetDraft != null) {
                            session.provider?.let { widgets.add(widgetDraft, it, pickerSizing) }
                                ?: session.builtinId?.let { widgets.setBuiltin(widgetDraft.copy(id = it)) }
                            widgetSession = null; sheet = ""; widgetPackage = null
                        } else {
                            leaveTemporaryWidgetPage(); widgetSession = null
                            widgetPlacementMessage = launcherActivity.getString(R.string.widget_no_room_there)
                        }
                    },
                    onCancelDrag = {
                        if (widgetSession != null) {
                            leaveTemporaryWidgetPage(); widgetSession = null
                        }
                    })
                widgetSession?.let { session ->
                    val placementDensity = LocalDensity.current
                    val sessionEntry = session.provider?.let { selected -> catalog?.firstOrNull {
                        it.provider.provider == selected.provider && it.provider.profile == selected.profile } }
                    // Legacy overflow replacements are locked to their existing view
                    // bounds and may begin below the canonical six-row grid. They have
                    // no Home-cell address; specialAnchor below is their visual anchor.
                    val candidateIndex = widgetDraft?.takeIf { session.candidate == null }
                        ?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                    val visualIndex = candidateIndex ?: widgetRawTarget?.index ?: session.targetIndex
                    val specialAnchor = session.candidate?.let { drag.regions[DropTarget.Widget(session.slot)]?.bounds }
                    val anchor = specialAnchor ?: visualIndex?.let { drag.regions[DropTarget.Home(it)]?.bounds }
                    Box(Modifier.fillMaxSize().testTag("widget-placement-mode")
                        .then(if (!session.dragging && session.candidate == null) Modifier.pointerInput(session.slot, session.span) {
                            detectTapGestures { local ->
                                val point = local + drag.rootOrigin
                                val cell = drag.regions.values.firstOrNull {
                                    it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(point)
                                }?.target as? DropTarget.Home
                                cell?.let { widgetSession = session.copy(pointer = point, targetIndex = it.index) }
                            }
                        } else Modifier)) {
                        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                            .background(Glass.copy(alpha = .97f), RoundedCornerShape(22.dp))
                            .testTag("widget-placement-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = widgetPickerBack) { Text(stringResource(R.string.back_to_widgets)) }
                            if (session.candidate != null) Text(stringResource(R.string.replace_here), color = Ink,
                                modifier = Modifier.testTag("widget-replacement-locked"))
                            val targetPage = homeCellPage(session.targetIndex ?: 0)
                            if (!session.dragging && session.candidate == null) IconButton(
                                enabled = targetPage > if (expandedWorkspace) -1 else 0, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = targetPage - 1
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.previous_home_page)) }
                            Text("${session.span.width} × ${session.span.height}", color = Ink)
                            if (!session.dragging && session.candidate == null) IconButton(enabled = targetPage < homePages, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = (targetPage + 1).coerceAtMost(homePages)
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.next_home_page)) }
                            if (!session.dragging) TextButton(enabled = widgetDraft != null, onClick = {
                                widgetDraft?.let { draft ->
                                    val contentSize = specialAnchor?.let { bounds -> with(placementDensity) {
                                        WidgetContentSize(bounds.width.toDp().value, bounds.height.toDp().value)
                                    } }
                                    session.provider?.let { widgets.add(draft, it, pickerSizing, contentSize) }
                                        ?: session.builtinId?.let { widgets.setBuiltin(draft.copy(id = it)) }
                                    widgetSession = null; sheet = ""; widgetPackage = null
                                }
                            }, modifier = Modifier.testTag("widget-placement-apply")) { Text(stringResource(R.string.place)) }
                            TextButton(onClick = { leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null },
                                modifier = Modifier.testTag("widget-placement-cancel")) { Text(stringResource(R.string.cancel)) }
                        }
                        if (anchor != null) {
                            val density = LocalDensity.current
                            val cellWidthPx = with(density) { (geometry.gridWidth / GRID_COLUMNS).dp.toPx() }
                            fun pickerRowTop(row: Int): Float = if (row <= 2) row * with(density) { topPitch.dp.toPx() }
                                else with(density) { (geometry.widgetHeight + 18f + (row - 2) * geometry.rowHeight).dp.toPx() }
                            val candidateRow = homeCellLocal(visualIndex ?: 0) / GRID_COLUMNS
                            val previewWidth = specialAnchor?.let { with(density) { it.width.toDp() } }
                                ?: with(density) { (cellWidthPx * session.span.width - 10.dp.toPx()).toDp() }
                            val previewHeight = specialAnchor?.let { with(density) { it.height.toDp() } }
                                ?: with(density) { (pickerRowTop(candidateRow + session.span.height) -
                                    pickerRowTop(candidateRow) - 18.dp.toPx()).coerceAtLeast(48.dp.toPx()).toDp() }
                            val previewX = if (specialAnchor != null) anchor.left
                                else anchor.left + with(density) { 5.dp.toPx() }
                            Surface(Modifier.offset { IntOffset(previewX.roundToInt(), anchor.top.roundToInt()) }
                                .size(previewWidth, previewHeight).testTag("widget-placement-preview")
                                .semantics { stateDescription = launcherActivity.getString(if (widgetDraft != null) R.string.ready_to_place else R.string.no_room_here) },
                                color = if (widgetDraft != null) Glass.copy(alpha = .82f) else Color(0xFFE7B6B6).copy(alpha = .9f),
                                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(3.dp,
                                    if (widgetDraft != null) Color.White else Color(0xFFFF6B6B))) {
                                Box(Modifier.fillMaxSize()) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    else Column(Modifier.align(Alignment.Center).padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(session.provider?.loadLabel(launcherActivity.packageManager)?.toString()
                                            ?: stringResource(when (session.builtinId) {
                                                CLOCK_WIDGET -> R.string.widget_clock
                                                DATE_WIDGET -> R.string.widget_date
                                                else -> R.string.widget_panel
                                            }), color = Ink,
                                            textAlign = TextAlign.Center)
                                        Text("${session.span.width} × ${session.span.height}", color = Ink)
                                    }
                                    if (widgetDraft == null) Box(Modifier.matchParentSize()
                                        .background(Color(0xFFB83B3B).copy(alpha = .34f)), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.no_room_here), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else if (session.dragging) {
                            Surface(Modifier.offset { IntOffset((session.pointer.x - 90.dp.toPx()).roundToInt(),
                                (session.pointer.y - 60.dp.toPx()).roundToInt()) }.size(180.dp, 120.dp)
                                .testTag("widget-placement-preview").semantics { stateDescription = launcherActivity.getString(R.string.no_room_here) },
                                color = Color(0xFFE7B6B6).copy(alpha = .9f), shape = RoundedCornerShape(24.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    Box(Modifier.matchParentSize().background(Color(0xFFB83B3B).copy(alpha = .34f)),
                                        contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_room_here), color = Color.White) }
                                }
                            }
                        }
                    }
                }
                widgetPlacementMessage?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        color = Glass, shape = RoundedCornerShape(18.dp)) { Text(message, Modifier.padding(16.dp), color = Ink) }
                }
            }
        }
        if (drag.active) {
            if (drag.moved) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge < 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-left"))
                if (pager.currentPage < homePages) Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge > 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-right"))
            }
            appsById[drag.source?.appId]?.let { app ->
                val size = 66.dp
                val px = with(LocalDensity.current) { size.toPx() }
                Image(app.icon.asImageBitmap(), stringResource(R.string.moving_app, app.label), Modifier
                    .offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - px / 2).roundToInt(), (drag.pointer.y - drag.rootOrigin.y - px * .65f).roundToInt()) }
                    .size(size).shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).testTag("drag-ghost"))
            }
            drag.source?.appId?.let { state.layout.folder(it) }?.let { folder ->
                Surface(Modifier.offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - 42.dp.toPx()).roundToInt(),
                    (drag.pointer.y - drag.rootOrigin.y - 52.dp.toPx()).roundToInt()) }.size(84.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)).testTag("folder-drag-ghost"),
                    color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(20.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(folder.title, color = Ink, textAlign = TextAlign.Center) }
                }
            }
            drag.source?.widgetId?.let { id ->
                val width = 144.dp; val height = 108.dp
                val x = with(LocalDensity.current) { width.toPx() }
                val y = with(LocalDensity.current) { height.toPx() }
                Surface(Modifier.offset { IntOffset((drag.pointer.x - x / 2).roundToInt(), (drag.pointer.y - y * .65f).roundToInt()) }
                    .size(width, height).shadow(16.dp, RoundedCornerShape(24.dp)).testTag("drag-ghost"),
                    color = Glass.copy(alpha = .95f), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Widgets, null, tint = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(widgetLabel(id, widgets), color = Ink, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blockedDock) Surface(
                Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(top = 10.dp, start = 20.dp, end = 100.dp),
                color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.dock_full),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink, fontSize = 13.sp)
            }
            if (drag.moved && drag.source?.target !is DropTarget.Library &&
                drag.source?.appId?.let(::isFolderId) != true) Surface(
                // Keep removal in the right-side control area that is vacated during a drag.
                // A centered target overlaps the expanded workspace's right-hand first cell.
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 12.dp)
                    .width((if (expandedWorkspace) state.expanded else state.compact).dockWidth.dp).height(64.dp)
                    .dropRegion(drag, DropTarget.Remove).testTag("remove-drop-target"),
                color = if (target == DropTarget.Remove) Color(0xFFB33B3B) else Glass.copy(alpha = .96f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text(stringResource(R.string.remove), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        resizeSlot?.let { slot ->
            val placement = model.placement(slot)
            val bounds = drag.regions[DropTarget.Widget(slot)]?.bounds
            if (placement != null && bounds != null) {
                val minW = resizeConstraints?.minimum?.width ?: 2
                val minH = resizeConstraints?.minimum?.height ?: 2
                val maxW = minOf(GRID_COLUMNS - placement.column, resizeConstraints?.maximum?.width ?: GRID_COLUMNS)
                val maxH = minOf(state.homeRows - placement.row, resizeConstraints?.maximum?.height ?: state.homeRows)
                val feasible = placement.page >= -1 && placement.row in 0 until state.homeRows &&
                    !(placement.id >= 0 && resizeConstraints == null) && minW <= maxW && minH <= maxH
                val candidate = resizeWidget(state.layout, slot, resizeWidth, resizeHeight)
                val valid = feasible && ((resizeWidth == placement.spanX && resizeHeight == placement.spanY) || candidate != state.layout)
                val widthPx = (bounds.width + (resizeWidth - placement.spanX) * resizePitchX).coerceAtLeast(resizePitchX)
                val density = LocalDensity.current
                fun resizeRowTop(row: Int) = if (row <= 2) row * resizeTopPitch else 2 * resizeTopPitch + (row - 2) * resizeAppPitch
                val heightPx = (resizeRowTop(placement.row + resizeHeight) - resizeRowTop(placement.row) -
                    with(density) { 18.dp.toPx() }).coerceAtLeast(resizePitchY)
                Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .border(3.dp, if (valid) Color.White else Color(0xFFFF6B6B), RoundedCornerShape(24.dp))
                    .testTag("widget-resize-preview-$slot")) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(44.dp)
                        .background(if (valid) Color.White else Color(0xFFFF6B6B), CircleShape)
                        .testTag("widget-resize-handle-$slot")
                        .pointerInput(slot, resizeConstraints) {
                            var dx = 0f; var dy = 0f; var startWidth = resizeWidth; var startHeight = resizeHeight
                            detectDragGestures(onDragStart = {
                                dx = 0f; dy = 0f; startWidth = resizeWidth; startHeight = resizeHeight
                            }, onDrag = { change, amount ->
                                change.consume(); dx += amount.x; dy += amount.y
                                if (feasible && resizeConstraints?.canResizeHorizontally != false)
                                    resizeWidth = (startWidth + (dx / resizePitchX).roundToInt()).coerceIn(minW, maxW)
                                if (feasible && resizeConstraints?.canResizeVertically != false)
                                    resizeHeight = (startHeight + (dy / resizePitchY).roundToInt()).coerceIn(minH, maxH)
                            })
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.OpenInFull, stringResource(R.string.drag_to_resize), tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .background(Glass.copy(alpha = .96f), RoundedCornerShape(20.dp))) {
                        TextButton(onClick = { resizeSlot = null }) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = valid, onClick = {
                            model.resizeWidget(slot, resizeWidth, resizeHeight); resizeSlot = null
                        }) { Text(stringResource(R.string.apply)) }
                    }
                    if (!feasible) Text(stringResource(R.string.widget_resize_outside),
                        color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .65f)).padding(8.dp))
                }
            }
        }
        appsById[selectedId]?.let { app ->
            val pinned = state.layout.indexOfShortcut(app.id) != null
            val packageName = app.packageName
            val hasWidgets = packageName.isNotEmpty() && runCatching {
                widgets.providersForPackage(packageName, app.user)
            }.getOrDefault(emptyList()).isNotEmpty()
            ModalBottomSheet(onDismissRequest = { appMoveMenu = false; selectedId = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)) {
                LauncherAppActionSheet(app, pinned, homePages, appMoveMenu, { appMoveMenu = it },
                    onAddOrRemove = { model.setPinned(app.id, !pinned); selectedId = null },
                    onMoveFirst = { model.move(app.id, -maxOf(HOME_CELLS, state.homeSlots.size)); selectedId = null },
                    onMoveEarlier = { model.move(app.id, -1); selectedId = null },
                    onMoveLater = { model.move(app.id, 1); selectedId = null },
                    onMovePage = { page -> model.applyDrop(app.id, DropTarget.Home(homeCellIndex(page, 0))); selectedId = null },
                    onInfo = { onAppInfo(app); selectedId = null },
                    onWidgets = if (hasWidgets) {{
                        val page = lastHomePage.coerceIn(0, homePages - 1)
                        widgetTargetIndex = homeCellIndex(page, 0); widgetExactTarget = false
                        widgetSlot = model.nextWidgetSlot(); widgetPackage = packageName
                        widgetProfileSerial = app.userSerial; selectedId = null; sheet = "widgets"
                    }} else null,
                    onCreateFolder = { createFolderFirstId = app.id; selectedId = null },
                    onClose = { appMoveMenu = false; selectedId = null })
            }
        }
        emptyCellIndex?.let { index ->
            ModalBottomSheet(onDismissRequest = { emptyCellIndex = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                EmptySpaceActionSheet(onWidgets = {
                        widgetTargetIndex = index; widgetExactTarget = true; widgetSlot = model.nextWidgetSlot(); widgetPackage = null; widgetProfileSerial = null
                        emptyCellIndex = null; sheet = "widgets"
                    }, onWallpaper = { emptyCellIndex = null; sheet = "settings:wallpaper" },
                    onCustomize = { emptyCellIndex = null; sheet = "settings" }, onClose = { emptyCellIndex = null })
            }
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            AlertDialog(onDismissRequest = { createFolderFirstId = null }, title = { Text(first?.let { stringResource(R.string.create_folder_with, it.label) } ?: stringResource(R.string.create_folder)) },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(state.apps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        TextButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.layout.unavailableCells()
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex)
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { createFolderFirstId = null }) { Text(stringResource(R.string.cancel)) } })
        }
        openFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.let { folder ->
                val blocked = state.layout.unavailableCells()
                val destinationPages = (if (expandedWorkspace) listOf(-1) else emptyList()) + (0 until homePages)
                val homeDestinations = destinationPages.mapNotNull { destinationPage ->
                    (0 until HOME_CELLS).map { homeCellIndex(destinationPage, it) }
                        .firstOrNull { it !in blocked && state.layout.slotAt(it) == null }
                }
                val folderPage = state.layout.indexOfShortcut(id)?.let(::homeCellPage)
                FolderPanel(folder, appsById, drag, pager.currentPage,
                    folderDestinations = homeDestinations.filter { homeCellPage(it) != folderPage },
                    onDismiss = { openFolderId = null }, onRename = { model.renameFolder(id, it) },
                    onLaunch = onLaunchFrom, transparency = state.folderTransparency,
                    origin = drag.regions[DropTarget.Folder(id)]?.bounds?.center,
                    onMoveFolder = { destination ->
                        if (model.applyDrop(id, DropTarget.Home(destination))) {
                            openFolderId = null
                            scope.launch { pager.animateScrollToPage(homeCellPage(destination).coerceIn(0, homePages - 1)) }
                        }
                    },
                    onDisband = { if (model.disbandFolder(id)) openFolderId = null })
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) AlertDialog(onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_picker_title)) },
            text = { Text(stringResource(R.string.backup_picker_detail)) },
            confirmButton = { TextButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text(stringResource(R.string.resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text(stringResource(R.string.cancel)) } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) AlertDialog(
            onDismissRequest = {}, title = { Text(stringResource(R.string.photo_picker_title)) },
            text = { Text(stringResource(R.string.photo_picker_detail)) },
            confirmButton = { TextButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text(stringResource(R.string.resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text(stringResource(R.string.cancel)) } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            AlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(stringResource(if (launcherActivity.backups.errorMessage != null) R.string.backup_problem else R.string.backup_title)) },
                text = { Text(message) }, confirmButton = { TextButton(onClick = launcherActivity.backups::clearMessage) { Text(stringResource(android.R.string.ok)) } })
        }
        widgets.failureMessage?.let { message ->
            AlertDialog(onDismissRequest = widgets::clearFailure, title = { Text(stringResource(R.string.widget_not_added)) },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { TextButton(onClick = widgets::clearFailure) { Text(stringResource(android.R.string.ok)) } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            val continueSetup = stringResource(R.string.widget_setup_continue)
            val cancelSetup = stringResource(R.string.widget_setup_cancel)
            AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.widget_setup_title)) },
                text = { Text(stringResource(R.string.widget_setup_detail)) },
                confirmButton = { Button(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = continueSetup }) { Text(stringResource(R.string.widget_setup_finish)) } },
                dismissButton = { TextButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = cancelSetup }) { Text(stringResource(R.string.cancel)) } })
        }
        widgets.reconfigureWidgetId?.let {
            AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.widget_settings)) },
                text = { Text(stringResource(R.string.widget_settings_interrupted)) },
                confirmButton = { Button(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text(stringResource(R.string.resume)) } },
                dismissButton = { TextButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text(stringResource(R.string.cancel)) } })
        }
        }
    }
}

@Composable
private fun ExpandedWorkspace(
    nativePager: androidx.compose.foundation.pager.PagerState,
    motion: WorkspacePageMotion,
    firstHome: Int,
    visibleHomePages: Int,
    panelWidth: Dp,
    contentHeight: Dp,
    bottomSpace: Dp,
    geometry: HomeGeometry,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    libraryQuery: String,
    onLibraryQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onPinned: (String, Boolean) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val viewportWidth = motion.pageWidth
    val stride = motion.homeStride
    val initialHomeOrigin = with(density) { panelWidth.toPx() }
    val homePaneWidth = with(density) { (geometry.gridWidth + 16f).dp.toPx() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val visibleHomes by remember(nativePager, motion, firstHome, visibleHomePages, initialHomeOrigin, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            val intersectingHomes = (0 until visibleHomePages).filter { page ->
                val start = initialHomeOrigin + page * stride
                start + homePaneWidth > scroll && start < scroll + viewportWidth
            }
            val nearestLogicalPage = nativePager.currentPage - firstHome
            // While Discover is current, keep the initial Home pair cached. Otherwise Home 2
            // is recreated midway through the first native exit and provider inflation can
            // block the gesture frame even though that pane began offscreen.
            val retentionAnchor = nearestLogicalPage.coerceAtLeast(0)
            (intersectingHomes + (retentionAnchor - 1..retentionAnchor + 1))
                .filter { it in 0 until visibleHomePages }.distinct().sorted()
        }
    }
    val place: Modifier.(Float) -> Modifier = { x ->
        offset {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            IntOffset((x - motion.offset(physicalPosition)).roundToInt(), 0)
        }
    }
    val showDiscover by remember(nativePager, firstHome) {
        derivedStateOf(structuralEqualityPolicy()) {
            firstHome > 0 && nativePager.currentPage + nativePager.currentPageOffsetFraction <= firstHome + .25f
        }
    }
    val leadingX = initialHomeOrigin - stride
    val showLeading by remember(nativePager, motion, firstHome, panelWidth, leadingX, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            panelWidth.value > 0f && physicalPosition - firstHome < 1f &&
                leadingX - scroll + homePaneWidth > 0f
        }
    }
    val libraryPhysicalPage = firstHome + visibleHomePages
    val showLibrary by remember(nativePager, libraryPhysicalPage) {
        derivedStateOf(structuralEqualityPolicy()) {
            nativePager.currentPage + nativePager.currentPageOffsetFraction >= libraryPhysicalPage - 1.25f
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().testTag("expanded-workspace")) {
        if (showDiscover) {
            key("discover-pane") {
                Box(Modifier.place(-viewportWidth).fillMaxSize()) {
                    if (state.leftPage == LeftPage.RSS) RssPage(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace),
                        active = nativePager.currentPage < firstHome)
                    else DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
                }
            }
        }

        if (showLeading) {
            key("expanded-leading-home") {
                Box(Modifier.place(leadingX).width((geometry.gridWidth + 16f).dp).fillMaxHeight()
                    .testTag("expanded-leading-home")) {
                    HomePagePane(
                        -1, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                        widgets, drag, target, insertionTarget, showLargeWidget = true,
                        onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                        onFolder = onFolder, onEmptyWidget = onEmptyWidget, onRefresh = onRefresh,
                        modifier = Modifier,
                    )
                }
            }
        }

        visibleHomes.forEach { page ->
            key("expanded-home-$page") {
                stateHolder.SaveableStateProvider("expanded-home-$page") {
                    Box(Modifier.place(initialHomeOrigin + page * stride)
                        .width((geometry.gridWidth + 16f).dp).fillMaxHeight()) {
                        HomePagePane(
                            page, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                            widgets, drag, target, insertionTarget, showLargeWidget = page > 0,
                            onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                            onFolder = onFolder,
                            onEmptyWidget = onEmptyWidget,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }

        if (showLibrary) {
            key("library-pane") {
                Box(Modifier.place((visibleHomePages - 1) * stride + viewportWidth).fillMaxSize()) {
                    AppLibrary(state, libraryQuery, onLibraryQuery, onLaunch, onPinned,
                        onActions = onActions,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace)
                            .testTag("library-page"),
                        drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = onTurnOnWork)
                }
            }
        }
    }
}

/** The cell Home options add to when opened from wallpaper rather than a specific empty cell. */
private fun firstEmptyHomeCell(state: LauncherState, page: Int): Int {
    val pageStart = homeCellIndex(page, 0)
    return (pageStart until pageStart + HOME_CELLS).firstOrNull { index ->
        state.layout.slotAt(index) == null && state.layout.cellVisible(index) && state.widgetPlacements.none { index in it.coveredIndices() }
    } ?: pageStart
}

@Composable
private fun HomePagePane(
    page: Int,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    contentHeight: Dp,
    bottomSpace: Dp,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    showLargeWidget: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeScroll = rememberScrollState()
    var paneBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val backgroundTarget = firstEmptyHomeCell(state, page)
    val verticalEdge = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(drag.active, page, paneBounds) {
        while (drag.active) {
            val pointer = drag.pointer
            val amount = when {
                !paneBounds.contains(pointer) -> 0f
                pointer.y < paneBounds.top + verticalEdge && homeScroll.canScrollBackward -> -18f
                pointer.y > paneBounds.bottom - verticalEdge && homeScroll.canScrollForward -> 18f
                else -> 0f
            }
            if (amount != 0f) homeScroll.scrollBy(amount)
            delay(16)
        }
    }
    val homeOptions = stringResource(R.string.home_options)
    Box(modifier.testTag("home-page-$page")
        .semantics {
            onLongClick(homeOptions) {
                if (!drag.active) onEmptyWidget(backgroundTarget)
                !drag.active
            }
        }
        .onGloballyPositioned { paneBounds = it.boundsInRoot() }
        .width((geometry.gridWidth + 16f).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        Box(Modifier.width(16.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active) {
                detectTapGestures(onLongPress = {
                    if (!drag.active) onEmptyWidget(backgroundTarget)
                })
            })
        Column(Modifier.offset(x = 16.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = geometry.contentTop.dp, bottom = 8.dp)) {
            SharedHomeGrid(page, state.homeRows, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry, state.labels, widgets, drag, target,
                folders = state.folders, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
private fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(visualSize.coerceAtLeast(48.dp)).testTag(tag)) {
        Box(Modifier.size(visualSize).testTag("$tag-visual").background(Glass.copy(alpha = .22f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SharedHomeGrid(
    page: Int,
    rows: Int,
    savedSlots: List<String?>,
    savedLeadingSlots: List<String?>,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    widgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    labels: Boolean,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    folders: List<FolderEntry>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
) {
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    val pageStart = homeCellIndex(page, 0)
    val pageRange = pageStart until pageStart + HOME_CELLS
    fun savedAt(index: Int) = if (page == -1) savedLeadingSlots.getOrNull(homeCellLocal(index)) else savedSlots.getOrNull(index)
    fun previewAt(index: Int) = if (page == -1) previewLeadingSlots.getOrNull(homeCellLocal(index)) else previewSlots.getOrNull(index)
    fun savedIndexOf(id: String) = if (page == -1) savedLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else savedSlots.indexOf(id).takeIf { it >= 0 }
    fun previewIndexOf(id: String) = if (page == -1) previewLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else previewSlots.indexOf(id).takeIf { it >= 0 }
    val draggedId = drag.source?.appId
    val homeTarget = (target as? DropTarget.Home)?.index
    val source = drag.source?.target as? DropTarget.Home
    // Cell indices are negative on the unfolded-only page, so absence is null, never -1.
    val draggedPreviewIndex = draggedId?.let(::previewIndexOf)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        homeTarget != null -> draggedPreviewIndex
        source != null && target !is DropTarget.Dock -> draggedPreviewIndex
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val resources = LocalContext.current.resources
    val chooseDockApp = stringResource(R.string.dock_choose_app)
    val pending = widgets.pendingPlacement?.takeIf { it.page == page }
    val pendingIsReplacement = pending != null && widgetPlacements.any { it.slot == pending.slot }
    val pageWidgets = widgetPlacements.filter { it.page == page } + listOfNotNull(pending?.takeUnless { pendingIsReplacement })
    val renderedRows = maxOf(rows, pageWidgets.maxOfOrNull { it.row + it.spanY } ?: rows)
    val topPitch = (geometry.widgetHeight + 18f) / 2f
    fun rowTop(row: Int) = if (row <= 2) row * topPitch else geometry.widgetHeight + 18f + (row - 2) * rowHeight
    BoxWithConstraints(Modifier.fillMaxWidth().height(rowTop(renderedRows).dp)) {
        val density = LocalDensity.current
        val cellWidth = maxWidth / 4
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val rowHeightPx = with(density) { rowHeight.dp.toPx() }

        repeat(rows * GRID_COLUMNS) { localIndex ->
            val globalIndex = pageStart + localIndex
            val cell = DropTarget.Home(globalIndex)
            val savedId = savedAt(globalIndex)
            val savedApp = appsById[savedId]
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val merging = drag.active && drag.mergeArmed && drag.mergeIndex == globalIndex
            val highlighted = drag.active && target == cell && !merging
            val gap = hiddenIndex == globalIndex
            val row = localIndex / GRID_COLUMNS
            val cellHeight = rowTop(row + 1) - rowTop(row)
            Box(Modifier.offset(x = cellWidth * (localIndex % GRID_COLUMNS), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                .combinedClickable(onClick = { savedFolder?.let { onFolder(it.id) } },
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) })
                .background(if (highlighted) Glass.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(16.dp))
                .border(if (highlighted) 2.dp else 0.dp,
                    if (highlighted) Color.White.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.TopCenter) {
                // A folder-shaped backdrop grows behind the resting target to show that dropping groups them.
                if (merging) Box(Modifier.offset(y = (-iconSize * .1f).dp).size((iconSize * 1.2f).dp).testTag("folder-merge-$globalIndex")
                    .background(Glass.copy(alpha = .72f), RoundedCornerShape((iconSize * .3f).dp))
                    .border(2.dp, Color.White.copy(alpha = .85f), RoundedCornerShape((iconSize * .3f).dp)))
                if (drag.active && drag.source?.appId != null && (gap || previewId == null)) Box(
                    Modifier.size(iconSize.dp).testTag(if (gap) "drag-gap-home-$globalIndex" else "empty-home-slot-$globalIndex")
                        .background(Glass.copy(alpha = if (gap) .16f else .08f), RoundedCornerShape(18.dp))
                        .border(if (gap) 2.dp else 1.dp, Color.White.copy(alpha = if (gap) .55f else .3f), RoundedCornerShape(18.dp)))
            }
        }

        val ids = (if (page == -1) savedLeadingSlots + previewLeadingSlots
            else savedSlots.slicePage(pageRange) + previewSlots.slicePage(pageRange)).filterNotNull().distinct()
        ids.forEach { id ->
            val previewIndex = previewIndexOf(id)
            val renderIndex = previewIndex?.takeIf { it in pageRange } ?: savedIndexOf(id)?.takeIf { it in pageRange } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val localIndex = renderIndex - pageStart
                val row = localIndex / GRID_COLUMNS
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(((localIndex % GRID_COLUMNS) * cellWidthPx).roundToInt(), with(density) { rowTop(row).dp.toPx() }.roundToInt()),
                    label = "home insertion $id",
                )
                val visible = previewIndex != null && previewIndex in pageRange && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (dimDragged && id == draggedId) .28f else 1f,
                    label = "home insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(cellWidth).height(rowHeight.dp)
                    .alpha(opacity).testTag("home-app-$id"), contentAlignment = Alignment.TopCenter) {
                    if (visible) AppTile(app, iconSize, labels,
                        onClick = { onLaunch(app, it) }, onLongClick = { onActions(app) })
                }
            }
        }
        folders.forEach { folder ->
            val renderIndex = previewIndexOf(folder.id)?.takeIf { it in pageRange }
                ?: savedIndexOf(folder.id)?.takeIf { it in pageRange } ?: return@forEach
            val localIndex = renderIndex - pageStart
            val row = localIndex / GRID_COLUMNS
            val x = cellWidth * (localIndex % GRID_COLUMNS)
            val y = rowTop(row).dp
            FolderTile(folder, appsById, iconSize, labels, drag, page,
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp)
                    .testTag("home-folder-${folder.id}"), onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val x = cellWidth * placement.column + 5.dp
                val width = (cellWidth * placement.spanX - 10.dp).coerceAtLeast(1.dp)
                val y = rowTop(placement.row)
                val height = (rowTop(placement.row + placement.spanY) - y - 18f).coerceAtLeast(48f)
                val pendingDescription = stringResource(R.string.widget_pending, widgets.pendingProvider?.shortClassName ?: stringResource(R.string.widget))
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = pendingDescription
                    }, color = Glass.copy(alpha = .72f),
                    shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.widget_setup_title), color = Ink)
                    }
                } else MovableWidget(placement.id, placement.slot, widgets, drag, target,
                    Modifier.offset(x = x, y = y.dp).width(width).height(height.dp), page = page) { onWidget(placement.slot) }
            }
        }
    }
}

@Composable
private fun DockAppColumn(
    savedDock: List<String?>,
    previewDock: List<String?>,
    appsById: Map<String, AppEntry>,
    rowHeight: Float,
    iconSize: Float,
    drag: HomeDragState,
    target: DropTarget?,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onChoose: (Int) -> Unit,
) {
    val draggedId = drag.source?.appId
    val dockTarget = (target as? DropTarget.Dock)?.index
    val source = drag.source?.target as? DropTarget.Dock
    val draggedPreviewIndex = previewDock.indexOf(draggedId)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        dockTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Home -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val resources = LocalContext.current.resources
    val chooseDockApp = stringResource(R.string.dock_choose_app)
    val launchBounds = remember(savedDock.size) { List(savedDock.size) { android.graphics.Rect() } }
    val interactions = remember(savedDock.size) { List(savedDock.size) { MutableInteractionSource() } }
    val slotScales = savedDock.indices.map { index ->
        val pressed by interactions[index].collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "dock press $index")
        scale
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.dp.toPx() }
    Box(Modifier.fillMaxWidth().height((rowHeight * savedDock.size).dp)) {
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .background(if (highlighted) Color.White.copy(alpha = .3f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                when {
                    gap -> Box(Modifier.size(iconSize.dp).testTag("drag-gap-dock-$index")
                        .background(Glass.copy(alpha = .16f), RoundedCornerShape(14.dp))
                        .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(14.dp)))
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .testTag("dock-slot-$index").dropRegion(drag, cell, savedApp?.id)
                .semantics(mergeDescendants = true) { contentDescription = savedApp?.label ?: resources.getString(R.string.dock_choose_slot, index + 1) }
                .combinedClickable(interactionSource = interactions[index], indication = LocalIndication.current, role = Role.Button, onClick = {
                    if (savedApp != null) onLaunch(savedApp, launchBounds[index]) else onChoose(index)
                }, onLongClick = null)
                .semantics { onLongClick(chooseDockApp) { onChoose(index); true } })
        }

        val ids = (savedDock + previewDock).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedDock.indexOf(id)
            val previewIndex = previewDock.indexOf(id)
            val renderIndex = previewIndex.takeIf { it >= 0 } ?: savedIndex.takeIf { it >= 0 } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(0, (renderIndex * rowHeightPx).roundToInt()), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.fillMaxWidth().height(rowHeight.dp).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Image(app.icon.asImageBitmap(), null, Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                        .onGloballyPositioned { if (savedIndex >= 0) launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()) }
                        .graphicsLayer { scaleX = slotScales[renderIndex]; scaleY = slotScales[renderIndex] }
                        .clip(RoundedCornerShape(11.dp)))
                }
            }
        }
    }
}

private fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
private fun FolderTile(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float, labels: Boolean,
    drag: HomeDragState, page: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val description = androidx.compose.ui.res.pluralStringResource(R.plurals.folder_description, folder.appIds.size, folder.title, folder.appIds.size)
    Column(modifier.clickable(onClick = onClick).semantics(mergeDescendants = true) {
        contentDescription = description
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .24f).dp))
            .background(Glass.copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape((size * .24f).dp))
            .dropRegion(drag, DropTarget.Folder(folder.id), page = page, folderId = folder.id)
            .testTag("folder-drop-${folder.id}")) {
            folder.appIds.take(4).forEachIndexed { index, id ->
                apps[id]?.let { app ->
                    Image(app.icon.asImageBitmap(), null, Modifier.align(when (index) {
                        0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomStart; else -> Alignment.BottomEnd
                    }).padding(5.dp).size((size * .38f).dp).clip(RoundedCornerShape(6.dp)))
                }
            }
        }
        if (labels) Text(folder.title, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppTile(app: AppEntry, size: Float, labels: Boolean, modifier: Modifier = Modifier, onClick: (android.graphics.Rect) -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "app press")
    val iconSize by animateDpAsState(size.dp, label = "icon size")
    val bounds = remember { android.graphics.Rect() }
    val appOptions = stringResource(R.string.app_options)
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = app.label }
        .clickable(interactionSource = interaction, indication = LocalIndication.current,
            role = Role.Button, onClick = { onClick(bounds) })
        .semantics { onLongClick(appOptions) { onLongClick(); true } }.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon.asImageBitmap(), null, Modifier.size(iconSize).onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()) }
            .graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape((size * .24f).dp)))
        if (labels) Text(app.label, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), 3f)), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        color = Glass.copy(alpha = .24f), shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

@Composable
private fun currentTime(): LocalDateTime {
    val time by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    return time
}

@Composable
private fun ClockCard(onClick: () -> Unit) {
    val time = currentTime()
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    GlassCard(onClick = onClick) {
        Icon(Icons.Rounded.Schedule, stringResource(R.string.clock_widget_description), tint = Color.White, modifier = Modifier.size(20.dp))
        Text(time.format(DateTimeFormatter.ofPattern(format)), color = Color.White, fontWeight = FontWeight.Light, fontSize = 30.sp, maxLines = 1)
        Text(stringResource(R.string.local_time), color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
    }
}

@Composable
private fun DateCard(onClick: () -> Unit) {
    val date = currentTime()
    val locale = LocalConfiguration.current.locales[0]
    GlassCard(onClick = onClick) {
        Text(date.format(localizedDateFormatter(locale, "EEEE")), color = Color.White, fontSize = 12.sp, maxLines = 1)
        Text(date.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 42.sp)
        Text(date.format(localizedDateFormatter(locale, "LLLL")), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
    }
}

@Composable
private fun ExpandedCard(onClick: () -> Unit) {
    val date = currentTime()
    val locale = LocalConfiguration.current.locales[0]
    GlassCard(onClick = onClick) {
        Column {
            Text(date.format(localizedDateFormatter(locale, "EEEE")), color = Color.White, fontSize = 22.sp)
            Text(date.format(localizedDateFormatter(locale, "MMMMd")), color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
        }
        Column {
            Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.info_card_title), color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.info_card_detail), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onClick) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_widget)) }
        }
    }
}

@Composable
private fun WidgetSlot(id: Int, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit, fallback: @Composable () -> Unit) {
    var restoreMessage by remember(slot) { mutableStateOf<String?>(null) }
    val restoreUnavailable = stringResource(R.string.widget_restore_unavailable)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).testTag("widget-slot-$slot")) {
        val displayedContentSize = WidgetContentSize(maxWidth.value, maxHeight.value)
        if (id == NEEDS_BINDING_WIDGET) {
            val restore = controller.restoreDescriptor(slot)
            Surface(Modifier.fillMaxSize().testTag("widget-restore-$slot"), color = Glass.copy(alpha = .88f),
                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .7f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(restore?.title ?: stringResource(R.string.saved_widget), color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(restore?.profileLabel?.let { profileName(it) } ?: stringResource(R.string.unavailable_profile), color = Ink.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall)
                    restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row {
                        TextButton(onClick = {
                            if (!controller.rebindRestoredWidget(slot, contentSize = displayedContentSize))
                                restoreMessage = restoreUnavailable
                        },
                            modifier = Modifier.testTag("widget-restore-reconnect-$slot")) { Text(stringResource(R.string.reconnect)) }
                        TextButton(onClick = onAdd, modifier = Modifier.testTag("widget-restore-replace-$slot")) { Text(stringResource(R.string.replace)) }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val info = remember(id) { if (id >= 0) controller.manager.getAppWidgetInfo(id) else null }
        if (info == null) fallback()
        else {
            key(id) {
                AndroidView(factory = { context -> controller.host.createView(context, id, info) },
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun widgetLabel(id: Int, controller: WidgetController) = when (id) {
    CLOCK_WIDGET -> stringResource(R.string.widget_clock)
    DATE_WIDGET -> stringResource(R.string.widget_date)
    INFO_WIDGET -> stringResource(R.string.widget_panel)
    EMPTY_WIDGET -> stringResource(R.string.add_widget)
    else -> remember(id, controller) { controller.label(id) }
}

@Composable
private fun MovableWidget(id: Int, slot: Int, controller: WidgetController, drag: HomeDragState,
    target: DropTarget?, modifier: Modifier, page: Int, onAdd: () -> Unit) {
    val cell = DropTarget.Widget(slot)
    val moveOrReplace = stringResource(R.string.move_or_replace_widget)
    WidgetSlot(id, slot, controller, modifier.dropRegion(drag, cell, page = page, widgetId = id)
        .alpha(if (drag.source?.target == cell) .3f else 1f)
        .border(if (drag.active && target == cell) 2.dp else 0.dp,
            if (drag.active && target == cell) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
        .semantics { onLongClick(moveOrReplace) { onAdd(); true } }, onAdd) {
        when (id) {
            CLOCK_WIDGET -> ClockCard(onAdd)
            DATE_WIDGET -> DateCard(onAdd)
            INFO_WIDGET -> if (slot % 3 == 2) ExpandedCard(onAdd) else GlassCard(onClick = onAdd) {
                Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.your_widgets), color = Color.White, fontSize = 15.sp, maxLines = 1)
                Text(stringResource(R.string.tap_to_choose), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
            else -> Surface(Modifier.fillMaxSize().clickable(onClick = onAdd), color = Glass.copy(alpha = .18f),
                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .25f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                    Text(stringResource(if (id >= 0) R.string.widget_unavailable else R.string.add_widget), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp).imePadding()) {
        Text(if (dockSlot == null) stringResource(R.string.your_apps) else stringResource(R.string.dock_position_label, dockSlot + 1), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("search-field")
            .releasesDiscoverWhileTyping("app-picker-search"),
            placeholder = { Text(stringResource(R.string.search_apps)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) } }, shape = RoundedCornerShape(20.dp))
        if (dockSlot != null) TextButton(onClick = onClear) { Text(stringResource(R.string.dock_leave_empty)) }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp).testTag("dock-full-guidance"))
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) item { Text(stringResource(R.string.no_apps_found), Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Text(app.label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.Add, stringResource(R.string.choose_app, app.label))
                }
            }
        }
    }
}

@Composable
private fun WidgetActions(
    placement: WidgetPlacement,
    constraints: WidgetSpanConstraints?,
    canConfigure: Boolean,
    onConfigure: () -> Unit,
    isValid: (Int, Int) -> Boolean,
    onResize: (Int, Int) -> Unit,
    onStartResize: (Int, Int) -> Unit,
    onMoveToPage: (Int) -> Boolean,
    homePages: Int,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    rows: Int = DEFAULT_HOME_ROWS,
) {
    val sheetMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * .88f).toDp()
    }
    var width by remember(placement.slot, placement.spanX) { mutableIntStateOf(placement.spanX) }
    var height by remember(placement.slot, placement.spanY) { mutableIntStateOf(placement.spanY) }
    val minWidth = constraints?.minimum?.width ?: 2
    val minHeight = constraints?.minimum?.height ?: 2
    val maxWidth = minOf(GRID_COLUMNS - placement.column, constraints?.maximum?.width ?: GRID_COLUMNS)
    val maxHeight = minOf(rows - placement.row, constraints?.maximum?.height ?: rows)
    val feasible = placement.page >= -1 && placement.row in 0 until rows &&
        !(placement.id >= 0 && constraints == null) && minWidth <= maxWidth && minHeight <= maxHeight
    val valid = feasible && isValid(width, height)
    Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.widget_options), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close_widget_options)) }
        }
        if (canConfigure) ActionRow(Icons.Rounded.Settings, stringResource(R.string.widget_settings), onConfigure,
            Modifier.testTag("widget-settings-${placement.slot}"))
        Text(stringResource(R.string.resize), style = MaterialTheme.typography.titleMedium)
        Button(enabled = feasible, onClick = { onStartResize(width, height) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.resize_on_home)) }
        if (!feasible) Text(stringResource(R.string.widget_resize_outside), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            repeat(homePages) { page -> TextButton(onClick = { onMoveToPage(page) },
                modifier = Modifier.testTag("widget-move-${placement.slot}-page-$page")) { Text(stringResource(R.string.move_to_page, page + 1)) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.width), Modifier.weight(1f))
            IconButton(enabled = constraints?.canResizeHorizontally != false,
                onClick = { if (feasible) width = (width - 1).coerceAtLeast(minWidth) }) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.decrease_width))
            }
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.columns, width, width), Modifier.width(88.dp), textAlign = TextAlign.Center)
            IconButton(enabled = constraints?.canResizeHorizontally != false,
                onClick = { if (feasible) width = (width + 1).coerceAtMost(maxWidth) }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.increase_width))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.height), Modifier.weight(1f))
            IconButton(enabled = constraints?.canResizeVertically != false,
                onClick = { if (feasible) height = (height - 1).coerceAtLeast(minHeight) }) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.decrease_height))
            }
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.rows, height, height), Modifier.width(88.dp), textAlign = TextAlign.Center)
            IconButton(enabled = constraints?.canResizeVertically != false,
                onClick = { if (feasible) height = (height + 1).coerceAtMost(maxHeight) }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.increase_height))
            }
        }
        Text(stringResource(R.string.resize_overlap_note), style = MaterialTheme.typography.bodySmall)
        if (!valid) Text(stringResource(R.string.resize_invalid), color = MaterialTheme.colorScheme.error)
        Button(enabled = valid, onClick = { onResize(width, height); onClose() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.apply_size)) }
        ActionRow(Icons.Rounded.FindReplace, stringResource(R.string.replace), onReplace)
        HorizontalDivider()
        ActionRow(Icons.Rounded.DeleteOutline, stringResource(R.string.remove), onRemove, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
    }
}
