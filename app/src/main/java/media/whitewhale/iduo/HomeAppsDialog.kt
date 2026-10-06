package media.whitewhale.iduo

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Every app not yet on Home, in the dock or in a folder, to tick and add to Home from the empty
 * cell [from]. Above the list, how many pages Home gains when the ticked apps outgrow the page.
 */
@Composable
internal fun HomeAppsDialog(layout: HomeLayout, apps: List<AppEntry>, from: Int,
    onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    var chosen by rememberSaveable(from) { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val choices = remember(apps, layout, locale) {
        val placed = (layout.slots + layout.leadingSlots + layout.dock).filterNotNull().toSet() + layout.folders.flatMap { it.appIds }
        val collator = java.text.Collator.getInstance(locale)
        apps.filter { it.available && it.id !in placed }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    val shown = remember(choices, query) { choices.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    val newPages = remember(layout, from, chosen.size) { homePagesAddedForApps(layout, from, chosen.size) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("home-apps-dialog"),
        title = { Text(stringResource(R.string.home_add_apps_title)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("home-apps-search"), singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_apps)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
                if (newPages > 0) Text(pluralStringResource(R.plurals.home_pages_added, newPages, newPages),
                    Modifier.padding(top = 12.dp).testTag("home-apps-new-pages"), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (shown.isEmpty()) Text(stringResource(if (choices.isEmpty()) R.string.home_add_apps_none else R.string.folder_no_apps_match),
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.id }) { app ->
                        val ticked = app.id in chosen
                        val toggle = { chosen = if (ticked) chosen - app.id else chosen + app.id }
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = toggle).testTag("home-apps-${app.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(ticked, { toggle() })
                            Image(app.icon.asImageBitmap(), null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (app.isWork) Text(profileName(app.profileLabel), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(chosen) }, Modifier.testTag("home-apps-add"), enabled = chosen.isNotEmpty()) {
            Text(if (chosen.isEmpty()) stringResource(R.string.folder_add_apps)
                else pluralStringResource(R.plurals.folder_add_count, chosen.size, chosen.size))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
