package media.whitewhale.iduo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sqrt

internal const val MAX_FOLDER_COLUMNS = 6
internal const val MAX_FOLDER_ROWS = 6
internal const val DEFAULT_FOLDER_TRANSPARENCY = .03f
internal const val MAX_FOLDER_TRANSPARENCY = .9f
internal val FOLDER_BACKDROP_BLUR = 6.dp
/** An opening folder starts at about its Home icon's size and grows to full size. */
private const val FOLDER_OPEN_START_SCALE = .25f

private val FolderCellWidth = 84.dp
private val FolderCellHeight = 104.dp
private val FolderColumnGap = 8.dp
private val FolderRowGap = 10.dp
private val FolderPadding = 18.dp
private val FolderMinWidth = 300.dp
// Title row with its text field, the grid's top margin, and the page dots.
private val FolderChromeHeight = 72.dp + 12.dp + 28.dp

/** How an open folder lays out its apps: pages of [columns] x [rows], [pages] of them. */
internal data class FolderGridShape(val columns: Int, val rows: Int, val pages: Int) {
    val perPage get() = columns * rows
}

/**
 * Sizes the grid to its apps, close to square but never taller than wide, within the space the
 * screen allows and at most 6 x 6. Apps beyond one full page continue on further pages.
 */
internal fun folderGridShape(count: Int, maxColumns: Int, maxRows: Int): FolderGridShape {
    val columnLimit = maxColumns.coerceIn(1, MAX_FOLDER_COLUMNS)
    val rowLimit = maxRows.coerceIn(1, MAX_FOLDER_ROWS)
    val apps = count.coerceAtLeast(1)
    val fullPage = columnLimit * rowLimit
    if (apps > fullPage) return FolderGridShape(columnLimit, rowLimit, (apps + fullPage - 1) / fullPage)
    var columns = minOf(columnLimit, ceil(sqrt(apps.toDouble())).toInt())
    if ((apps + columns - 1) / columns > rowLimit) columns = (apps + rowLimit - 1) / rowLimit
    return FolderGridShape(columns, (apps + columns - 1) / columns, 1)
}

private fun fitting(space: Dp, cell: Dp, gap: Dp) = ((space + gap) / (cell + gap)).toInt()

@Composable
internal fun FolderPanel(
    folder: FolderEntry, apps: Map<String, AppEntry>, drag: HomeDragState, page: Int,
    folderDestinations: List<Int>, onDismiss: () -> Unit,
    onRename: (String) -> Unit, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMoveFolder: (Int) -> Unit, onDisband: () -> Unit, transparency: Float = DEFAULT_FOLDER_TRANSPARENCY,
    /** Centre of the folder's Home icon in root coordinates; the panel grows out of it. */
    origin: Offset? = null,
) {
    val opening = remember(folder.id) { Animatable(0f) }
    LaunchedEffect(folder.id) { opening.animateTo(1f, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow)) }
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var title by rememberSaveable(folder.id) { mutableStateOf(folder.title) }
    var editing by rememberSaveable(folder.id) { mutableStateOf(false) }
    // A blank name keeps the current one; closing the folder while editing keeps what was typed.
    fun commitTitle() {
        if (title.isNotBlank() && title.trim() != folder.title) onRename(title.trim()) else title = folder.title
        editing = false
    }
    val dismiss = { if (editing) commitTitle(); onDismiss() }
    BackHandler { dismiss() }
    DisposableEffect(drag, folder.id) {
        drag.activeSourceScope = folder.id
        onDispose { if (drag.activeSourceScope == folder.id) drag.activeSourceScope = null }
    }
    val closeLabel = stringResource(R.string.close_folder)
    // The panel lives in the safe area, but the dim must also cover the wallpaper under the system bars.
    val bars = WindowInsets.safeDrawing
    val scrimLayout = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize().drawBehind {
        val left = bars.getLeft(this, scrimLayout).toFloat(); val top = bars.getTop(this).toFloat()
        drawRect(Color.Black.copy(alpha = .28f * opening.value.coerceIn(0f, 1f)), Offset(-left, -top),
            Size(size.width + left + bars.getRight(this, scrimLayout), size.height + top + bars.getBottom(this)))
    }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = closeLabel,
            onClick = dismiss,
        )
        .imePadding().testTag("folder-panel"),
        contentAlignment = Alignment.Center) {
      BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
        val shownApps = folder.appIds.filter { it in apps }
        val shape = folderGridShape(shownApps.size,
            fitting(maxWidth - FolderPadding * 2, FolderCellWidth, FolderColumnGap),
            fitting(maxHeight - FolderPadding * 2 - FolderChromeHeight, FolderCellHeight, FolderRowGap))
        val gridWidth = FolderCellWidth * shape.columns + FolderColumnGap * (shape.columns - 1)
        val gridHeight = FolderCellHeight * shape.rows + FolderRowGap * (shape.rows - 1)
        val pager = rememberPagerState { shape.pages }
        Surface(Modifier.width(maxOf(gridWidth + FolderPadding * 2, FolderMinWidth).coerceAtMost(maxWidth))
            // Measured before the layer below, so the bounds are the settled, unscaled panel.
            .onGloballyPositioned { panelBounds = it.boundsInRoot() }
            .graphicsLayer {
                val progress = opening.value
                val scale = FOLDER_OPEN_START_SCALE + (1f - FOLDER_OPEN_START_SCALE) * progress
                scaleX = scale; scaleY = scale
                alpha = (progress * 1.6f).coerceIn(0f, 1f)
                transformOrigin = origin?.takeIf { panelBounds.width > 0f && panelBounds.height > 0f }?.let {
                    TransformOrigin((it.x - panelBounds.left) / panelBounds.width, (it.y - panelBounds.top) / panelBounds.height)
                } ?: TransformOrigin.Center
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .testTag("folder-panel-content"),
            color = Glass.copy(alpha = 1f - transparency.coerceIn(0f, MAX_FOLDER_TRANSPARENCY)), shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .6f))) {
            Column(Modifier.padding(FolderPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val focus = remember { FocusRequester() }
                    OutlinedTextField(title, { title = it }, Modifier.weight(1f).focusRequester(focus).testTag("folder-name-field"),
                        singleLine = true, label = { Text(stringResource(R.string.folder_name)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitTitle() }))
                    IconButton(onClick = ::commitTitle, Modifier.testTag("folder-name-save")) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.folder_name_save))
                    }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Balances the pencil and menu on the right so the name stays centred.
                    Spacer(Modifier.width(84.dp))
                    Text(folder.title, Modifier.weight(1f).semantics { heading() }.testTag("folder-name"),
                        style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { title = folder.title; editing = true }, Modifier.size(36.dp).testTag("folder-rename")) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.rename_folder), Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
                    }
                    FolderMenu(folderDestinations, onMoveFolder, onDisband)
                }
                HorizontalPager(pager, Modifier.padding(top = 12.dp).size(gridWidth, gridHeight).testTag("folder-pages"),
                    pageSpacing = FolderPadding, key = { it }) { folderPage ->
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(FolderRowGap)) {
                        shownApps.drop(folderPage * shape.perPage).take(shape.perPage).chunked(shape.columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(FolderColumnGap)) {
                                row.forEach { appId -> key(appId) {
                                    FolderChild(apps.getValue(appId), folder.id, drag, page, onLaunch)
                                } }
                            }
                        }
                    }
                }
                if (shape.pages > 1) PageDots(shape.pages, pager.currentPage, Modifier.testTag("folder-page-dots"))
            }
        }
      }
    }
}

/** Actions for the whole folder; single apps move out by dragging them. */
@Composable
private fun FolderMenu(destinations: List<Int>, onMove: (Int) -> Unit, onDisband: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.testTag("folder-options")) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.folder_options))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            destinations.distinctBy(::homeCellPage).forEach { destination ->
                val destinationPage = homeCellPage(destination)
                DropdownMenuItem(text = { Text(if (destinationPage == -1) stringResource(R.string.move_to_leading_page)
                    else stringResource(R.string.move_to_page, destinationPage + 1)) },
                    onClick = { open = false; onMove(destination) },
                    modifier = Modifier.testTag("folder-move-page-$destinationPage"))
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.disband_folder)) },
                onClick = { open = false; onDisband() }, modifier = Modifier.testTag("folder-disband"))
        }
    }
}

/** Dots under a paged grid, the current page larger and darker. */
@Composable
internal fun PageDots(pages: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pages) { index ->
            Box(Modifier.size(if (index == current) 8.dp else 6.dp).background(
                Ink.copy(alpha = if (index == current) .9f else .35f), CircleShape))
        }
    }
}

@Composable
private fun FolderChild(app: AppEntry, folderId: String, drag: HomeDragState, page: Int,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit) {
    Column(Modifier.size(FolderCellWidth, FolderCellHeight).testTag("folder-child-${app.id}")
        .dropRegion(drag, DropTarget.Library(app.id), app.id, page, folderId = folderId, scope = folderId)
        .clip(RoundedCornerShape(18.dp)).clickable(enabled = app.available) { onLaunch(app, null) }
        .padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
        Text(app.label, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelMedium)
        if (app.isWork || !app.available) Text(if (app.available) profileName(app.profileLabel) else stringResource(R.string.profile_unavailable, profileName(app.profileLabel)),
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}
