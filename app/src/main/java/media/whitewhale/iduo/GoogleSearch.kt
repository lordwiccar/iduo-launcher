package media.whitewhale.iduo

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** The Google app, which provides the public global search screen. */
internal const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

/** Public search entry point; no query is submitted and no private Google component is named. */
internal fun googleSearchIntent() = Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
    .setPackage(GOOGLE_PACKAGE)
    .putExtra(SearchManager.QUERY, "")
    .putExtra(SearchManager.APP_DATA, Bundle().apply { putString("source", "launcher-search") })

/** Google results for a phrase: the Google app's web search, then Google in any browser. */
internal fun webSearchIntents(query: String) = listOf(
    Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE_PACKAGE).putExtra(SearchManager.QUERY, query),
    Intent(Intent.ACTION_VIEW, Uri.parse(googleSearchUrl(query))).addCategory(Intent.CATEGORY_BROWSABLE),
)

internal fun googleSearchUrl(query: String) = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
