package media.whitewhale.iduo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.runtime.mutableFloatStateOf

/** A short-lived image of our own Home window, used only while returning from Discover.
 * Google stays live in its own window; no Google pixels, permissions, or injected input are used.
 */
internal object DiscoverMotion {
    val progress = mutableFloatStateOf(1f)
    var pageWidth = 0f
    private var home: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var captureGeneration = 0

    fun capture(activity: Activity, ready: () -> Unit) {
        reset()
        val attempt = captureGeneration
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) { ready(); return }
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())
        var completed = false
        fun complete(result: Int) {
            if (completed || attempt != captureGeneration) return
            completed = true
            if (result == PixelCopy.SUCCESS) home = bitmap
            if (!activity.isFinishing && !activity.isDestroyed) ready()
        }
        try { PixelCopy.request(activity.window, bitmap, { complete(it) }, handler) }
        catch (_: IllegalArgumentException) { complete(PixelCopy.ERROR_SOURCE_INVALID) }
        // A missed copy must never prevent navigation. The image is optional.
        handler.postDelayed({ complete(PixelCopy.ERROR_TIMEOUT) }, 150)
    }

    fun reset() { captureGeneration++; home = null; pageWidth = 0f; progress.floatValue = 1f }

    fun hasHome(width: Float, height: Float): Boolean = home?.let {
        kotlin.math.abs(it.width - width) < 3 && kotlin.math.abs(it.height - height) < 3
    } == true

    /** Draw the incoming Home page in full-window coordinates, clipped before the fixed dock. */
    fun drawHome(canvas: Canvas, originX: Float, originY: Float, width: Float, height: Float) {
        val bitmap = home ?: return
        if (!hasHome(width, height) || progress.floatValue >= 1f || pageWidth <= 0f) return
        val offset = progress.floatValue.coerceIn(0f, 1f) * pageWidth
        val saved = canvas.save()
        canvas.translate(-originX, -originY)
        canvas.clipRect(offset, 0f, pageWidth, height)
        canvas.drawBitmap(bitmap, offset, 0f, paint)
        canvas.restoreToCount(saved)
    }
}
