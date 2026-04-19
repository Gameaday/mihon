package ephyra.feature.migration.list

import androidx.annotation.FloatRange
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.SmartSourceSearchEngine
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toSManga
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.feature.migration.list.models.MigratingManga
import ephyra.feature.migration.list.models.MigratingManga.SearchResult
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority

class MigrationListScreenModel(
    mangaIds: Collection<Long>,
    extraSearchQuery: String?,
    private val preferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val migrateManga: MigrateMangaUseCase,
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId,
) : StateScreenModel<MigrationListScreenModel.State>(State()) {

    private val smartSearchEngine = SmartSourceSearchEngine(extraSearchQuery)

    val items
        inline get() = state.value.items

    private val hideUnmatched = preferences.migrationHideUnmatched().getSync()
    private val hideWithoutUpdates = preferences.migrationHideWithoutUpdates().getSync()

    private val navigateBackChannel = Channel<Unit>()
    val navigateBackEvent = navigateBackChannel.receiveAsFlow()

    /** Emitted when a migration target has no chapters — the UI should show a toast. */
    private val missingChaptersChannel = Channel<Unit>()
    val missingChaptersEvent = missingChaptersChannel.receiveAsFlow()

    private var migrateJob: Job? = null

    init {
        screenModelScope.launchIO {
            val manga = mangaIds
                .map {
                    async {
                        val manga = getManga.await(it) ?: return@async null
                        val chapterInfo = getChapterInfo(it)
                        MigratingManga(
                            manga = manga,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                            source = sourceManager.getOrStub(manga.source).getNameForMangaInfo(preferences),
                            parentContext = screenModelScope.coroutineContext,
                        )
                    }
                }
                .awaitAll()
                .filterNotNull()
            mutableState.update { it.copy(items = manga.toImmutableList()) }
            runMigrations(manga)
        }
    }

    private suspend fun getChapterInfo(id: Long) = getChaptersByMangaId.await(id).let { chapters ->
        ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }

    private suspend fun Manga.toSuccessSearchResult(matchConfidence: Double = 1.0): SearchResult.Success {
        val chapterInfo = getChapterInfo(id)
        val source = sourceManager.getOrStub(source).getNameForMangaInfo(preferences)
        return SearchResult.Success(
            manga = this,
            chapterCount = chapterInfo.chapterCount,
            latestChapter = chapterInfo.latestChapter,
            source = source,
            matchConfidence = matchConfidence,
        )
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        val prioritizeByChapters = preferences.migrationPrioritizeByChapters().get()
        val deepSearchMode = preferences.migrationDeepSearchMode().get()

        val sources = preferences.migrationSources().get()
            .mapNotNull { sourceManager.get(it) as? CatalogueSource }

        for (manga in mangas) {
            if (!currentCoroutineContext().isActive) break
            if (manga.manga.id !in state.value.mangaIds) continue
            if (manga.searchResult.value != SearchResult.Searching) continue
            if (!manga.migrationScope.isActive) continue

            val result = try {
                manga.migrationScope.async {
                    if (prioritizeByChapters) {
                        val sourceSemaphore = Semaphore(5)
                        sources.map { source ->
                            async innerAsync@{
                                sourceSemaphore.withPermit {
                                    val result = searchSource(manga.manga, source, deepSearchMode)
                                    if (result == null || result.chapterInfo.chapterCount == 0) return@innerAsync null
                                    result
                                }
                            }
                        }
                            .mapNotNull { it.await() }
                            .maxByOrNull { it.chapterInfo.latestChapter ?: 0.0 }
                    } else {
                        sources.forEach { source ->
                            val result = searchSource(manga.manga, source, deepSearchMode)
                            if (result != null) return@async result
                        }
                        null
                    }
                }
                    .await()
            } catch (_: CancellationException) {
                continue
            }

            if (result != null && result.manga.thumbnailUrl == null) {
                try {
                    val newManga = sourceManager.getOrStub(result.manga.source).getMangaDetails(result.manga.toSManga())
                    updateManga.awaitUpdateFromSource(result.manga, newManga, true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) {
                        "Failed to fetch thumbnail details for '${result.manga.title}' during migration search"
                    }
                }
            }

            manga.searchResult.value = result?.manga?.toSuccessSearchResult(result.matchConfidence)
                ?: SearchResult.NotFound

            if (result == null && hideUnmatched) {
                removeManga(manga)
            }
            if (result != null &&
                hideWithoutUpdates &&
                (result.chapterInfo.latestChapter ?: 0.0) <= (manga.latestChapter ?: 0.0)
            ) {
                removeManga(manga)
            }

            updateMigrationProgress()
        }
    }

    private suspend fun searchSource(
        manga: Manga,
        source: CatalogueSource,
        deepSearchMode: Boolean,
    ): SourceSearchResult? {
        return try {
            // Tiered search strategy — each tier is tried only if the previous returned null.
            // Tier 1: Canonical ID (FREE — local DB lookup, 0 API calls)
            // Tier 2: Primary title search (1 API call)
            // Tier 3: Alternative titles search (1 API call per alt title)
            // Tier 3b: Best near-match from tiers 2–3 (0 additional API calls)
            // Tier 4: Deep search with cleaned/split title (multiple API calls, only if enabled)
            val canonicalMatch = findByCanonicalId(manga, source.id)
            val searchResult: Manga?
            val matchConfidence: Double
            if (canonicalMatch != null) {
                logcat(LogPriority.DEBUG) { "Tier 1 (canonical ID) matched ${manga.title} on source ${source.id}" }
                searchResult = canonicalMatch
                matchConfidence = 1.0 // Canonical ID is an exact identity match
            } else {
                val titleResult = smartSearchEngine.multiTitleSearch(
                    source = source,
                    primaryTitle = manga.title,
                    alternativeTitles = manga.alternativeTitles,
                    deepSearchFallback = deepSearchMode,
                )
                if (titleResult != null) {
                    logcat(LogPriority.DEBUG) { "Title search matched ${manga.title} on source ${source.id}" }
                    searchResult = titleResult.first
                    matchConfidence = titleResult.second
                } else {
                    searchResult = null
                    matchConfidence = 0.0
                }
            }

            if (searchResult == null || (searchResult.url == manga.url && source.id == manga.source)) return null

            val localManga = networkToLocalManga(searchResult)
            try {
                val chapters = source.getChapterList(localManga.toSManga())
                syncChaptersWithSource.await(chapters, localManga, source)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
            SourceSearchResult(localManga, getChapterInfo(localManga.id), matchConfidence)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Source search failed for '${manga.title}' on source ${source.id}; skipping" }
            null
        }
    }

    /**
     * Attempts to find a library manga on the target source that shares the same canonical identity.
     * This is a zero-API-call lookup — it checks the local database only.
     * Returns null if no canonical ID is set or no match found on the target source.
     */
    private suspend fun findByCanonicalId(manga: Manga, targetSourceId: Long): Manga? {
        val canonicalId = manga.canonicalId ?: return null
        return try {
            getFavoritesByCanonicalId.await(canonicalId, manga.id)
                .firstOrNull { it.source == targetSourceId }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Canonical ID lookup failed for manga id=${manga.id}" }
            null
        }
    }

    private suspend fun updateMigrationProgress() {
        mutableState.update { state ->
            state.copy(
                finishedCount = items.count { it.searchResult.value != SearchResult.Searching },
                migrationComplete = migrationComplete(),
            )
        }
        if (items.isEmpty()) {
            navigateBack()
        }
    }

    // ── UDF entry-point ──────────────────────────────────────────────────────
    fun onEvent(event: MigrationListScreenEvent) {
        when (event) {
            is MigrationListScreenEvent.UseMangaForMigration -> useMangaForMigration(event.current, event.target)
            is MigrationListScreenEvent.MigrateMangas -> migrateMangas()
            is MigrationListScreenEvent.CopyMangas -> copyMangas()
            is MigrationListScreenEvent.CancelMigrate -> cancelMigrate()
            is MigrationListScreenEvent.MigrateNow -> migrateNow(event.mangaId, event.replace)
            is MigrationListScreenEvent.RemoveManga -> removeManga(event.mangaId)
            is MigrationListScreenEvent.ShowMigrateDialog -> showMigrateDialog(event.copy)
            is MigrationListScreenEvent.ShowExitDialog -> showExitDialog()
            is MigrationListScreenEvent.DismissDialog -> dismissDialog()
        }
    }

    private fun migrationComplete() = items.all { it.searchResult.value != SearchResult.Searching } &&
        items.any { it.searchResult.value is SearchResult.Success }

    private fun useMangaForMigration(current: Long, target: Long) {
        val migratingManga = items.find { it.manga.id == current } ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        screenModelScope.launchIO {
            val result = migratingManga.migrationScope.async {
                val manga = getManga.await(target) ?: return@async null
                try {
                    val source = sourceManager.get(manga.source)!!
                    val chapters = source.getChapterList(manga.toSManga())
                    syncChaptersWithSource.await(chapters, manga, source)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) {
                        "Chapter sync failed for '${manga.title}' during migration; target will be missing chapters"
                    }
                    return@async null
                }
                manga
            }
                .await()

            if (result == null) {
                migratingManga.searchResult.value = SearchResult.NotFound
                missingChaptersChannel.send(Unit)
                return@launchIO
            }

            try {
                val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toSManga())
                updateManga.awaitUpdateFromSource(result, newManga, true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to update manga details for '${result.title}' during migration" }
            }
            migratingManga.searchResult.value = result.toSuccessSearchResult()
            updateMigrationProgress()
        }
    }

    private fun migrateMangas() {
        migrateMangas(replace = true)
    }

    private fun copyMangas() {
        migrateMangas(replace = false)
    }

    private fun migrateMangas(replace: Boolean) {
        migrateJob = screenModelScope.launchIO {
            mutableState.update { it.copy(dialog = Dialog.Progress(0f)) }
            val items = items
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        val target = manga.searchResult.value.let {
                            if (it is SearchResult.Success) {
                                it.manga
                            } else {
                                null
                            }
                        }
                        if (target != null) {
                            migrateManga(current = manga.manga, target = target, replace = replace)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    mutableState.update {
                        it.copy(dialog = Dialog.Progress((index.toFloat() / items.size).coerceAtMost(1f)))
                    }
                }

                navigateBack()
            } finally {
                mutableState.update { it.copy(dialog = null) }
                migrateJob = null
            }
        }
    }

    private fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateBack() {
        navigateBackChannel.send(Unit)
    }

    private fun migrateNow(mangaId: Long, replace: Boolean) {
        screenModelScope.launchIO {
            val manga = items.find { it.manga.id == mangaId } ?: return@launchIO
            val target = (manga.searchResult.value as? SearchResult.Success)?.manga ?: return@launchIO
            migrateManga(current = manga.manga, target = target, replace = replace)

            removeManga(mangaId)
        }
    }

    private fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            removeManga(item)
            item.migrationScope.cancel()
            updateMigrationProgress()
        }
    }

    private fun removeManga(item: MigratingManga) {
        mutableState.update { it.copy(items = items.toPersistentList().remove(item)) }
    }

    override fun onDispose() {
        super.onDispose()
        items.forEach {
            it.migrationScope.cancel()
        }
    }

    private fun showMigrateDialog(copy: Boolean) {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = items.size,
                    skippedCount = items.count { it.searchResult.value == SearchResult.NotFound },
                ),
            )
        }
    }

    private fun showExitDialog() {
        mutableState.update {
            it.copy(dialog = Dialog.Exit)
        }
    }

    private fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    data class ChapterInfo(
        val latestChapter: Double?,
        val chapterCount: Int,
    )

    private data class SourceSearchResult(
        val manga: Manga,
        val chapterInfo: ChapterInfo,
        val matchConfidence: Double,
    )

    sealed interface Dialog {
        data class Migrate(val copy: Boolean, val totalCount: Int, val skippedCount: Int) : Dialog
        data class Progress(@FloatRange(0.0, 1.0) val progress: Float) : Dialog
        data object Exit : Dialog
    }

    data class State(
        val items: ImmutableList<MigratingManga> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        val mangaIds: List<Long> = items.map { it.manga.id }
    }
}
