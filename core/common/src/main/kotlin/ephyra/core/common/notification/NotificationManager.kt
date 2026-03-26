package ephyra.core.common.notification

/**
 * Interface for application-wide notifications, enabling decoupling of feature modules
 * from the app-module's concrete notification logic and UI.
 */
interface NotificationManager {
    /**
     * Dismisses the "new chapters found" notification for a given manga.
     */
    fun dismissNewChaptersNotification(mangaId: Long)
}
