package media.whitewhale.iduo

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language. Android 13+ keeps the choice in the system (it also appears under the app's
 * Language setting in Android Settings); Android 12 keeps it here and wraps each context.
 */
object AppLanguage {
    /** The empty tag follows the system language. */
    val tags = listOf("", "en", "cs", "sk", "pl", "de", "zh-Hans", "ja", "ko")
    private const val PREFS = "language"
    private const val KEY = "tag"

    fun current(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) "" else tagFor(locales[0])
        } else prefs(context).getString(KEY, "").orEmpty()
        return tag.takeIf { it in tags } ?: ""
    }

    /** The offered language for [locale]; a script, as in Traditional Chinese, must not differ from the offered one. */
    internal fun tagFor(locale: Locale): String = tags.drop(1).firstOrNull { tag ->
        val offered = Locale.forLanguageTag(tag)
        offered.language == locale.language && (locale.script.isEmpty() || locale.script == offered.script)
    }.orEmpty()

    fun set(activity: Activity, tag: String) {
        if (tag == current(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates visible activities with the new configuration.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().putString(KEY, tag).commit()
            activity.recreate()
        }
    }

    /** Applies the stored language before Android 13; later versions localize every context themselves. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY, "").orEmpty()
        if (tag !in tags || tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return base.createConfigurationContext(config)
    }

    /** The device language, ignoring this app's own choice. */
    fun systemLocale(context: Context): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.getSystemService(LocaleManager::class.java).systemLocales[0]
        else android.content.res.Resources.getSystem().configuration.locales[0]

    /** A language's own name, so people can find theirs whatever the current language is. */
    fun nativeName(tag: String): String {
        if (tag == "zh-Hans") return "简体中文"
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Profile labels are stored in English; show the built-in ones in the current language. */
@androidx.compose.runtime.Composable
internal fun profileName(label: String): String = when (label) {
    "Personal" -> androidx.compose.ui.res.stringResource(R.string.profile_personal)
    "Work" -> androidx.compose.ui.res.stringResource(R.string.profile_work)
    else -> label
}

/** A locale's own ordering and word forms for [skeleton]: "MMMMd" is "MMMM d" in English and "d. MMMM" in Czech. */
internal fun localizedDateFormatter(locale: Locale, skeleton: String): java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** A failure whose message is already localized for people; other failures show a generic message. */
internal class UserFacingException(message: String) : IllegalArgumentException(message)

internal fun Throwable.userMessage(fallback: String): String = (this as? UserFacingException)?.message ?: fallback

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
