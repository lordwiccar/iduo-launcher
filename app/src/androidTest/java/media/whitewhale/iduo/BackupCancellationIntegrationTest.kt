package media.whitewhale.iduo

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Proves a destroyed Activity cannot clear the durable import owned by its replacement. */
class BackupCancellationIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private val uri = Uri.parse("content://media.whitewhale.iduo.test.blocking/payload")

    @Test fun canceledOldImportCannotClearReplacementTransaction() {
        compose.waitUntil(20_000) { !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading }
        val context = compose.activity.applicationContext
        val pending = context.getSharedPreferences("layout_backup_pending", Context.MODE_PRIVATE)
        val before = pending.all.toMap()
        assumeTrue("Do not replace a real interrupted backup transaction", before.isEmpty())
        val state = ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value
        val raw = encodeLayoutBackup(state.copy(widgetPlacements = emptyList(), widgetRestores = emptyList()),
            emptyList(), layoutBackupScope(context))
        val resolver = context.contentResolver
        fun provider(method: String, argument: String? = null, extras: Bundle? = null) =
            requireNotNull(resolver.call(uri, method, argument, extras))
        fun reads() = provider("status").getInt("reads")
        fun completed() = provider("status").getInt("completed")
        fun restorePrefs() {
            pending.edit().clear().commit()
            val editor = pending.edit()
            before.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            } }
            editor.commit()
        }

        try {
            provider("reset", extras = Bundle().apply { putString("payload", raw) })
            pending.edit().clear().putString("operation", "import").putString("uri", uri.toString()).commit()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { reads() >= 1 }
            var firstLifecycleJob: Job? = null
            compose.activityRule.scenario.onActivity {
                firstLifecycleJob = it.lifecycleScope.coroutineContext[Job]
            }
            assertNotNull(firstLifecycleJob)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { reads() >= 2 }

            provider("release", "1")
            compose.waitUntil(10_000) { completed() >= 1 }
            compose.waitUntil(10_000) { firstLifecycleJob?.isCompleted == true }
            assertEquals(uri.toString(), pending.getString("uri", null))
            assertEquals("import", pending.getString("operation", null))
            assertNull(compose.activity.backups.preview)

            provider("release", "2")
            compose.waitUntil(20_000) { compose.activity.backups.preview != null }
            assertEquals("preview", pending.getString("operation", null))
            assertNotNull(pending.getString("preview", null))
        } finally {
            provider("release", "1")
            provider("release", "2")
            provider("release", "3")
            compose.activity.backups.cancelImport()
            restorePrefs()
        }
    }
}
