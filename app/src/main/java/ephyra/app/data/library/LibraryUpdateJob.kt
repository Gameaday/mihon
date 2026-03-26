package ephyra.app.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.toSManga
import ephyra.app.data.cache.CoverCache
import ephyra.app.data.download.DownloadManager
import ephyra.app.data.notification.Notifications
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import ephyra.app.util.storage.getUriCompat
import ephyra.presentation.core.util.system.createFileInCacheDir
import ephyra.app.util.system.isConnectedToWifi
import ephyra.app.util.system.isPowerSaveMode
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
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.NoChaptersException
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import ephyra.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import ephyra.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import ephyra.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.SourceStatus
import ephyra.domain.source.model.SourceNotInstalledException
import ephyra.domain.source.service.SourceManager
import ephyra.i18n.MR
import ephyra.source.local.isLocal
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
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

    /** Cover cache filenames belonging to favorited manga — never pruned. */
    private var favoriteCoverNames: Set<String> = emptySet()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            // Defer automatic updates while battery saver is active to conserve energy.
            if (context.isPowerSaveMode) {
                return Result.retry()
            }

            // Find a running manual worker. If exists, try again later
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
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                // Prune covers that haven't been accessed in 30 days to prevent
                // unbounded disk growth from browsing activity. Favorite covers
                // are always kept so offline viewing still looks good after a break.
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
        val notifier = LibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    /**
     * Adds list of manga to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addMangaToQueue(categoryId: Long) {
        val libraryManga = getLibraryManga.await()

        // Build set of cover filenames for ALL favorites so they're never pruned.
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

        // Pre-resolve skip-reason strings once; each is identical for every manga that fails the same check.
        val skipReasonNotAlwaysUpdate = context.stringResource(MR.strings.skipped_reason_not_always_update)
        val skipReasonCompleted = if (MANGA_NON_COMPLETED in
            restrictions
        ) {
            context.stringResource(MR.strings.skipped_reason_completed)
        } else {
            null
        }
        val skipReasonNotCaughtUp = if (MANGA_HAS_UNREAD in
            restrictions
        ) {
            context.stringResource(MR.strings.skipped_reason_not_caught_up)
        } else {
            null
        }
        val skipReasonNotStarted = if (MANGA_NON_READ in
            restrictions
        ) {
            context.stringResource(MR.strings.skipped_reason_not_started)
        } else {
            null
        }
        val skipReasonOutsideReleasePeriod = if (MANGA_OUTSIDE_RELEASE_PERIOD in
            restrictions
        ) {
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

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates manga in [mangaToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each manga it calls [updateManga] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInt(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()
        val newUpdates = CopyOnWriteArrayList<Pair<Manga, Array<Chapter>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = fetchInterval.getWindow(ZonedDateTime.now())
        val autoUpdateMetadata = libraryPreferences.autoUpdateMetadata().get()
        val newUpdatesCount = AtomicInt(0)

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }.values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                // Don't continue to update if manga is not in library
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
                                                hasDownloads.store(true)
                                            }

                                            newUpdatesCount.fetchAndAdd(newChapters.size)

                                            // Convert to the manga that contains new chapters
                                            newUpdates.add(manga to newChapters.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoChaptersException -> context.stringResource(
                                                MR.strings.no_chapters_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
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

        // Write accumulated new-chapter count to preferences in a single call instead of
        // one per updated manga (which previously caused N concurrent preference read-modify-writes).
        val totalNewUpdates = newUpdatesCount.load()
        if (totalNewUpdates > 0) {
            libraryPreferences.newUpdatesCount().getAndSet { it + totalNewUpdates }
        }

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.load()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }

        // Check for unhealthy sources among the manga we just updated.
        // This queries the local DB — zero additional API cost.
        val deadManga = mutableListOf<Manga>()
        val degradedManga = mutableListOf<Manga>()
        for (libraryManga in mangaToUpdate) {
            val refreshed = getManga.await(libraryManga.manga.id) ?: continue
            // Skip local sources — they don't have network health concerns
            if (refreshed.isLocal()) continue
            when (SourceStatus.fromValue(refreshed.sourceStatus)) {
                SourceStatus.DEAD -> deadManga.add(refreshed)
                SourceStatus.DEGRADED -> degradedManga.add(refreshed)
                else -> { /* HEALTHY or REPLACED — no notification needed */ }
            }
        }
        notifier.showSourceHealthNotification(deadManga, degradedManga)

        // Check for manga that have been persistently DEAD — suggest bulk migration.
        // Only prompt if there are manga that have been DEAD for >= DEAD_MIGRATION_THRESHOLD_MS.
        val now = System.currentTimeMillis()
        val persistentlyDead = deadManga.filter { manga ->
            val deadSince = manga.deadSince
            deadSince != null && (now - deadSince) >= DEAD_MIGRATION_THRESHOLD_MS
        }
        if (persistentlyDead.isNotEmpty()) {
            notifier.showMigrationSuggestionNotification(persistentlyDead)
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
    }

    /**
     * Updates the chapters for the given manga and adds them to the database.
     *
     * @param manga the manga to update.
     * @return a pair of the inserted and removed chapters.
     */
    private suspend fun updateManga(
        manga: Manga,
        fetchWindow: Pair<Long, Long>,
        autoUpdateMetadata: Boolean,
    ): List<Chapter> {
        val isAuthorityOnly = manga.source == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID

        // Authority-only manga (no content source) — refresh canonical metadata only,
        // skip source fetching and chapter listing entirely.
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

        // Update manga metadata if needed
        // If a metadata source is configured, use that for metadata instead of the chapter source
        if (autoUpdateMetadata) {
            val metadataSourceId = manga.metadataSource?.takeIf { it > 0 }
            val metadataUrl = manga.metadataUrl?.takeIf { it.isNotEmpty() }

            // When using a dedicated metadata source, throttle automatic refreshes.
            // Metadata (description, cover, author) changes far less frequently than chapters,
            // so we only re-fetch every 7 days to be respectful of remote sources.
            val shouldFetchMetadata = if (metadataSourceId != null && metadataUrl != null) {
                val lastUpdate = manga.lastUpdate
                val daysSinceUpdate = if (lastUpdate > 0) {
                    java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
                        System.currentTimeMillis() - lastUpdate,
                    )
                } else {
                    Long.MAX_VALUE
                }
                daysSinceUpdate >= METADATA_REFRESH_INTERVAL_DAYS
            } else {
                true
            }

            if (shouldFetchMetadata) {
                try {
                    val (metaSource, metaSManga) = if (
                        metadataSourceId != null && metadataUrl != null
                    ) {
                        val metaSrc = sourceManager.getOrStub(metadataSourceId)
                        val sM = manga.toSManga().apply { url = metadataUrl }
                        metaSrc to sM
                    } else {
                        source to sManga
                    }
                    val networkManga = metaSource.getMangaDetails(metaSManga)
                    // When using a metadata source, preserve the chapter source's updateStrategy
                    // since it controls chapter fetching behavior, not metadata
                    if (metadataSourceId != null) {
                        networkManga.update_strategy = manga.updateStrategy
                    }
                    updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch = false, coverCache)
                } catch (e: Exception) {
                    // If the metadata source fails (e.g. source removed, network issue),
                    // fall back to the chapter source for metadata to avoid losing updates
                    if (metadataSourceId != null) {
                        logcat(LogPriority.WARN, e) {
                            "Metadata source failed for ${manga.title}, falling back to chapter source"
                        }
                        try {
                            val networkManga = source.getMangaDetails(sManga)
                            updateManga.awaitUpdateFromSource(
                                manga,
                                networkManga,
                                manualFetch = false,
                                coverCache,
                            )
                        } catch (_: Exception) {
                            // Both sources failed, skip metadata update entirely
                        }
                    }
                }
            }
        }

        // Refresh canonical metadata from the authoritative tracker source.
        // This captures updates like series status changes, new descriptions, and cover art
        // from the canonical source (MAL/AniList/MangaUpdates). Throttled to every 7 days.
        // No DB re-read needed: awaitUpdateFromSource preserves authority-owned fields,
        // so the original manga still has correct authority values.
        if (autoUpdateMetadata && manga.canonicalId != null) {
            val lastUpdate = manga.lastUpdate
            val daysSinceUpdate = if (lastUpdate > 0) {
                java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
                    System.currentTimeMillis() - lastUpdate,
                )
            } else {
                Long.MAX_VALUE
            }
            if (daysSinceUpdate >= METADATA_REFRESH_INTERVAL_DAYS) {
                try {
                    refreshCanonicalMetadata.await(manga)
                } catch (e: Exception) {
                    logcat(LogPriority.DEBUG, e) {
                        "Canonical metadata refresh failed for ${manga.title}"
                    }
                }
            }
        }

        val chapters = source.getChapterList(sManga)

        // Get manga from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbManga = getManga.await(manga.id)?.takeIf { it.favorite } ?: return emptyList()

        // Source health detection: compare fetched chapter count against what we had before.
        // This runs on every refresh using data we already fetched — zero additional API cost.
        // Skip for local sources — they don't have network health concerns.
        if (!manga.isLocal()) {
            val previousChapterCount = getChaptersByMangaId.await(manga.id).size
            val newStatus = detectSourceHealth(chapters.size, previousChapterCount)
            if (newStatus.value != dbManga.sourceStatus) {
                val oldStatus = SourceStatus.fromValue(dbManga.sourceStatus)
                logcat(LogPriority.INFO) {
                    "Source health changed for ${manga.title}: $oldStatus → $newStatus " +
                        "(chapters: $previousChapterCount → ${chapters.size})"
                }
                // Track dead_since: set timestamp when first marked DEAD, clear on recovery
                val deadSince = when {
                    newStatus == SourceStatus.DEAD && oldStatus != SourceStatus.DEAD ->
                        System.currentTimeMillis()
                    newStatus != SourceStatus.DEAD && oldStatus == SourceStatus.DEAD ->
                        DEAD_SINCE_CLEARED
                    else -> null // No change to dead_since
                }
                updateManga.await(
                    MangaUpdate(
                        id = manga.id,
                        sourceStatus = newStatus.value,
                        deadSince = deadSince,
                    ),
                )
            }
        }

        return syncChaptersWithSource.await(chapters, dbManga, source, false, fetchWindow)
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
        completed.incrementAndFetch()
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            mangaToUpdate.size,
        )
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("ephyra_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n")
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Manga
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                        out.write("\n! ${error}\n")
                        mangas.groupBy { it.source }.forEach { (srcId, mangas) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            mangas.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write library update error log" }
        }
        return File("")
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://ephyra.app/docs/guides/troubleshooting/"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Chapter drop threshold for DEGRADED detection.
         * If newCount < previousCount * 0.7, the source is marked DEGRADED.
         * Uses integer comparison (newCount * 10 < previousCount * 7) to avoid floating-point issues.
         */
        internal const val CHAPTER_DROP_THRESHOLD_NUMERATOR = 7
        internal const val CHAPTER_DROP_THRESHOLD_DENOMINATOR = 10

        /**
         * Sentinel value used when clearing dead_since on recovery.
         * Using 0 instead of null since SQLDelight coalesce(:deadSince, dead_since)
         * can't distinguish "set null" from "no change". We use 0 as the SQL update
         * handles this via a CASE expression.
         */
        internal const val DEAD_SINCE_CLEARED = 0L

        /**
         * How long a manga must be DEAD before suggesting migration (3 days in ms).
         * This avoids prompting for temporary outages.
         */
        internal const val DEAD_MIGRATION_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000

        /**
         * Determines source health status by comparing fetched chapter count to previous count.
         * Pure function extracted for testability.
         *
         * Rules:
         * - DEAD: fetched 0 chapters when previously had > 0
         * - DEGRADED: fetched < 70% of previous chapter count
         * - HEALTHY: all other cases (including recovery from DEGRADED/DEAD)
         */
        internal fun detectSourceHealth(fetchedCount: Int, previousCount: Int): SourceStatus {
            return when {
                fetchedCount == 0 && previousCount > 0 -> SourceStatus.DEAD
                previousCount > 0 &&
                    fetchedCount * CHAPTER_DROP_THRESHOLD_DENOMINATOR <
                    previousCount * CHAPTER_DROP_THRESHOLD_NUMERATOR -> SourceStatus.DEGRADED
                else -> SourceStatus.HEALTHY
            }
        }

        /**
         * Minimum number of days between automatic metadata refreshes for manga
         * with a dedicated metadata source. Metadata (description, cover, author)
         * changes far less frequently than chapters, so we throttle to be respectful
         * of remote sources. Manual refreshes bypass this throttle.
         */
        private const val METADATA_REFRESH_INTERVAL_DAYS = 7L

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"

        fun setupTask(
            context: Context,
            preferences: LibraryPreferences,
            prefInterval: Int? = null,
        ) {
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequest = NetworkRequest.Builder().apply {
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    if (DEVICE_ONLY_ON_WIFI in restrictions) {
                        addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    }
                    if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                }
                    .build()
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequest, networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<LibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(
            context: Context,
            category: Category? = null,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
            )
            val request = OneTimeWorkRequestBuilder<LibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
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

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        // TODO: how to get preferences here? 
                        // For now we assume setupTask will be called correctly from elsewhere
                        // Or we can use Koin Java API
                        // setupTask(context, GlobalContext.get().get())
                    }
                }
        }
    }
}
