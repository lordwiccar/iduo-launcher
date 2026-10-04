@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package media.whitewhale.iduo

import android.graphics.BitmapFactory
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

/** The settings destinations. Top-level pages are grouped on the overview; the rest sit under Help & information. */
internal enum class SettingsPage { OVERVIEW, APPEARANCE, HOME, DOCK, GESTURES, NEWS, SYSTEM, SUPPORT, HELP, ABOUT, CHANGELOG;
    /** Where Back and the header arrow lead. */
    val parent get() = if (this == HELP || this == ABOUT || this == CHANGELOG) SUPPORT else OVERVIEW
}

/** Colors of the settings screen, which sits on an opaque page rather than over Home. */
private class SettingsColors(val page: Color, val card: Color, val divider: Color, val selected: Color,
    val customize: Pair<Color, Color>, val control: Pair<Color, Color>, val system: Pair<Color, Color>,
    val alert: Color, val onAlert: Color)

private val LightSettings = SettingsColors(Color(0xFFEAF0F2), Color.White, Color(0xFFE3EAED), Color(0xFFD3E3EA),
    Color(0xFFDBE8EE) to Color(0xFF2E5566), Color(0xFFE4E2F3) to Color(0xFF4A3F8F), Color(0xFFE3EBE2) to Color(0xFF36613A),
    Color(0xFFFCEBDC), Color(0xFF8A3F06))
private val DarkSettings = SettingsColors(Color(0xFF10191D), Color(0xFF1B2A31), Color(0xFF26363E), Color(0xFF2A4450),
    Color(0xFF26414D) to Color(0xFF9BC5D7), Color(0xFF332F52) to Color(0xFFC4BCF2), Color(0xFF2B3D2C) to Color(0xFFA9D3A6),
    Color(0xFF3D2A1B), Color(0xFFF2B98A))
private val LocalSettingsColors = staticCompositionLocalOf { LightSettings }

private data class Category(val page: SettingsPage, val icon: ImageVector, val title: Int, val tag: String)

private val CATEGORY_GROUPS = listOf(
    R.string.settings_group_customize to listOf(
        Category(SettingsPage.APPEARANCE, Icons.Rounded.Wallpaper, R.string.customize_wallpaper, "customization-wallpaper"),
        Category(SettingsPage.HOME, Icons.Rounded.GridView, R.string.customize_home, "customization-home"),
        Category(SettingsPage.DOCK, Icons.Rounded.ViewSidebar, R.string.customize_dock, "customization-dock")),
    R.string.settings_group_control to listOf(
        Category(SettingsPage.GESTURES, Icons.Rounded.TouchApp, R.string.customize_gestures, "customization-gestures"),
        Category(SettingsPage.NEWS, Icons.Rounded.Newspaper, R.string.help_news_title, "customization-news")),
    R.string.settings_group_system to listOf(
        Category(SettingsPage.SYSTEM, Icons.Rounded.Language, R.string.customize_system, "customization-system"),
        Category(SettingsPage.SUPPORT, Icons.Rounded.HelpOutline, R.string.customize_support, "customization-support")),
)

private fun SettingsColors.accent(page: SettingsPage) = when (page) {
    SettingsPage.APPEARANCE, SettingsPage.HOME, SettingsPage.DOCK -> customize
    SettingsPage.GESTURES, SettingsPage.NEWS -> control
    else -> system
}

/** The launcher's settings: a full-screen page list, or a list beside the page on a wide screen. */
@Composable
internal fun SettingsScreen(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, maxRowsFit: Int, page: SettingsPage, onPage: (SettingsPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperSettings: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {}, homeGesturesOn: Boolean = false,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        val view = LocalView.current
        LaunchedEffect(dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                // The page slides itself; the window's own fade would only blur that motion.
                window.setWindowAnimations(0)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                }
            }
        }
        CompositionLocalProvider(LocalSettingsColors provides if (dark) DarkSettings else LightSettings,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            val colors = LocalSettingsColors.current
            val scope = rememberCoroutineScope()
            var wide by rememberSaveable { mutableStateOf(initiallyWide) }
            var query by rememberSaveable { mutableStateOf("") }
            // Settings rises from the bottom once; a recreated activity shows it in place.
            var entered by rememberSaveable { mutableStateOf(false) }
            val rise = remember { Animatable(if (entered) 0f else 1f) }
            LaunchedEffect(Unit) { rise.animateTo(0f, tween(340, easing = EmphasizedDecelerate)); entered = true }
            var closing by remember { mutableStateOf(false) }
            val close: () -> Unit = {
                if (!closing) { closing = true; scope.launch { rise.animateTo(1f, tween(260, easing = EmphasizedAccelerate)); onClose() } }
            }
            val back = remember { PredictiveBackState() }
            BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { translationY = rise.value * size.height }
                .background(colors.page).windowInsetsPadding(WindowInsets.safeDrawing).testTag("settings-screen")) {
                val twoPane = maxWidth >= 600.dp
                // On a wide screen the list stays visible, so its first page opens beside it.
                val shown = if (twoPane && page == SettingsPage.OVERVIEW) SettingsPage.APPEARANCE else page
                val backCloses = query.isEmpty() && (if (twoPane) shown.parent == SettingsPage.OVERVIEW else page == SettingsPage.OVERVIEW)
                SettingsBackHandler(back, preview = query.isEmpty()) {
                    if (query.isNotEmpty() && (page == SettingsPage.OVERVIEW || twoPane)) query = ""
                    else if (shown.parent != SettingsPage.OVERVIEW) onPage(shown.parent)
                    else if (!twoPane && page != SettingsPage.OVERVIEW) onPage(SettingsPage.OVERVIEW)
                    else close()
                }
                val overview: @Composable (Modifier, SettingsPage?) -> Unit = { modifier, selected ->
                    SettingsOverview(state, isDefaultHome, homeGesturesOn, query, { query = it }, selected, onPage,
                        close, onMakeDefault, onShadeSetup, model, appearance, modifier)
                }
                val detail: @Composable (SettingsPage, Modifier) -> Unit = { target, modifier ->
                    SettingsDetail(target, twoPane, onBack = { onPage(target.parent) }, modifier = modifier,
                        pinned = if (target == SettingsPage.HOME || target == SettingsPage.DOCK) ({
                            DisplayHeader(state, wide, { wide = it }, backgrounds.previewBitmap)
                        }) else null) {
                        when (target) {
                            SettingsPage.OVERVIEW -> Unit
                            SettingsPage.APPEARANCE -> AppearancePage(model, state, appearance, onAppearanceMode, onAppearanceManual,
                                onAppearanceDeviceLocation, onAppearanceClear, backgrounds, onWallpaperSettings)
                            SettingsPage.HOME -> HomePage(state, wide, initiallyWide, model, homePage, maxRowsFit, onEditPins, onWidget, onAddWidget, onRemoveWidget)
                            SettingsPage.DOCK -> DockPage(state, wide, model)
                            SettingsPage.GESTURES -> GesturesPage(state, model, homeGesturesOn, onShadeSetup)
                            SettingsPage.NEWS -> NewsSettingsPage(state, model)
                            SettingsPage.SYSTEM -> SystemPage(isDefaultHome, onMakeDefault, onExportLayout, onImportLayout)
                            SettingsPage.SUPPORT -> SupportPage(onPage)
                            SettingsPage.HELP -> LauncherHelp(isDefaultHome, onMakeDefault, { onAddWidget(homePage) }, onShadeSetup, onPage)
                            SettingsPage.ABOUT -> AboutPage()
                            SettingsPage.CHANGELOG -> ChangelogPage()
                        }
                    }
                }
                // A back swipe previews its result: what would leave shrinks toward the swipe.
                val whole = if (backCloses) Modifier.predictiveBack(back) else Modifier
                val pane = if (backCloses) Modifier else Modifier.predictiveBack(back)
                if (twoPane) Row(Modifier.fillMaxSize().then(whole)) {
                    overview(Modifier.width(300.dp).fillMaxHeight(), shown.let { if (it.parent == SettingsPage.SUPPORT) SettingsPage.SUPPORT else it })
                    VerticalDivider(color = colors.divider)
                    // Beside a fixed list, a changed page only cross-fades.
                    AnimatedContent(shown, Modifier.weight(1f).fillMaxHeight().then(pane), label = "settings pane",
                        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) }) { target ->
                        detail(target, Modifier.fillMaxSize().background(colors.page))
                    }
                } else AnimatedContent(page, Modifier.fillMaxSize().then(whole).then(pane), label = "settings page",
                    transitionSpec = {
                        // Deeper pages enter from the right; going back sends them out to the right.
                        if (targetState.depth > initialState.depth)
                            slideInHorizontally(tween(300, easing = EmphasizedDecelerate)) { it } togetherWith
                                (slideOutHorizontally(tween(300, easing = EmphasizedDecelerate)) { -it / 4 } + fadeOut(tween(300)))
                        else (slideInHorizontally(tween(300, easing = EmphasizedDecelerate)) { -it / 4 } + fadeIn(tween(300))) togetherWith
                            slideOutHorizontally(tween(300, easing = EmphasizedDecelerate)) { it }
                    }) { target ->
                    // Opaque, so a page sliding over another never shows the one beneath.
                    if (target == SettingsPage.OVERVIEW) overview(Modifier.fillMaxSize().background(colors.page), null)
                    else detail(target, Modifier.fillMaxSize().background(colors.page))
                }
            }
        }
    }
}

private val EmphasizedDecelerate = CubicBezierEasing(.05f, .7f, .1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(.3f, 0f, .8f, .15f)

private val SettingsPage.depth get() = when {
    this == SettingsPage.OVERVIEW -> 0
    parent == SettingsPage.OVERVIEW -> 1
    else -> 2
}

/** How far a system back swipe has gone, and from which edge. */
private class PredictiveBackState {
    val progress = Animatable(0f)
    var fromLeft by mutableStateOf(true)
}

/** Shrinks the content toward the swipe as Android's predictive back gesture moves. */
private fun Modifier.predictiveBack(state: PredictiveBackState) = graphicsLayer {
    val p = state.progress.value
    if (p > 0f) {
        val scale = 1f - .1f * p
        scaleX = scale; scaleY = scale
        translationX = (if (state.fromLeft) 1f else -1f) * 32.dp.toPx() * p
        shape = RoundedCornerShape(28.dp * p); clip = true
    }
}

/**
 * Back inside the settings window, including the predictive back gesture on Android 14 and later.
 * [preview] turns the swipe preview off where Back only clears the search.
 */
@Composable
private fun SettingsBackHandler(state: PredictiveBackState, preview: Boolean, onBack: () -> Unit) {
    val localView = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentPreview by rememberUpdatedState(preview)
    val dispatcherOwner = remember(localView) {
        (localView.parent as? DialogWindowProvider)?.window?.decorView?.findViewTreeOnBackPressedDispatcherOwner()
    }
    DisposableEffect(dispatcherOwner, lifecycleOwner) {
        val callback = object : OnBackPressedCallback(dispatcherOwner != null) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                state.fromLeft = backEvent.swipeEdge == BackEventCompat.EDGE_LEFT
            }
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (currentPreview) scope.launch { state.progress.snapTo(backEvent.progress) }
            }
            override fun handleOnBackCancelled() { scope.launch { state.progress.animateTo(0f, tween(200)) } }
            override fun handleOnBackPressed() {
                currentOnBack()
                scope.launch { state.progress.animateTo(0f, tween(300, easing = EmphasizedDecelerate)) }
            }
        }
        dispatcherOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}

/** A settings item that search can find, and the page that holds it. */
private data class SettingsEntry(val title: String, val page: SettingsPage)

@Composable
private fun SettingsOverview(state: LauncherState, isDefaultHome: Boolean, homeGesturesOn: Boolean,
    query: String, onQuery: (String) -> Unit, selected: SettingsPage?, onPage: (SettingsPage) -> Unit,
    onClose: () -> Unit, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit, model: LauncherModel,
    appearance: AppearanceState, modifier: Modifier) {
    val colors = LocalSettingsColors.current
    val compact = selected != null
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_title), Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            FilledTonalIconButton(onClick = onClose, Modifier.size(44.dp).testTag("settings-close")) {
                Icon(Icons.Rounded.Close, stringResource(R.string.customize_close))
            }
        }
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().testTag("settings-search"), singleLine = true,
            placeholder = { Text(stringResource(R.string.settings_search)) }, shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) } },
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.card, unfocusedContainerColor = colors.card,
                unfocusedBorderColor = Color.Transparent))
        if (query.isNotBlank()) {
            val entries = settingsEntries()
            val matches = remember(entries, query) { rankedMatches(entries, query, 30) { it.title } }
            if (matches.isEmpty()) Text(stringResource(R.string.settings_no_results), Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else SettingsGroup {
                matches.forEachIndexed { index, entry ->
                    if (index > 0) GroupDivider()
                    ValueRow(entry.title, stringResource(pageTitle(entry.page)), onClick = { onQuery(""); onPage(entry.page) })
                }
            }
            return@Column
        }
        if (!isDefaultHome) AlertCard(stringResource(R.string.settings_not_default_title),
            stringResource(R.string.help_home_not_default), stringResource(R.string.set_as_home_app),
            "default-home-settings", onMakeDefault)
        if (!homeGesturesOn && (state.swipeDownShade || state.doubleTapLock)) AlertCard(
            stringResource(R.string.settings_gestures_off_title), stringResource(R.string.settings_gestures_off_detail),
            stringResource(R.string.settings_turn_on), "settings-gestures-alert", onShadeSetup)
        if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
            Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.undo_layout_change)) }
        val summaries = categorySummaries(state, appearance, homeGesturesOn)
        CATEGORY_GROUPS.forEach { (caption, categories) ->
            SectionCaption(stringResource(caption))
            SettingsGroup {
                categories.forEachIndexed { index, category ->
                    if (index > 0) GroupDivider()
                    CategoryRow(category, if (compact) null else summaries[category.page], category.page == selected) { onPage(category.page) }
                }
            }
        }
    }
}

@Composable
private fun categorySummaries(state: LauncherState, appearance: AppearanceState, homeGesturesOn: Boolean): Map<SettingsPage, String> {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(Unit) { GoogleNewsSettings.load(context); NewsFeeds.custom.load(context) }
    val preset = state.compact
    val gestures = listOfNotNull(
        stringResource(R.string.gesture_down).takeIf { state.swipeDownShade },
        stringResource(R.string.gesture_double).takeIf { state.doubleTapLock },
        stringResource(R.string.gesture_up).takeIf { state.swipeUpSearch })
    val languageTag = AppLanguage.current(context)
    return mapOf(
        SettingsPage.APPEARANCE to stringResource(R.string.settings_color_mode_value, stringResource(appearanceLabel(appearance.mode))),
        SettingsPage.HOME to stringResource(R.string.settings_home_summary, state.homeColumns, state.homeRows - 2, preset.iconSize.toInt(),
            state.widgetPlacements.size),
        SettingsPage.DOCK to stringResource(when (state.dockMode) {
            DockMode.SHOWN -> R.string.dock_mode_shown
            DockMode.SLIDE -> R.string.dock_mode_slide
            DockMode.HIDDEN -> R.string.dock_mode_hidden
        }) + " · " + stringResource(R.string.settings_dock_summary, state.dock.size),
        SettingsPage.GESTURES to if (!homeGesturesOn && (state.swipeDownShade || state.doubleTapLock))
            stringResource(R.string.settings_gestures_off_title)
            else gestures.joinToString(" · ").ifEmpty { stringResource(R.string.settings_gestures_none) },
        SettingsPage.NEWS to if (state.leftPage == LeftPage.GOOGLE_NEWS)
            "Google News · " + GoogleNewsSettings.edition.label(locale)
            else stringResource(R.string.settings_rss_summary, NewsFeeds.custom.sources.size),
        SettingsPage.SYSTEM to if (languageTag.isEmpty()) stringResource(R.string.language_system) else AppLanguage.nativeName(languageTag),
        SettingsPage.SUPPORT to stringResource(R.string.settings_support_summary),
    )
}

private fun appearanceLabel(mode: AppearanceMode) = when (mode) {
    AppearanceMode.LIGHT -> R.string.appearance_light
    AppearanceMode.DARK -> R.string.appearance_dark
    AppearanceMode.SYSTEM -> R.string.appearance_system_short
    AppearanceMode.SUNRISE_SUNSET -> R.string.appearance_sun_short
}

private fun pageTitle(page: SettingsPage) = when (page) {
    SettingsPage.OVERVIEW -> R.string.settings_title
    SettingsPage.APPEARANCE -> R.string.customize_wallpaper
    SettingsPage.HOME -> R.string.customize_home
    SettingsPage.DOCK -> R.string.customize_dock
    SettingsPage.GESTURES -> R.string.customize_gestures
    SettingsPage.NEWS -> R.string.help_news_title
    SettingsPage.SYSTEM -> R.string.customize_system
    SettingsPage.SUPPORT -> R.string.customize_support
    SettingsPage.HELP -> R.string.customize_help
    SettingsPage.ABOUT -> R.string.customize_about
    SettingsPage.CHANGELOG -> R.string.customize_changelog
}

@Composable
private fun settingsEntries(): List<SettingsEntry> {
    fun entries(page: SettingsPage, vararg titles: Int) = titles.map { page to it }
    val all = entries(SettingsPage.APPEARANCE, R.string.wallpaper, R.string.use_iduo_dunes, R.string.choose_photo,
            R.string.android_wallpaper, R.string.settings_color_mode, R.string.appearance_dark, R.string.appearance_sun,
            R.string.folder_transparency, R.string.settings_icon_pack) +
        entries(SettingsPage.HOME, R.string.grid_layout, R.string.icon_size, R.string.row_spacing, R.string.show_app_names,
            R.string.choose_home_apps, R.string.widgets, R.string.show_status, R.string.all_apps_view, R.string.reset_layout,
            R.string.settings_cover_rotation, R.string.all_apps_view_home, R.string.all_apps_view_folders, R.string.settings_display_layout,
            R.string.display_layout_separate, R.string.cover_columns) +
        entries(SettingsPage.DOCK, R.string.settings_dock_mode, R.string.dock_mode_slide, R.string.settings_dock_count, R.string.dock_width, R.string.dock_align, R.string.dock_height) +
        entries(SettingsPage.GESTURES, R.string.gesture_down, R.string.gesture_double, R.string.gesture_up,
            R.string.gesture_side, R.string.settings_search_button, R.string.settings_service_title) +
        entries(SettingsPage.NEWS, R.string.left_page, R.string.news_edition, R.string.news_topics, R.string.rss_sources) +
        entries(SettingsPage.SYSTEM, R.string.customize_language, R.string.backup_title, R.string.help_home_title) +
        entries(SettingsPage.SUPPORT, R.string.customize_help, R.string.customize_about, R.string.customize_changelog,
            R.string.customize_feedback)
    return all.map { (page, title) -> SettingsEntry(stringResource(title), page) }
}

@Composable
private fun SettingsDetail(page: SettingsPage, twoPane: Boolean, onBack: () -> Unit, modifier: Modifier,
    pinned: (@Composable () -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!twoPane || page.parent != SettingsPage.OVERVIEW) IconButton(onClick = onBack, Modifier.testTag("customization-back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            } else Spacer(Modifier.width(12.dp))
            Text(stringResource(pageTitle(page)), Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
        }
        pinned?.let { Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) { it() } }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

// ---- Building blocks ----

@Composable
private fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = LocalSettingsColors.current.card,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(content = content)
    }
}

@Composable private fun GroupDivider() = HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = LocalSettingsColors.current.divider)

@Composable
private fun SectionCaption(text: String) {
    Text(text.uppercase(LocalConfiguration.current.locales[0]), Modifier.padding(start = 8.dp, top = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CategoryRow(category: Category, summary: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    val (tint, onTint) = colors.accent(category.page)
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(if (selected) colors.selected else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag(category.tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(category.icon, null, Modifier.size(22.dp), tint = onTint)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (summary != null) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlertCard(title: String, detail: String, action: String, tag: String, onAction: () -> Unit) {
    val colors = LocalSettingsColors.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = colors.alert,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = colors.onAlert)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onAction, Modifier.heightIn(min = 44.dp).testTag(tag),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.onAlert, contentColor = colors.alert)) { Text(action) }
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, detail: String? = null, value: String? = null, tag: String? = null,
    chevron: Boolean = value == null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null,
    badge: String? = null, icon: ImageVector? = null) {
    val colors = LocalSettingsColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, role = Role.Switch, onValueChange = onChecked)
        .padding(horizontal = 18.dp, vertical = 10.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        icon?.let {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.control.first), contentAlignment = Alignment.Center) {
                Icon(it, null, Modifier.size(22.dp), tint = colors.control.second)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            badge?.let { Text(it, Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(colors.alert)
                .padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = colors.onAlert) }
        }
        Switch(checked, null)
    }
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 4.dp)) {
        Row { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        Slider(current, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = title })
    }
}

/** One choice out of a few, as connected buttons. */
@Composable
private fun Choice(options: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, tag) ->
            SegmentedButton(index == selected, { onSelect(index) }, SegmentedButtonDefaults.itemShape(index, options.size),
                Modifier.testTag(tag), icon = {}) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun GroupBody(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable private fun GroupTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable private fun Detail(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ---- Pages ----

@Composable
private fun AppearancePage(model: LauncherModel, state: LauncherState, appearance: AppearanceState,
    onMode: (AppearanceMode) -> Unit, onManual: (String, Double, Double) -> Unit, onDeviceLocation: () -> Unit,
    onClear: () -> Unit, backgrounds: LauncherBackgroundController, onWallpaperSettings: () -> Unit) {
    val context = LocalContext.current
    val dunes = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.default_wallpaper,
            BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
    }
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.wallpaper))
            Detail(stringResource(R.string.wallpaper_detail))
            // A photo can be moved and zoomed for each screen, in its preview and once it is set.
            var editingCrop by rememberSaveable { mutableStateOf(false) }
            var editingPreviewCrop by rememberSaveable { mutableStateOf(false) }
            if (backgrounds.previewPending) {
                MiniHomePreview(backgrounds.previewBitmap, state, state.layout, state.compact, 200.dp,
                    onClick = { if (backgrounds.previewBitmap != null) editingPreviewCrop = true })
                OutlinedButton(onClick = { editingPreviewCrop = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("wallpaper-preview-crop"), enabled = backgrounds.previewBitmap != null && !backgrounds.loading) {
                    Text(stringResource(R.string.wallpaper_crop))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("background-preview-cancel"), enabled = !backgrounds.loading) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = backgrounds::requestPhotoApply, enabled = backgrounds.previewBitmap != null && !backgrounds.loading,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("background-preview-apply")) { Text(stringResource(R.string.apply)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WallpaperTile(stringResource(R.string.settings_tile_dunes), "background-dunes", !backgrounds.loading,
                    backgrounds::requestDunes, Modifier.weight(1f)) {
                    dunes?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                WallpaperTile(stringResource(R.string.settings_tile_photo), "background-choose", !backgrounds.loading,
                    backgrounds::choosePhoto, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                }
                WallpaperTile(stringResource(R.string.settings_tile_android), "wallpaper-settings", true,
                    onWallpaperSettings, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Wallpaper, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (HomeWallpaper.isPhoto && backgrounds.photoSelected && !backgrounds.previewPending) {
                OutlinedButton(onClick = { editingCrop = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wallpaper-crop")) {
                    Text(stringResource(R.string.wallpaper_crop))
                }
                Detail(stringResource(R.string.wallpaper_crop_detail))
            }
            if (editingCrop) WallpaperCropEditor(onDismiss = { editingCrop = false })
            val previewImage = remember(backgrounds.previewBitmap) { backgrounds.previewBitmap?.asImageBitmap() }
            if (editingPreviewCrop && previewImage != null) {
                val cover = onCoverScreen()
                WallpaperCropEditor(previewImage, HomeWallpaper.pendingCrop(cover), cover,
                    { HomeWallpaper.setPendingCrop(cover, it) }, onDismiss = { editingPreviewCrop = false })
            }
            if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
            (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
            }
        }
    }
    backgrounds.targetRequest?.let { WallpaperTargetDialog(backgrounds::applyTo, backgrounds::dismissTargetRequest) }
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.settings_color_mode))
            val modes = AppearanceMode.entries
            Choice(modes.map { stringResource(appearanceLabel(it)) to "appearance-${it.name.lowercase()}" },
                modes.indexOf(appearance.mode), { onMode(modes[it]) })
            Detail(stringResource(R.string.settings_sun_detail))
            if (appearance.mode == AppearanceMode.SUNRISE_SUNSET) SunLocationSettings(appearance, onManual, onDeviceLocation, onClear)
        }
    }
    IconPackSettings(state.iconPack, model::setIconPack)
    SettingsGroup {
        SliderRow(stringResource(R.string.folder_transparency),
            stringResource(R.string.value_percent, Math.round(state.folderTransparency * 100)),
            state.folderTransparency, 0f..MAX_FOLDER_TRANSPARENCY) { model.setFolderTransparency(it) }
    }
}

@Composable
private fun WallpaperTile(label: String, tag: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier,
    thumb: @Composable BoxScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick)
        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f)).padding(8.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center, content = thumb)
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2)
    }
}

/** The chosen icon pack, a list of installed ones, and a way to find more on Google Play. */
@Composable
private fun IconPackSettings(current: String?, onChoose: (String?) -> Unit) {
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    // Packs can be installed while Settings is open, so the list is read again when it opens.
    val packs by produceState(emptyList<IconPackInfo>(), choosing) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconPacks.installed(context) }
    }
    val chosen = packs.firstOrNull { it.packageName == current }
    SettingsGroup {
        ValueRow(stringResource(R.string.settings_icon_pack),
            chosen?.label ?: stringResource(if (current == null) R.string.settings_icon_pack_system else R.string.settings_icon_pack_missing),
            tag = "icon-pack") { choosing = true }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.settings_icon_pack)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Detail(stringResource(R.string.settings_icon_pack_detail))
                Spacer(Modifier.height(8.dp))
                IconPackOption(null, stringResource(R.string.settings_icon_pack_system), current == null) { onChoose(null); choosing = false }
                packs.forEach { pack ->
                    IconPackOption(pack.icon, pack.label, pack.packageName == current, pack.packageName) {
                        onChoose(pack.packageName); choosing = false }
                }
                if (packs.isEmpty()) Detail(stringResource(R.string.settings_icon_pack_none))
                TextButton(onClick = {
                    val search = "icon pack"
                    val market = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://search?q=" + android.net.Uri.encode(search) + "&c=apps"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (runCatching { context.startActivity(market) }.isFailure)
                        context.openLink("https://play.google.com/store/search?q=" + android.net.Uri.encode(search) + "&c=apps")
                }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("icon-pack-store")) {
                    Text(stringResource(R.string.settings_icon_pack_get))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun IconPackOption(icon: android.graphics.drawable.Drawable?, label: String, selected: Boolean, tag: String? = null, onClick: () -> Unit) {
    val bitmap = remember(icon) { icon?.let { launcherIcon(it).asImageBitmap() } }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick).testTag("icon-pack-${tag ?: "system"}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, null)
        if (bitmap != null) Image(bitmap, null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
        Text(label, Modifier.weight(1f))
    }
}

/** A live Home preview beside the choice of which screen the layout sliders change. */
@Composable
private fun DisplayHeader(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit, staged: android.graphics.Bitmap?) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniHomePreview(staged, state, state.layoutFor(cover = !wide), if (wide) state.expanded else state.compact, 176.dp,
            dock = wide || state.dockMode != DockMode.HIDDEN)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Detail(stringResource(R.string.settings_applies_to))
            listOf(false to R.string.settings_display_cover, true to R.string.settings_display_inner).forEach { (value, label) ->
                val selected = wide == value
                Surface(onClick = { onWide(value) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag(if (value) "display-inner" else "display-cover"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else LocalSettingsColors.current.card,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(label), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(state: LauncherState, wide: Boolean, onWideScreen: Boolean, model: LauncherModel, homePage: Int, maxRowsFit: Int,
    onEditPins: () -> Unit, onWidget: (Int) -> Unit, onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    var choosingRows by rememberSaveable { mutableStateOf(false) }
    var confirmMirror by rememberSaveable { mutableStateOf(false) }
    // The settings may describe the other screen than the one in use; its layout may differ.
    val shown = state.layoutFor(cover = !wide)
    val otherScreen = state.separateCover && wide != onWideScreen
    if (confirmMirror) AlertDialog(onDismissRequest = { confirmMirror = false },
        title = { Text(stringResource(R.string.display_layout_mirror_confirm_title)) },
        text = { Text(stringResource(R.string.display_layout_mirror_confirm)) },
        confirmButton = { TextButton(onClick = { confirmMirror = false; model.setSeparateCover(false) },
            Modifier.testTag("display-layout-mirror-confirm")) { Text(stringResource(R.string.display_layout_mirror)) } },
        dismissButton = { TextButton(onClick = { confirmMirror = false }) { Text(stringResource(R.string.cancel)) } })
    SectionCaption(stringResource(R.string.settings_section_grid))
    SettingsGroup {
        ValueRow(stringResource(R.string.grid_layout), stringResource(R.string.settings_grid_detail),
            stringResource(R.string.settings_grid_value, shown.columns, shown.rows - 2), tag = "grid-layout") { choosingRows = true }
        if (!wide && state.separateCover) {
            GroupDivider()
            GroupBody {
                GroupTitle(stringResource(R.string.cover_columns))
                val canFive = state.dockMode != DockMode.SHOWN
                if (canFive) Choice(listOf("4" to "cover-columns-4", "5" to "cover-columns-5"), shown.columns - DEFAULT_HOME_COLUMNS,
                    { model.setCoverColumns(DEFAULT_HOME_COLUMNS + it) })
                Detail(stringResource(if (canFive) R.string.cover_columns_detail else R.string.cover_columns_locked))
            }
        }
        GroupDivider()
        SliderRow(stringResource(R.string.icon_size), stringResource(R.string.value_dp, p.iconSize.toInt()), p.iconSize, 40f..68f) {
            model.setPreset(wide, p.copy(iconSize = it)) }
        SliderRow(stringResource(R.string.row_spacing), stringResource(R.string.value_dp, p.rowGap.toInt()), p.rowGap, 0f..28f) {
            model.setPreset(wide, p.copy(rowGap = it)) }
        GroupDivider()
        SwitchRow(stringResource(R.string.show_app_names), stringResource(R.string.settings_names_detail), state.labels,
            model::setLabels, "label-switch")
    }
    if (choosingRows) GridLayoutDialog(shown.rows, shown.columns, if (wide == onWideScreen) maxRowsFit else GRID_ROWS,
        onDismiss = { choosingRows = false }) { rows ->
        model.setHomeRows(rows, cover = !wide); choosingRows = false
    }
    SectionCaption(stringResource(R.string.settings_section_content))
    // Apps and widgets are arranged on the screen whose layout they belong to.
    if (otherScreen) SettingsGroup {
        GroupBody { Detail(stringResource(if (wide) R.string.display_layout_edit_on_inner else R.string.display_layout_edit_on_cover)) }
        GroupDivider()
        SwitchRow(stringResource(R.string.show_status), stringResource(R.string.settings_status_detail), state.verticalStatus,
            model::setVerticalStatus, "status-switch")
        if (!wide && state.verticalStatus) CoverStatusPlace(state, model)
    } else SettingsGroup {
        val pinned = (state.homeSlots + state.leadingSlots).count { it != null }
        ValueRow(stringResource(R.string.choose_home_apps), stringResource(R.string.settings_pinned_count, pinned),
            tag = "choose-home-apps", onClick = onEditPins)
        GroupDivider()
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.widgets_on_page, homePage + 1), style = MaterialTheme.typography.titleMedium)
            val removeWidget = stringResource(R.string.remove_widget)
            val removeLeadingWidget = stringResource(R.string.remove_widget_leading)
            state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (placement.page == -1) stringResource(R.string.unfolded_only_page)
                        else stringResource(R.string.widget_size_row, placement.spanX, placement.spanY, placement.row + 1),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics {
                        contentDescription = if (placement.page == -1) removeLeadingWidget else removeWidget }) { Icon(Icons.Rounded.DeleteOutline, null) }
                    TextButton(onClick = { onWidget(placement.slot) }) { Text(stringResource(R.string.replace)) }
                }
            }
            TextButton(onClick = { onAddWidget(homePage) }, Modifier.heightIn(min = 48.dp).testTag("settings-add-widget")) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_widget_to_page))
            }
        }
        GroupDivider()
        SwitchRow(stringResource(R.string.show_status), stringResource(R.string.settings_status_detail), state.verticalStatus,
            model::setVerticalStatus, "status-switch")
        if (!wide && state.verticalStatus) CoverStatusPlace(state, model)
    }
    SectionCaption(stringResource(R.string.settings_section_screen))
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.settings_display_layout))
            Choice(listOf(stringResource(R.string.display_layout_mirror) to "display-layout-mirror",
                stringResource(R.string.display_layout_separate) to "display-layout-separate"),
                if (state.separateCover) 1 else 0, { if (it == 1) model.setSeparateCover(true) else if (state.separateCover) confirmMirror = true })
            Detail(stringResource(if (state.separateCover) R.string.display_layout_separate_detail else R.string.display_layout_mirror_detail))
        }
        GroupDivider()
        SwitchRow(stringResource(R.string.settings_cover_rotation), stringResource(R.string.settings_cover_rotation_detail),
            state.coverRotation, model::setCoverRotation, "cover-rotation-switch", badge = stringResource(R.string.settings_experimental))
        // The effect needs runtime shaders, which arrived in Android 13.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            GroupDivider()
            SwitchRow(stringResource(R.string.settings_fold_animation), stringResource(R.string.settings_fold_animation_detail),
                state.foldAnimation, model::setFoldAnimation, "fold-animation-switch")
        }
    }
    SectionCaption(stringResource(R.string.all_apps))
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.all_apps_view))
            Choice(listOf(stringResource(R.string.all_apps_view_list) to "library-view-list",
                stringResource(R.string.all_apps_view_grid) to "library-view-grid",
                stringResource(R.string.all_apps_view_folders) to "library-view-folders",
                stringResource(R.string.all_apps_view_home) to "library-view-home"),
                when { state.appsOnHome -> 3; state.libraryFolders -> 2; state.libraryGrid -> 1; else -> 0 }, {
                    if (it == 3) model.setAppsOnHome(true)
                    else { model.setAppsOnHome(false); model.setLibraryGrid(it == 1); model.setLibraryFolders(it == 2) }
                })
            Detail(stringResource(when {
                state.appsOnHome -> R.string.settings_apps_on_home_detail
                state.libraryFolders -> R.string.settings_library_folders_detail
                else -> R.string.settings_all_apps_detail
            }))
        }
    }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(if (wide) R.string.settings_reset_inner else R.string.settings_reset_cover))
    }
}

/** Where the cover screen shows status: across the top, or above the dock as on the inner screen. */
@Composable
private fun CoverStatusPlace(state: LauncherState, model: LauncherModel) {
    GroupBody {
        GroupTitle(stringResource(R.string.cover_status_place))
        Choice(listOf(stringResource(R.string.cover_status_top) to "cover-status-top",
            stringResource(R.string.cover_status_dock) to "cover-status-dock"),
            if (state.coverStatusAtTop) 0 else 1, { model.setCoverStatusAtTop(it == 0) })
        Detail(stringResource(if (state.coverStatusAtTop) R.string.cover_status_top_detail else R.string.cover_status_dock_detail))
    }
}

@Composable
private fun DockPage(state: LauncherState, wide: Boolean, model: LauncherModel) {
    val p = if (wide) state.expanded else state.compact
    // Only the cover's dock may slide in or hide; the inner screen always shows it.
    if (wide) SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.settings_dock_mode))
            Detail(stringResource(R.string.dock_mode_inner_always))
        }
    } else SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.settings_dock_mode))
            val modes = DockMode.entries
            Choice(modes.map { mode -> stringResource(when (mode) {
                DockMode.SHOWN -> R.string.dock_mode_shown
                DockMode.SLIDE -> R.string.dock_mode_slide
                DockMode.HIDDEN -> R.string.dock_mode_hidden
            }) to "dock-mode-${mode.name.lowercase()}" }, modes.indexOf(state.dockMode), { model.setDockMode(modes[it]) })
            Detail(stringResource(when (state.dockMode) {
                DockMode.SHOWN -> R.string.dock_mode_shown_detail
                DockMode.SLIDE -> R.string.dock_mode_slide_detail
                DockMode.HIDDEN -> R.string.dock_mode_hidden_detail
            }))
        }
    }
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.settings_dock_count))
            Choice((MIN_DOCK_SLOTS..MAX_DOCK_SLOTS).map { "$it" to "dock-slots-$it" },
                state.dock.size - MIN_DOCK_SLOTS, { model.setDockSlots(MIN_DOCK_SLOTS + it) })
            Detail(stringResource(R.string.settings_dock_count_detail))
        }
    }
    SettingsGroup {
        SwitchRow(stringResource(R.string.dock_recents), stringResource(R.string.dock_recents_detail), state.dockRecents,
            model::setDockRecents, "dock-recents-switch")
    }
    SettingsGroup {
        SliderRow(stringResource(R.string.dock_width), stringResource(R.string.value_dp, p.dockWidth.toInt()), p.dockWidth, 56f..84f) {
            model.setPreset(wide, p.copy(dockWidth = it)) }
        GroupDivider()
        SwitchRow(stringResource(R.string.dock_align), null, p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) },
            "dock-align-switch")
        if (!p.dockAlignToGrid) SliderRow(stringResource(R.string.dock_height),
            stringResource(R.string.value_percent, (p.dockPosition * 100).toInt()), p.dockPosition, .25f.. .75f) {
            model.setPreset(wide, p.copy(dockPosition = it)) }
    }
}

@Composable
private fun GesturesPage(state: LauncherState, model: LauncherModel, serviceOn: Boolean, onShadeSetup: () -> Unit) {
    val colors = LocalSettingsColors.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = colors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (serviceOn) null else BorderStroke(2.dp, colors.onAlert.copy(alpha = .4f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_service_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Detail(stringResource(if (serviceOn) R.string.settings_service_on else R.string.settings_service_off))
            }
            if (!serviceOn) Button(onClick = onShadeSetup, Modifier.heightIn(min = 44.dp).testTag("gestures-service-setup"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.onAlert, contentColor = colors.alert)) {
                Text(stringResource(R.string.settings_turn_on))
            }
        }
    }
    val needsService = if (serviceOn) null else stringResource(R.string.settings_needs_service)
    SectionCaption(stringResource(R.string.settings_section_gestures))
    SettingsGroup {
        SwitchRow(stringResource(R.string.gesture_down), stringResource(R.string.gesture_down_detail), state.swipeDownShade,
            model::setSwipeDownShade, "swipe-down-switch", needsService, Icons.Rounded.SwipeDown)
        GroupDivider()
        SwitchRow(stringResource(R.string.gesture_double), stringResource(R.string.gesture_double_detail), state.doubleTapLock,
            model::setDoubleTapLock, "double-tap-lock-switch", needsService, Icons.Rounded.Lock)
        GroupDivider()
        SwitchRow(stringResource(R.string.gesture_up), stringResource(R.string.gesture_up_detail), state.swipeUpSearch,
            model::setSwipeUpSearch, "swipe-up-switch", icon = Icons.Rounded.SwipeUp)
        GroupDivider()
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.control.first), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SwipeRight, null, Modifier.size(22.dp), tint = colors.control.second)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gesture_side), style = MaterialTheme.typography.titleMedium)
                Detail(stringResource(R.string.gesture_side_detail))
            }
        }
    }
    SectionCaption(stringResource(R.string.settings_search_button))
    SettingsGroup {
        GroupBody {
            Detail(stringResource(R.string.settings_search_button_detail))
            Choice(listOf(stringResource(R.string.settings_search_google) to "google-search-switch",
                stringResource(R.string.settings_search_apps) to "search-apps-choice"),
                if (state.googleSearch) 0 else 1, { model.setGoogleSearch(it == 0) })
        }
    }
}

@Composable
private fun NewsSettingsPage(state: LauncherState, model: LauncherModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { GoogleNewsSettings.load(context); NewsFeeds.custom.load(context) }
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.left_page))
            Choice(listOf(stringResource(R.string.left_page_google_news) to "left-page-google-news",
                stringResource(R.string.left_page_rss) to "left-page-rss"),
                if (state.leftPage == LeftPage.RSS) 1 else 0, { model.setLeftPage(if (it == 1) LeftPage.RSS else LeftPage.GOOGLE_NEWS) })
            Detail(stringResource(R.string.settings_news_detail))
        }
    }
    if (state.leftPage == LeftPage.GOOGLE_NEWS) {
        val locale = LocalConfiguration.current.locales[0]
        var choosingEdition by rememberSaveable { mutableStateOf(false) }
        SettingsGroup {
            ValueRow(stringResource(R.string.news_edition), GoogleNewsSettings.edition.label(locale), tag = "news-edition") { choosingEdition = true }
            GroupDivider()
            GroupBody {
                GroupTitle(stringResource(R.string.news_topics))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NewsTopic.entries.forEach { topic ->
                        FilterChip(topic in GoogleNewsSettings.topics, { GoogleNewsSettings.toggle(context, topic) },
                            label = { Text(stringResource(topic.label)) }, modifier = Modifier.testTag("news-topic-${topic.name}"))
                    }
                }
                Detail(stringResource(R.string.news_source_note))
            }
        }
        if (choosingEdition) NewsEditionDialog(locale, onDismiss = { choosingEdition = false }) {
            GoogleNewsSettings.setEdition(context, it); choosingEdition = false
        }
    } else RssSourcesSettings()
}

@Composable
private fun RssSourcesSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val reader = NewsFeeds.custom
    var address by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    fun submit() {
        if (address.isBlank() || adding) return
        adding = true; error = null
        scope.launch {
            try { reader.add(context, address); address = ""; keyboard?.hide() }
            catch (failure: RssAddException) { error = failure.messageRes }
            finally { adding = false }
        }
    }
    SettingsGroup {
        GroupBody {
            GroupTitle(stringResource(R.string.rss_sources))
            OutlinedTextField(address, { address = it; error = null }, Modifier.fillMaxWidth().testTag("rss-address"),
                placeholder = { Text(stringResource(R.string.rss_add_hint)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }), isError = error != null)
            error?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::submit, enabled = address.isNotBlank() && !adding,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rss-add")) {
                Text(stringResource(if (adding) R.string.rss_adding else R.string.rss_add))
            }
            reader.sources.forEach { source ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(source.url.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { reader.remove(context, source) }) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.rss_remove_source, source.title))
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemPage(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onExportLayout: () -> Unit, onImportLayout: () -> Unit) {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val system = remember { AppLanguage.systemLocale(context) }
    SectionCaption(stringResource(R.string.customize_language))
    SettingsGroup {
        AppLanguage.tags.forEachIndexed { index, tag ->
            if (index > 0) GroupDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .selectable(tag == current, role = Role.RadioButton) { context.findActivity()?.let { AppLanguage.set(it, tag) } }
                .padding(horizontal = 12.dp).testTag("language-${tag.ifEmpty { "system" }}"), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(tag == current, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (tag.isEmpty()) stringResource(R.string.language_system) else AppLanguage.nativeName(tag))
                    // Name the language "system" currently resolves to, in that language.
                    if (tag.isEmpty()) Detail(system.getDisplayLanguage(system).replaceFirstChar { it.titlecase(system) })
                }
            }
        }
    }
    SectionCaption(stringResource(R.string.backup_title))
    SettingsGroup {
        GroupBody {
            Detail(stringResource(R.string.backup_detail))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text(stringResource(R.string.save)) }
                Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text(stringResource(R.string.restore)) }
            }
            Detail(stringResource(R.string.backup_restore_note))
        }
    }
    SectionCaption(stringResource(R.string.help_home_title))
    SettingsGroup {
        GroupBody {
            Detail(stringResource(if (isDefaultHome) R.string.help_home_default else R.string.help_home_not_default))
            OutlinedButton(onClick = onMakeDefault, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("system-home-settings")) {
                Text(stringResource(if (isDefaultHome) R.string.change_home_app else R.string.set_as_home_app))
            }
        }
    }
}

@Composable
private fun SupportPage(onPage: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    SettingsGroup {
        ValueRow(stringResource(R.string.customize_help), stringResource(R.string.customize_help_detail), tag = "customization-help") {
            onPage(SettingsPage.HELP) }
        GroupDivider()
        ValueRow(stringResource(R.string.customize_about), stringResource(R.string.customize_about_detail), tag = "customization-about") {
            onPage(SettingsPage.ABOUT) }
        GroupDivider()
        ValueRow(stringResource(R.string.customize_changelog), stringResource(R.string.customize_changelog_detail),
            tag = "customization-changelog") { onPage(SettingsPage.CHANGELOG) }
        GroupDivider()
        ValueRow(stringResource(R.string.customize_feedback), stringResource(R.string.customize_feedback_detail),
            tag = "customization-feedback") { context.openLink(FEEDBACK_URL) }
    }
    // Turning notices on asks Android for the notification permission when it is missing.
    var notices by remember { mutableStateOf(UpdateNotice.enabled(context) && UpdateNotice.permitted(context)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        UpdateNotice.setEnabled(context, granted); notices = granted
    }
    SettingsGroup {
        SwitchRow(stringResource(R.string.update_notice_setting), stringResource(R.string.update_notice_setting_detail), notices, { on ->
            if (on && !UpdateNotice.permitted(context)) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else { UpdateNotice.setEnabled(context, on); notices = on }
        }, "update-notice-switch")
    }
}

@Composable
private fun LauncherHelp(isDefaultHome: Boolean, onHomeSettings: () -> Unit, onAddWidget: () -> Unit, onShadeSetup: () -> Unit,
    onPage: (SettingsPage) -> Unit = {}) {
    SettingsGroup {
        GroupBody {
            HelpSection(Icons.Rounded.Home, stringResource(R.string.help_home_title),
                stringResource(if (isDefaultHome) R.string.help_home_default else R.string.help_home_not_default))
            OutlinedButton(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
                Text(stringResource(if (isDefaultHome) R.string.change_home_app else R.string.set_duo_as_home))
            }
            HelpSection(Icons.Rounded.TouchApp, stringResource(R.string.help_customize_title), stringResource(R.string.help_customize))
            HelpSection(Icons.Rounded.Apps, stringResource(R.string.help_app_menu_title), stringResource(R.string.help_app_menu))
            HelpSection(Icons.Rounded.ViewSidebar, stringResource(R.string.help_dock_title), stringResource(R.string.help_dock))
            TextButton(onClick = { onPage(SettingsPage.DOCK) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-dock")) {
                Text(stringResource(R.string.help_open_dock))
            }
            HelpSection(Icons.Rounded.Smartphone, stringResource(R.string.help_cover_title), stringResource(R.string.help_cover))
            HelpSection(Icons.Rounded.GridView, stringResource(R.string.help_all_apps_title), stringResource(R.string.help_all_apps))
            TextButton(onClick = { onPage(SettingsPage.HOME) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-page")) {
                Text(stringResource(R.string.help_open_home))
            }
            HelpSection(Icons.Rounded.Widgets, stringResource(R.string.widgets), stringResource(R.string.help_widgets))
            OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
                Text(stringResource(R.string.add_widget_to_page))
            }
            HelpSection(Icons.Rounded.SwipeDown, stringResource(R.string.help_shade_title), stringResource(R.string.help_shade))
            TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
                Text(stringResource(R.string.help_shade_setup))
            }
            HelpSection(Icons.Rounded.SwipeUp, stringResource(R.string.help_search_title), stringResource(R.string.help_search))
            HelpSection(Icons.Rounded.Newspaper, stringResource(R.string.help_news_title), stringResource(R.string.help_news))
        }
    }
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Detail(detail)
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

/** A small Home: the wallpaper, the first Home icons at [preset]'s size and spacing, and the dock. */
@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState, layout: HomeLayout,
    preset: LayoutPreset, previewHeight: Dp, dock: Boolean = true, onClick: (() -> Unit)? = null) {
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val rows = (layout.rows - 2).coerceAtLeast(1)
    val homeIcons = (0 until rows).map { row -> List(layout.columns) { column ->
        layout.slots.getOrNull(row * GRID_COLUMNS + column)?.let(apps::get) } }
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    val icon = 24f * preset.iconSize / 66f
    val staged = remember(stagedBitmap) { stagedBitmap?.asImageBitmap() }
    val cover = onCoverScreen()
    Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .testTag("customization-home-preview")) {
        WallpaperStandIn()
        // A photo in preview shows the part chosen for this screen.
        staged?.let { image -> Canvas(Modifier.matchParentSize()) { drawWallpaper(image, HomeWallpaper.pendingCrop(cover)) } }
        Column(Modifier.fillMaxSize().padding(start = unit(14f), top = unit(18f), end = unit(if (dock) 52f else 14f)),
            verticalArrangement = Arrangement.spacedBy(unit(4f + preset.rowGap * .5f))) {
            Box(Modifier.fillMaxWidth().height(unit(40f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
            homeIcons.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { app ->
                    if (app != null) Image(app.icon.asImageBitmap(), null, Modifier.size(unit(icon)).clip(RoundedCornerShape(unit(icon * .3f))))
                    else Spacer(Modifier.size(unit(icon)))
                }
            } }
        }
        if (dock) Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f * preset.dockWidth / 68f))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
            .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(unit(8f))) {
            dockIcons.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(22f)).clip(RoundedCornerShape(unit(7f)))) }
        }
    }
}

/** Home rows on a wheel; layouts that cannot fit this screen stay visible but cannot be chosen. */
@Composable private fun GridLayoutDialog(current: Int, columns: Int, maxRowsFit: Int, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    val options = (DEFAULT_HOME_ROWS..GRID_ROWS).toList()
    var chosen by remember { mutableIntStateOf(current) }
    fun fits(rows: Int) = rows <= maxOf(DEFAULT_HOME_ROWS, maxRowsFit)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.grid_layout)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_rows_setting), style = MaterialTheme.typography.bodyMedium)
                WheelPicker(options.map { stringResource(R.string.home_rows_option, columns, it - 2) },
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
