package media.whitewhale.iduo

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * A Google News edition: the `hl` language Google expects, the country ([gl]) and the edition id
 * ([ceid]). Every entry was checked to return its own headlines; Google silently falls back to
 * another edition for unsupported combinations, which are therefore not listed.
 */
enum class NewsEdition(val hl: String, val gl: String, val ceid: String) {
    US("en-US", "US", "US:en"),
    US_ES("es-419", "US", "US:es-419"),
    CA("en-CA", "CA", "CA:en"),
    CA_FR("fr-CA", "CA", "CA:fr"),
    MX("es-419", "MX", "MX:es-419"),
    BR("pt-BR", "BR", "BR:pt-419"),
    AR("es-419", "AR", "AR:es-419"),
    CO("es-419", "CO", "CO:es-419"),
    CL("es-419", "CL", "CL:es-419"),
    PE("es-419", "PE", "PE:es-419"),
    VE("es-419", "VE", "VE:es-419"),
    CU("es-419", "CU", "CU:es-419"),
    GB("en-GB", "GB", "GB:en"),
    IE("en-IE", "IE", "IE:en"),
    DE("de", "DE", "DE:de"),
    AT("de", "AT", "AT:de"),
    CH("de", "CH", "CH:de"),
    CH_FR("fr", "CH", "CH:fr"),
    FR("fr", "FR", "FR:fr"),
    BE("fr", "BE", "BE:fr"),
    BE_NL("nl", "BE", "BE:nl"),
    NL("nl", "NL", "NL:nl"),
    IT("it", "IT", "IT:it"),
    ES("es", "ES", "ES:es"),
    PT("pt-PT", "PT", "PT:pt-150"),
    PL("pl", "PL", "PL:pl"),
    CZ("cs", "CZ", "CZ:cs"),
    SK("sk", "SK", "SK:sk"),
    HU("hu", "HU", "HU:hu"),
    RO("ro", "RO", "RO:ro"),
    BG("bg", "BG", "BG:bg"),
    GR("el", "GR", "GR:el"),
    SI("sl", "SI", "SI:sl"),
    RS("sr", "RS", "RS:sr"),
    SE("sv", "SE", "SE:sv"),
    NO("no", "NO", "NO:no"),
    FI("fi", "FI", "FI:fi"),
    EE("et", "EE", "EE:et"),
    LV("lv", "LV", "LV:lv"),
    LT("lt", "LT", "LT:lt"),
    UA("uk", "UA", "UA:uk"),
    UA_RU("ru", "UA", "UA:ru"),
    RU("ru", "RU", "RU:ru"),
    TR("tr", "TR", "TR:tr"),
    IL("he", "IL", "IL:he"),
    IL_EN("en-IL", "IL", "IL:en"),
    EG("ar", "EG", "EG:ar"),
    SA("ar", "SA", "SA:ar"),
    AE("ar", "AE", "AE:ar"),
    LB("ar", "LB", "LB:ar"),
    MA("ar", "MA", "MA:ar"),
    MA_FR("fr", "MA", "MA:fr"),
    SN("fr", "SN", "SN:fr"),
    NG("en-NG", "NG", "NG:en"),
    GH("en-GH", "GH", "GH:en"),
    KE("en-KE", "KE", "KE:en"),
    UG("en-UG", "UG", "UG:en"),
    TZ("en-TZ", "TZ", "TZ:en"),
    ET("en-ET", "ET", "ET:en"),
    ZA("en-ZA", "ZA", "ZA:en"),
    NA("en-NA", "NA", "NA:en"),
    BW("en-BW", "BW", "BW:en"),
    ZW("en-ZW", "ZW", "ZW:en"),
    IN("en-IN", "IN", "IN:en"),
    IN_HI("hi", "IN", "IN:hi"),
    IN_BN("bn", "IN", "IN:bn"),
    IN_TA("ta", "IN", "IN:ta"),
    IN_TE("te", "IN", "IN:te"),
    IN_MR("mr", "IN", "IN:mr"),
    IN_ML("ml", "IN", "IN:ml"),
    PK("en-PK", "PK", "PK:en"),
    BD("bn", "BD", "BD:bn"),
    JP("ja", "JP", "JP:ja"),
    KR("ko", "KR", "KR:ko"),
    CN("zh-CN", "CN", "CN:zh-Hans"),
    TW("zh-TW", "TW", "TW:zh-Hant"),
    HK("zh-HK", "HK", "HK:zh-Hant"),
    TH("th", "TH", "TH:th"),
    VN("vi", "VN", "VN:vi"),
    ID("id", "ID", "ID:id"),
    MY("en-MY", "MY", "MY:en"),
    MY_MS("ms", "MY", "MY:ms"),
    SG("en-SG", "SG", "SG:en"),
    PH("en-PH", "PH", "PH:en"),
    AU("en-AU", "AU", "AU:en"),
    NZ("en-NZ", "NZ", "NZ:en");

    /** The edition's base language, as in [Locale.getLanguage]. */
    val language get() = ceid.substringAfter(':').substringBefore('-')
}

/** "Country" or, where a country has several editions, "Country (language)", in [locale]. */
fun NewsEdition.label(locale: Locale): String {
    val country = Locale("", gl).getDisplayCountry(locale)
    if (NewsEdition.entries.count { it.gl == gl } == 1) return country
    return "$country (${Locale.forLanguageTag(hl).getDisplayLanguage(locale)})"
}

/** Google News sections; [TOP] is the edition's front page, the rest are its topic sections. */
enum class NewsTopic(@StringRes val label: Int, val section: String?) {
    TOP(R.string.news_topic_top, null),
    WORLD(R.string.news_topic_world, "WORLD"),
    NATION(R.string.news_topic_nation, "NATION"),
    BUSINESS(R.string.news_topic_business, "BUSINESS"),
    TECHNOLOGY(R.string.news_topic_technology, "TECHNOLOGY"),
    SCIENCE(R.string.news_topic_science, "SCIENCE"),
    HEALTH(R.string.news_topic_health, "HEALTH"),
    SPORTS(R.string.news_topic_sports, "SPORTS"),
    ENTERTAINMENT(R.string.news_topic_entertainment, "ENTERTAINMENT"),
}

/** Google's public RSS address for one section of one edition. */
fun googleNewsFeedUrl(edition: NewsEdition, topic: NewsTopic): String {
    val query = "hl=${edition.hl}&gl=${edition.gl}&ceid=${edition.ceid}"
    return if (topic.section == null) "https://news.google.com/rss?$query"
    else "https://news.google.com/rss/headlines/section/topic/${topic.section}?$query"
}

/**
 * The edition for a language and country: an exact match, else the language's first edition, else
 * the country's first edition, else the US.
 */
fun defaultNewsEdition(language: String, country: String): NewsEdition {
    // Older Android versions report Hebrew with its legacy code.
    val lang = if (language == "iw") "he" else language
    return NewsEdition.entries.firstOrNull { it.language == lang && it.gl.equals(country, ignoreCase = true) }
        ?: NewsEdition.entries.firstOrNull { it.language == lang }
        ?: NewsEdition.entries.firstOrNull { it.gl.equals(country, ignoreCase = true) }
        ?: NewsEdition.US
}

/** Sources for the chosen sections, in [NewsTopic] order so the list is stable. */
fun googleNewsSources(edition: NewsEdition, topics: Set<NewsTopic>): List<RssSource> =
    NewsTopic.entries.filter { it in topics }.map { RssSource(googleNewsFeedUrl(edition, it), "Google News") }

private const val PREFS = "google_news"
private const val EDITION = "edition"
private const val TOPICS = "topics"

/** The chosen Google News edition and sections, kept on the device. */
internal object GoogleNewsSettings {
    var edition by mutableStateOf(NewsEdition.US)
        private set
    var topics by mutableStateOf(setOf(NewsTopic.TOP))
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val language = AppLanguage.current(context).ifEmpty { AppLanguage.systemLocale(context).language }
        edition = NewsEdition.entries.firstOrNull { it.name == prefs.getString(EDITION, null) }
            ?: defaultNewsEdition(language, Locale.getDefault().country)
        topics = prefs.getString(TOPICS, null)?.split(',')?.mapNotNull { name -> NewsTopic.entries.firstOrNull { it.name == name } }
            ?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(NewsTopic.TOP)
        NewsFeeds.googleNews.replaceSources(context, googleNewsSources(edition, topics))
    }

    fun setEdition(context: Context, value: NewsEdition) { edition = value; save(context) }

    /** Turns a section on or off; the last section stays on so the page is never empty. */
    fun toggle(context: Context, topic: NewsTopic) {
        val next = if (topic in topics) topics - topic else topics + topic
        if (next.isEmpty()) return
        topics = next; save(context)
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(EDITION, edition.name).putString(TOPICS, topics.joinToString(",") { it.name }).apply()
        NewsFeeds.googleNews.replaceSources(context, googleNewsSources(edition, topics))
    }
}
