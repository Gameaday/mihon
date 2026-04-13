package ephyra.app.data.backup.restore

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.app.data.backup.BackupNotifier
import ephyra.core.common.util.system.isRunning
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.core.common.util.system.workManager
import ephyra.data.backup.restore.BackupRestorer
import ephyra.data.backup.restore.RestoreOptions
import logcat.LogPriority

class BackupRestoreJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val backupRestorer: BackupRestorer,
) : CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        setForegroundSafely()

        val uriString = inputData.getString(LOCATION_EXTRA) ?: return Result.failure()
        val uri = Uri.parse(uriString)
        val optionsArray = inputData.getBooleanArray(OPTIONS_KEY)
        val options = optionsArray?.let { RestoreOptions.fromBooleanArray(it) }

        return try {
            backupRestorer.restore(uri, options) { progress, total, title ->
                notifier.showRestoreProgress(progress, total, title)
            }
            notifier.showRestoreComplete(0, 0, uri.path)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(e.message)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupRestore"
        const val LOCATION_EXTRA = "location"
        private const val OPTIONS_KEY = "restore_options"

        fun start(context: Context, uri: Uri, optionsArray: BooleanArray? = null) {
            val data = Data.Builder()
                .putString(LOCATION_EXTRA, uri.toString())
            optionsArray?.let { data.putBooleanArray(OPTIONS_KEY, it) }

            val request = OneTimeWorkRequestBuilder<BackupRestoreJob>()
                .addTag(TAG)
                .setInputData(data.build())
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
