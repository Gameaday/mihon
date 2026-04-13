package ephyra.app.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.isPowerSaveMode
import ephyra.core.common.util.system.isRunning
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.core.common.util.system.workManager
import ephyra.data.cache.CoverCache
import ephyra.data.notification.Notifications
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.NoChaptersException
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toSManga
import ephyra.domain.source.model.SourceNotInstalledException
import ephyra.domain.source.service.SourceManager
import ephyra.i18n.MR
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import ephyra.domain.manga.model.SourceStatus

class LibraryUpdateJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val sourceManager: SourceManager,
    private val libraryPreferences: LibraryPreferences,
    private val downloadManager: DownloadManager,
    private val coverCache: CoverCache,
    private val getLibraryManga: GetLibraryManga,
    private val getManga: GetManga,
    private val updateManga: UpdateManga,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val fetchInterval: FetchInterval,
    private val filterChaptersForDownload: FilterChaptersForDownload,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val refreshCanonicalMetadata: ephyra.domain.track.interactor.RefreshCanonicalMetadata,
    private val notifier: LibraryUpdateNotifier,
) : CoroutineWorker(context, workerParams) {

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    private var favoriteCoverNames: Set<String> = emptySet()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            @Suppress("DEPRECATION")
            if (context.isPowerSaveMode) {
                return Result.retry()
            }

            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        setForegroundSafely()

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addMangaToQueue(categoryId)

        return withIOContext {
            try {
                updateChapterList()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                try {
                    val pruned = coverCache.pruneOldCovers(protectedNames = favoriteCoverNames)
                    if (pruned > 0) {
                        logcat(LogPriority.DEBUG) { "Cover cache: pruned $pruned stale covers" }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Cover cache pruning failed" }
                }
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private suspend fun addMangaToQueue(categoryId: Long) {
        val libraryManga = getLibraryManga.await()

        favoriteCoverNames = coverCache.coverFileNames(
            libraryManga.map { it.manga.thumbnailUrl },
        )

        val listToUpdate = if (categoryId != -1L) {
            libraryManga.filter { categoryId in it.categories }
        } else {
            val includedCategories = libraryPreferences.updateCategories().get().mapTo(HashSet()) { it.toLong() }
            val excludedCategories = libraryPreferences.updateCategoriesExclude().get().mapTo(HashSet()) { it.toLong() }

            libraryManga.filter {
                val included = includedCategories.isEmpty() || it.categories.any { cat -> cat in includedCategories }
                val excluded = it.categories.any { cat -> cat in excludedCategories }
                included && !excluded
            }
        }

        val restrictions = libraryPreferences.autoUpdateMangaRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Manga, String?>>()
        val (_, fetchWindowUpperBound) = fetchInterval.getWindow(ZonedDateTime.now())

        val skipReasonNotAlwaysUpdate = context.stringResource(MR.strings.skipped_reason_not_always_update)
        val skipReasonCompleted = if (MANGA_NON_COMPLETED in restrictions) {
            context.stringResource(MR.strings.skipped_reason_completed)
        } else {
            null
        }
        val skipReasonNotCaughtUp = if (MANGA_HAS_UNREAD in restrictions) {
            context.stringResource(MR.strings.skipped_reason_not_caught_up)
        } else {
            null
        }
        val skipReasonNotStarted = if (MANGA_NON_READ in restrictions) {
            context.stringResource(MR.strings.skipped_reason_not_started)
        } else {
            null
        }
        val skipReasonOutsideReleasePeriod = if (MANGA_OUTSIDE_RELEASE_PERIOD in restrictions) {
            context.stringResource(MR.strings.skipped_reason_not_in_release_period)
        } else {
            null
        }

        mangaToUpdate = listToUpdate
            .filter {
                when {
                    it.manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && it.totalChapters > 0L -> {
                        skippedUpdates.add(it.manga to skipReasonNotAlwaysUpdate)
                        false
                    }

                    skipReasonCompleted != null && it.manga.status.toInt() == SManga.COMPLETED -> {
                        skippedUpdates.add(it.manga to skipReasonCompleted)
                        false
                    }

                    skipReasonNotCaughtUp != null && it.unreadCount != 0L -> {
                        skippedUpdates.add(it.manga to skipReasonNotCaughtUp)
                        false
                    }

                    skipReasonNotStarted != null && it.totalChapters > 0L && !it.hasStarted -> {
                        skippedUpdates.add(it.manga to skipReasonNotStarted)
                        false
                    }

                    skipReasonOutsideReleasePeriod != null && it.manga.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(it.manga to skipReasonOutsideReleasePeriod)
                        false
                    }

                    else -> true
                }
            }
            .sortedBy { it.manga.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)
    }

    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()
        val newUpdates = CopyOnWriteArrayList<Pair<Manga, Array<Chapter>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = fetchInterval.getWindow(ZonedDateTime.now())
        val autoUpdateMetadata = libraryPreferences.autoUpdateMetadata().get()
        val newUpdatesCount = AtomicInteger(0)

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }.values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                if (!getManga.isFavorite(manga.id)) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    try {
                                        val newChapters = updateManga(manga, fetchWindow, autoUpdateMetadata)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newChapters.isNotEmpty()) {
                                            val chaptersToDownload = filterChaptersForDownload.await(manga, newChapters)

                                            if (chaptersToDownload.isNotEmpty()) {
                                                downloadChapters(manga, chaptersToDownload)
                                                hasDownloads.set(true)
                                            }

                                            newUpdatesCount.addAndGet(newChapters.size)

                                            newUpdates.add(manga to newChapters.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoChaptersException -> context.stringResource(
                                                MR.strings.no_chapters_error,
                                            )
                                            is SourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )

                                            else -> e.message
                                        }
                                        failedUpdates.add(manga to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        val totalNewUpdates = newUpdatesCount.get()
        if (totalNewUpdates > 0) {
            libraryPreferences.newUpdatesCount().getAndSet { it + totalNewUpdates }
        }

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            // val errorFile = writeErrorFile(failedUpdates)
            // notifier.showUpdateErrorNotification(failedUpdates.size, errorFile.getUriCompat(context))
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        downloadManager.downloadChapters(manga, chapters, false)
    }

    private suspend fun updateManga(
        manga: Manga,
        fetchWindow: Pair<Long, Long>,
        autoUpdateMetadata: Boolean,
    ): List<Chapter> {
        val isAuthorityOnly = manga.source == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID

        if (isAuthorityOnly) {
            if (autoUpdateMetadata && manga.canonicalId != null) {
                try {
                    refreshCanonicalMetadata.await(manga)
                } catch (e: Exception) {
                    logcat(LogPriority.DEBUG, e) {
                        "Canonical metadata refresh failed for authority manga ${manga.title}"
                    }
                }
            }
            return emptyList()
        }

        val source = sourceManager.getOrStub(manga.source)
        val sManga = manga.toSManga()

        if (autoUpdateMetadata) {
            try {
                val networkManga = source.getMangaDetails(sManga)
                updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch = false)
            } catch (_: Exception) {}
        }

        val chapters = source.getChapterList(sManga)
        val dbManga = getManga.await(manga.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncChaptersWithSource.await(chapters, dbManga, source, false, fetchWindow)
    }

    private suspend fun withUpdateNotification(
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInteger,
        manga: Manga,
        block: suspend () -> Unit,
    ): Unit = coroutineScope {
        ensureActive()

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )

        block()

        ensureActive()

        updatingManga.remove(manga)
        completed.incrementAndGet()
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val KEY_CATEGORY = "category"

        /** Sentinel value written to [ephyra.domain.manga.model.Manga.deadSince] to signal "cleared". */
        const val DEAD_SINCE_CLEARED = 0L

        /** Manga that have been DEAD for this long are eligible for automatic migration suggestions. */
        const val DEAD_MIGRATION_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000

        /**
         * Chapter-drop threshold expressed as a fraction: NUMERATOR / DENOMINATOR.
         * A fetch that returns fewer than 70% of the previous chapter count is [SourceStatus.DEGRADED].
         */
        const val CHAPTER_DROP_THRESHOLD_NUMERATOR = 7
        const val CHAPTER_DROP_THRESHOLD_DENOMINATOR = 10

        /**
         * Determines the [SourceStatus] of a manga based on the number of chapters fetched vs.
         * the number previously known.
         *
         * @param fetchedCount chapters returned by the source in the latest refresh.
         * @param previousCount chapters known before the refresh.
         */
        fun detectSourceHealth(fetchedCount: Int, previousCount: Int): SourceStatus {
            return when {
                fetchedCount == 0 && previousCount > 0 -> SourceStatus.DEAD
                fetchedCount * CHAPTER_DROP_THRESHOLD_DENOMINATOR <
                    previousCount * CHAPTER_DROP_THRESHOLD_NUMERATOR -> SourceStatus.DEGRADED
                else -> SourceStatus.HEALTHY
            }
        }

        fun setupTask(context: Context, preferences: LibraryPreferences) {
            // Placeholder
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }

        /**
         * Enqueues a one-time manual library update, optionally restricted to a single [category].
         *
         * @return `true` if the work request was enqueued, `false` if a manual run is already
         *         in progress.
         */
        fun startNow(context: Context, category: ephyra.domain.category.model.Category? = null): Boolean {
            val wm = context.workManager
            if (wm.isRunning(WORK_NAME_MANUAL)) return false
            val inputData = workDataOf(KEY_CATEGORY to (category?.id ?: -1L))
            val request = OneTimeWorkRequestBuilder<LibraryUpdateJob>()
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)
            return true
        }
    }
}
