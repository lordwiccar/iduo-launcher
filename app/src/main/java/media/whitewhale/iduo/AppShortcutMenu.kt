package media.whitewhale.iduo

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Shortcuts an app offers at most in the menu, as Android's own launchers show. */
private const val MAX_SHORTCUTS = 4

/** One of an app's shortcuts, ready to show. */
internal class AppShortcut(val info: ShortcutInfo, val label: String, val icon: ImageBitmap?)

/** What the menu needs to know about an app beyond its entry. */
internal class AppMenuDetails(val shortcuts: List<AppShortcut>, val canUninstall: Boolean)

/**
 * Background thread. The app's static and dynamic shortcuts, best first. Android shares them only
 * with the default Home app, so other launchers get none.
 */
internal fun appMenuDetails(context: Context, app: AppEntry): AppMenuDetails {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val shortcuts = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return@runCatching emptyList()
        val query = LauncherApps.ShortcutQuery().setPackage(app.packageName).setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        val density = context.resources.displayMetrics.densityDpi
        launcherApps.getShortcuts(query, app.user).orEmpty().filter { it.isEnabled }
            .sortedWith(compareBy<ShortcutInfo>({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .take(MAX_SHORTCUTS).map { info ->
                val icon = runCatching { launcherApps.getShortcutIconDrawable(info, density)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                AppShortcut(info, (info.shortLabel ?: info.longLabel ?: "").toString(), icon)
            }.filter { it.label.isNotBlank() }
    }.getOrDefault(emptyList())
    // Apps that came with the phone can be turned off in App info, but not uninstalled.
    val flags = runCatching { launcherApps.getApplicationInfo(app.packageName, 0, app.user).flags }.getOrDefault(0)
    val system = flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    return AppMenuDetails(shortcuts, !system)
}

/** Opens [shortcut] the way Android's launchers do. Returns false if the app refused it. */
internal fun startAppShortcut(context: Context, shortcut: AppShortcut, bounds: android.graphics.Rect?): Boolean = runCatching {
    context.getSystemService(LauncherApps::class.java).startShortcut(shortcut.info, bounds, null)
}.isSuccess

/** Asks Android to uninstall [app]; the user confirms in Android's own dialog. */
internal fun requestUninstall(context: Context, app: AppEntry): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName))
        .putExtra(Intent.EXTRA_USER, app.user).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Places the menu above [anchor] when it fits there, else below, kept on screen. */
private class AnchoredMenuPosition(private val anchor: IntRect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val x = (anchor.center.x - popupContentSize.width / 2).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val above = anchor.top - gap - popupContentSize.height
        val y = if (above >= 0) above else (anchor.bottom + gap).coerceAtMost(maxOf(0, windowSize.height - popupContentSize.height))
        return IntOffset(x, y)
    }
}

/** An action at the foot of the menu: an icon over a short label. */
internal class AppMenuAction(val icon: ImageVector, val label: String, val tag: String, val onClick: () -> Unit)

/**
 * The menu a long press on an app opens beside it: the app's shortcuts, then actions on the icon
 * and the app. [anchor] is the icon's bounds in the window.
 */
@Composable
internal fun AppShortcutMenu(app: AppEntry, anchor: Rect, details: AppMenuDetails?, actions: List<AppMenuAction>,
    onShortcut: (AppShortcut) -> Unit, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(anchor, gap) {
        AnchoredMenuPosition(IntRect(anchor.left.roundToInt(), anchor.top.roundToInt(), anchor.right.roundToInt(), anchor.bottom.roundToInt()), gap)
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(Modifier.widthIn(min = 260.dp, max = 340.dp).testTag("app-menu-${app.id}"), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 8.dp) {
            Column(Modifier.padding(vertical = 8.dp)) {
                details?.shortcuts?.forEach { shortcut ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onShortcut(shortcut) }
                        .padding(horizontal = 18.dp).testTag("app-shortcut-${shortcut.info.id}"), verticalAlignment = Alignment.CenterVertically) {
                        shortcut.icon?.let { Image(it, null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))) }
                            ?: Spacer(Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(shortcut.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!details?.shortcuts.isNullOrEmpty()) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    actions.forEach { action ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = action.onClick)
                            .padding(vertical = 8.dp).testTag(action.tag), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                Surface(Modifier.matchParentSize(), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {}
                                Icon(action.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(action.label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Icons for the menu's actions, kept together so every caller draws the same ones. */
internal object AppMenuIcons {
    val remove = Icons.Rounded.RemoveCircleOutline
    val add = Icons.Rounded.Home
    val uninstall = Icons.Rounded.Delete
    val icon = Icons.Rounded.Palette
    val info = Icons.Rounded.Info
    val more = Icons.Rounded.MoreHoriz
    val replace = Icons.Rounded.SwapHoriz
    val pin = Icons.Rounded.PushPin
}

/**
 * Chooses one icon for [app] from an installed icon pack: first the pack, then its drawing, with
 * search. [onChoose] gets null to restore the app's usual icon.
 */
@Composable
internal fun IconChoiceDialog(app: AppEntry, current: IconChoice?, onChoose: (IconChoice?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { IconPacks.installed(context) } }
    var packName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val pack by produceState<IconPack?>(null, packName) {
        value = packName?.let { name -> withContext(Dispatchers.IO) { IconPacks.load(context, name) } }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (packName != null) IconButton(onClick = { packName = null; query = "" }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                }
                Text(stringResource(R.string.app_icon_choose, app.label), maxLines = 2)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                val chosenPack = packName
                if (chosenPack == null) {
                    if (current != null) TextButton(onClick = { onChoose(null) }, Modifier.fillMaxWidth().testTag("app-icon-reset")) {
                        Text(stringResource(R.string.app_icon_reset))
                    }
                    val list = packs
                    when {
                        list == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        list.isEmpty() -> {
                            Text(stringResource(R.string.settings_icon_pack_none))
                            TextButton(onClick = { openIconPackStore(context) }, Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.settings_icon_pack_get))
                            }
                        }
                        else -> list.forEach { info ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { packName = info.packageName }.padding(horizontal = 8.dp)
                                .testTag("app-icon-pack-${info.packageName}"), verticalAlignment = Alignment.CenterVertically) {
                                info.icon?.let { Image(it.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(36.dp)) }
                                Spacer(Modifier.width(14.dp))
                                Text(info.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("app-icon-search"), singleLine = true,
                        placeholder = { Text(stringResource(R.string.app_icon_search)) })
                    Spacer(Modifier.height(8.dp))
                    val loaded = pack
                    if (loaded == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    else {
                        val words = query.trim().lowercase().split(' ').filter(String::isNotBlank)
                        val names = remember(loaded, words) {
                            loaded.iconNames.filter { name -> words.all { name.lowercase().replace('_', ' ').contains(it) } }
                        }
                        LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                            items(names, key = { it }) { name ->
                                val icon by produceState<ImageBitmap?>(null, loaded, name) {
                                    value = withContext(Dispatchers.IO) { loaded.drawable(name)?.let { launcherIcon(it).asImageBitmap() } }
                                }
                                Box(Modifier.padding(4.dp).size(56.dp).clip(RoundedCornerShape(14.dp))
                                    .clickable { onChoose(IconChoice(chosenPack, name)) }, contentAlignment = Alignment.Center) {
                                    icon?.let { Image(it, name.replace('_', ' '), Modifier.size(48.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Google Play's search for icon packs, or its website when the Play Store is missing. */
internal fun openIconPackStore(context: Context) {
    val search = Uri.encode("icon pack")
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$search&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(market) }.isFailure) context.openLink("https://play.google.com/store/search?q=$search&c=apps")
}
