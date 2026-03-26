package ephyra.app.data.notification

import android.content.Context
import ephyra.core.common.notification.NotificationManager

/**
 * Concrete implementation of the NotificationManager, bridging core logic with the
 * application's notification system. This implementation must reside in the app module
 * because it depends on specific notification channels and receivers that are part of
 * the monolithic core.
 */
class NotificationManagerImpl(private val context: Context) : NotificationManager {
    override fun dismissNewChaptersNotification(mangaId: Long) {
        NotificationReceiver.dismissNotification(context, mangaId.hashCode(), Notifications.ID_NEW_CHAPTERS)
    }
}
