package media.whitewhale.iduo

import android.content.Context
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Paints the best available stand-in for Android's Home wallpaper, for surfaces that cannot show
 * the wallpaper itself. Launcher windows show the real wallpaper; see [SystemWallpaper].
 */
@Composable
internal fun WallpaperStandIn(modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current.applicationContext
    val revision = LauncherBackgroundCache.revision.intValue
    val initial = remember(revision) { LauncherBackgroundCache.bitmap?.takeUnless { it.isRecycled } }
    val photo = produceState(initialValue = initial, key1 = context, key2 = revision) {
        value = withContext(Dispatchers.IO) { loadLauncherBackground(context) }
    }.value
    Canvas(modifier) { drawLauncherBackground(photo?.asImageBitmap()) }
}

/** Short edge the bundled landscape is decoded to: the Fold 7's tallest crop (inner display) stays sharp. */
private const val DEFAULT_WALLPAPER_SHORT_EDGE = 2200

/** The bundled iDuo dunes, decoded on demand while they are the mirrored Home wallpaper. */
internal object DefaultWallpaper {
    /** Snapshot state, so canvases drawn before the decode finishes redraw once it lands. */
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    @Volatile var failed = false
        private set

    @Synchronized
    fun load(context: Context): ImageBitmap? {
        bitmap?.let { return it }
        if (failed) return null
        val decoded = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.resources, R.drawable.default_wallpaper)) { decoder, info, _ ->
                val width = info.size.width; val height = info.size.height
                val scale = minOf(1f, DEFAULT_WALLPAPER_SHORT_EDGE.toFloat() / minOf(width, height).coerceAtLeast(1))
                decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
                // Discover's frame draws on a software canvas, which cannot take hardware bitmaps.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull()
        if (decoded == null) failed = true else bitmap = decoded
        return decoded
    }
}

/**
 * Draws the mirror of a wallpaper iDuo set ([photo], or the bundled dunes), or else a gradient
 * of the Home wallpaper's colours, since Android does not let apps read its pixels.
 */
internal fun DrawScope.drawLauncherBackground(photo: ImageBitmap?) {
    if (photo != null && photo.width > 0 && photo.height > 0) {
        drawCovering(photo)
        return
    }
    val bundled = DefaultWallpaper.bitmap
    if (SystemWallpaper.mirrorsBundled && bundled != null) {
        drawCovering(bundled)
        return
    }
    val colors = SystemWallpaper.colors
    val stops = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
        .map { Color(it.toArgb()) }
    when (stops.size) {
        0 -> drawRect(Brush.verticalGradient(listOf(Color(0xFF41687E), Color(0xFF94ADB5), Color(0xFFD8CEB6))))
        1 -> drawRect(stops[0])
        else -> drawRect(Brush.verticalGradient(stops))
    }
}

/** Draws [photo] centre-cropped to fill the canvas. */
private fun DrawScope.drawCovering(photo: ImageBitmap) {
    val destinationWidth = size.width.toInt().coerceAtLeast(1)
    val destinationHeight = size.height.toInt().coerceAtLeast(1)
    val sourceAspect = photo.width.toFloat() / photo.height
    val destinationAspect = destinationWidth.toFloat() / destinationHeight
    val sourceWidth: Int
    val sourceHeight: Int
    if (sourceAspect > destinationAspect) {
        sourceHeight = photo.height
        sourceWidth = (sourceHeight * destinationAspect).toInt().coerceIn(1, photo.width)
    } else {
        sourceWidth = photo.width
        sourceHeight = (sourceWidth / destinationAspect).toInt().coerceIn(1, photo.height)
    }
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - sourceWidth) / 2, (photo.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(destinationWidth, destinationHeight),
    )
}
