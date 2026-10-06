package media.whitewhale.iduo

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/**
 * How many notifications each app has waiting, for the badges on Home. Counted only while the
 * person has given iDuo notification access; nothing about a notification but its app is kept,
 * and nothing leaves the phone.
 */
internal object NotificationBadges {
    /** Waiting notifications by [key]. */
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        internal set
    /** Whether iDuo has notification access, checked each time Home comes to the front. */
    var accessGranted by mutableStateOf(false)
        private set

    fun key(packageName: String, user: UserHandle) = "$packageName/${user.hashCode()}"

    fun refreshAccess(context: Context) {
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!accessGranted) counts = emptyMap()
    }

    /** Android's page for giving iDuo notification access, straight to iDuo where Android allows it. */
    fun accessSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NotificationBadgeService::class.java).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

/**
 * Counts each app's notifications: a notification counts as its own number when it gives one,
 * else as one. Ongoing ones (music, running services), group summaries and apps whose badges are
 * turned off in Android's settings do not count.
 */
class NotificationBadgeService : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() { NotificationBadges.counts = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) = recount()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val scratch = Ranking()
        NotificationBadges.counts = active.filter { sbn ->
            val flags = sbn.notification.flags
            flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) == 0 &&
                (ranking?.getRanking(sbn.key, scratch) != true || scratch.canShowBadge())
        }.groupingBy { NotificationBadges.key(it.packageName, it.user) }
            .fold(0) { total, sbn -> total + sbn.notification.number.coerceAtLeast(1) }
    }
}

/** Waiting notification counts for the badges on Home; empty while badges are off. */
internal val LocalBadgeCounts = compositionLocalOf<Map<String, Int>> { emptyMap() }

@Composable
internal fun badgeCount(app: AppEntry?): Int =
    app?.let { LocalBadgeCounts.current[NotificationBadges.key(it.packageName, it.user)] } ?: 0

@Composable
internal fun badgeCount(ids: List<String>, apps: Map<String, AppEntry>): Int {
    val counts = LocalBadgeCounts.current
    if (counts.isEmpty()) return 0
    return ids.sumOf { id -> apps[id]?.let { counts[NotificationBadges.key(it.packageName, it.user)] } ?: 0 }
}

/** A red dot with the count in white, to sit on the top right corner of an icon [iconSize] across. */
@Composable
internal fun NotificationBadge(count: Int, iconSize: Dp, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val diameter = maxOf(iconSize * .36f, 17.dp)
    val text = if (count > 99) "99+" else count.toString()
    val fontSize = with(LocalDensity.current) { (diameter * .6f).toSp() }
    Box(modifier.defaultMinSize(minWidth = diameter, minHeight = diameter)
        .background(BadgeRed, RoundedCornerShape(50)).padding(horizontal = diameter * .2f),
        contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A small red dot for an app with waiting notifications, on a folder's small icons. */
@Composable
internal fun NotificationDot(count: Int, iconSize: Dp, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.size(maxOf(iconSize * .34f, 6.dp)).background(BadgeRed, androidx.compose.foundation.shape.CircleShape))
}

private val BadgeRed = Color(0xFFE5332E)
