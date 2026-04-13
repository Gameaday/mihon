package ephyra.app.data.backup.create

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.app.data.backup.BackupNotifier
import ephyra.core.common.util.system.isRunning
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.core.common.util.system.workManager
import ephyra.data.backup.create.BackupCreator
import ephyra.data.backup.create.BackupOptions
import ephyra.data.notification.Notifications
import logcat.LogPriority
import java.util.concurrent.TimeUnit

class BackupCreateJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val backupCreator: BackupCreator,
) : CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        setForegroundSafely()

        val uriString = inputData.getString(URI_KEY)
        val uri = uriString?.let { Uri.parse(it) }
        val optionsArray = inputData.getBooleanArray(OPTIONS_KEY)
        val options = optionsArray?.let { BackupOptions.fromBooleanArray(it) }

        return try {
            notifier.showBackupProgress()
            val resultUri = backupCreator.createBackup(uri, options)
            notifier.showBackupComplete(resultUri)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showBackupError(e.message)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupCreate"
        private const val URI_KEY = "backup_uri"
        private const val OPTIONS_KEY = "backup_options"

        fun setupTask(context: Context, interval: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                interval.toLong(),
                TimeUnit.HOURS,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun startNow(context: Context, uri: Uri? = null, optionsArray: BooleanArray? = null) {
            val data = Data.Builder()
            uri?.let { data.putString(URI_KEY, it.toString()) }
            optionsArray?.let { data.putBooleanArray(OPTIONS_KEY, it) }

            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG)
                .setInputData(data.build())
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }
    }
}
