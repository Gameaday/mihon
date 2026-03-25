package ephyra.app.data.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.notification.Notifications
import ephyra.app.util.system.cancelNotification
import ephyra.app.util.system.isRunning
import ephyra.app.util.system.setForegroundSafely
import ephyra.app.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.logcat
import ephyra.i18n.MR

class BackupRestoreJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val backupRestorer: BackupRestorer,
    private val notifier: BackupNotifier,
) : CoroutineWorker(context, workerParams) {


    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { RestoreOptions.fromBooleanArray(it) }

        if (uri == null || options == null) {
            return Result.failure()
        }

        val isSync = inputData.getBoolean(SYNC_KEY, false)

        setForegroundSafely()

        return try {
            backupRestorer.restore(uri, options, isSync)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError(e.message)
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        private const val TAG = "BackupRestore"

        private const val LOCATION_URI_KEY = "location_uri" // String
        private const val SYNC_KEY = "sync" // Boolean
        private const val OPTIONS_KEY = "options" // BooleanArray

        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun start(
            context: Context,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
        ) {
            val inputData = workDataOf(
                LOCATION_URI_KEY to uri.toString(),
                SYNC_KEY to sync,
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupRestoreJob>()
                .addTag(TAG)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
