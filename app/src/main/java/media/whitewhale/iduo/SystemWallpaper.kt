package media.whitewhale.iduo

import android.annotation.SuppressLint
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/** Samsung's flags for the inner (main) and cover (sub) screen's wallpaper. */
private const val SAMSUNG_MAIN_DISPLAY = 4
private const val SAMSUNG_SUB_DISPLAY = 16

/** Where Android shows a wallpaper that iDuo sets. */
enum class WallpaperTarget(val flags: Int) {
    HOME(WallpaperManager.FLAG_SYSTEM),
    LOCK(WallpaperManager.FLAG_LOCK),
    BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK);

    val changesHome get() = flags and WallpaperManager.FLAG_SYSTEM != 0
}

/**
 * Android's Home wallpaper is iDuo's background: launcher windows show it directly, so a change
 * made in Android appears at once and a wallpaper chosen in iDuo is set in Android.
 *
 * Apps cannot read the wallpaper's pixels. The customization preview, which must paint a
 * stand-in, uses a mirror of a wallpaper iDuo set itself, trusted only while
 * Android still reports that wallpaper's id, and otherwise the wallpaper's colours.
 */
internal object SystemWallpaper {
    /** Colours of the Home wallpaper; snapshot state so stand-ins repaint when it changes. */
    var colors by mutableStateOf<WallpaperColors?>(null)
        private set
    /** Colours of the cover screen's own Home wallpaper, on Samsung foldables that give it one. */
    private var coverColors by mutableStateOf<WallpaperColors?>(null)

    /** Colours of the Home wallpaper on the cover or inner screen. */
    fun colorsFor(cover: Boolean): WallpaperColors? = if (cover) coverColors ?: colors else colors
    /** True while the Home wallpaper is the bundled iDuo dunes that iDuo set. */
    var mirrorsBundled by mutableStateOf(false)
        private set
    private var applying = 0
    private var listening = false

    /** Main thread. Reads the current colours and drops a mirror of a wallpaper that was replaced. */
    fun initialize(context: Context) {
        val app = context.applicationContext
        val manager = WallpaperManager.getInstance(app)
        if (!listening) {
            listening = true
            // Fires for every wallpaper change, including ones made while iDuo is in the background.
            manager.addOnColorsChangedListener({ updated, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) { readColors(manager); verify(app) }
            }, Handler(Looper.getMainLooper()))
        }
        readColors(manager)
        verify(app)
    }

    /**
     * Samsung foldables keep a Home wallpaper for each screen, selected by extra flags, and the
     * plain Home flag reports the inner screen's. Elsewhere both screens share one wallpaper.
     */
    private fun readColors(manager: WallpaperManager) {
        fun read(which: Int) = runCatching { manager.getWallpaperColors(which) }.getOrNull()
        val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        colors = (if (samsung) read(WallpaperManager.FLAG_SYSTEM or SAMSUNG_MAIN_DISPLAY) else null)
            ?: read(WallpaperManager.FLAG_SYSTEM)
        coverColors = if (samsung) read(WallpaperManager.FLAG_SYSTEM or SAMSUNG_SUB_DISPLAY) else null
    }

    /** Main thread. Keeps the mirror only while Android still shows the wallpaper iDuo set. */
    fun verify(context: Context) {
        if (applying > 0) return
        val mirrored = mirroredWallpaperId(context)
        val current = homeWallpaperId(context)
        val valid = mirrored != null && current != null && mirrored == current
        if (!valid && (mirrored != null || launcherBackgroundEnabled(context))) forgetWallpaperMirror(context)
        mirrorsBundled = valid && mirrorIsBundled(context)
        if (mirrorsBundled && DefaultWallpaper.bitmap == null) {
            val app = context.applicationContext
            thread(name = "default-wallpaper") { DefaultWallpaper.load(app) }
        }
    }

    /**
     * Main thread, around setting a wallpaper: Android reports the change before iDuo has
     * recorded the new wallpaper's id, and that report must not discard the fresh mirror.
     */
    fun beginApply() { applying++ }
    fun endApply(context: Context) { applying--; verify(context) }

    /** Background thread. Returns the new Home wallpaper id, or null if Home was not changed. */
    fun set(context: Context, bitmap: Bitmap, target: WallpaperTarget): Int? {
        val id = WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, target.flags)
        check(id != 0) { "Android did not accept the wallpaper" }
        return if (target.changesHome) homeWallpaperId(context) else null
    }

    /** Background thread. Sets the bundled iDuo dunes at full resolution. */
    // The nodpi JPEG is packaged unchanged, so its raw bytes are the original image.
    @SuppressLint("ResourceType")
    fun setBundled(context: Context, target: WallpaperTarget): Int? {
        val id = context.resources.openRawResource(R.drawable.default_wallpaper).use {
            WallpaperManager.getInstance(context).setStream(it, null, true, target.flags)
        }
        check(id != 0) { "Android did not accept the wallpaper" }
        return if (target.changesHome) homeWallpaperId(context) else null
    }

    private fun homeWallpaperId(context: Context): Int? = runCatching {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrNull()?.takeIf { it > 0 }
}

/** Blurs whatever lies behind this window (Android's wallpaper, for Home) by [radius] pixels; 0 clears it. */
internal fun android.view.Window.setWallpaperBlur(radius: Int) {
    if (radius > 0) addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    else clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    attributes = attributes.also { it.blurBehindRadius = radius }
}
