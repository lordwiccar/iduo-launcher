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

/** How folders are outlined on Home and in the dock. */
enum class FolderShape { SQUARE, SQUIRCLE, CIRCLE }

/** The folder shape chosen in Settings. */
internal val LocalFolderShape = staticCompositionLocalOf { FolderShape.SQUARE }

/** The outline of a folder [side] across. */
internal fun FolderShape.outline(side: Dp): Shape = when (this) {
    FolderShape.SQUARE -> RoundedCornerShape(side * .24f)
    FolderShape.SQUIRCLE -> SquircleShape
    FolderShape.CIRCLE -> CircleShape
}

/** How far in from the edge a two by two grid of icons sits so that it stays inside the outline, as part of the side. */
internal val FolderShape.contentInset: Float get() = when (this) {
    FolderShape.SQUARE -> .08f
    FolderShape.SQUIRCLE -> .1f
    FolderShape.CIRCLE -> .16f
}

internal val FolderShape.label: Int get() = when (this) {
    FolderShape.SQUARE -> R.string.folder_shape_square
    FolderShape.SQUIRCLE -> R.string.folder_shape_squircle
    FolderShape.CIRCLE -> R.string.folder_shape_circle
}

/** A superellipse: straighter sides and softer, longer corners than a rounded square. */
private object SquircleShape : Shape {
    private const val EXPONENT = 4.0
    private const val STEPS = 96

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rx = size.width / 2f
        val ry = size.height / 2f
        val path = Path()
        for (step in 0..STEPS) {
            val angle = 2 * Math.PI * step / STEPS
            val c = cos(angle)
            val s = sin(angle)
            val x = rx + rx * (sign(c) * abs(c).pow(2 / EXPONENT)).toFloat()
            val y = ry + ry * (sign(s) * abs(s).pow(2 / EXPONENT)).toFloat()
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
