package media.whitewhale.iduo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun LayoutRestorePreview(preview: LayoutImportPreview, onRestore: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, modifier = Modifier.testTag("layout-restore-preview"),
        title = { Text(stringResource(R.string.restore_review_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(listOf(pluralStringResource(R.plurals.restore_apps, preview.appCount, preview.appCount),
                    pluralStringResource(R.plurals.restore_folders, preview.folderCount, preview.folderCount),
                    pluralStringResource(R.plurals.restore_widgets, preview.widgetCount, preview.widgetCount)).joinToString(" · "))
                if (preview.layout.leadingSlots.any { it != null } || preview.layout.widgetPlacements.any { it.page == -1 })
                    Text(stringResource(R.string.restore_includes_leading), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.restore_settings_note))
                Text(stringResource(R.string.restore_photo_note),
                    style = MaterialTheme.typography.bodySmall)
                if (preview.missingApps.isNotEmpty()) {
                    Text(stringResource(R.string.restore_unavailable_apps, preview.missingApps.size), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    val unavailableApp = stringResource(R.string.restore_unavailable_app)
                    preview.missingApps.forEach { saved ->
                        val label = saved.substringAfterLast('(').removeSuffix(")").takeIf { it.isNotBlank() } ?: unavailableApp
                        Text(stringResource(R.string.restore_missing_app, label))
                    }
                }
                if (preview.profileIssues.isNotEmpty()) {
                    Text(stringResource(R.string.restore_profile_attention), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.profileIssues.forEach { Text(stringResource(R.string.restore_profile_issue, it.title, profileName(it.profileLabel))) }
                }
                val reconnect = preview.layout.widgetPlacements.count { it.id == NEEDS_BINDING_WIDGET }
                if (reconnect > 0) Text(pluralStringResource(R.plurals.restore_reconnect, reconnect, reconnect))
                Text(stringResource(R.string.restore_nothing_changes), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { Button(onClick = onRestore, modifier = Modifier.testTag("layout-restore-apply")) { Text(stringResource(R.string.restore)) } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("layout-restore-cancel")) { Text(stringResource(R.string.cancel)) } })
}
