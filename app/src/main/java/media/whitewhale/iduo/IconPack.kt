package media.whitewhale.iduo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/** Side of a rendered launcher icon, in pixels. */
internal const val ICON_SIZE = 144

/** An installed icon pack, as listed in Settings. */
internal data class IconPackInfo(val packageName: String, val label: String, val icon: Drawable?)

/**
 * The drawing rules of an ADW/Nova-style icon pack, read from its `appfilter.xml`: a drawable per
 * launch activity, and optionally a backdrop, mask, overlay and scale for apps it does not draw.
 */
internal class IconPack(
    val packageName: String,
    /** Package and install time, which change when the pack is updated. */
    val stamp: String,
    private val resources: Resources,
    private val drawables: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float,
) {
    /** Every drawing the pack names, for choosing one icon by hand. */
    val iconNames: List<String> by lazy { drawables.values.distinct().sorted() }

    /** Package names the pack draws, for a fallback when an app renamed its launch activity. */
    private val byPackage: Map<String, String> = drawables.entries
        .groupBy({ it.key.substringBefore('/') }, { it.value }).mapValues { it.value.first() }

    // A pack's resources belong to another app, so they can only be found by name.
    @android.annotation.SuppressLint("DiscouragedApi")
    fun drawable(name: String): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 } ?: return null
        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }

    /** The pack's own drawing for [component], if it has one. */
    fun iconFor(component: ComponentName): Drawable? =
        (drawables[component.packageName + "/" + component.className] ?: byPackage[component.packageName])?.let(::drawable)

    /**
     * [icon] fitted onto the pack's backdrop, cut by its mask and covered by its overlay, so apps
     * the pack does not draw still match it. Null when the pack has no backdrop.
     */
    fun compose(icon: Bitmap, key: String): Bitmap? {
        if (backs.isEmpty()) return null
        val back = drawable(backs[Math.floorMod(key.hashCode(), backs.size)]) ?: return null
        val result = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        back.setBounds(0, 0, ICON_SIZE, ICON_SIZE); back.draw(canvas)
        val inner = (ICON_SIZE * scale).toInt().coerceIn(1, ICON_SIZE)
        val offset = (ICON_SIZE - inner) / 2f
        val layer = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(layer).apply {
            drawBitmap(Bitmap.createScaledBitmap(icon, inner, inner, true), offset, offset, null)
            mask?.let(::drawable)?.let { maskDrawable ->
                // The mask's opaque parts are cut away from the app icon.
                drawBitmap(maskDrawable.toBitmap(ICON_SIZE, ICON_SIZE), 0f, 0f,
                    Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
            }
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        upon?.let(::drawable)?.let { it.setBounds(0, 0, ICON_SIZE, ICON_SIZE); it.draw(canvas) }
        return result
    }
}

/** Finds installed icon packs and reads the chosen one. */
internal object IconPacks {
    /** The launcher theme actions icon packs register for; the manifest queries the same ones. */
    private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME")

    /** Recently read packs by stamp: the chosen pack and any opened to pick a single icon. */
    private val cached = object : LinkedHashMap<String, IconPack>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IconPack>?) = size > 3
    }

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        return ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }.map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString(), runCatching { pm.getApplicationIcon(it) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The pack in [packageName], read once per installed version; null if missing or unreadable. */
    @Synchronized
    fun load(context: Context, packageName: String): IconPack? {
        val pm = context.packageManager
        val stamp = runCatching { "$packageName@" + pm.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull() ?: return null
        cached[stamp]?.let { return it }
        val pack = runCatching { read(context, packageName, stamp) }.getOrNull() ?: return null
        cached[stamp] = pack
        return pack
    }

    // A pack's resources belong to another app, so they can only be found by name.
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun read(context: Context, packageName: String, stamp: String): IconPack? {
        val resources = context.packageManager.getResourcesForApplication(packageName)
        val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
        val parser: XmlPullParser = if (xmlId != 0) resources.getXml(xmlId) else {
            val stream = context.createPackageContext(packageName, 0).assets.open("appfilter.xml")
            Xml.newPullParser().apply { setInput(stream, null) }
        }
        val drawables = mutableMapOf<String, String>()
        val backs = mutableListOf<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        fun images() = (0 until parser.attributeCount)
            .filter { parser.getAttributeName(it).startsWith("img") }.map { parser.getAttributeValue(it) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "item" -> {
                    val component = parser.getAttributeValue(null, "component")?.let(::appfilterComponent)
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && !drawable.isNullOrBlank()) drawables.putIfAbsent(component, drawable)
                }
                "iconback" -> backs += images()
                "iconmask" -> mask = images().firstOrNull()
                "iconupon" -> upon = images().firstOrNull()
                "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.coerceIn(.2f, 1f) ?: 1f
            }
        }
        if (drawables.isEmpty() && backs.isEmpty()) return null
        return IconPack(packageName, stamp, resources, drawables, backs, mask, upon, scale)
    }
}

/** `ComponentInfo{pkg/.Activity}` from an appfilter, as a full flattened component name. */
internal fun appfilterComponent(value: String): String? {
    val inner = value.substringAfter("ComponentInfo{", "").substringBefore('}').trim()
    if (inner.isEmpty() || '/' !in inner) return null
    val pkg = inner.substringBefore('/').trim()
    var cls = inner.substringAfter('/').trim()
    if (pkg.isEmpty() || cls.isEmpty()) return null
    if (cls.startsWith('.')) cls = pkg + cls
    return "$pkg/$cls"
}
