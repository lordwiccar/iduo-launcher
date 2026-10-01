package media.whitewhale.iduo

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Samsung's own blur of what lies behind a window, which on Home is Android's wallpaper. Samsung
 * turns off Android's cross-window blur and does not tell apps the wallpaper's colours, so this
 * is the only way to frost a wallpaper chosen in Samsung's settings.
 */
internal object SamsungBlur {
    private val info = runCatching { Class.forName("android.view.SemBlurInfo") }.getOrNull()
    private val builder = runCatching { Class.forName("android.view.SemBlurInfo\$Builder") }.getOrNull()
    private val windowMode = runCatching { info?.getField("BLUR_MODE_WINDOW")?.getInt(null) }.getOrNull()
    private val setInfo = runCatching { View::class.java.getMethod("semSetBlurInfo", info) }.getOrNull()

    val available = info != null && builder != null && windowMode != null && setInfo != null

    /** Blurs the wallpaper behind [view] by [radius] pixels, tinted by [tint], with rounded corners; 0 turns it off. */
    fun apply(view: View, radius: Int, tint: Int, corner: Float): Boolean = runCatching {
        val blur = if (radius <= 0) null else builder!!.getConstructor(Int::class.javaPrimitiveType).newInstance(windowMode).let { b ->
            builder.getMethod("setRadius", Int::class.javaPrimitiveType).invoke(b, radius)
            builder.getMethod("setBackgroundColor", Int::class.javaPrimitiveType).invoke(b, tint)
            builder.getMethod("setBackgroundCornerRadius", Float::class.javaPrimitiveType).invoke(b, corner)
            builder.getMethod("build").invoke(b)
        }
        setInfo!!.invoke(view, blur)
        true
    }.getOrDefault(false)
}

/** A layer showing Samsung's blur of the wallpaper behind it, tinted by [tint]. */
@Composable
internal fun SamsungWallpaperBlur(radius: Dp, corner: Dp, tint: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.roundToPx() }
    val cornerPx = with(density) { corner.toPx() }
    AndroidView({ View(it) }, modifier, update = { SamsungBlur.apply(it, radiusPx, tint.toArgb(), cornerPx) })
}

/** How strongly Samsung's blur frosts the wallpaper behind the dock. */
internal val DOCK_SAMSUNG_BLUR = 40.dp
/** Steps Samsung's blur of the whole wallpaper takes from none to full. */
internal const val SAMSUNG_BLUR_STEPS = 6
