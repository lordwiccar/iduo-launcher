package media.whitewhale.iduo

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Which part of the wallpaper a screen shows: [zoom] beyond filling the screen (1 fills it) and
 * the point of the image, as fractions of its width and height, at the screen's centre.
 */
data class WallpaperCrop(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f) {
    /** Where the [image] lies on a [screen] showing this crop, kept so that it always covers the screen. */
    fun placement(image: Size, screen: Size): Pair<Offset, Size> {
        if (image.width <= 0f || image.height <= 0f) return Offset.Zero to screen
        val scale = maxOf(screen.width / image.width, screen.height / image.height) * zoom.coerceIn(1f, MAX_WALLPAPER_ZOOM)
        val drawn = Size(image.width * scale, image.height * scale)
        val halfX = screen.width / 2f / drawn.width
        val halfY = screen.height / 2f / drawn.height
        val x = centerX.coerceIn(halfX, 1f - halfX)
        val y = centerY.coerceIn(halfY, 1f - halfY)
        return Offset(screen.width / 2f - x * drawn.width, screen.height / 2f - y * drawn.height) to drawn
    }

    companion object {
        fun parse(value: String?): WallpaperCrop? = value?.split(',')?.mapNotNull(String::toFloatOrNull)
            ?.takeIf { it.size == 3 && it.all(Float::isFinite) }?.let { WallpaperCrop(it[0], it[1], it[2]) }
    }
    override fun toString() = "$zoom,$centerX,$centerY"
}

const val MAX_WALLPAPER_ZOOM = 4f

/**
 * The wallpaper iDuo set (a photo or the bundled dunes), which Home draws itself with each
 * screen's own crop. Android crops one wallpaper differently for a foldable's two screens, so
 * drawing it here keeps Home, its blur and the frosted dock in step. Null when Android's
 * wallpaper is not one iDuo set, and Home shows Android's wallpaper instead.
 */
internal object HomeWallpaper {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    /** Whether the wallpaper is a photo, the only kind whose crop the user adjusts. */
    var isPhoto by mutableStateOf(false)
        private set
    private var crops by mutableStateOf<Map<Boolean, WallpaperCrop>>(emptyMap())

    private fun key(cover: Boolean) = if (cover) "cropCover" else "cropInner"

    fun crop(cover: Boolean): WallpaperCrop = crops[cover] ?: WallpaperCrop()

    fun setCrop(context: Context, cover: Boolean, crop: WallpaperCrop) {
        crops = crops + (cover to crop)
        launcherBackgroundPreferences(context).edit().putString(key(cover), crop.toString()).apply()
    }

    /** Crops chosen for a photo still in preview; they apply with it. */
    private var pendingCrops by mutableStateOf<Map<Boolean, WallpaperCrop>>(emptyMap())

    fun pendingCrop(cover: Boolean): WallpaperCrop = pendingCrops[cover] ?: WallpaperCrop()
    fun setPendingCrop(cover: Boolean, crop: WallpaperCrop) { pendingCrops = pendingCrops + (cover to crop) }
    fun clearPendingCrops() { pendingCrops = emptyMap() }

    /** A new photo shows the crops chosen in its preview, and otherwise fills each screen from its centre. */
    fun adoptPendingCrops(context: Context) {
        crops = pendingCrops
        pendingCrops = emptyMap()
        val editor = launcherBackgroundPreferences(context).edit()
        listOf(true, false).forEach { cover ->
            val crop = crops[cover]
            if (crop != null) editor.putString(key(cover), crop.toString()) else editor.remove(key(cover))
        }
        editor.apply()
    }

    fun update(context: Context, photo: android.graphics.Bitmap?, dunes: ImageBitmap?) {
        val prefs = launcherBackgroundPreferences(context)
        crops = listOf(true, false).mapNotNull { cover -> WallpaperCrop.parse(prefs.getString(key(cover), null))?.let { cover to it } }.toMap()
        isPhoto = photo != null
        image = photo?.takeUnless { it.isRecycled }?.asImageBitmap() ?: dunes
    }
}

/** Keeps [HomeWallpaper] and the dock's frost in step with the wallpaper iDuo set, off the main thread. */
@Composable
internal fun FollowHomeWallpaper() {
    val context = LocalContext.current.applicationContext
    val revision = LauncherBackgroundCache.revision.intValue
    val bundled = SystemWallpaper.mirrorsBundled
    val dunes = DefaultWallpaper.bitmap
    LaunchedEffect(revision, bundled, dunes) {
        val photo = withContext(Dispatchers.IO) { loadLauncherBackground(context) }
        HomeWallpaper.update(context, photo, if (bundled) dunes else null)
        withContext(Dispatchers.Default) {
            WallpaperFrost.update(HomeWallpaper.image?.asAndroidBitmap())
        }
    }
}

/** Draws [image] on this canvas as [crop] places it on a screen of this size. */
internal fun DrawScope.drawWallpaper(image: ImageBitmap, crop: WallpaperCrop, origin: Offset = Offset.Zero,
    screen: Size = size, sourceSize: Size = Size(image.width.toFloat(), image.height.toFloat())) {
    // A smaller copy (the dock's frost) is placed as its full-size original would be.
    val (offset, drawn) = crop.placement(sourceSize, screen)
    drawImage(image, dstOffset = IntOffset((offset.x - origin.x).roundToInt(), (offset.y - origin.y).roundToInt()),
        dstSize = IntSize(drawn.width.roundToInt().coerceAtLeast(1), drawn.height.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium)
}

/**
 * True on the cover screen: one under 4 inches across, or any window narrower than 650dp. Inches
 * keep a wide cover, such as the Fold 8's with a smaller display size, from passing for the inner
 * screen.
 */
@Composable
internal fun onCoverScreen(): Boolean {
    val config = LocalConfiguration.current
    val metrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    return config.screenWidthDp < 650 || isCoverWindow(
        (minOf(config.screenWidthDp, config.screenHeightDp) * metrics.density).toInt(), metrics.xdpi, metrics.density)
}

/** Home's wallpaper layer: the wallpaper iDuo set, drawn with this screen's crop, and blurred by [blur] from 0 to 1. */
@Composable
internal fun HomeWallpaperLayer(blur: Float, modifier: Modifier = Modifier) {
    val image = HomeWallpaper.image ?: return
    val crop = HomeWallpaper.crop(onCoverScreen())
    Box(modifier.fillMaxSize().testTag("home-wallpaper")) {
        Canvas(Modifier.fillMaxSize()) { drawWallpaper(image, crop) }
        if (blur > 0f) Canvas(Modifier.fillMaxSize().blur(WALLPAPER_BACKDROP_BLUR * blur, BlurredEdgeTreatment.Rectangle)) {
            drawWallpaper(image, crop)
        }
    }
}

/**
 * Moves and zooms the photo for the screen in use: drag to move it, pinch to zoom. The other
 * screen keeps its own crop.
 */
@Composable
internal fun WallpaperCropEditor(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cover = onCoverScreen()
    val image = HomeWallpaper.image ?: return
    WallpaperCropEditor(image, HomeWallpaper.crop(cover), cover, { HomeWallpaper.setCrop(context, cover, it) }, onDismiss)
}

/** Moves and zooms [image], starting from [initial], for the cover or inner screen; [onDone] receives the result. */
@Composable
internal fun WallpaperCropEditor(image: ImageBitmap, initial: WallpaperCrop, cover: Boolean,
    onDone: (WallpaperCrop) -> Unit, onDismiss: () -> Unit) {
    var crop by remember(image) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("wallpaper-crop-editor")) {
            Canvas(Modifier.fillMaxSize().pointerInput(image) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val screen = Size(size.width.toFloat(), size.height.toFloat())
                    val imageSize = Size(image.width.toFloat(), image.height.toFloat())
                    val zoom = (crop.zoom * gestureZoom).coerceIn(1f, MAX_WALLPAPER_ZOOM)
                    val (_, before) = crop.placement(imageSize, screen)
                    val (_, after) = crop.copy(zoom = zoom).placement(imageSize, screen)
                    // Keep the point under the fingers in place while zooming, and follow the drag.
                    val fromCenter = centroid - Offset(screen.width / 2f, screen.height / 2f)
                    val centerX = crop.centerX + fromCenter.x / before.width - fromCenter.x / after.width - pan.x / after.width
                    val centerY = crop.centerY + fromCenter.y / before.height - fromCenter.y / after.height - pan.y / after.height
                    val halfX = screen.width / 2f / after.width
                    val halfY = screen.height / 2f / after.height
                    crop = WallpaperCrop(zoom, centerX.coerceIn(halfX, 1f - halfX), centerY.coerceIn(halfY, 1f - halfY))
                }
            }) { drawWallpaper(image, crop) }
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = .55f), contentColor = Color.White) {
                Text(stringResource(if (cover) R.string.wallpaper_crop_hint_cover else R.string.wallpaper_crop_hint_inner),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                FilledTonalButton(onClick = { crop = WallpaperCrop() }, Modifier.heightIn(min = 48.dp).testTag("wallpaper-crop-reset")) {
                    Text(stringResource(R.string.wallpaper_crop_reset))
                }
                Button(onClick = { onDone(crop); onDismiss() },
                    Modifier.heightIn(min = 48.dp).testTag("wallpaper-crop-done")) { Text(stringResource(R.string.done)) }
            }
        }
    }
}
