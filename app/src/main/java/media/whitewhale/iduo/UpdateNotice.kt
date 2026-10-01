package media.whitewhale.iduo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Tells the user when Google Play has updated iDuo, with a notification that opens the changelog.
 * The choice is device-local, and nothing is posted without Android's notification permission.
 */
internal object UpdateNotice {
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 1101
    private const val PREFS = "update_notice"
    private const val ENABLED = "enabled"
    private const val ASKED = "asked"
    private const val LAST_VERSION = "lastVersion"
    const val DESTINATION = "changelog"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, true)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(ENABLED, value).apply()
    fun permitted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun versionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /**
     * Main thread, on start. True once after an update, while notices are on but Android has not
     * been asked for the permission yet: the moment to ask, since the user just got a new version.
     * A first install records its version without asking.
     */
    fun shouldAskAfterUpdate(context: Context): Boolean {
        val prefs = prefs(context)
        val current = versionCode(context)
        val last = prefs.getLong(LAST_VERSION, 0L)
        prefs.edit().putLong(LAST_VERSION, current).apply()
        val updated = last in 1 until current
        if (!updated || !enabled(context) || permitted(context) || prefs.getBoolean(ASKED, false)) return false
        prefs.edit().putBoolean(ASKED, true).apply()
        return true
    }

    /** Posts the notice for the version now installed, when the user wants it and Android allows it. */
    fun post(context: Context) {
        if (!enabled(context) || !permitted(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_notice_channel),
            NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.update_notice_channel_detail) })
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).putExtra("duo_destination", DESTINATION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.update_notice_title, version))
            .setContentText(context.getString(R.string.update_notice_text))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

/** Android tells an app when it has been replaced by a new version. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateNotice.post(context)
    }
}
