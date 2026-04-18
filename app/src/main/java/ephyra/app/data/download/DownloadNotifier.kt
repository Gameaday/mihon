package ephyra.app.data.download

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import ephyra.app.R
import ephyra.app.data.notification.NotificationHandler
import ephyra.app.data.notification.NotificationReceiver
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.lang.chop
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.notificationBuilder
import ephyra.core.common.util.system.notify
import ephyra.data.notification.Notifications
import ephyra.domain.download.model.Download
import ephyra.i18n.MR
import java.util.regex.Pattern
import ephyra.domain.download.service.DownloadNotifier as IDownloadNotifier

/**
 * DownloadNotifier is used to show notifications when downloading one or multiple chapters.
 *
 * @param context context of application
 */
class DownloadNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences,
) : IDownloadNotifier {

    private val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setAutoCancel(false)
            setOnlyAlertOnce(true)
        }
    }

    private val errorNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_ERROR) {
            setAutoCancel(false)
        }
    }

    /**
     * Status of download. Used for correct notification icon.
     */
    private var isDownloading = false

    /**
     * Shows a notification from this builder.
     *
     * @param id the id of the notification.
     */
    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    /**
     * Dismiss the downloader's notification. Downloader error notifications use a different id, so
     * those can only be dismissed by the user.
     */
    override fun dismissProgress() {
        context.cancelNotification(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS)
    }

    /**
     * Called when download progress changes.
     *
     * @param download download object containing download information.
     */
    override suspend fun onProgressChange(download: Download) {
        with(progressNotificationBuilder) {
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                clearActions()
                // Open download manager when clicked
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                isDownloading = true
                // Pause action
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.stringResource(MR.strings.action_pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
                addAction(
                    R.drawable.ic_book_24dp,
                    context.stringResource(MR.strings.action_show_manga),
                    NotificationReceiver.openEntryPendingActivity(context, download.manga.id),
                )
            }

            val downloadingProgressText = context.stringResource(
                MR.strings.chapter_downloading_progress,
                download.downloadedImages,
                download.pages!!.size,
            )

            if (securityPreferences.hideNotificationContent().get()) {
                setContentTitle(downloadingProgressText)
                setContentText(null)
            } else {
                val title = download.manga.title.chop(15)
                val quotedTitle = Pattern.quote(title)
                val chapter = download.chapter.name.replaceFirst(
                    "$quotedTitle[\\s]*[-]*[\\s]*".toRegex(RegexOption.IGNORE_CASE),
                    "",
                )
                setContentTitle("$title - $chapter".chop(30))
                setContentText(downloadingProgressText)
            }

            setProgress(download.pages!!.size, download.downloadedImages, false)
            setOngoing(true)

            show(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS)
        }
    }

    /**
     * Show notification when download is paused.
     */
    override fun onPaused() {
        with(progressNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.chapter_paused))
            setContentText(context.stringResource(MR.strings.download_notifier_download_paused))
            setSmallIcon(R.drawable.ic_pause_24dp)
            setProgress(0, 0, false)
            setOngoing(false)
            clearActions()
            // Open download manager when clicked
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            // Resume action
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.stringResource(MR.strings.action_resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            // Clear action
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )

            show(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS)
        }

        // Reset initial values
        isDownloading = false
    }

    /**
     * Resets the state once downloads are completed.
     */
    override fun onComplete() {
        dismissProgress()

        // Reset states to default
        isDownloading = false
    }

    /**
     * Called when the downloader receives a warning.
     *
     * @param reason the text to show.
     * @param timeout duration after which to automatically dismiss the notification.
     * @param mangaId the id of the entry being warned about
     * Only works on Android 8+.
     */
    override fun onWarning(reason: String, timeout: Long?, mangaId: Long?) {
        with(errorNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.download_notifier_downloader_title))
            setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setAutoCancel(true)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            if (mangaId != null) {
                addAction(
                    R.drawable.ic_book_24dp,
                    context.stringResource(MR.strings.action_show_manga),
                    NotificationReceiver.openEntryPendingActivity(context, mangaId),
                )
            }
            setProgress(0, 0, false)
            timeout?.let { setTimeoutAfter(it) }

            show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)
        }

        // Reset download information
        isDownloading = false
    }

    /**
     * Called when the downloader receives an error. It's shown as a separate notification to avoid
     * being overwritten.
     *
     * @param error string containing error information.
     * @param chapter string containing chapter title.
     * @param mangaId the id of the entry that the error occurred on
     */
    override fun onError(error: String?, chapter: String?, mangaTitle: String?, mangaId: Long?) {
        // Create notification
        with(errorNotificationBuilder) {
            setContentTitle(
                mangaTitle?.plus(": $chapter") ?: context.stringResource(MR.strings.download_notifier_downloader_title),
            )
            setContentText(error ?: context.stringResource(MR.strings.download_notifier_unknown_error))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            if (mangaId != null) {
                addAction(
                    R.drawable.ic_book_24dp,
                    context.stringResource(MR.strings.action_show_manga),
                    NotificationReceiver.openEntryPendingActivity(context, mangaId),
                )
            }
            setProgress(0, 0, false)

            show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)
        }

        // Reset download information
        isDownloading = false
    }
}
