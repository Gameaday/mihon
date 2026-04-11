package ephyra.app.util.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import androidx.core.content.PermissionChecker
import androidx.core.content.getSystemService
import ephyra.app.R
import ephyra.presentation.core.R as PresentationCoreR

val Context.notificationManager: NotificationManager
    get() = getSystemService()!!

private fun Context.hasNotificationPermission(): Boolean {
    return PermissionChecker.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PermissionChecker.PERMISSION_GRANTED
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun Context.notify(id: Int, channelId: String, block: (NotificationCompat.Builder.() -> Unit)? = null) {
    val notification = notificationBuilder(channelId, block).build()
    this.notify(id, notification)
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun Context.notify(id: Int, notification: Notification) {
    if (!hasNotificationPermission()) return
    NotificationManagerCompat.from(this).notify(id, notification)
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun Context.notify(notificationWithIdAndTags: List<NotificationWithIdAndTag>) {
    if (!hasNotificationPermission()) return
    NotificationManagerCompat.from(this).notify(notificationWithIdAndTags)
}

fun Context.cancelNotification(id: Int) {
    NotificationManagerCompat.from(this).cancel(id)
}

/**
 * Helper method to create a notification builder.
 *
 * @param id the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
fun Context.notificationBuilder(
    channelId: String,
    block: (NotificationCompat.Builder.() -> Unit)? = null,
): NotificationCompat.Builder {
    val builder = NotificationCompat.Builder(this, channelId)
        .setColor(getColor(PresentationCoreR.color.accent_blue))
    if (block != null) {
        builder.block()
    }
    return builder
}

/**
 * Helper method to build a notification channel group.
 *
 * @param channelId the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification channel group to be displayed or updated.
 */
fun buildNotificationChannelGroup(
    channelId: String,
    block: (NotificationChannelGroupCompat.Builder.() -> Unit),
): NotificationChannelGroupCompat {
    val builder = NotificationChannelGroupCompat.Builder(channelId)
    builder.block()
    return builder.build()
}

/**
 * Helper method to build a notification channel.
 *
 * @param channelId the channel id.
 * @param channelImportance the channel importance.
 * @param block the function that will execute inside the builder.
 * @return a notification channel to be displayed or updated.
 */
fun buildNotificationChannel(
    channelId: String,
    channelImportance: Int,
    block: (NotificationChannelCompat.Builder.() -> Unit),
): NotificationChannelCompat {
    val builder = NotificationChannelCompat.Builder(channelId, channelImportance)
    builder.block()
    return builder.build()
}
