package media.whitewhale.iduo

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** An outline for folders, or for app icons, on Home and in the dock. */
enum class HomeShape { SQUARE, SQUIRCLE, CIRCLE }

/** How app icons are coloured: as their makers drew them, on a dark backdrop, or as one tint on dark. */
enum class IconStyle { ORIGINAL, DARK, TINTED }

/** The folder shape chosen in Settings. */
internal val LocalFolderShape = staticCompositionLocalOf { HomeShape.SQUARE }

/** The outline of a folder [side] across. */
internal fun HomeShape.outline(side: Dp): Shape = when (this) {
    HomeShape.SQUARE -> RoundedCornerShape(side * .24f)
    HomeShape.SQUIRCLE -> SquircleShape
    HomeShape.CIRCLE -> CircleShape
}

/** How far in from the edge a two by two grid of icons sits so that it stays inside the outline, as part of the side. */
internal val HomeShape.contentInset: Float get() = when (this) {
    HomeShape.SQUARE -> .08f
    HomeShape.SQUIRCLE -> .1f
    HomeShape.CIRCLE -> .16f
}

internal val HomeShape.label: Int get() = when (this) {
    HomeShape.SQUARE -> R.string.folder_shape_square
    HomeShape.SQUIRCLE -> R.string.folder_shape_squircle
    HomeShape.CIRCLE -> R.string.folder_shape_circle
}

/** The outline as an Android path [side] pixels across, for drawing icons into bitmaps. */
internal fun HomeShape.path(side: Float): android.graphics.Path = android.graphics.Path().apply {
    when (this@path) {
        HomeShape.SQUARE -> addRoundRect(0f, 0f, side, side, side * .236f, side * .236f, android.graphics.Path.Direction.CW)
        HomeShape.CIRCLE -> addCircle(side / 2f, side / 2f, side / 2f, android.graphics.Path.Direction.CW)
        HomeShape.SQUIRCLE -> squirclePoints(side, side) { index, x, y -> if (index == 0) moveTo(x, y) else lineTo(x, y) }.also { close() }
    }
}

/** Walks a superellipse [width] by [height]: straighter sides and softer, longer corners than a rounded square. */
private inline fun squirclePoints(width: Float, height: Float, point: (Int, Float, Float) -> Unit) {
    val rx = width / 2f
    val ry = height / 2f
    for (step in 0..SQUIRCLE_STEPS) {
        val angle = 2 * Math.PI * step / SQUIRCLE_STEPS
        val c = cos(angle)
        val s = sin(angle)
        point(step, rx + rx * (sign(c) * abs(c).pow(2 / SQUIRCLE_EXPONENT)).toFloat(),
            ry + ry * (sign(s) * abs(s).pow(2 / SQUIRCLE_EXPONENT)).toFloat())
    }
}

private const val SQUIRCLE_EXPONENT = 4.0
private const val SQUIRCLE_STEPS = 96

private object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        squirclePoints(size.width, size.height) { index, x, y -> if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        path.close()
        return Outline.Generic(path)
    }
}

internal val IconStyle.label: Int get() = when (this) {
    IconStyle.ORIGINAL -> R.string.icon_style_original
    IconStyle.DARK -> R.string.icon_style_dark
    IconStyle.TINTED -> R.string.icon_style_tinted
}
