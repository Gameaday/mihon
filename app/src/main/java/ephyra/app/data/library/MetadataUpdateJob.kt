package ephyra.app.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.toSManga
import ephyra.app.data.cache.CoverCache
import ephyra.app.data.notification.Notifications
import ephyra.app.util.system.isRunning
import ephyra.app.util.system.setForegroundSafely
import ephyra.app.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

@OptIn(ExperimentalAtomicApi::class)
class MetadataUpdateJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val sourceManager: SourceManager,
    private val coverCache: CoverCache,
    private val getLibraryManga: GetLibraryManga,
    private val updateManga: UpdateManga,
    private val notifier: LibraryUpdateNotifier,
) : CoroutineWorker(context, workerParams) {


    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    override suspend fun doWork(): Result {
        setForegroundSafely()

        addMangaToQueue()

        return withIOContext {
            try {
                updateMetadata()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = LibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    /**
     * Adds list of manga to be updated.
     */
    private suspend fun addMangaToQueue() {
        mangaToUpdate = getLibraryManga.await()
        notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)
    }

    private suspend fun updateMetadata() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInt(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            // Resolve the source once per group instead of once per manga.
                            // All manga in mangaInSource share the same source ID (grouped
                            // on line 102), so a null result applies to the entire batch.
                            val source = sourceManager.get(mangaInSource.first().manga.source)
                                ?: return@withPermit
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    try {
                                        val networkManga = source.getMangaDetails(manga.toSManga())
                                        updateManga.awaitUpdateFromSource(
                                            manga,
                                            networkManga,
                                            manualFetch = true,
                                            coverCache = coverCache,
                                        )
                                    } catch (e: Throwable) {
                                        // Ignore errors and continue
                                        logcat(LogPriority.ERROR, e)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    private suspend fun withUpdateNotification(
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInt,
        manga: Manga,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            mangaToUpdate.size,
        )

        block()

        ensureActive()

        updatingManga.remove(manga)
        completed.fetchAndIncrement()
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            mangaToUpdate.size,
        )
    }

    companion object {
        private const val TAG = "MetadataUpdate"
        private const val WORK_NAME_MANUAL = "MetadataUpdate"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }
            val request = OneTimeWorkRequestBuilder<MetadataUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)
                }
        }
    }
}
