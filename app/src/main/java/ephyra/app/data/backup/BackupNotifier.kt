package ephyra.app.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import ephyra.app.R
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.notificationBuilder
import ephyra.core.common.util.system.notify
import ephyra.data.notification.Notifications
import ephyra.i18n.MR
import ephyra.domain.backup.service.BackupNotifier as DomainBackupNotifier

class BackupNotifier(private val context: Context) : DomainBackupNotifier {

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    override fun showBackupProgress() {
        context.notify(
            Notifications.ID_BACKUP_PROGRESS,
            Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS,
        ) {
            setContentTitle(context.stringResource(MR.strings.creating_backup))
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
        }
    }

    override fun showBackupComplete(uriString: String) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        context.notify(
            Notifications.ID_BACKUP_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(MR.strings.backup_created))
            setContentText(android.net.Uri.parse(uriString).path)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }

    override fun showBackupError(error: String?) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        context.notify(
            Notifications.ID_BACKUP_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(MR.strings.creating_backup_error))
            setContentText(error)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }

    override fun showRestoreProgress(progress: Int, total: Int, title: String) {
        context.notify(
            Notifications.ID_RESTORE_PROGRESS,
            Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS,
        ) {
            setContentTitle(context.stringResource(MR.strings.restoring_backup))
            setContentText(title)
            setProgress(total, progress, false)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
        }
    }

    override fun showRestoreComplete(time: Long, errorCount: Int, path: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        context.notify(
            Notifications.ID_RESTORE_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(MR.strings.restore_completed))
            setContentText(path)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }

    override fun showRestoreError(error: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        context.notify(
            Notifications.ID_RESTORE_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(MR.strings.restoring_backup_error))
            setContentText(error)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }
}
