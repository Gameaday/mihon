package ephyra.core.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ephyra.app.core.common.R
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.NetworkState
import ephyra.core.common.util.system.activeNetworkState
import ephyra.core.common.util.system.networkStateFlow
import ephyra.core.common.util.system.notificationBuilder
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.data.notification.Notifications
import ephyra.domain.download.service.DownloadPreferences
import ephyra.i18n.MR
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 */
class DownloadJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val downloadManager: DownloadManager,
    private val downloadPreferences: DownloadPreferences,
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    override suspend fun doWork(): Result {
        var networkCheck = checkNetworkState(
            context.activeNetworkState(),
            downloadPreferences.downloadOnlyOverWifi().get(),
        )
        var active = networkCheck && downloadManager.downloaderStart()

        if (!active) {
            return Result.failure()
        }

        setForegroundSafely()

        coroutineScope {
            combineTransform(
                context.networkStateFlow(),
                downloadPreferences.downloadOnlyOverWifi().changes(),
                transform = { a, b -> emit(checkNetworkState(a, b)) },
            )
                .onEach { networkCheck = it }
                .launchIn(this)
        }

        // Keep the worker running when needed
        while (active) {
            active = !isStopped && downloadManager.isRunning && networkCheck
        }

        return Result.success()
    }

    private fun checkNetworkState(state: NetworkState, requireWifi: Boolean): Boolean {
        return if (state.isOnline) {
            val noWifi = requireWifi && !state.isWifi
            if (noWifi) {
                downloadManager.downloaderStop(
                    context.stringResource(MR.strings.download_notifier_text_only_wifi),
                )
            }
            !noWifi
        } else {
            downloadManager.downloaderStop(context.stringResource(MR.strings.download_notifier_no_network))
            false
        }
    }

    companion object {
        private const val TAG = "Downloader"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosByTagFlow(TAG)
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
