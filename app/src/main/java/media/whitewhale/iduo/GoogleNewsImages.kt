package media.whitewhale.iduo

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private const val MAX_ARTICLE_PAGE_BYTES = 2 * 1024 * 1024
private const val MAX_PUBLISHER_HEAD_BYTES = 1024 * 1024
private const val PREFS = "google_news_images"
private const val KEPT_IMAGES = 400

/**
 * Pictures for Google News articles. Google's feed has none and links to Google rather than the
 * article, so each article is looked up once, when it shows: Google's page for it gives the key to
 * ask Google for the article's address, and the article's own page names its picture. The answer,
 * a picture or none, is kept on the device.
 */
internal object GoogleNewsImages {
    private val running = HashMap<String, CompletableDeferred<String?>>()
    private val lookups = Semaphore(3)
    @Volatile private var memory: MutableMap<String, String>? = null

    /** Google's article id for a Google News link, or null for any other link. */
    fun articleId(link: String): String? =
        Regex("""^https://news\.google\.com/(?:rss/)?articles/([A-Za-z0-9_-]+)""").find(link)?.groupValues?.get(1)

    /** The picture found earlier: its address, "" when the article has none, or null when not yet looked up. */
    fun cached(context: Context, id: String): String? = entries(context)[id]?.substringAfter('|')

    /** Background-safe. Finds the article's picture, or null. */
    suspend fun find(context: Context, id: String): String? {
        cached(context, id)?.let { return it.ifEmpty { null } }
        val (deferred, owner) = synchronized(running) {
            running[id]?.let { it to false } ?: CompletableDeferred<String?>().also { running[id] = it }.let { it to true }
        }
        if (!owner) return deferred.await()
        val found = try {
            lookups.withPermit { withContext(Dispatchers.IO) { lookUp(id) } }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) {
                synchronized(running) { running.remove(id) }; deferred.complete(null); throw failure
            }
            null
        }
        // A failed lookup is not remembered, so the next showing tries again; an article without a picture is.
        if (found != null) remember(context, id, found)
        synchronized(running) { running.remove(id) }
        deferred.complete(found?.ifEmpty { null })
        return found?.ifEmpty { null }
    }

    /** The picture's address, "" when the article has none, or null when a step failed. */
    private fun lookUp(id: String): String? {
        // The time and signature sit near the end of Google's page for the article.
        val page = String(download("https://news.google.com/rss/articles/$id", MAX_ARTICLE_PAGE_BYTES))
        val timestamp = Regex("""data-n-a-ts="(\d+)"""").find(page)?.groupValues?.get(1) ?: return null
        val signature = Regex("""data-n-a-sg="([^"]+)"""").find(page)?.groupValues?.get(1) ?: return null
        val article = articleAddress(id, timestamp, signature) ?: return null
        if (!article.startsWith("https://")) return ""
        val head = String(download(article, MAX_PUBLISHER_HEAD_BYTES, stopAfter = "</head>"))
        return pictureOf(head)?.let { resolveUrl(article, it) }?.takeIf { it.startsWith("https://") } ?: ""
    }

    /** Asks Google which address an article id stands for. */
    private fun articleAddress(id: String, timestamp: String, signature: String): String? {
        val request = """["garturlreq",[["X","X",["FINANCE_TOP_INDICES","WEB_TEST_1_0_0"],null,null,1,1,"US:en",null,180,""" +
            """null,null,null,null,null,0,null,null,[1608992183,723341000]],"X","X",1,[2,3,4,8],1,0,"655000234",0,0,null,0],""" +
            """${JSONObject.quote(id)},$timestamp,${JSONObject.quote(signature)}]"""
        val form = "f.req=" + URLEncoder.encode("""[[["Fbv4je",${JSONObject.quote(request)},null,"generic"]]]""", "UTF-8")
        val response = String(download("https://news.google.com/_/DotsSplashUi/data/batchexecute", 256 * 1024, form = form))
        val calls = JSONArray(response.substring(response.indexOf('[')))
        for (index in 0 until calls.length()) {
            val call = calls.optJSONArray(index) ?: continue
            if (call.optString(0) != "wrb.fr" || call.optString(1) != "Fbv4je") continue
            return runCatching { JSONArray(call.getString(2)).getString(1) }.getOrNull()
        }
        return null
    }

    private fun entries(context: Context): MutableMap<String, String> = memory ?: synchronized(this) {
        memory ?: context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNullTo(ArrayList()) { (key, value) -> (value as? String)?.let { key to it } }
            .toMap(LinkedHashMap()).let { java.util.Collections.synchronizedMap(it) }.also { memory = it }
    }

    /** Keeps the answer with the time it was found, forgetting the oldest beyond [KEPT_IMAGES]. */
    private fun remember(context: Context, id: String, picture: String) {
        val all = entries(context)
        all[id] = "${System.currentTimeMillis()}|$picture"
        val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(id, all.getValue(id))
        if (all.size > KEPT_IMAGES) synchronized(all) {
            all.entries.sortedBy { it.value.substringBefore('|').toLongOrNull() ?: 0L }.take(all.size - KEPT_IMAGES)
                .map { it.key }.forEach { all.remove(it); editor.remove(it) }
        }
        editor.apply()
    }
}

/** The page's picture for sharing: Open Graph's, else Twitter's, else the linked image. */
internal fun pictureOf(head: String): String? {
    val tags = Regex("""<(?:meta|link)\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(head).map { it.value }.toList()
    fun attribute(tag: String, name: String) =
        Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag)
            ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
    fun meta(key: String) = tags.firstOrNull { tag ->
        listOf("property", "name").any { attribute(tag, it).equals(key, ignoreCase = true) }
    }?.let { attribute(it, "content") }
    val picture = meta("og:image:secure_url") ?: meta("og:image") ?: meta("og:image:url") ?: meta("twitter:image")
        ?: meta("twitter:image:src")
        ?: tags.firstOrNull { attribute(it, "rel").equals("image_src", ignoreCase = true) }?.let { attribute(it, "href") }
    return picture?.trim()?.replace("&amp;", "&")?.takeIf { it.isNotEmpty() }
}
