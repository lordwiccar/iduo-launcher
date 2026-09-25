package media.whitewhale.iduo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class CustomizationPage { OVERVIEW, WALLPAPER, HOME, GESTURES, LANGUAGE, BACKUP, HELP }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, maxRowsFit: Int, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperSettings: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = stringResource(when (page) {
        CustomizationPage.OVERVIEW -> R.string.customize_title
        CustomizationPage.WALLPAPER -> R.string.customize_wallpaper
        CustomizationPage.HOME -> R.string.customize_home
        CustomizationPage.GESTURES -> R.string.customize_gestures
        CustomizationPage.LANGUAGE -> R.string.customize_language
        CustomizationPage.BACKUP -> R.string.customize_backup
        CustomizationPage.HELP -> R.string.customize_help
    })
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page != CustomizationPage.OVERVIEW) IconButton(onClick = { onPage(CustomizationPage.OVERVIEW) },
                Modifier.testTag("customization-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.customize_close)) }
        }
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> {
                    if (!isDefaultHome) Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text(stringResource(R.string.set_as_home_app)) }
                    if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.undo_layout_change)) }
                    MiniHomePreview(backgrounds.previewBitmap, state, 176.dp)
                    CustomizationDestination(Icons.Rounded.Wallpaper, stringResource(R.string.customize_wallpaper),
                        stringResource(if (backgrounds.previewPending) R.string.customize_wallpaper_pending else R.string.customize_wallpaper_detail),
                        "customization-wallpaper") { onPage(CustomizationPage.WALLPAPER) }
                    CustomizationDestination(Icons.Rounded.GridView, stringResource(R.string.customize_home),
                        stringResource(R.string.customize_home_detail), "customization-home") { onPage(CustomizationPage.HOME) }
                    CustomizationDestination(Icons.Rounded.Search, stringResource(R.string.customize_gestures),
                        stringResource(R.string.customize_gestures_detail), "customization-gestures") { onPage(CustomizationPage.GESTURES) }
                    val languageTag = AppLanguage.current(LocalContext.current)
                    CustomizationDestination(Icons.Rounded.Language, stringResource(R.string.customize_language),
                        if (languageTag.isEmpty()) stringResource(R.string.language_system) else AppLanguage.nativeName(languageTag),
                        "customization-language") { onPage(CustomizationPage.LANGUAGE) }
                    CustomizationDestination(Icons.Rounded.Save, stringResource(R.string.customize_backup),
                        stringResource(R.string.customize_backup_detail), "customization-backup") { onPage(CustomizationPage.BACKUP) }
                    CustomizationDestination(Icons.Rounded.HelpOutline, stringResource(R.string.customize_help),
                        stringResource(R.string.customize_help_detail), "customization-help") {
                        onPage(CustomizationPage.HELP)
                    }
                    if (isDefaultHome) TextButton(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text(stringResource(R.string.change_home_app)) }
                }
                CustomizationPage.WALLPAPER -> {
                    MiniHomePreview(backgrounds.previewBitmap, state, 228.dp)
                    Text(stringResource(R.string.wallpaper), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wallpaper_detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-choose")) {
                        Text(stringResource(if (backgrounds.previewPending) R.string.choose_different_photo else R.string.choose_photo))
                    }
                    if (backgrounds.previewPending) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("background-preview-cancel"), enabled = !backgrounds.loading) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = backgrounds::requestPhotoApply, enabled = backgrounds.previewBitmap != null && !backgrounds.loading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("background-preview-apply")) { Text(stringResource(R.string.apply)) }
                    }
                    if (!backgrounds.previewPending) OutlinedButton(onClick = backgrounds::requestDunes, enabled = !backgrounds.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-dunes")) { Text(stringResource(R.string.use_iduo_dunes)) }
                    backgrounds.targetRequest?.let { WallpaperTargetDialog(backgrounds::applyTo, backgrounds::dismissTargetRequest) }
                    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                        TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(stringResource(R.string.android_wallpaper), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.android_wallpaper_detail),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onWallpaperSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("wallpaper-settings")) { Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_wallpaper_settings)) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
                }
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage, maxRowsFit,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget)
                CustomizationPage.GESTURES -> {
                    Text(stringResource(R.string.left_page), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.leftPage == LeftPage.DISCOVER, { model.setLeftPage(LeftPage.DISCOVER) },
                            label = { Text(stringResource(R.string.left_page_discover)) }, modifier = Modifier.testTag("left-page-discover"))
                        FilterChip(state.leftPage == LeftPage.RSS, { model.setLeftPage(LeftPage.RSS) },
                            label = { Text(stringResource(R.string.left_page_rss)) }, modifier = Modifier.testTag("left-page-rss"))
                    }
                    SettingsSwitch(stringResource(R.string.show_app_names), state.labels, model::setLabels, "label-switch")
                    SettingsSwitch(stringResource(R.string.show_status), state.verticalStatus, model::setVerticalStatus, "status-switch")
                    SettingsSwitch(stringResource(R.string.search_opens_google), state.googleSearch, model::setGoogleSearch, "google-search-switch")
                    Text(stringResource(R.string.search_local_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.gestures_note),
                        style = MaterialTheme.typography.bodyMedium)
                }
                CustomizationPage.LANGUAGE -> LanguageSettings()
                CustomizationPage.BACKUP -> {
                    Text(stringResource(R.string.backup_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text(stringResource(R.string.save)) }
                        Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text(stringResource(R.string.restore)) }
                    }
                    Text(stringResource(R.string.backup_restore_note), style = MaterialTheme.typography.bodySmall)
                }
                CustomizationPage.HELP -> LauncherHelp(
                    isDefaultHome = isDefaultHome,
                    onHomeSettings = onMakeDefault,
                    onAddWidget = { onAddWidget(homePage) },
                    onShadeSetup = onShadeSetup,
                )
            }
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    HelpSection(Icons.Rounded.Home, stringResource(R.string.help_home_title),
        stringResource(if (isDefaultHome) R.string.help_home_default else R.string.help_home_not_default))
    Button(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
        Text(stringResource(if (isDefaultHome) R.string.change_home_app else R.string.set_duo_as_home))
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.TouchApp, stringResource(R.string.help_customize_title), stringResource(R.string.help_customize))
    HelpSection(Icons.Rounded.Widgets, stringResource(R.string.widgets), stringResource(R.string.help_widgets))
    OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
        Text(stringResource(R.string.add_widget_to_page))
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.SwipeDown, stringResource(R.string.help_shade_title), stringResource(R.string.help_shade))
    TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
        Text(stringResource(R.string.help_shade_setup))
    }
    HelpSection(Icons.Rounded.Explore, stringResource(R.string.discover), stringResource(R.string.help_discover))
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

/** Asks where Android should show a wallpaper chosen in iDuo. */
@Composable private fun WallpaperTargetDialog(onTarget: (WallpaperTarget) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.wallpaper_target_title)) },
        text = {
            Column {
                listOf(WallpaperTarget.HOME to R.string.wallpaper_target_home, WallpaperTarget.LOCK to R.string.wallpaper_target_lock,
                    WallpaperTarget.BOTH to R.string.wallpaper_target_both).forEach { (target, label) ->
                    TextButton(onClick = { onTarget(target) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("wallpaper-target-${target.name.lowercase()}")) {
                        Text(stringResource(label), Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp) {
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview")) {
            WallpaperStandIn()
            stagedBitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(24f)).clip(RoundedCornerShape(unit(7f)))) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(22f)).clip(RoundedCornerShape(unit(7f)))) }
            }
        }
    }
}

/** Home rows on a wheel; layouts that cannot fit this screen stay visible but cannot be chosen. */
@Composable private fun GridLayoutDialog(current: Int, maxRowsFit: Int, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    val options = (DEFAULT_HOME_ROWS..GRID_ROWS).toList()
    var chosen by remember { mutableIntStateOf(current) }
    fun fits(rows: Int) = rows <= maxOf(DEFAULT_HOME_ROWS, maxRowsFit)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.grid_layout)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_rows_setting), style = MaterialTheme.typography.bodyMedium)
                WheelPicker(options.map { stringResource(R.string.home_rows_option, GRID_COLUMNS, it - 2) },
                    initial = options.indexOf(current).coerceAtLeast(0), onSelected = { chosen = options[it] },
                    enabled = { fits(options[it]) })
                if (!fits(chosen)) Text(stringResource(R.string.home_rows_no_room),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(chosen) }, enabled = fits(chosen),
            modifier = Modifier.testTag("grid-layout-apply")) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, maxRowsFit: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!wide, { onWide(false) }, label = { Text(stringResource(R.string.display_cover)) })
        FilterChip(wide, { onWide(true) }, label = { Text(stringResource(R.string.display_inner)) })
    }
    OutlinedButton(onClick = onEditPins, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.choose_home_apps)) }
    Text(stringResource(R.string.dock_apps_setting), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (MIN_DOCK_SLOTS..MAX_DOCK_SLOTS).forEach { count ->
            FilterChip(state.dock.size == count, { model.setDockSlots(count) }, label = { Text("$count") },
                modifier = Modifier.testTag("dock-slots-$count"))
        }
    }
    var choosingRows by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { choosingRows = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("grid-layout")) {
        Text(stringResource(R.string.grid_layout), Modifier.weight(1f))
        Text(stringResource(R.string.home_rows_option, GRID_COLUMNS, state.homeRows - 2), fontWeight = FontWeight.SemiBold)
    }
    if (choosingRows) GridLayoutDialog(state.homeRows, maxRowsFit, onDismiss = { choosingRows = false }) { rows ->
        model.setHomeRows(rows); choosingRows = false
    }
    CustomizationSlider(stringResource(R.string.folder_transparency),
        stringResource(R.string.value_percent, Math.round(state.folderTransparency * 100)),
        state.folderTransparency, 0f..MAX_FOLDER_TRANSPARENCY) { model.setFolderTransparency(it) }
    Text(stringResource(R.string.all_apps_view), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!state.libraryGrid, { model.setLibraryGrid(false) }, label = { Text(stringResource(R.string.all_apps_view_list)) },
            modifier = Modifier.testTag("library-view-list"))
        FilterChip(state.libraryGrid, { model.setLibraryGrid(true) }, label = { Text(stringResource(R.string.all_apps_view_grid)) },
            modifier = Modifier.testTag("library-view-grid"))
    }
    CustomizationSlider(stringResource(R.string.icon_size), stringResource(R.string.value_dp, p.iconSize.toInt()), p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider(stringResource(R.string.row_spacing), stringResource(R.string.value_dp, p.rowGap.toInt()), p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider(stringResource(R.string.dock_width), stringResource(R.string.value_dp, p.dockWidth.toInt()), p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    SettingsSwitch(stringResource(R.string.dock_align), p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider(stringResource(R.string.dock_height), stringResource(R.string.value_percent, (p.dockPosition * 100).toInt()), p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_layout)) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text(stringResource(R.string.widgets_on_page, homePage + 1), style = MaterialTheme.typography.titleMedium)
    val removeWidget = stringResource(R.string.remove_widget)
    val removeLeadingWidget = stringResource(R.string.remove_widget_leading)
    state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (placement.page == -1) stringResource(R.string.unfolded_only_page)
                else stringResource(R.string.widget_size_row, placement.spanX, placement.spanY, placement.row + 1), Modifier.weight(1f))
            IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) removeLeadingWidget else removeWidget }) { Icon(Icons.Rounded.DeleteOutline, null) }
            TextButton(onClick = { onWidget(placement.slot) }) { Text(stringResource(R.string.replace)) }
        }
    }
    TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.add_widget_to_page)) }
}

@Composable private fun LanguageSettings() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val system = remember { AppLanguage.systemLocale(context) }
    AppLanguage.tags.forEach { tag ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(tag == current, role = Role.RadioButton) { context.findActivity()?.let { AppLanguage.set(it, tag) } }
            .testTag("language-${tag.ifEmpty { "system" }}"), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(tag == current, null); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (tag.isEmpty()) stringResource(R.string.language_system) else AppLanguage.nativeName(tag))
                // Name the language "system" currently resolves to, in that language.
                if (tag.isEmpty()) Text(system.getDisplayLanguage(system).replaceFirstChar { it.titlecase(system) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChecked, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label }) }
}
