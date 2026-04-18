package ephyra.app.data.updater

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import ephyra.app.R
import ephyra.app.data.notification.NotificationHandler
import ephyra.app.data.notification.NotificationReceiver
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.notificationBuilder
import ephyra.core.common.util.system.notify
import ephyra.data.notification.Notifications
import ephyra.domain.release.model.Release
import ephyra.i18n.MR
import ephyra.domain.release.service.AppUpdateNotifier as DomainAppUpdateNotifier

class AppUpdateNotifier(private val context: Context) : DomainAppUpdateNotifier {

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_APP_UPDATE)

    /**
     * Call to show notification.
     *
     * @param id id of the notification channel.
     */
    private fun NotificationCompat.Builder.show(id: Int = Notifications.ID_APP_UPDATER) {
        context.notify(id, build())
    }

    override fun cancel() {
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATER)
    }

    @SuppressLint("LaunchActivityFromNotification")
    override fun promptUpdate(release: Release) {
        val updateIntent = NotificationReceiver.downloadAppUpdatePendingBroadcast(
            context,
            release.downloadLink,
            release.version,
        )

        val releaseIntent = Intent(Intent.ACTION_VIEW, release.releaseLink.toUri()).run {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                context,
                release.hashCode(),
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.update_check_notification_update_available))
            setContentText(release.version)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentIntent(updateIntent)

            clearActions()
            addAction(
                android.R.drawable.stat_sys_download_done,
                context.stringResource(MR.strings.action_download),
                updateIntent,
            )
            addAction(
                R.drawable.ic_info_24dp,
                context.stringResource(MR.strings.whats_new),
                releaseIntent,
            )
        }
        notificationBuilder.show()
    }

    /**
     * Call when apk download starts.
     *
     * @param title tile of notification.
     */
    override fun onDownloadStarted(title: String?) {
        with(notificationBuilder) {
            title?.let { setContentTitle(title) }
            setContentText(context.stringResource(MR.strings.update_check_notification_download_in_progress))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.cancelDownloadAppUpdatePendingBroadcast(context),
            )
        }
        notificationBuilder.show()
    }

    fun getDownloadStartedNotification(): android.app.Notification {
        return notificationBuilder.build()
    }

    /**
     * Call when apk download progress changes.
     *
     * @param progress progress of download (xx%/100).
     */
    override fun onProgressChange(progress: Int) {
        with(notificationBuilder) {
            setProgress(100, progress, false)
            setOnlyAlertOnce(true)
        }
        notificationBuilder.show()
    }

    /**
     * Call when apk download is finished.
     *
     * @param uriString path location of apk, as a URI string.
     */
    override fun promptInstall(uriString: String) {
        val uri = uriString.toUri()
        val installIntent = NotificationHandler.installApkPendingActivity(context, uri)
        with(notificationBuilder) {
            setContentText(context.stringResource(MR.strings.update_check_notification_download_complete))
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setOnlyAlertOnce(false)
            setProgress(0, 0, false)
            setContentIntent(installIntent)
            setOngoing(true)

            clearActions()
            addAction(
                R.drawable.ic_system_update_alt_white_24dp,
                context.stringResource(MR.strings.action_install),
                installIntent,
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_PROMPT),
            )
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_PROMPT)
    }

    /**
     * Call when apk download throws an error
     *
     * @param url web location of apk to download.
     */
    override fun onDownloadError(url: String) {
        with(notificationBuilder) {
            setContentText(context.stringResource(MR.strings.update_check_notification_download_error))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOnlyAlertOnce(false)
            setProgress(0, 0, false)

            clearActions()
            addAction(
                R.drawable.ic_refresh_24dp,
                context.stringResource(MR.strings.action_retry),
                NotificationReceiver.downloadAppUpdatePendingBroadcast(context, url),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_ERROR),
            )
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_ERROR)
    }
}
