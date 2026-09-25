package media.whitewhale.iduo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val RSS_PREFS = "rss"
private const val RSS_SOURCES = "sources"
private const val RSS_UPDATED = "updated"
private const val RSS_CACHE_FILE = "rss-cache.json"
private const val MAX_FEED_BYTES = 4 * 1024 * 1024
private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
/** A page shown again within this time keeps its articles instead of fetching. */
private const val RSS_FRESH_MS = 15 * 60 * 1000L

/** Why adding a source failed, as a string resource. */
class RssAddException(val messageRes: Int) : Exception()

/**
 * The RSS page's sources and articles. Sources live in preferences; the last fetched articles in a
 * private file so the page opens instantly. Every request goes straight from the phone to the
 * source over https; nothing passes through an iDuo server.
 */
internal object RssReader {
    var sources by mutableStateOf<List<RssSource>>(emptyList())
        private set
    var items by mutableStateOf<List<RssItem>>(emptyList())
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** Sources that failed in the last refresh, by URL. */
    var failedSources by mutableStateOf<Set<String>>(emptySet())
        private set
    var updatedAt by mutableLongStateOf(0L)
        private set
    private var loaded = false
    private val refreshLock = Mutex()

    /** Main thread; reads sources and cached articles once per process. */
    suspend fun load(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(RSS_PREFS, Context.MODE_PRIVATE)
        sources = runCatching { decodeSources(prefs.getString(RSS_SOURCES, "[]") ?: "[]") }.getOrDefault(emptyList())
        updatedAt = prefs.getLong(RSS_UPDATED, 0)
        items = withContext(Dispatchers.IO) {
            runCatching { decodeItems(File(app.filesDir, RSS_CACHE_FILE).readText()) }.getOrDefault(emptyList())
        }
    }

    suspend fun refreshIfStale(context: Context) {
        if (sources.isNotEmpty() && System.currentTimeMillis() - updatedAt > RSS_FRESH_MS) refresh(context)
    }

    /** Fetches every source in parallel; a source that fails keeps its previously fetched articles. */
    suspend fun refresh(context: Context) {
        if (!refreshLock.tryLock()) return
        refreshing = true
        try {
            val app = context.applicationContext
            val current = sources
            val results = withContext(Dispatchers.IO) {
                coroutineScope { current.map { source -> async { source to runCatching { fetchFeed(source.url) } } }.awaitAll() }
            }
            val failed = results.filter { it.second.isFailure }.mapTo(mutableSetOf()) { it.first.url }
            val fresh = results.flatMap { (source, result) ->
                result.getOrNull()?.items?.map { it.copy(sourceTitle = source.title) }
                    ?: items.filter { it.sourceUrl == source.url }
            }
            items = mergeFeedItems(fresh)
            failedSources = failed
            updatedAt = System.currentTimeMillis()
            app.getSharedPreferences(RSS_PREFS, Context.MODE_PRIVATE).edit().putLong(RSS_UPDATED, updatedAt).apply()
            val snapshot = items
            withContext(Dispatchers.IO) { runCatching { File(app.filesDir, RSS_CACHE_FILE).writeText(encodeItems(snapshot)) } }
        } finally {
            refreshing = false
            refreshLock.unlock()
        }
    }

    /**
     * Adds what the user typed: a feed address, or a web page that advertises its feed. Throws
     * [RssAddException] when the address is invalid, already added, or has no feed.
     */
    suspend fun add(context: Context, input: String) {
        val address = normalizeFeedAddress(input) ?: throw RssAddException(R.string.rss_no_feed)
        if (sources.any { it.url.equals(address, ignoreCase = true) }) throw RssAddException(R.string.rss_duplicate)
        val (url, feed) = withContext(Dispatchers.IO) {
            val body = runCatching { download(address, MAX_FEED_BYTES) }.getOrElse { throw RssAddException(R.string.rss_no_feed) }
            parseFeed(body.inputStream(), address)?.let { address to it } ?: run {
                val linked = discoverFeedLink(String(body, Charsets.UTF_8), address)
                    ?.let(::normalizeFeedAddress) ?: throw RssAddException(R.string.rss_no_feed)
                val linkedFeed = runCatching { parseFeed(download(linked, MAX_FEED_BYTES).inputStream(), linked) }.getOrNull()
                    ?: throw RssAddException(R.string.rss_no_feed)
                linked to linkedFeed
            }
        }
        if (sources.any { it.url.equals(url, ignoreCase = true) }) throw RssAddException(R.string.rss_duplicate)
        sources = sources + RssSource(url, feed.title)
        saveSources(context)
        items = mergeFeedItems(items + feed.items)
        failedSources = failedSources - url
    }

    fun remove(context: Context, source: RssSource) {
        sources = sources.filterNot { it.url == source.url }
        items = items.filterNot { it.sourceUrl == source.url }
        failedSources = failedSources - source.url
        saveSources(context)
    }

    private fun saveSources(context: Context) {
        context.applicationContext.getSharedPreferences(RSS_PREFS, Context.MODE_PRIVATE).edit()
            .putString(RSS_SOURCES, encodeSources(sources)).apply()
    }

    private fun fetchFeed(url: String): ParsedFeed =
        parseFeed(download(url, MAX_FEED_BYTES).inputStream(), url) ?: throw IOException("Not a feed: $url")

    /** Downsampled article pictures, shared across recompositions and pages. */
    private val imageCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cachedImage(url: String): Bitmap? = imageCache.get(url)

    /** Background thread. Decodes the picture at roughly [targetPx] wide, or null. */
    fun loadImage(url: String, targetPx: Int): Bitmap? {
        imageCache.get(url)?.let { return it }
        if (!url.startsWith("https://")) return null
        val bytes = runCatching { download(url, MAX_IMAGE_BYTES) }.getOrNull() ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        imageCache.put(url, bitmap)
        return bitmap
    }
}

/** Reads at most [limit] bytes over https, following redirects that stay on https. */
private fun download(address: String, limit: Int): ByteArray {
    var url = URL(address)
    repeat(5) {
        if (url.protocol != "https") throw IOException("Only https is allowed")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "iDuo Launcher RSS")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5")
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                url = URL(url, connection.getHeaderField("Location") ?: throw IOException("Redirect without location"))
                return@repeat
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.use { it.readLimited(limit) }
        } finally { connection.disconnect() }
    }
    throw IOException("Too many redirects")
}

private fun InputStream.readLimited(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        if (out.size() > limit) throw IOException("Response too large")
    }
    return out.toByteArray()
}

internal fun encodeSources(sources: List<RssSource>): String = JSONArray().apply {
    sources.forEach { put(JSONObject().put("url", it.url).put("title", it.title)) }
}.toString()

internal fun decodeSources(json: String): List<RssSource> {
    val array = JSONArray(json)
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { item ->
            item.optString("url").takeIf { it.startsWith("https://") }?.let { RssSource(it, item.optString("title", it)) }
        }
    }
}

private fun encodeItems(items: List<RssItem>): String = JSONArray().apply {
    items.forEach { item ->
        put(JSONObject().put("id", item.id).put("source", item.sourceUrl).put("sourceTitle", item.sourceTitle)
            .put("title", item.title).put("link", item.link).put("summary", item.summary)
            .put("image", item.imageUrl ?: "").put("published", item.published))
    }
}.toString()

private fun decodeItems(json: String): List<RssItem> {
    val array = JSONArray(json)
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { item ->
            RssItem(item.optString("id"), item.optString("source"), item.optString("sourceTitle"), item.optString("title"),
                item.optString("link"), item.optString("summary"), item.optString("image").ifEmpty { null }, item.optLong("published"))
        }
    }
}
