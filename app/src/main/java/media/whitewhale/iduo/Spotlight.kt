package media.whitewhale.iduo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

private val MARKS = Regex("\\p{Mn}+")

/** Lower case without diacritics, so "kalkulacka" finds "Kalkulačka". */
internal fun searchKey(text: String): String =
    MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

/**
 * Items whose [label] contains [query], ignoring case and diacritics, best first: labels that start
 * with it, then labels with a word that starts with it, then the rest; alphabetical within each.
 */
internal fun <T> rankedMatches(items: List<T>, query: String, limit: Int, label: (T) -> String): List<T> {
    val key = searchKey(query.trim())
    if (key.isEmpty() || limit <= 0) return emptyList()
    return items.mapNotNull { item ->
        val text = searchKey(label(item))
        val at = text.indexOf(key)
        when {
            at < 0 -> null
            at == 0 -> Triple(0, text, item)
            !text[at - 1].isLetterOrDigit() -> Triple(1, text, item)
            else -> Triple(2, text, item)
        }
    }.sortedWith(compareBy({ it.first }, { it.second })).take(limit).map { it.third }
}

/** Width of one result column, including its label. */
private val SPOTLIGHT_CELL = 84.dp
internal const val SPOTLIGHT_ROWS = 2

/**
 * Home's search panel: a field at the top and up to [SPOTLIGHT_ROWS] rows of matching apps. The
 * keyboard's search key or the field's magnifier searches Google for the typed phrase.
 */
@Composable
internal fun SpotlightPanel(
    apps: List<AppEntry>, onDismiss: () -> Unit,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, onWebSearch: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onDismiss)
    val search = { if (query.isNotBlank()) onWebSearch(query.trim()) }
    // Home behind is blurred like behind a folder; a tap outside the panel closes it.
    Box(Modifier.fillMaxSize()
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        .testTag("spotlight")) {
        BoxWithConstraints(Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxWidth()
            .padding(horizontal = 16.dp).padding(top = 24.dp)) {
            val columns = ((maxWidth - 16.dp) / SPOTLIGHT_CELL).toInt().coerceIn(4, 7)
            val matches = remember(apps, query, columns) {
                rankedMatches(apps.filter { it.available }, query, columns * SPOTLIGHT_ROWS) { it.label }
            }
            Column(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("spotlight-field"),
                    placeholder = { Text(stringResource(R.string.spotlight_hint)) }, singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = {
                        IconButton(onClick = search, Modifier.testTag("spotlight-google")) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.search_google))
                        }
                    },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Ink,
                        focusedContainerColor = Glass.copy(alpha = .72f), unfocusedContainerColor = Glass.copy(alpha = .72f),
                        focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                        focusedPlaceholderColor = Ink.copy(alpha = .7f), unfocusedPlaceholderColor = Ink.copy(alpha = .7f),
                        focusedLeadingIconColor = Ink, unfocusedLeadingIconColor = Ink,
                        focusedTrailingIconColor = Ink, unfocusedTrailingIconColor = Ink))
                if (query.isNotBlank()) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    color = Glass.copy(alpha = .6f), contentColor = Ink,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .38f))) {
                    if (matches.isEmpty()) Text(stringResource(R.string.no_apps_found), Modifier.padding(20.dp).testTag("spotlight-empty"))
                    else Column(Modifier.padding(8.dp)) {
                        matches.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app -> SpotlightApp(app, Modifier.weight(1f), onLaunch) }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightApp(app: AppEntry, modifier: Modifier, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit) {
    val bounds = remember { android.graphics.Rect() }
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable { onLaunch(app, bounds) }
        .padding(horizontal = 4.dp, vertical = 8.dp).testTag("spotlight-app-${app.id}"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon.asImageBitmap(), null, Modifier.size(52.dp)
            .onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(13.dp)))
        Text(app.label, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 14.sp)
    }
}
