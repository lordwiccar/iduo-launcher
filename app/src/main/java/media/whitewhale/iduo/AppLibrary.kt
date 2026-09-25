@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package media.whitewhale.iduo

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val hasWork = state.profiles.any { it.isWork } || state.apps.any { it.isWork }
    var showWork by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val selectedProfile = if (showWork) state.profiles.firstOrNull { it.isWork } else state.profiles.firstOrNull { it.isPersonal }
    LaunchedEffect(showWork, selectedProfile?.available, selectedProfile?.quiet) {
        listState.scrollToItem(0)
    }
    val visibleApps = remember(state.apps, query, showWork, hasWork) {
        state.apps.filter { (!hasWork || it.isWork == showWork) && it.label.contains(query.trim(), true) }
    }
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (editing) R.string.pin_home_apps_title else R.string.all_apps), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(if (editing) pluralStringResource(R.plurals.pinned_count, pinned.size, pinned.size) else "${visibleApps.size}", color = ink, fontSize = 12.sp)
            }
            if (hasWork) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showWork, onClick = { showWork = false }, label = { Text(stringResource(R.string.profile_personal)) })
                FilterChip(selected = showWork, onClick = { showWork = true }, label = { Text(stringResource(R.string.profile_work)) })
            }
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(if (editing) "pin-search" else "library-search")
                .releasesDiscoverWhileTyping("library-search"),
                placeholder = { Text(stringResource(R.string.search_apps)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) } },
                colors = if (glass) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedContainerColor = Color.White.copy(alpha = .18f), unfocusedContainerColor = Color.White.copy(alpha = .12f),
                    focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                    focusedPlaceholderColor = ink, unfocusedPlaceholderColor = ink,
                    focusedLeadingIconColor = ink, unfocusedLeadingIconColor = ink,
                    focusedTrailingIconColor = ink, unfocusedTrailingIconColor = ink,
                ) else OutlinedTextFieldDefaults.colors())
            val workUnavailable = showWork && selectedProfile?.available == false
            if (state.libraryGrid && !editing && !workUnavailable && visibleApps.isNotEmpty()) {
                LibraryGrid(visibleApps, resetKey = query to showWork, drag, Modifier.weight(1f)) { app ->
                    val launchBounds = remember { android.graphics.Rect() }
                    Column(Modifier.fillMaxSize().testTag("library-app-${app.id}")
                        .then(libraryItemInput(app, drag, page, { onLaunchFrom(app, launchBounds) }, onActions))
                        .padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(app.icon.asImageBitmap(), null, Modifier.size(52.dp)
                            .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(13.dp)))
                        Text(app.label, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 14.sp)
                    }
                }
            } else LazyColumn(Modifier.weight(1f).testTag("all-apps-list"), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                if (showWork && selectedProfile?.available == false) item("work-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (selectedProfile.quiet) R.string.work_paused else R.string.work_unavailable))
                        if (selectedProfile.quiet) Button(onClick = { onTurnOnWork(selectedProfile.userSerial) },
                            Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text(stringResource(R.string.turn_on_work)) }
                    }
                }
                if (groups.isEmpty()) item { Text(stringResource(if (state.loading) R.string.loading_apps else R.string.no_apps_found), Modifier.padding(vertical = 20.dp)) }
                groups.forEach { (letter, entries) ->
                    stickyHeader(key = "heading-$letter") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // An opaque small chip prevents text from showing through the sticky letter.
                            Box(Modifier.size(width = 32.dp, height = 28.dp).background(
                                if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3))
                                else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(letter, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            if (glass) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
                        }
                    }
                    items(entries, key = { it.id }) { app ->
                        val isPinned = app.id in pinned
                        val launchBounds = remember { android.graphics.Rect() }
                        val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("library-app-${app.id}")
                            .then(libraryItemInput(app, drag, page, click, onActions))
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon.asImageBitmap(), null, Modifier.size(40.dp)
                                .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(10.dp)))
                            Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, fontSize = 14.sp)
                            if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.testTag("pin-${app.id}")) {
                                Icon(if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                    stringResource(if (isPinned) R.string.unpin_app else R.string.pin_app, app.label),
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tap to open; long press offers actions, or on Home starts dragging the app out of All apps. */
@Composable
private fun libraryItemInput(app: AppEntry, drag: HomeDragState?, page: Int?, onClick: () -> Unit,
    onActions: (AppEntry) -> Unit): Modifier {
    val appOptions = stringResource(R.string.app_options)
    val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
    return dragModifier.clip(RoundedCornerShape(14.dp))
        .then(if (drag == null) Modifier.combinedClickable(onClick = onClick, onLongClick = { onActions(app) })
            else Modifier.clickable(onClick = onClick).semantics { onLongClick(appOptions) { onActions(app); true } })
}

private val LibraryCellWidth = 84.dp
private val LibraryCellHeight = 100.dp

/** How many whole cells of [cell] fit in [space]; at least one. */
private fun cellsFitting(space: Dp, cell: Dp) = maxOf(1, (space / cell).toInt())

/**
 * All apps as a grid split into horizontal pages that fill the available space. Swiping past
 * the first page hands the gesture back to the Home pager.
 */
@Composable
private fun LibraryGrid(apps: List<AppEntry>, resetKey: Any, drag: HomeDragState?, modifier: Modifier,
    cell: @Composable (AppEntry) -> Unit) {
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    BoxWithConstraints(modifier.fillMaxWidth().testTag("all-apps-grid")
        .onGloballyPositioned { bounds = it.boundsInRoot() }) {
        val columns = cellsFitting(maxWidth, LibraryCellWidth)
        val rows = cellsFitting(maxHeight - 28.dp, LibraryCellHeight)
        val perPage = columns * rows
        val pages = (apps.size + perPage - 1) / perPage
        val pager = rememberPagerState { pages }
        LaunchedEffect(resetKey) { pager.scrollToPage(0) }
        if (drag != null) DisposableEffect(drag, pager) {
            // Swipes page the grid; at its first or last page they page Home instead.
            val region = ChildPagerRegion({ bounds }) { travel ->
                if (travel < 0f) pager.canScrollForward else pager.canScrollBackward
            }
            drag.childPager = region
            onDispose { if (drag.childPager === region) drag.childPager = null }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
                Column(Modifier.fillMaxSize()) {
                    apps.drop(index * perPage).take(perPage).chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(LibraryCellHeight)) {
                            row.forEach { app -> Box(Modifier.weight(1f).fillMaxHeight()) { cell(app) } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (pages > 1) PageDots(pages, pager.currentPage, Modifier.testTag("library-page-dots"))
        }
    }
}
