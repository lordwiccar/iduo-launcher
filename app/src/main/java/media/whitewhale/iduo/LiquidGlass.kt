package media.whitewhale.iduo

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** How Home's panels are made: frosted, or clear glass with bright edges. */
enum class GlassStyle { FROSTED, LIQUID }

internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.FROSTED }

/**
 * How blurred Home's own wallpaper is, 0 to 1, as it blurs behind All apps, news or a folder. Glass
 * reads it while drawing, so a wallpaper bent through glass blurs in step without recomposing.
 */
internal object WallpaperBlurState {
    var source by mutableStateOf<State<Float>?>(null)
}

/** How strongly panels with text are tinted under Liquid, for legibility on the blurred wallpaper. */
internal const val LIQUID_PANEL_TINT = .26f
/** Search floats over Home's own widgets, which can be dark: its text needs a stronger tint than a panel's. */
internal const val LIQUID_SEARCH_TINT = .62f
/** The dock's tint: nearly clear, so the wallpaper reads through. */
internal const val LIQUID_DOCK_TINT = .07f
/** A folder is a lighter pane, set apart from the dark app icons around it. */
internal const val LIQUID_FOLDER_TINT = .2f

/**
 * Liquid glass: bright edges lit from the upper left and a soft sheen, over whatever lies behind.
 * With [refract], the wallpaper is drawn through the element and bends at its edges. That needs its
 * pixels, so it only happens over a wallpaper iDuo set, and from Android 13; Android does not let
 * apps read any other wallpaper, and there the glass is simply clear.
 */
internal fun Modifier.liquidGlass(shape: Shape, refract: Boolean = false): Modifier = composed {
    val view = LocalView.current
    val cover = onCoverScreen()
    val bounds = remember { arrayOf(Rect.Zero) }
    var positioned by remember { mutableIntStateOf(0) }
    val image = if (refract && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) HomeWallpaper.image else null
    val lens = remember(image) {
        if (image != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { WallpaperLens(image) }.getOrNull() else null
    }
    onGloballyPositioned { coordinates ->
        val next = coordinates.boundsInWindow()
        if (next != bounds[0]) { bounds[0] = next; positioned++ }
    }.drawWithContent {
        positioned // Repaint whenever the element moves over the wallpaper.
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        if (lens != null && image != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: (minOf(size.width, size.height) * .25f)
            val window = Size(view.rootView.width.coerceAtLeast(1).toFloat(), view.rootView.height.coerceAtLeast(1).toFloat())
            val source = Size(image.width.toFloat(), image.height.toFloat())
            val (offset, drawn) = HomeWallpaper.crop(cover).placement(source, window)
            val blur = (WallpaperBlurState.source?.value ?: 0f) * WALLPAPER_BACKDROP_BLUR.toPx()
            lens.draw(this, path, radius, 14.dp.toPx(), 16.dp.toPx(), offset - bounds[0].topLeft, drawn, source, blur)
        }
        drawContent()
        clipPath(path) {
            // A sheen falling from the top, and a little light gathered at the bottom.
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .13f), .3f to Color.Transparent,
                .82f to Color.Transparent, 1f to Color.White.copy(alpha = .07f)))
            // A soft inner glow, then the bright edge; clipping keeps both strokes inside the shape.
            drawPath(path, Color.White.copy(alpha = .06f), style = Stroke(9.dp.toPx()))
            drawPath(path, Brush.linearGradient(
                0f to Color.White.copy(alpha = .8f), .42f to Color.White.copy(alpha = .14f),
                .6f to Color.White.copy(alpha = .1f), 1f to Color.White.copy(alpha = .5f),
                start = Offset.Zero, end = Offset(size.width, size.height)), style = Stroke(2.4.dp.toPx()))
        }
    }
}

/** A folder's own surface: frosted as ever, or liquid glass that bends the wallpaper like the dock's. */
internal fun Modifier.folderGlass(shape: Shape): Modifier = composed {
    if (LocalGlassStyle.current == GlassStyle.LIQUID) background(Color.White.copy(alpha = LIQUID_FOLDER_TINT), shape).liquidGlass(shape, refract = true)
    else background(Glass.copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .55f), shape)
}

/**
 * Draws the wallpaper through an element, bent towards its middle in a band along its edges, and
 * blurred as much as the wallpaper around it. The blur covers a margin beyond the element, so its
 * colours run on into the glass as they do outside it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class WallpaperLens(image: ImageBitmap) {
    private val picture = BitmapShader(image.asAndroidBitmap(), android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
    private val shader = RuntimeShader(LENS_SHADER)
    private val matrix = Matrix()
    private val paint = Paint().apply { shader = this@WallpaperLens.shader }
    private val blurred = RenderNode("liquid-glass")

    fun draw(scope: DrawScope, path: Path, radius: Float, band: Float, strength: Float, origin: Offset, drawn: Size, source: Size, blur: Float) {
        val width = scope.size.width
        val height = scope.size.height
        matrix.setScale(drawn.width / source.width, drawn.height / source.height)
        matrix.postTranslate(origin.x, origin.y)
        picture.setLocalMatrix(matrix)
        shader.setInputShader("picture", picture)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("band", band)
        shader.setFloatUniform("strength", strength)
        paint.shader = shader
        scope.clipPath(path) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                if (blur < .5f || !canvas.isHardwareAccelerated) canvas.drawRect(0f, 0f, width, height, paint)
                else {
                    val margin = ceil(blur * 3f)
                    blurred.setPosition(0, 0, (width + 2 * margin).toInt(), (height + 2 * margin).toInt())
                    blurred.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP))
                    val recording = blurred.beginRecording()
                    recording.translate(margin, margin)
                    recording.drawRect(-margin, -margin, width + margin, height + margin, paint)
                    blurred.endRecording()
                    canvas.save()
                    canvas.translate(-margin, -margin)
                    canvas.drawRenderNode(blurred)
                    canvas.restore()
                }
            }
        }
    }
}

/**
 * The distance to the rounded edge gives how deep a pixel lies in the band and which way the edge
 * faces. Near the edge the picture is sampled further in, as through the curved rim of a lens, with
 * the colours parting slightly.
 */
private const val LENS_SHADER = """
uniform shader picture;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float strength;

float edge(float2 p) {
    float2 q = abs(p - size * 0.5) - size * 0.5 + radius;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

half4 main(float2 p) {
    float distance = edge(p);
    // Outside the glass, in the margin the blur reads, and well inside it, the picture lies straight.
    if (distance >= 0.0 || distance <= -band) return picture.eval(p);
    float depth = 1.0 + distance / band;
    float2 facing = normalize(float2(edge(p + float2(1.0, 0.0)) - edge(p - float2(1.0, 0.0)),
        edge(p + float2(0.0, 1.0)) - edge(p - float2(0.0, 1.0))) + float2(0.0001));
    float2 bend = facing * depth * depth * strength;
    half4 c = picture.eval(p - bend);
    c.r = picture.eval(p - bend * 1.08).r;
    c.b = picture.eval(p - bend * 0.92).b;
    return c;
}
"""
