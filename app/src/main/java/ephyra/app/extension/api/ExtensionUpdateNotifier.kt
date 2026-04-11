package ephyra.app.extension.api

import android.content.Context
import androidx.core.app.NotificationCompat
import ephyra.app.R
import ephyra.app.data.notification.NotificationReceiver
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.i18n.pluralStringResource
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.notify
import ephyra.data.notification.Notifications
import ephyra.i18n.MR

class ExtensionUpdateNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences,
) {
    fun promptUpdates(names: List<String>) {
        context.notify(
            Notifications.ID_UPDATES_TO_EXTS,
            Notifications.CHANNEL_EXTENSIONS_UPDATE,
        ) {
            setContentTitle(
                context.pluralStringResource(
                    MR.plurals.update_check_notification_ext_updates,
                    names.size,
                    names.size,
                ),
            )
            if (!securityPreferences.hideNotificationContent().getSync()) {
                val extNames = names.joinToString(", ")
                setContentText(extNames)
                setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
            }
            setSmallIcon(R.drawable.ic_extension_24dp)
            setContentIntent(NotificationReceiver.openExtensionsPendingActivity(context))
            setAutoCancel(true)
        }
    }

    fun dismiss() {
        context.cancelNotification(Notifications.ID_UPDATES_TO_EXTS)
    }
}
