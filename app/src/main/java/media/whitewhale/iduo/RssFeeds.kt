package media.whitewhale.iduo

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/** A news source the user added to the RSS page. */
data class RssSource(val url: String, val title: String)

/** One article from a source. [published] is epoch milliseconds, 0 when the feed gives no date. */
data class RssItem(
    val id: String,
    val sourceUrl: String,
    val sourceTitle: String,
    val title: String,
    val link: String,
    val summary: String,
    val imageUrl: String?,
    val published: Long,
)

data class ParsedFeed(val title: String, val items: List<RssItem>)

/** Most articles kept per source and in total, so the cache and the page stay small. */
internal const val RSS_ITEMS_PER_SOURCE = 40
internal const val RSS_ITEMS_TOTAL = 200

/**
 * Parses RSS 2.0, RSS 1.0 (RDF) and Atom with the platform SAX parser, which also runs in local
 * unit tests. Returns null when the document is not a feed.
 */
fun parseFeed(input: InputStream, sourceUrl: String): ParsedFeed? {
    val handler = FeedHandler(sourceUrl)
    val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
    // Feeds never need external entities; refusing them avoids XXE and network lookups.
    runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    val parsed = runCatching { factory.newSAXParser().parse(InputSource(input), handler) }
    if (!handler.isFeed) return null
    if (parsed.isFailure && handler.items.isEmpty()) return null
    val title = handler.feedTitle.trim().ifEmpty { URI(sourceUrl).host ?: sourceUrl }
    return ParsedFeed(title, handler.items.take(RSS_ITEMS_PER_SOURCE).map { it.copy(sourceTitle = title) })
}

fun parseFeed(text: String, sourceUrl: String): ParsedFeed? =
    parseFeed(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), sourceUrl)

private class FeedHandler(private val sourceUrl: String) : DefaultHandler() {
    var isFeed = false
    var feedTitle = ""
    val items = mutableListOf<RssItem>()
    private val text = StringBuilder()
    private val path = ArrayDeque<String>()
    private var entry: MutableMap<String, String>? = null
    private var entryImage: String? = null

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        val name = (localName?.takeIf { it.isNotEmpty() } ?: qName ?: "").substringAfter(':').lowercase(Locale.ROOT)
        if (path.isEmpty() && name in setOf("rss", "feed", "rdf")) isFeed = true
        path.addLast(name)
        text.setLength(0)
        val current = entry
        when {
            name == "item" || name == "entry" -> { entry = mutableMapOf(); entryImage = null }
            current != null && name == "link" -> {
                // Atom links carry the address in href; prefer the alternate (article) link.
                val href = attributes.getValue("href")
                val rel = attributes.getValue("rel") ?: "alternate"
                if (href != null && rel == "alternate" && current["link"] == null) current["link"] = href
                if (href != null && rel == "enclosure" && attributes.getValue("type")?.startsWith("image/") == true)
                    entryImage = entryImage ?: href
            }
            current != null && (name == "thumbnail" || name == "content" || name == "enclosure") -> {
                val url = attributes.getValue("url")
                val type = attributes.getValue("type") ?: attributes.getValue("medium") ?: ""
                if (url != null && (name == "thumbnail" || type.startsWith("image") || looksLikeImage(url)))
                    entryImage = entryImage ?: url
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) { text.append(ch, start, length) }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val name = path.removeLastOrNull() ?: return
        val value = text.toString().trim()
        text.setLength(0)
        val current = entry
        if (current == null) {
            if (name == "title" && feedTitle.isEmpty() && path.lastOrNull() in setOf("channel", "feed")) feedTitle = value
            return
        }
        when (name) {
            "item", "entry" -> { finishEntry(current); entry = null }
            "title" -> if (path.lastOrNull() in setOf("item", "entry")) current.putIfAbsent("title", value)
            "link" -> if (value.isNotEmpty()) current.putIfAbsent("link", value)
            "guid", "id" -> current.putIfAbsent("id", value)
            "description", "summary" -> current.putIfAbsent("summary", value)
            "encoded" -> current.putIfAbsent("content", value)
            "content" -> if (value.isNotEmpty()) current.putIfAbsent("content", value)
            "pubdate", "published", "date", "updated", "issued" -> if (value.isNotEmpty()) current.putIfAbsent("date", value)
        }
    }

    private fun finishEntry(fields: Map<String, String>) {
        val title = plainText(fields["title"].orEmpty())
        val link = fields["link"]?.let { resolveUrl(sourceUrl, it) }.orEmpty()
        if (title.isEmpty() && link.isEmpty()) return
        val body = fields["summary"] ?: fields["content"] ?: ""
        val image = entryImage ?: firstImage(fields["content"].orEmpty()) ?: firstImage(body)
        items += RssItem(
            id = (fields["id"]?.takeIf { it.isNotBlank() } ?: link.ifEmpty { title }),
            sourceUrl = sourceUrl, sourceTitle = "", title = title.ifEmpty { link }, link = link,
            summary = plainText(body).take(280), imageUrl = image?.let { resolveUrl(sourceUrl, it) },
            published = parseFeedDate(fields["date"].orEmpty()),
        )
    }
}

private fun looksLikeImage(url: String) =
    url.substringBefore('?').lowercase(Locale.ROOT).let { u -> listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any(u::endsWith) }

private val IMG_SRC = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
internal fun firstImage(html: String): String? = IMG_SRC.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") || it.startsWith("/") }

private val TAGS = Regex("<[^>]*>")
private val SPACE = Regex("\\s+")
private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "hellip" to "…", "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’",
    "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»")

/** Feed text often carries HTML markup and entities; the page shows it as plain text. */
internal fun plainText(html: String): String {
    val stripped = TAGS.replace(html, " ")
    val decoded = ENTITY.replace(stripped) { match ->
        val code = match.groupValues[1]
        when {
            code.startsWith("#x") || code.startsWith("#X") -> code.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            code.startsWith("#") -> code.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> NAMED_ENTITIES[code.lowercase(Locale.ROOT)]
        } ?: match.value
    }
    return SPACE.replace(decoded, " ").trim()
}

internal fun resolveUrl(base: String, href: String): String =
    runCatching { URI(base).resolve(href.trim()).toString() }.getOrDefault(href.trim())

private val RFC822_PATTERNS = listOf("EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz", "d MMM yyyy HH:mm:ss Z",
    "EEE, d MMM yyyy HH:mm Z", "EEE, d MMM yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss")

/** RFC 822 (RSS) and ISO 8601 (Atom) dates to epoch milliseconds; 0 when unreadable. */
internal fun parseFeedDate(value: String): Long {
    val text = value.trim()
    if (text.isEmpty()) return 0
    runCatching { return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
    runCatching { return java.time.Instant.parse(text).toEpochMilli() }
    runCatching { return java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
    for (pattern in RFC822_PATTERNS) {
        val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = true }
        val position = ParsePosition(0)
        val date = format.parse(text, position)
        if (date != null && position.index > 0) return date.time
    }
    return 0
}

private val FEED_LINK = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
private val ATTRIBUTE = Regex("""([a-zA-Z-]+)\s*=\s*["']([^"']*)["']""")

/** The feed a web page advertises with `<link rel="alternate" type="application/rss+xml" ...>`. */
internal fun discoverFeedLink(html: String, pageUrl: String): String? =
    FEED_LINK.findAll(html).mapNotNull { tag ->
        val attributes = ATTRIBUTE.findAll(tag.value).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
        val type = attributes["type"]?.lowercase(Locale.ROOT).orEmpty()
        val href = attributes["href"]
        if (attributes["rel"]?.lowercase(Locale.ROOT)?.contains("alternate") == true && href != null &&
            (type.contains("rss") || type.contains("atom"))) resolveUrl(pageUrl, href) else null
    }.firstOrNull()

/** What the user typed, as an https address: a bare host gains a scheme, and http is upgraded. */
internal fun normalizeFeedAddress(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
        trimmed.contains("://") -> return null
        else -> "https://$trimmed"
    }
    val host = runCatching { URI(withScheme).host }.getOrNull()
    return withScheme.takeIf { host != null && host.contains('.') }
}

/** Newest first, undated articles last, duplicates (same link) once, limited to [RSS_ITEMS_TOTAL]. */
internal fun mergeFeedItems(items: List<RssItem>): List<RssItem> =
    items.distinctBy { it.link.ifEmpty { it.sourceUrl + it.id } }
        .sortedWith(compareByDescending<RssItem> { it.published }.thenBy { it.sourceTitle })
        .take(RSS_ITEMS_TOTAL)
