package media.whitewhale.iduo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

internal const val DEVELOPER_EMAIL = "studio@whitewhale.media"
internal const val PROJECT_URL = "https://github.com/lordwiccar/iduo-launcher"
internal const val PRIVACY_URL = "https://lordwiccar.github.io/iduo-launcher/privacy-policy.html"
internal const val UPSTREAM_URL = "https://github.com/jakesgoodapps/DuoLauncher"
internal const val FEEDBACK_URL = "https://docs.google.com/forms/d/e/1FAIpQLSf-s-I6Pd8hi7mgYOcvu5_xWqGST4qzGBLK5K94D3aLukqH0g/viewform?usp=dialog"

/** One release in CHANGELOG.md: its heading and its entries, each a paragraph or a bullet. */
internal data class ChangelogRelease(val title: String, val entries: List<ChangelogEntry>)
internal data class ChangelogEntry(val text: String, val bullet: Boolean, val heading: Boolean = false)

/**
 * Reads the project's Markdown changelog: `##` headings start releases, `###` headings group their
 * entries, `- ` lines are bullets,
 * other lines join into paragraphs. Emphasis and code marks are dropped for plain display.
 */
internal fun parseChangelog(markdown: String): List<ChangelogRelease> {
    val releases = mutableListOf<ChangelogRelease>()
    var title: String? = null
    var entries = mutableListOf<ChangelogEntry>()
    var current: StringBuilder? = null
    var bullet = false
    fun flushEntry() {
        current?.let { entries += ChangelogEntry(plainMarkdown(it.toString()), bullet) }
        current = null
    }
    fun flushRelease() {
        flushEntry()
        title?.let { releases += ChangelogRelease(it, entries) }
        entries = mutableListOf()
    }
    for (raw in markdown.lineSequence()) {
        val line = raw.trimEnd()
        when {
            line.startsWith("## ") -> { flushRelease(); title = line.removePrefix("## ").trim() }
            line.startsWith("### ") -> {
                flushEntry(); entries += ChangelogEntry(plainMarkdown(line.removePrefix("### ").trim()), bullet = false, heading = true)
            }
            line.startsWith("# ") -> flushEntry()
            line.isBlank() -> flushEntry()
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushEntry(); bullet = true; current = StringBuilder(line.drop(2).trim())
            }
            current != null -> current!!.append(' ').append(line.trim())
            else -> { bullet = false; current = StringBuilder(line.trim()) }
        }
    }
    flushRelease()
    return releases
}

private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]*\\)")

internal fun plainMarkdown(text: String): String =
    MARKDOWN_LINK.replace(text) { it.groupValues[1] }.replace("**", "").replace("`", "")

private fun Context.asset(path: String): String =
    runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")

internal fun Context.openLink(uri: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
internal fun AboutPage() {
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(icon, null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.padding(start = 14.dp)) {
            Text("iDuo Launcher", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_version, info.versionName ?: "", info.longVersionCode),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("about-version"))
        }
    }
    AboutSection(stringResource(R.string.about_developer_title), stringResource(R.string.about_developer))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { context.openLink("mailto:$DEVELOPER_EMAIL") }, Modifier.heightIn(min = 48.dp)) {
            Text(DEVELOPER_EMAIL)
        }
        OutlinedButton(onClick = { context.openLink(PROJECT_URL) }, Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.about_source))
        }
    }
    TextButton(onClick = { context.openLink(PRIVACY_URL) }, Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.about_privacy))
    }
    HorizontalDivider()
    AboutSection(stringResource(R.string.about_upstream_title), stringResource(R.string.about_upstream))
    TextButton(onClick = { context.openLink(UPSTREAM_URL) }, Modifier.heightIn(min = 48.dp).testTag("about-upstream")) {
        Text("github.com/jakesgoodapps/DuoLauncher")
    }
    LicenseText(stringResource(R.string.about_license), context.asset("licenses/MIT-DuoLauncher.txt"), "about-license", startOpen = true)
    LicenseText(stringResource(R.string.about_third_party), context.asset("licenses/THIRD-PARTY-NOTICES.txt"), "about-notices")
    LicenseText("Apache License 2.0", context.asset("licenses/Apache-2.0.txt"), "about-apache")
}

@Composable
private fun AboutSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium)
}

/** A licence text that folds away, since the full texts are long. */
@Composable
private fun LicenseText(title: String, text: String, tag: String, startOpen: Boolean = false) {
    var open by rememberSaveable(tag) { mutableStateOf(startOpen) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(horizontal = 14.dp).testTag(tag),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            if (open) Text(text.trim(), Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
internal fun ChangelogPage() {
    val context = LocalContext.current
    val releases = remember { parseChangelog(context.asset("CHANGELOG.md")) }
    val unreleased = stringResource(R.string.changelog_unreleased)
    Text(stringResource(R.string.changelog_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    releases.forEach { release ->
        Text(if (release.title.equals("Unreleased", ignoreCase = true)) unreleased else release.title,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).testTag("changelog-${release.title}"))
        release.entries.forEach { entry ->
            if (entry.heading) Text(entry.text, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            else if (entry.bullet) Row {
                Text("•", Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            } else Text(entry.text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
