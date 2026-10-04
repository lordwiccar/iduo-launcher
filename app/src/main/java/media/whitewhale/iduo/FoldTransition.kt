package media.whitewhale.iduo

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI

/**
 * Which screen Home last left because the phone folded or unfolded, and when. Android starts a
 * new Home window on the other screen, which reads this to know it arrived by a fold and should
 * come into focus rather than simply appear.
 */
internal object FoldHandoff {
    private var leftCover: Boolean? = null
    private var leftAt = 0L

    fun leave(cover: Boolean) {
        leftCover = cover
        leftAt = SystemClock.uptimeMillis()
    }

    /** Whether this window on the [cover] or the inner screen replaces one on the other screen. */
    fun arrived(cover: Boolean): Boolean {
        val from = leftCover
        leftCover = null
        return from != null && from != cover && SystemClock.uptimeMillis() - leftAt < ARRIVAL_WINDOW_MS
    }

    /** Longer than the slowest measured hand-over from one screen to the other. */
    private const val ARRIVAL_WINDOW_MS = 8_000L
}

/**
 * The hinge angle in degrees, 0 shut to 180 flat, or null until Android reports it. Samsung's
 * public hinge sensor only ever reports 0, 90 and 180, so on a Galaxy Fold this says shut, half
 * open or flat; other foldables and the emulator report every angle. Listens only while Home is
 * started.
 */
internal class HingeMonitor(context: Context) : DefaultLifecycleObserver, SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val hinge: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    var angle by mutableStateOf<Float?>(null)
        private set

    override fun onStart(owner: LifecycleOwner) {
        hinge?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onStop(owner: LifecycleOwner) {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        angle = event.values[0].coerceIn(0f, 180f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * How far Home is out of focus because the phone is folding or unfolding, 0 sharp to 1 fully
 * frosted. Half open is fully frosted on both screens. On the inner screen it clears as the
 * phone opens flat; on the [cover] it clears as the phone shuts. A window that [arrived] by a
 * fold, or whose window moved to the other screen, starts frosted and clears once the hinge allows and Home is in front, not while the lock
 * screen still covers it.
 */
@Composable
internal fun rememberFoldFrost(cover: Boolean, arrived: Boolean, hinge: HingeMonitor): State<Float> {
    val frost = remember { Animatable(if (arrived) 1f else 0f) }
    val resumed = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val target = if (resumed) foldFrostTarget(cover, hinge.angle) else frost.value
    // Samsung sometimes moves Home's window to the other screen instead of starting a new one;
    // that is an arrival too.
    val shownOnCover = remember { booleanArrayOf(cover) }
    LaunchedEffect(target, cover) {
        if (shownOnCover[0] != cover) {
            shownOnCover[0] = cover
            frost.snapTo(1f)
        }
        // Clearing is the arrival and is given time to be seen; frosting answers the hand at once.
        if (target < frost.value) frost.animateTo(target, tween(CLEAR_MS, easing = LinearOutSlowInEasing))
        else frost.animateTo(target, tween(FROST_MS, easing = FastOutSlowInEasing))
    }
    return frost.asState()
}

/**
 * How frosted Home should be at a hinge [angle], 0 sharp to 1 fully frosted: half open is fully
 * frosted on both screens; the inner screen clears toward flat and the [cover] toward shut. An
 * unknown angle leaves Home sharp.
 */
internal fun foldFrostTarget(cover: Boolean, angle: Float?): Float = when {
    angle == null -> 0f
    cover -> (angle / HALF_OPEN_DEG).coerceIn(0f, 1f)
    else -> ((180f - angle) / (180f - HALF_OPEN_DEG)).coerceIn(0f, 1f)
}

/**
 * Draws Home as if the half of the phone that swings were glass tilting away from the viewer
 * about the hinge: the hinge stays sharp, and toward the free edge the picture recedes into a
 * narrowing wedge, blurs and falls into shadow, with dark corners where it no longer covers the
 * screen. On the inner screen that is the half opposite the dock, the cover's side; the cover
 * recedes from its hinge edge across to its free edge. Nothing happens below Android 13, which
 * has no runtime shaders.
 */
internal fun Modifier.foldFrost(frost: State<Float>, cover: Boolean): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) this else foldFrostEffect(frost, cover)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.foldFrostEffect(frost: State<Float>, cover: Boolean): Modifier =
    composed {
        // A screen whose graphics driver refuses the shader simply shows Home without the effect.
        val shader = remember { runCatching { RuntimeShader(FOLD_FROST_SHADER) }.getOrNull() }
        val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
        val metrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
        val pxPerMm = (if (metrics.xdpi > 0f) metrics.xdpi else metrics.densityDpi.toFloat()) / 25.4f
        val look = if (cover) FoldLook.COVER else FoldLook.INNER
        graphicsLayer {
            val amount = frost.value
            if (shader == null || amount <= .001f || size.width <= 0f || size.height <= 0f) {
                renderEffect = null
                return@graphicsLayer
            }
            val (dx, dy) = freeEdgeDirection(cover, rotation)
            val across = if (dx != 0f) size.width else size.height
            val extent = if (cover) across else across / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            // The hinge runs through the middle of the inner screen and along the cover's edge.
            val hx = if (cover) cx - dx * across / 2f else cx
            val hy = if (cover) cy - dy * across / 2f else cy
            // The viewer faces the middle of the swinging half, looking down on it from above
            // its centre when the hinge is upright.
            val ex = if (dx != 0f) hx + dx * extent / 2f else size.width / 2f
            val ey = if (dx != 0f) size.height * look.eyeHeight else hy + dy * extent / 2f
            // Blur and its shading were set on a 15.9 px/mm screen; keep them the same size in millimetres.
            val scale = pxPerMm / REFERENCE_PX_PER_MM
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("hinge", hx, hy)
            shader.setFloatUniform("dir", dx, dy)
            shader.setFloatUniform("eyeAt", ex, ey)
            shader.setFloatUniform("extent", extent)
            shader.setFloatUniform("tilt", amount * MAX_TILT_DEG * PI.toFloat() / 180f)
            shader.setFloatUniform("eye", look.eyeMm * pxPerMm)
            shader.setFloatUniform("spread", look.spread)
            shader.setFloatUniform("maxBlur", look.maxBlurPx * scale)
            shader.setFloatUniform("shade", look.edgeShade)
            shader.setFloatUniform("blurShade", look.blurShade)
            shader.setFloatUniform("milk", look.milk)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }

/**
 * How each screen recedes. The inner screen is held flat and looked down on, so its viewer sits
 * well above the middle; the cover's a little less so.
 */
private enum class FoldLook(val eyeMm: Float, val eyeHeight: Float, val spread: Float, val maxBlurPx: Float,
    val edgeShade: Float, val blurShade: Float, val milk: Float) {
    INNER(eyeMm = 260f, eyeHeight = .30f, spread = .088f, maxBlurPx = 52f, edgeShade = .85f, blurShade = .39f, milk = .10f),
    COVER(eyeMm = 240f, eyeHeight = .38f, spread = .084f, maxBlurPx = 56f, edgeShade = .90f, blurShade = .42f, milk = .10f),
}

/**
 * The way from the hinge to the swinging half's free edge on screen: to the left of the hinge on
 * the inner screen and to the right of it on the cover, in the screen's natural orientation.
 */
internal fun freeEdgeDirection(cover: Boolean, rotation: Int): Pair<Float, Float> {
    val nx = if (cover) 1f else -1f
    return when (rotation) {
        Surface.ROTATION_90 -> 0f to -nx
        Surface.ROTATION_180 -> -nx to 0f
        Surface.ROTATION_270 -> 0f to nx
        else -> nx to 0f
    }
}

/**
 * Frosts Android's own wallpaper behind the swinging half, which the shader in [foldFrost] cannot
 * reach because Android draws it behind Home's window. Samsung's blur covers whole rectangles, so
 * the half is cut into strips that blur more the further they lie from the hinge. Only on
 * Samsung phones, and only for a wallpaper iDuo does not draw itself.
 */
@Composable
internal fun FoldWallpaperBlur(frost: State<Float>, cover: Boolean) {
    if (!SamsungBlur.available || HomeWallpaper.image != null) return
    // Each change of Samsung's blur redraws the window behind, so it moves in steps.
    val step by remember { androidx.compose.runtime.derivedStateOf {
        kotlin.math.round(frost.value * SAMSUNG_BLUR_STEPS).toInt() } }
    if (step == 0) return
    val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val (dx, dy) = freeEdgeDirection(cover, rotation)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val across = if (dx != 0f) maxWidth else maxHeight
        val half = if (cover) across else across / 2
        val strip = half / WALLPAPER_STRIPS
        for (i in 0 until WALLPAPER_STRIPS) {
            // Strip i lies i strips out from the hinge toward the free edge.
            val near = if (cover) strip * i else across / 2 + strip * i
            val start = if ((if (dx != 0f) dx else dy) > 0f) near else across - near - strip
            val place = if (dx != 0f) Modifier.offset(x = start).width(strip).fillMaxHeight()
                else Modifier.offset(y = start).height(strip).fillMaxWidth()
            SamsungWallpaperBlur(FOLD_WALLPAPER_BLUR * step * (i + 1) / (SAMSUNG_BLUR_STEPS * WALLPAPER_STRIPS),
                0.dp, androidx.compose.ui.graphics.Color.Transparent, place)
        }
    }
}

private const val WALLPAPER_STRIPS = 4
private val FOLD_WALLPAPER_BLUR = 48.dp

/** Hinge angle that counts as half open, where both screens are fully frosted. */
private const val HALF_OPEN_DEG = 90f
private const val CLEAR_MS = 650
private const val FROST_MS = 300
/** How far the swinging half seems to tilt away from the viewer when fully frosted. */
private const val MAX_TILT_DEG = 60f
private const val REFERENCE_PX_PER_MM = 15.9f

/**
 * Per pixel: the swinging half tilts by `tilt` about the hinge, so a point `s` from the hinge
 * moves to `s cos(tilt)` across the screen and `s sin(tilt)` out of it. A ray from the viewer
 * through that point, carried on to the screen, finds what shows here: further out the more the
 * point has left the screen, so the far side shrinks into a wedge. Off the screen is shadow.
 * Around the point a disc of samples blurs in proportion to its depth, and the free edge darkens
 * and slightly whitens. Pixels across the hinge pass through. Colours are premultiplied, and the
 * shading also darkens Android's wallpaper behind wherever Home is see-through.
 */
private const val FOLD_FROST_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 hinge;
uniform float2 dir;
uniform float2 eyeAt;
uniform float extent;
uniform float tilt;
uniform float eye;
uniform float spread;
uniform float maxBlur;
uniform float shade;
uniform float blurShade;
uniform float milk;

half4 main(float2 p) {
    float s = dot(p - hinge, dir);
    if (s <= 0.0) {
        return content.eval(p);
    }
    float2 side = float2(-dir.y, dir.x);
    float sn = sin(tilt);
    float2 turned = hinge + dir * (s * cos(tilt)) + side * dot(p - hinge, side);
    float depth = s * sn;
    float2 hit = eyeAt + (turned - eyeAt) * (eye / max(eye - depth, eye * 0.25));
    float radius = min(spread * depth, maxBlur);
    if (hit.x < -radius || hit.y < -radius || hit.x > size.x + radius || hit.y > size.y + radius) {
        return half4(0.02, 0.02, 0.02, 1.0);
    }
    float t = clamp(s / extent, 0.0, 1.0);
    float edge = clamp(pow(t, 1.8) * (tilt / 0.785398) * 1.35, 0.0, 1.0) * shade;
    float atten = clamp((1.0 - edge) * (1.0 - blurShade * radius / maxBlur), 0.0, 1.0);
    float white = milk * (radius / maxBlur) * (1.0 - edge);
    // Each pixel turns its disc by a different amount, so too few samples read as frosted grain
    // rather than as ghost copies of the picture.
    float spin = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) * 6.28318;
    half4 sum = half4(0.0);
    for (int i = 0; i < 16; i++) {
        float k = float(i);
        float a = k * 2.39996 + spin;
        float r = radius * sqrt((k + 0.5) / 16.0);
        sum += content.eval(hit + float2(cos(a), sin(a)) * r);
    }
    half4 c = sum / 16.0;
    half4 o = half4(c.rgb * half(atten), 1.0 - (1.0 - c.a) * half(atten));
    return half4(o.rgb * half(1.0 - white) + half(white), 1.0 - (1.0 - o.a) * half(1.0 - white));
}
"""
