package ephyra.app.data.backup.create

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.app.data.notification.Notifications
import ephyra.app.util.system.cancelNotification
import ephyra.app.util.system.isRunning
import ephyra.app.util.system.setForegroundSafely
import ephyra.app.util.system.workManager
import logcat.LogPriority
import ephyra.core.common.util.system.logcat
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.storage.service.StorageManager
import java.util.concurrent.TimeUnit

class BackupCreateJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val backupCreator: BackupCreator,
    private val storageManager: StorageManager,
    private val backupPreferences: BackupPreferences,
    private val notifier: BackupNotifier,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)

        if (isAutoBackup && BackupRestoreJob.isRunning(context)) return Result.retry()

        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: getAutomaticBackupLocation()
            ?: return Result.failure()

        setForegroundSafely()

        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { BackupOptions.fromBooleanArray(it) }
            ?: BackupOptions()

        return try {
            val location = backupCreator.backup(uri, options, isAutoBackup)
            if (!isAutoBackup) {
                notifier.showBackupComplete(UniFile.fromUri(context, location.toUri())!!)
            }
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            if (!isAutoBackup) notifier.showBackupError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun getAutomaticBackupLocation(): Uri? {
        return storageManager.getAutomaticBackupsDirectory()?.uri
    }

    companion object {
        private const val TAG_AUTO = "BackupCreator"
        private const val TAG_MANUAL = "$TAG_AUTO:manual"

        private const val IS_AUTO_BACKUP_KEY = "is_auto_backup" // Boolean
        private const val LOCATION_URI_KEY = "location_uri" // String
        private const val OPTIONS_KEY = "options" // BooleanArray

        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL)
        }

        fun setupTask(
            context: Context,
            backupPreferences: BackupPreferences,
            prefInterval: Int? = null,
        ) {
            val interval = prefInterval ?: backupPreferences.backupInterval().get()
            if (interval > 0) {
                val constraints = Constraints(
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .addTag(TAG_AUTO)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        fun startNow(context: Context, uri: Uri, options: BackupOptions) {
            val inputData = workDataOf(
                IS_AUTO_BACKUP_KEY to false,
                LOCATION_URI_KEY to uri.toString(),
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG_MANUAL)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}
