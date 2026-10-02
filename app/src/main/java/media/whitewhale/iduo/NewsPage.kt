@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package media.whitewhale.iduo

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RssThumbnailSize = 76.dp

/**
 * Home's left page: Google News or the user's own RSS sources, in the same glass panel as All apps.
 * The small settings icon in the top-left corner switches the panel to its settings.
 */
@Composable
internal fun NewsPage(modifier: Modifier, active: Boolean, mode: LeftPage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleNews = mode == LeftPage.GOOGLE_NEWS
    val reader = if (googleNews) NewsFeeds.googleNews else NewsFeeds.custom
    var managing by rememberSaveable(mode) { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (googleNews) GoogleNewsSettings.load(context)
        reader.load(context)
    }
    // Changing the Google News edition or sections replaces the sources and marks them stale.
    LaunchedEffect(active, reader, reader.sources) { if (active) { reader.load(context); reader.refreshIfStale(context) } }
    Surface(modifier.testTag("rss-page"), shape = RoundedCornerShape(24.dp), color = Glass.copy(alpha = .48f),
        contentColor = Ink, border = BorderStroke(1.dp, Color.White.copy(alpha = .38f))) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .09f), Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { managing = !managing }, Modifier.size(36.dp).testTag("rss-settings")) {
                    if (managing) Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), Modifier.size(20.dp))
                    else Icon(Icons.Rounded.Settings, stringResource(R.string.rss_manage), Modifier.size(18.dp),
                        tint = Ink.copy(alpha = .7f))
                }
                Text(if (managing) stringResource(if (googleNews) R.string.news_settings else R.string.rss_sources)
                    else if (googleNews) stringResource(R.string.left_page_google_news) else stringResource(R.string.rss_title),
                    Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                if (!managing && reader.sources.isNotEmpty()) IconButton(enabled = !reader.refreshing,
                    onClick = { scope.launch { reader.refresh(context) } }, modifier = Modifier.testTag("rss-refresh")) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.rss_refresh), Modifier.size(20.dp))
                }
            }
            if (managing && googleNews) GoogleNewsEditor(Modifier.weight(1f))
            else if (managing) RssSourcesEditor(reader, Modifier.weight(1f))
            else RssArticles(reader, Modifier.weight(1f), onManage = { managing = true })
        }
    }
}

@Composable
private fun RssArticles(reader: FeedReader, modifier: Modifier, onManage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PullToRefreshBox(isRefreshing = reader.refreshing, onRefresh = { scope.launch { reader.refresh(context) } },
        modifier = modifier.fillMaxWidth()) {
        if (reader.sources.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.RssFeed, null, Modifier.size(40.dp), tint = Ink.copy(alpha = .7f))
            Text(stringResource(R.string.rss_empty), Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onManage, Modifier.testTag("rss-add-first")) { Text(stringResource(R.string.rss_add_first)) }
        } else LazyColumn(Modifier.fillMaxSize().testTag("rss-list"), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (reader.failedSources.isNotEmpty()) item("failed") {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp), tint = Ink.copy(alpha = .7f))
                    Text(stringResource(R.string.rss_load_failed), Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            items(reader.items, key = { it.sourceUrl + "\u0000" + it.id }) { item ->
                RssArticleRow(item) {
                    if (item.link.startsWith("http")) runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .22f))
            }
        }
    }
}

@Composable
private fun RssArticleRow(item: RssItem, onOpen: () -> Unit) {
    val now = System.currentTimeMillis()
    val age = if (item.published > 0) DateUtils.getRelativeTimeSpanString(item.published, now,
        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() else null
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)
        .padding(vertical = 10.dp).testTag("rss-item"), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(item.sourceTitle.takeIf { it.isNotBlank() }, age).joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Ink.copy(alpha = .72f))
            Text(item.title, Modifier.padding(top = 3.dp), maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (item.summary.isNotBlank()) Text(item.summary, Modifier.padding(top = 3.dp), maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .8f))
        }
        val googleArticle = remember(item.link) { GoogleNewsImages.articleId(item.link) }
        if (item.imageUrl != null) RssThumbnail(item.imageUrl, Modifier.padding(start = 12.dp))
        else if (googleArticle != null) GoogleNewsThumbnail(googleArticle, Modifier.padding(start = 12.dp))
    }
}

/** A Google News article's picture, looked up when the article first shows; an empty place meanwhile. */
@Composable
private fun GoogleNewsThumbnail(id: String, modifier: Modifier) {
    val context = LocalContext.current
    val picture by produceState(GoogleNewsImages.cached(context, id), id) {
        if (value == null) value = GoogleNewsImages.find(context, id) ?: ""
    }
    when {
        picture == null -> Box(modifier.size(RssThumbnailSize).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .16f)))
        picture!!.isNotEmpty() -> RssThumbnail(picture!!, modifier)
    }
}

@Composable
private fun RssThumbnail(url: String, modifier: Modifier) {
    val px = with(LocalDensity.current) { RssThumbnailSize.roundToPx() }
    var failed by remember(url) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(FeedImages.cachedImage(url), url) {
        if (value == null) value = withContext(Dispatchers.IO) { FeedImages.loadImage(url, px) }.also { failed = it == null }
    }
    if (failed) return
    Box(modifier.size(RssThumbnailSize).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .16f))) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun RssSourcesEditor(reader: FeedReader, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var address by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    fun submit() {
        if (address.isBlank() || adding) return
        adding = true; error = null
        scope.launch {
            try { reader.add(context, address); address = ""; keyboard?.hide() }
            catch (failure: RssAddException) { error = failure.messageRes }
            finally { adding = false }
        }
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(address, { address = it; error = null },
            Modifier.fillMaxWidth().padding(top = 8.dp).testTag("rss-address"),
            placeholder = { Text(stringResource(R.string.rss_add_hint)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }), isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Ink,
                focusedContainerColor = Color.White.copy(alpha = .18f), unfocusedContainerColor = Color.White.copy(alpha = .12f),
                focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                focusedPlaceholderColor = Ink.copy(alpha = .7f), unfocusedPlaceholderColor = Ink.copy(alpha = .7f)))
        error?.let { Text(stringResource(it), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error) }
        Button(onClick = ::submit, enabled = address.isNotBlank() && !adding,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).heightIn(min = 48.dp).testTag("rss-add")) {
            Text(stringResource(if (adding) R.string.rss_adding else R.string.rss_add))
        }
        LazyColumn(Modifier.weight(1f).testTag("rss-sources"), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(reader.sources, key = { it.url }) { source ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(source.url.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, color = Ink.copy(alpha = .7f))
                    }
                    if (source.url in reader.failedSources) Icon(Icons.Rounded.ErrorOutline,
                        stringResource(R.string.rss_load_failed), Modifier.size(18.dp), tint = Ink.copy(alpha = .7f))
                    IconButton(onClick = { reader.remove(context, source) }) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.rss_remove_source, source.title))
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .22f))
            }
        }
    }
}

/** Google News settings: the edition, and which of its sections to show. */
@Composable
private fun GoogleNewsEditor(modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val locale = LocalConfiguration.current.locales[0]
        var choosingEdition by rememberSaveable { mutableStateOf(false) }
        Text(stringResource(R.string.news_edition), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { choosingEdition = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("news-edition")) {
            Text(GoogleNewsSettings.edition.label(locale), Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
        if (choosingEdition) NewsEditionDialog(locale, onDismiss = { choosingEdition = false }) {
            GoogleNewsSettings.setEdition(context, it); choosingEdition = false
        }
        Text(stringResource(R.string.news_topics), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NewsTopic.entries.forEach { topic ->
                FilterChip(topic in GoogleNewsSettings.topics, { GoogleNewsSettings.toggle(context, topic) },
                    label = { Text(stringResource(topic.label)) }, modifier = Modifier.testTag("news-topic-${topic.name}"))
            }
        }
        Text(stringResource(R.string.news_source_note), Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .75f))
    }
}

/** Every edition, sorted by its name in [locale], with a filter for the long list. */
@Composable
internal fun NewsEditionDialog(locale: java.util.Locale, onDismiss: () -> Unit, onChoose: (NewsEdition) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val sorted = remember(locale) {
        val collator = java.text.Collator.getInstance(locale)
        NewsEdition.entries.map { it to it.label(locale) }.sortedWith { a, b -> collator.compare(a.second, b.second) }
    }
    val shown = remember(sorted, query) { sorted.filter { it.second.contains(query.trim(), ignoreCase = true) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.news_edition)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("news-edition-search"), singleLine = true,
                    placeholder = { Text(stringResource(R.string.news_edition_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) })
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.first.name }) { (edition, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onChoose(edition) }.testTag("news-edition-${edition.name}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = GoogleNewsSettings.edition == edition, onClick = { onChoose(edition) })
                            Text(label, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
