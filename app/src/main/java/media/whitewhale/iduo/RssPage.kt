@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
 * The RSS reader on Home's left page, in the same glass panel as All apps. The small settings icon
 * in the top-left corner switches the panel to managing sources.
 */
@Composable
internal fun RssPage(modifier: Modifier, active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var managing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) {
        RssReader.load(context)
        if (active) RssReader.refreshIfStale(context)
    }
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
                Text(stringResource(if (managing) R.string.rss_sources else R.string.rss_title),
                    Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                if (!managing && RssReader.sources.isNotEmpty()) IconButton(enabled = !RssReader.refreshing,
                    onClick = { scope.launch { RssReader.refresh(context) } }, modifier = Modifier.testTag("rss-refresh")) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.rss_refresh), Modifier.size(20.dp))
                }
            }
            if (managing) RssSourcesEditor(Modifier.weight(1f))
            else RssArticles(Modifier.weight(1f), onManage = { managing = true })
        }
    }
}

@Composable
private fun RssArticles(modifier: Modifier, onManage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PullToRefreshBox(isRefreshing = RssReader.refreshing, onRefresh = { scope.launch { RssReader.refresh(context) } },
        modifier = modifier.fillMaxWidth()) {
        if (RssReader.sources.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.RssFeed, null, Modifier.size(40.dp), tint = Ink.copy(alpha = .7f))
            Text(stringResource(R.string.rss_empty), Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onManage, Modifier.testTag("rss-add-first")) { Text(stringResource(R.string.rss_add_first)) }
        } else LazyColumn(Modifier.fillMaxSize().testTag("rss-list"), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (RssReader.failedSources.isNotEmpty()) item("failed") {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp), tint = Ink.copy(alpha = .7f))
                    Text(stringResource(R.string.rss_load_failed), Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            items(RssReader.items, key = { it.sourceUrl + "\u0000" + it.id }) { item ->
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
        item.imageUrl?.let { RssThumbnail(it, Modifier.padding(start = 12.dp)) }
    }
}

@Composable
private fun RssThumbnail(url: String, modifier: Modifier) {
    val px = with(LocalDensity.current) { RssThumbnailSize.roundToPx() }
    var failed by remember(url) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(RssReader.cachedImage(url), url) {
        if (value == null) value = withContext(Dispatchers.IO) { RssReader.loadImage(url, px) }.also { failed = it == null }
    }
    if (failed) return
    Box(modifier.size(RssThumbnailSize).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .16f))) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun RssSourcesEditor(modifier: Modifier) {
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
            try { RssReader.add(context, address); address = ""; keyboard?.hide() }
            catch (failure: RssAddException) { error = failure.messageRes }
            finally { adding = false }
        }
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(address, { address = it; error = null },
            Modifier.fillMaxWidth().padding(top = 8.dp).testTag("rss-address").releasesDiscoverWhileTyping("rss-address"),
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
            items(RssReader.sources, key = { it.url }) { source ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(source.url.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, color = Ink.copy(alpha = .7f))
                    }
                    if (source.url in RssReader.failedSources) Icon(Icons.Rounded.ErrorOutline,
                        stringResource(R.string.rss_load_failed), Modifier.size(18.dp), tint = Ink.copy(alpha = .7f))
                    IconButton(onClick = { RssReader.remove(context, source) }) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.rss_remove_source, source.title))
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .22f))
            }
        }
    }
}
