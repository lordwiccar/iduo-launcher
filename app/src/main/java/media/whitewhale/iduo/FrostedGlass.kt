package media.whitewhale.iduo

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Height the frosted copy of the wallpaper is reduced to before it is blurred. */
private const val FROST_HEIGHT = 240
/** Box blur radius at [FROST_HEIGHT], in pixels; three passes approximate a Gaussian. */
private const val FROST_RADIUS = 5

/**
 * A blurred copy of the Home wallpaper for frosted glass, such as the dock, drawn where the
 * wallpaper lies behind it. Apps cannot read Android's wallpaper, so this exists only for a wallpaper iDuo set
 * (a photo or the bundled dunes); otherwise panels frost the wallpaper's colours.
 */
internal object WallpaperFrost {
    /** Snapshot state, so panels repaint when the wallpaper changes. */
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    /** The wallpaper's horizontal position, 0 to 1, as Home last reported it to Android. */
    var xOffset by mutableFloatStateOf(.5f)
    /** Size of the wallpaper the frost was made from, so it is placed exactly as Home draws it. */
    var sourceSize by mutableStateOf(androidx.compose.ui.geometry.Size.Zero)
        private set
    private var source: Bitmap? = null

    fun update(from: Bitmap?) {
        if (from === source && (from == null || bitmap != null)) return
        source = from
        sourceSize = from?.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            ?: androidx.compose.ui.geometry.Size.Zero
        bitmap = from?.takeUnless { it.isRecycled }?.let { frost(it).asImageBitmap() }
    }
}

/** A small, blurred copy of [source]: it is drawn scaled up, so bilinear filtering finishes the blur. */
private fun frost(source: Bitmap): Bitmap {
    val height = FROST_HEIGHT.coerceAtMost(source.height)
    val width = (source.width * height.toFloat() / source.height).roundToInt().coerceAtLeast(1)
    // Scaling first keeps the copy small; a hardware bitmap has to become a software one to be read.
    val small = Bitmap.createScaledBitmap(source, width, height, true).copy(Bitmap.Config.ARGB_8888, false)
    val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
    repeat(3) {
        boxBlur(pixels, width, height, horizontal = true)
        boxBlur(pixels, width, height, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlur(pixels: IntArray, width: Int, height: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val line = IntArray(length)
    for (l in 0 until lines) {
        fun index(i: Int) = if (horizontal) l * width + i else i * width + l
        for (i in 0 until length) line[i] = pixels[index(i)]
        for (i in 0 until length) {
            var r = 0; var g = 0; var b = 0; var count = 0
            for (k in (i - FROST_RADIUS).coerceAtLeast(0)..(i + FROST_RADIUS).coerceAtMost(length - 1)) {
                val c = line[k]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; count++
            }
            pixels[index(i)] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
}

/**
 * Frosted glass: paints the part of the blurred wallpaper that lies behind this element, inside
 * [shape], so the element's own translucent colour tints a blur of what is behind it. Without a
 * copy of the wallpaper it paints the wallpaper's colours instead.
 */
internal fun Modifier.frostedWallpaper(shape: Shape): Modifier = composed {
    val view = LocalView.current
    val cover = onCoverScreen()
    val bounds = remember { arrayOf(Rect.Zero) }
    var positioned by remember { mutableStateOf(0) }
    onGloballyPositioned { coordinates ->
        val next = coordinates.boundsInWindow()
        if (next != bounds[0]) { bounds[0] = next; positioned++ }
    }.drawBehind {
        positioned // Repaint whenever the element moves over the wallpaper.
        val window = IntSize(view.rootView.width.coerceAtLeast(1), view.rootView.height.coerceAtLeast(1))
        val origin = bounds[0].topLeft
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        clipPath(path) {
            val frost = WallpaperFrost.bitmap
            if (frost != null && HomeWallpaper.image != null) {
                // The part of Home's own wallpaper behind this element, with this screen's crop.
                drawWallpaper(frost, HomeWallpaper.crop(cover), origin,
                    androidx.compose.ui.geometry.Size(window.width.toFloat(), window.height.toFloat()), WallpaperFrost.sourceSize)
            } else if (!SamsungBlur.available) {
                // Samsung's own blur, a layer inside the element, frosts Android's wallpaper there.
                val colors = SystemWallpaper.colorsFor(cover)
                val stops = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
                    .map { Color(it.toArgb()) }.ifEmpty { listOf(Color(0xFF41687E), Color(0xFF94ADB5), Color(0xFFD8CEB6)) }
                if (stops.size == 1) drawRect(stops[0])
                else drawRect(Brush.verticalGradient(stops, startY = -origin.y, endY = window.height - origin.y))
            }
        }
    }
}
