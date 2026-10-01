package media.whitewhale.iduo

import android.content.pm.ApplicationInfo
import java.text.Normalizer

/** The library's folders, in the order it shows them. [RECENT] holds recently opened apps. */
enum class AppCategory(val label: Int) {
    RECENT(R.string.category_recent),
    SOCIAL(R.string.category_social),
    PRODUCTIVITY(R.string.category_productivity),
    MEDIA(R.string.category_media),
    PHOTO(R.string.category_photo),
    FINANCE(R.string.category_finance),
    SHOPPING(R.string.category_shopping),
    HEALTH(R.string.category_health),
    TRAVEL(R.string.category_travel),
    UTILITIES(R.string.category_utilities),
    GAMES(R.string.category_games),
    NEWS(R.string.category_news),
    EDUCATION(R.string.category_education),
    GOOGLE(R.string.category_google),
    MAKER(R.string.category_maker),
    SYSTEM(R.string.category_system),
    OTHER(R.string.category_other),
}

/** Package prefixes of phone makers' own apps. */
private val MAKER_PREFIXES = listOf("com.samsung.", "com.sec.", "com.osp.", "com.miui.", "com.xiaomi.", "com.huawei.",
    "com.hihonor.", "com.oneplus.", "com.oppo.", "com.coloros.", "com.heytap.", "com.realme.", "com.motorola.",
    "com.sonymobile.", "com.asus.", "com.nothing.", "com.lge.", "com.htc.", "com.vivo.", "com.zte.")

/**
 * Words in an app's package name or label that tell its purpose, checked in this order. Words of
 * five letters or more also match inside longer words; shorter ones must stand alone, so "fit"
 * matches "AFit Health" but not "benefit".
 */
private val KEYWORDS: List<Pair<AppCategory, List<String>>> = listOf(
    AppCategory.FINANCE to listOf("bank", "banka", "banking", "smartbanking", "airbank", "fio", "moneta", "raiffeisen",
        "csob", "george", "revolut", "paypal", "klarna", "twisto", "pluxee", "sodexo", "edenred", "generali", "pojistovna",
        "allianz", "kooperativa", "insurance", "invest", "trading", "broker", "crypto", "coinbase", "binance", "wise",
        "fakturoid", "finance", "pay"),
    AppCategory.HEALTH to listOf("health", "fitness", "fit", "gym", "workout", "sleep", "meditation", "meditate", "calm",
        "headspace", "hydration", "hydratace", "weight", "yazio", "nutri", "diet", "vzp", "eocko", "ezkarta", "strava",
        "garmin", "running", "yoga", "finch", "wellbeing", "zdravi"),
    AppCategory.SHOPPING to listOf("shop", "eshop", "store", "alza", "allegro", "amazon", "ebay", "aliexpress", "temu",
        "vinted", "kaufland", "lidl", "tesco", "clubcard", "albert", "billa", "penny", "rohlik", "globus", "rossmann",
        "ikea", "zalando", "sinsay", "cropp", "action", "kupi", "decathlon", "wolt", "foodora", "starbucks", "zasilkovna",
        "bageterie", "bbdomu", "mall"),
    AppCategory.TRAVEL to listOf("travel", "trip", "booking", "airbnb", "flight", "maps", "mapy", "navigation", "waze",
        "transport", "litacka", "vlak", "idos", "uber", "bolt", "taxi", "parking", "easypark", "parksimply", "fuel",
        "fuelio", "lpg", "benzina", "orlen", "shell", "molcesko", "drone", "dronemap", "dukapka", "ryanair", "regiojet",
        "dacia", "carscanner", "midrive", "dashcam"),
    AppCategory.MEDIA to listOf("music", "spotify", "audio", "audioteka", "audiobook", "podcast", "radio", "tidal",
        "deezer", "soundcloud", "guitar", "tguitar", "tabs", "suno", "reader", "elevenlabs", "video", "videos", "tv",
        "ivysilani", "voyo", "oneplay", "prima", "iprima", "netflix", "hbo", "disney", "stremio", "twitch", "player",
        "movie", "film", "cinema", "kino"),
    AppCategory.PHOTO to listOf("photo", "camera", "cam", "gallery", "snapseed", "lightroom", "canva", "picsart",
        "gopro", "vlog", "dji", "ronin", "realityscan", "portalapp"),
    AppCategory.GAMES to listOf("game", "games", "xbox", "steam", "playstation", "nintendo", "lego"),
    AppCategory.SOCIAL to listOf("chat", "messenger", "message", "whatsapp", "telegram", "signal", "facebook",
        "instagram", "discord", "twitter", "threads", "tiktok", "snapchat", "viber", "pinterest", "reddit", "luma"),
    AppCategory.EDUCATION to listOf("education", "learn", "duolingo", "school", "skola", "course", "quiz", "babbel",
        "merlin", "birdid"),
    AppCategory.NEWS to listOf("news", "zpravy", "magazine", "rss", "feed"),
    AppCategory.UTILITIES to listOf("launcher", "kwgt", "kustom", "widget", "widgetwall", "icon", "iconpack", "theme",
        "weather", "weawow", "meteor", "kweather", "shizuku", "terminal", "speedtest", "authenticator", "bitwarden",
        "password", "vpn", "files", "filemanager", "browser", "brave", "firefox", "opera", "tuya", "govee",
        "homeassistant", "irobot", "lockin", "ipixels", "printer", "printercontrol", "prusa", "colbor", "foldy",
        "duofold", "ampscan", "remote"),
    AppCategory.PRODUCTIVITY to listOf("office", "word", "excel", "outlook", "notion", "slack", "teams", "zoom",
        "chatgpt", "claude", "gemini", "deepl", "translator", "github", "teleprompter", "notes", "calendar", "todo",
        "task", "edoklady", "obcan", "portal", "linkedin", "companyportal"),
)

private val DIACRITICS = Regex("\\p{Mn}+")
private val SEPARATORS = Regex("[^a-z0-9]+")

/** Lowercase words of [text] without accents, so "Lítačka" and "litacka" match. */
private fun words(text: String): List<String> =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "").split(SEPARATORS).filter(String::isNotEmpty)

/**
 * The library folder for an app: Google's and the phone maker's apps first, then words in its
 * name, then Android's category for it. Many apps call themselves "productivity" whatever they
 * do, so that category counts last.
 */
fun appCategory(packageName: String, label: String, androidCategory: Int, preinstalled: Boolean): AppCategory {
    if (packageName.startsWith("com.google.") || packageName == "com.android.chrome" || packageName == "com.android.vending")
        return AppCategory.GOOGLE
    if (MAKER_PREFIXES.any(packageName::startsWith)) return AppCategory.MAKER
    val tokens = words(packageName) + words(label)
    KEYWORDS.firstOrNull { (_, keywords) ->
        keywords.any { keyword -> tokens.any { it == keyword || (keyword.length >= 5 && it.contains(keyword)) } }
    }?.let { return it.first }
    when (androidCategory) {
        ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.MEDIA
        ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.PHOTO
        ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRAVEL
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppCategory.UTILITIES
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
    }
    return if (preinstalled || packageName.startsWith("com.android.")) AppCategory.SYSTEM else AppCategory.OTHER
}

/** The library's folders: recent apps first, then every category that has apps, each alphabetical. */
fun <T> libraryFolders(apps: List<T>, recent: List<String>, id: (T) -> String, category: (T) -> AppCategory): List<Pair<AppCategory, List<T>>> {
    val byId = apps.associateBy(id)
    val recentApps = recent.mapNotNull(byId::get).take(8)
    val grouped = apps.groupBy(category)
    return listOfNotNull(recentApps.takeIf { it.isNotEmpty() }?.let { AppCategory.RECENT to it }) +
        AppCategory.entries.filter { it != AppCategory.RECENT }.mapNotNull { category -> grouped[category]?.let { category to it } }
}
