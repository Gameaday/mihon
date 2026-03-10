package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.hippo.unifile.UniFile
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.chaptersFiltered
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.MatchUnlinkedManga
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.jellyfin.JellyfinApi
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.math.floor

class MangaScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val mangaId: Long,
    private val isFromSource: Boolean,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    readerPreferences: ReaderPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getAvailableScanlators: GetAvailableScanlators = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val filterChaptersForDownload: FilterChaptersForDownload = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<MangaScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeToEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeToStartAction().get()
    var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    private val skipFiltered by readerPreferences.skipFiltered().asState(screenModelScope)

    val isUpdateIntervalEnabled =
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateMangaRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            combine(
                getMangaAndChapters.subscribe(mangaId, applyScanlatorFilter = true).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { mangaAndChapters, _, _ -> mangaAndChapters }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (manga, chapters) ->
                    val metadataSourceName = manga.metadataSource?.takeIf { it > 0 }?.let { id ->
                        Injekt.get<SourceManager>().get(id)?.name
                    }
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = chapters.toChapterListItems(manga),
                            metadataSourceName = metadataSourceName,
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            getExcludedScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators)
                    }
                }
        }

        screenModelScope.launchIO {
            getAvailableScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators)
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val manga = getMangaAndChapters.awaitManga(mangaId)
            val chapters = getMangaAndChapters.awaitChapters(mangaId, applyScanlatorFilter = true)
                .toChapterListItems(manga)

            if (!manga.favorite) {
                setMangaDefaultChapterFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
                val sourceManager = Injekt.get<SourceManager>()
                val metadataSourceName = manga.metadataSource?.takeIf { it > 0 }?.let { id ->
                    sourceManager.get(id)?.name
                }
                State.Success(
                    manga = manga,
                    source = sourceManager.getOrStub(manga.source),
                    isFromSource = isFromSource,
                    chapters = chapters,
                    availableScanlators = getAvailableScanlators.await(mangaId),
                    excludedScanlators = getExcludedScanlators.await(mangaId),
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                    hideMissingChapters = libraryPreferences.hideMissingChapters().get(),
                    metadataSourceName = metadataSourceName,
                )
            }

            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // Fetch info-chapters when needed
            if (screenModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchMangaFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
                async { fetchMangaFromSource(manualFetch) },
                async { fetchChaptersFromSource(manualFetch) },
            )
            fetchFromSourceTasks.awaitAll()

            // Authority refresh is handled inside fetchMangaFromSource() when
            // manualFetch=true, so no separate call is needed here.  Doing it
            // twice caused the description to visibly flicker (authority vs
            // content-source descriptions swapping).

            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    // Manga info - start

    /**
     * Fetch manga information from source.
     * If a metadata source is configured, uses that for metadata instead of the chapter source.
     * The updateStrategy is preserved from the chapter source since it controls chapter fetching.
     * If the metadata source fails, falls back to the chapter source automatically.
     */
    private suspend fun fetchMangaFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val manga = state.manga
                val isAuthorityOnly =
                    manga.source == eu.kanade.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID

                // Authority-only manga have no content source — refresh from canonical tracker only.
                if (isAuthorityOnly) {
                    if (manga.canonicalId != null) {
                        val refreshCanonical: eu.kanade.domain.track.interactor.RefreshCanonicalMetadata =
                            Injekt.get()
                        refreshCanonical.await(manga)
                    }
                    return@withIOContext
                }

                val metadataSourceId = manga.metadataSource?.takeIf { it > 0 }
                val metadataUrl = manga.metadataUrl?.takeIf { it.isNotEmpty() }

                var networkManga: SManga? = null

                // Try metadata source first if configured
                if (metadataSourceId != null && metadataUrl != null) {
                    try {
                        val metaSrc = Injekt.get<SourceManager>().getOrStub(metadataSourceId)
                        val sM = manga.toSManga().apply { url = metadataUrl }
                        networkManga = metaSrc.getMangaDetails(sM).also {
                            // Preserve the chapter source's updateStrategy
                            it.update_strategy = manga.updateStrategy
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.WARN, e) {
                            "Metadata source failed for ${manga.title}, falling back to chapter source"
                        }
                    }
                }

                // Fall back to chapter source if metadata source wasn't used or failed
                if (networkManga == null) {
                    networkManga = state.source.getMangaDetails(manga.toSManga())
                }

                updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch)

                // On manual refresh, also refresh metadata from the canonical tracker source
                // to capture updates (status changes, new cover art, description updates).
                // This uses a separate API call from the content source refresh above.
                if (manualFetch && manga.canonicalId != null) {
                    try {
                        val refreshCanonical: eu.kanade.domain.track.interactor.RefreshCanonicalMetadata =
                            Injekt.get()
                        refreshCanonical.await(manga)
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) {
                            "Canonical metadata refresh failed for ${manga.title}"
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    /**
     * Set a preferred metadata source for this manga.
     * When set, metadata (description, cover, etc.) will be fetched from this source
     * instead of the chapter source.
     */
    fun setMetadataSource(sourceId: Long, mangaUrl: String) {
        val manga = successState?.manga ?: return
        screenModelScope.launchIO {
            updateManga.await(
                tachiyomi.domain.manga.model.MangaUpdate(
                    id = manga.id,
                    metadataSource = sourceId,
                    metadataUrl = mangaUrl,
                ),
            )
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.metadata_source_set),
                withDismissAction = true,
            )
        }
    }

    /**
     * Toggles a per-field metadata lock (Jellyfin-style).
     * When locked, the authority refresh will skip this field, preserving user edits.
     */
    fun toggleLockedField(field: Long) {
        val manga = successState?.manga ?: return
        val newLocked = manga.lockedFields xor field
        screenModelScope.launchIO {
            updateManga.await(
                tachiyomi.domain.manga.model.MangaUpdate(
                    id = manga.id,
                    lockedFields = newLocked,
                ),
            )
        }
    }

    fun setLockedFields(mask: Long) {
        val manga = successState?.manga ?: return
        screenModelScope.launchIO {
            updateManga.await(
                tachiyomi.domain.manga.model.MangaUpdate(
                    id = manga.id,
                    lockedFields = mask,
                ),
            )
        }
    }

    /**
     * Refreshes metadata from the canonical authority source only, without
     * touching the content source.  This is the Jellyfin-style "re-scan from
     * metadata provider" action — it respects per-field locks.
     */
    fun refreshFromAuthority() {
        val manga = successState?.manga ?: return
        if (manga.canonicalId == null) return
        screenModelScope.launchIO {
            try {
                val refreshCanonical = Injekt.get<eu.kanade.domain.track.interactor.RefreshCanonicalMetadata>()
                refreshCanonical.await(manga)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "Authority-only refresh failed for ${manga.title}"
                }
            }
        }
    }

    fun toggleFavorite() {
        val hasAuthority = successState?.manga?.canonicalId != null
        toggleFavorite(
            onRemoved = {
                screenModelScope.launch {
                    if (hasDownloads()) {
                        val result = snackbarHostState.showSnackbar(
                            message = context.stringResource(MR.strings.delete_downloads_for_manga),
                            actionLabel = context.stringResource(MR.strings.action_delete),
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            deleteDownloads()
                        }
                    }
                    if (hasAuthority) {
                        val result = snackbarHostState.showSnackbar(
                            message = context.stringResource(MR.strings.unlink_authority_on_remove),
                            actionLabel = context.stringResource(MR.strings.edit_metadata_unlink),
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            unlinkAuthority()
                        }
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val manga = state.manga

            if (isFavorited) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.removeCovers() != manga) {
                        updateManga.awaitUpdateCoverLastModified(manga.id)
                    }
                    // Jellyfin: unmark favorite on server when removing from library
                    markJellyfinFavoriteIfLinked(manga, favorite = false)
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicates = getDuplicateLibraryManga(manga)

                    if (duplicates.isNotEmpty()) {
                        updateSuccessState { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> showChangeCategoryDialog()
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(manga, state.source)

                // Auto-link to authority source in background (Jellyfin-style:
                // adding to library automatically resolves metadata provider)
                if (manga.canonicalId == null) {
                    try {
                        val matchUnlinkedManga: MatchUnlinkedManga = Injekt.get()
                        matchUnlinkedManga.awaitSingle(manga)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) { "Auto-link on add-to-library skipped" }
                    }
                }

                // Jellyfin: mark as favorite on server when adding to library
                // (Jellyfin "favorite" ≈ "in library" concept)
                markJellyfinFavoriteIfLinked(manga, favorite = true)
            }
        }
    }

    fun showChangeCategoryDialog() {
        val manga = successState?.manga ?: return
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    fun showSetFetchIntervalDialog() {
        val manga = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetFetchInterval(manga))
        }
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateManga.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = mangaRepository.getMangaById(manga.id)
                updateSuccessState { it.copy(manga = updatedManga) }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteManga(state.manga, state.source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveMangaToCategory(categoryIds)
    }

    private fun moveMangaToCategory(categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            downloadManager.progressFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private fun List<Chapter>.toChapterListItems(manga: Manga): List<ChapterList.Item> {
        val isLocal = manga.isLocal()
        return map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    manga.title,
                    manga.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
            )
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        // Authority-only manga have no content source — no chapters to fetch.
        if (state.manga.source == eu.kanade.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID) return
        try {
            withIOContext {
                val chapters = state.source.getChapterList(state.manga.toSManga())

                val newChapters = syncChaptersWithSource.await(
                    chapters,
                    state.manga,
                    state.source,
                    manualFetch,
                )

                if (manualFetch) {
                    downloadNewChapters(newChapters)
                }
            }
        } catch (e: Throwable) {
            val message = if (e is NoChaptersException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newManga = mangaRepository.getMangaById(mangaId)
            updateSuccessState { it.copy(manga = newManga, isRefreshingData = false) }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        screenModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    private fun getUnreadChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.read && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getChapterSort(manga))
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun getBookmarkedChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> chapter.bookmark && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(chapterId)
            } else {
                downloadChapters(chapters)
            }

            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.snack_add_to_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        when (action) {
            DownloadAction.SYNC_TO_JELLYFIN,
            DownloadAction.SYNC_READ_TO_JELLYFIN,
            DownloadAction.SYNC_ALL_TO_JELLYFIN,
            -> {
                syncToJellyfin(action)
                return
            }
            DownloadAction.NEXT_1_CHAPTER,
            DownloadAction.NEXT_5_CHAPTERS,
            DownloadAction.NEXT_10_CHAPTERS,
            DownloadAction.NEXT_25_CHAPTERS,
            DownloadAction.UNREAD_CHAPTERS,
            DownloadAction.BOOKMARKED_CHAPTERS,
            -> { /* handled below */ }
        }
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_CHAPTERS -> getUnreadChaptersSorted().take(25)
            DownloadAction.UNREAD_CHAPTERS -> getUnreadChapters()
            DownloadAction.BOOKMARKED_CHAPTERS -> getBookmarkedChapters()
            DownloadAction.SYNC_TO_JELLYFIN,
            DownloadAction.SYNC_READ_TO_JELLYFIN,
            DownloadAction.SYNC_ALL_TO_JELLYFIN,
            -> emptyList() // already handled above
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    /**
     * Syncs chapters to the Jellyfin server by downloading them locally with
     * Jellyfin-compatible naming and then triggering a server library scan.
     *
     * Compares local chapter list against what Jellyfin already has, then downloads
     * only the missing chapters. This implements the "download once, read everywhere"
     * philosophy: content is downloaded from any source, named for Jellyfin compatibility,
     * and a library scan makes it available on all devices.
     *
     * Pre-flight checks (fail fast with clear messages):
     * 1. User must be logged in to Jellyfin
     * 2. Manga must be linked to a Jellyfin library item
     * 3. Server must be reachable (connectivity check before starting expensive work)
     *
     * Error handling:
     * - Not logged in → toast with login prompt
     * - Manga not linked to Jellyfin → toast with explanation
     * - Server unreachable → toast with connectivity message
     * - Library scan not attempted for non-admin users (proactive gating)
     * - Library scan fails → toast explaining the issue, sync still succeeds
     *
     * @param action determines which chapters to sync:
     *   - [DownloadAction.SYNC_TO_JELLYFIN]: unread chapters missing from JF
     *   - [DownloadAction.SYNC_READ_TO_JELLYFIN]: read chapters missing from JF (fill gaps)
     *   - [DownloadAction.SYNC_ALL_TO_JELLYFIN]: ALL chapters missing from JF (complete library)
     */
    private fun syncToJellyfin(action: DownloadAction) {
        val successState = successState ?: return
        val manga = successState.manga

        // Pre-check: user must be logged in to Jellyfin
        if (!trackerManager.jellyfin.isLoggedIn) {
            screenModelScope.launchIO {
                withUIContext {
                    context.toast(context.stringResource(MR.strings.jellyfin_sync_not_logged_in))
                }
            }
            return
        }

        // Pre-check: manga must be linked to Jellyfin
        val canonicalId = manga.canonicalId
        if (canonicalId == null || !canonicalId.startsWith("jf:")) {
            screenModelScope.launchIO {
                withUIContext {
                    context.toast(context.stringResource(MR.strings.jellyfin_sync_not_linked))
                }
            }
            return
        }

        screenModelScope.launchIO {
            // Pre-flight: verify server is reachable before starting expensive work
            val serverUrl = trackerManager.jellyfin.getServerUrl()
            if (serverUrl.isBlank() || !trackerManager.jellyfin.api.checkServerReachable(serverUrl)) {
                withUIContext {
                    context.toast(context.stringResource(MR.strings.jellyfin_sync_server_unreachable))
                }
                return@launchIO
            }

            // Temporarily enable Jellyfin-compatible naming for the sync download,
            // then restore the original setting afterward.
            val wasJellyfinNamingEnabled = libraryPreferences.jellyfinCompatibleNaming().get()
            libraryPreferences.jellyfinCompatibleNaming().set(true)

            try {
                // Get the actual chapter items from Jellyfin for accurate comparison
                val jellyfinChapters = getJellyfinChapterNames(manga)

                // Select chapters missing from Jellyfin based on the action
                val allChapterItems = allChapters.orEmpty()
                val missingFromServer = when (action) {
                    DownloadAction.SYNC_READ_TO_JELLYFIN -> allChapterItems.filter { item ->
                        item.chapter.read && !isChapterOnServer(item.chapter, jellyfinChapters)
                    }
                    DownloadAction.SYNC_ALL_TO_JELLYFIN -> allChapterItems.filter { item ->
                        !isChapterOnServer(item.chapter, jellyfinChapters)
                    }
                    else -> allChapterItems.filter { item ->
                        !item.chapter.read && !isChapterOnServer(item.chapter, jellyfinChapters)
                    }
                }

                // Split into chapters that need downloading vs already downloaded locally
                val needsDownload = missingFromServer
                    .filter { it.downloadState == Download.State.NOT_DOWNLOADED }
                    .map { it.chapter }
                val alreadyDownloadedItems = missingFromServer
                    .filter { it.downloadState == Download.State.DOWNLOADED }
                val alreadyDownloadedCount = alreadyDownloadedItems.size

                if (needsDownload.isNotEmpty()) {
                    downloadChapters(needsDownload)
                }

                // Copy already-downloaded chapters to Jellyfin library folder if configured
                val jellyfinFolder = downloadPreferences.jellyfinLibraryFolder().get()
                if (jellyfinFolder.isNotBlank() && alreadyDownloadedItems.isNotEmpty()) {
                    copyChaptersToJellyfinFolder(
                        manga,
                        alreadyDownloadedItems.map { it.chapter },
                        jellyfinFolder,
                    )
                }

                val totalSynced = needsDownload.size + alreadyDownloadedCount
                if (totalSynced > 0) {
                    withUIContext {
                        context.toast(
                            context.stringResource(
                                MR.strings.jellyfin_sync_started,
                                totalSynced,
                            ),
                        )
                    }
                } else {
                    withUIContext {
                        context.toast(context.stringResource(MR.strings.jellyfin_sync_up_to_date))
                    }
                }

                // Trigger a Jellyfin library scan so the server discovers files.
                // Scans are useful even when only already-downloaded chapters are
                // missing from the server (they're on disk but not yet indexed).
                if (totalSynced > 0) {
                    val scanResult = triggerJellyfinLibraryScan()
                    if (scanResult != null) {
                        withUIContext {
                            context.toast(scanResult)
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Jellyfin sync failed" }
                withUIContext {
                    context.toast(
                        context.stringResource(MR.strings.jellyfin_sync_error, e.message ?: "Unknown error"),
                    )
                }
            } finally {
                // Restore the original Jellyfin naming preference
                libraryPreferences.jellyfinCompatibleNaming().set(wasJellyfinNamingEnabled)
            }
        }
    }

    /**
     * Copies already-downloaded chapter CBZ files to the configured Jellyfin library folder.
     * This makes locally-downloaded content discoverable by the Jellyfin server via library scan,
     * even when the app's download directory is not directly accessible to the server (e.g., the
     * Jellyfin folder is an SMB/NFS network share on a NAS).
     */
    private fun copyChaptersToJellyfinFolder(
        manga: Manga,
        chapters: List<Chapter>,
        jellyfinFolderUri: String,
    ) {
        try {
            val jellyfinRoot = UniFile.fromUri(
                context,
                Uri.parse(jellyfinFolderUri),
            ) ?: run {
                logcat(LogPriority.WARN) { "Jellyfin library folder not accessible" }
                return
            }

            val seriesDir = jellyfinRoot.createDirectory(
                DiskUtil.buildValidFilename(manga.title),
            ) ?: run {
                logcat(LogPriority.WARN) { "Failed to create series dir in Jellyfin folder" }
                return
            }

            val source = Injekt.get<SourceManager>().getOrStub(manga.source)
            val mangaDirResult = downloadProvider.getMangaDir(manga.title, source)
            val mangaDir = mangaDirResult.getOrNull() ?: run {
                logcat(LogPriority.WARN) { "Could not resolve manga download directory" }
                return
            }

            var copied = 0
            for (chapter in chapters) {
                val jellyfinName = downloadProvider.getJellyfinChapterDirName(
                    manga.title,
                    chapter.chapterNumber,
                    chapter.name,
                )
                val cbzName = "$jellyfinName.cbz"

                // Skip if already in the Jellyfin folder
                val existing = seriesDir.findFile(cbzName)
                if (existing != null && existing.length() > 0) continue

                // Try Jellyfin-named CBZ first, then fall back to standard naming
                val sourceCbz = mangaDir.findFile(cbzName)
                    ?: mangaDir.findFile(
                        downloadProvider.getChapterDirName(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                        ) + ".cbz",
                    )

                if (sourceCbz == null || !sourceCbz.exists()) continue

                val destFile = seriesDir.createFile(cbzName) ?: continue
                try {
                    sourceCbz.openInputStream().use { input ->
                        destFile.openOutputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    copied++
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to copy $cbzName to Jellyfin folder" }
                    destFile.delete()
                }
            }

            if (copied > 0) {
                logcat(LogPriority.INFO) { "Copied $copied chapter(s) to Jellyfin library folder" }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy chapters to Jellyfin folder" }
        }
    }

    /**
     * Returns the names of chapters that already exist in the Jellyfin library
     * for the manga linked to this screen. Returns an empty set if the manga
     * is not linked to Jellyfin or if the query fails.
     *
     * Uses actual item names from the server for accurate comparison instead
     * of relying on sequential count, which breaks for series with chapter gaps.
     */
    private suspend fun getJellyfinChapterNames(manga: Manga): Set<String> {
        val canonicalId = manga.canonicalId ?: return emptySet()
        if (!canonicalId.startsWith("jf:")) return emptySet()

        val tracks = getTracks.await(manga.id)
        val jellyfinTrack = tracks.firstOrNull {
            it.trackerId == trackerManager.jellyfin.id
        } ?: return emptySet()

        return try {
            trackerManager.jellyfin.getChaptersFromServer(jellyfinTrack.remoteUrl)
                .map { it.name.lowercase(java.util.Locale.ROOT) }
                .toSet()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin chapter list" }
            emptySet()
        }
    }

    /**
     * Returns the count of chapters that already exist in the Jellyfin library
     * for the manga linked to this screen. Returns 0 if the manga is not
     * linked to Jellyfin or if the query fails.
     *
     * Jellyfin returns children sorted by SortName ascending, so the count
     * of children maps directly to which sequential chapters are present.
     */
    private suspend fun getJellyfinChapterCount(manga: Manga): Int {
        val canonicalId = manga.canonicalId ?: return 0
        if (!canonicalId.startsWith("jf:")) return 0

        val tracks = getTracks.await(manga.id)
        val jellyfinTrack = tracks.firstOrNull {
            it.trackerId == trackerManager.jellyfin.id
        } ?: return 0

        return try {
            trackerManager.jellyfin.getChaptersFromServer(jellyfinTrack.remoteUrl).size
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin chapter list" }
            0
        }
    }

    /**
     * Checks if a chapter already exists on the Jellyfin server by comparing
     * against the set of chapter names returned from the server.
     *
     * Falls back to count-based comparison if the name set is empty
     * (e.g., if server names don't match local chapter format).
     * The name comparison is case-insensitive and checks if the server-side
     * name contains the chapter number to handle different naming conventions.
     */
    private fun isChapterOnServer(chapter: Chapter, serverChapterNames: Set<String>): Boolean {
        if (serverChapterNames.isEmpty()) return false

        // Format the chapter number the way Jellyfin-compatible naming produces it
        val chapterNum = chapter.chapterNumber
        val formattedNum = if (chapterNum == kotlin.math.floor(chapterNum)) {
            String.format(java.util.Locale.ROOT, "%03d", chapterNum.toInt())
        } else {
            String.format(java.util.Locale.ROOT, "%06.2f", chapterNum)
        }

        // Check if any server chapter name contains this chapter number pattern
        val chPattern = "ch. $formattedNum".lowercase(java.util.Locale.ROOT)
        val chPatternAlt = "ch.$formattedNum".lowercase(java.util.Locale.ROOT)
        return serverChapterNames.any { name ->
            name.contains(chPattern) || name.contains(chPatternAlt)
        }
    }

    /**
     * Triggers a Jellyfin library scan after syncing content. This makes newly
     * downloaded files appear in the Jellyfin library without manual intervention.
     *
     * Proactively checks cached admin status before making the network request.
     * Non-admin users get instant feedback instead of waiting for a 403 response.
     * The admin status is cached during login from the user's Policy.IsAdministrator.
     *
     * Returns a user-facing warning message if the scan failed or was skipped,
     * or null on success.
     */
    private suspend fun triggerJellyfinLibraryScan(): String? {
        val jellyfin = trackerManager.jellyfin
        if (!jellyfin.isLoggedIn) return null
        val serverUrl = jellyfin.getServerUrl()
        if (serverUrl.isBlank()) return null

        // Proactive admin gating: skip the network request entirely for non-admin users.
        // The admin status was cached at login from AuthenticateByName → User.Policy.IsAdministrator.
        val isAdmin = trackPreferences.jellyfinIsAdmin().get()
        if (!isAdmin) {
            logcat(LogPriority.INFO) { "Skipping library scan — user is not admin (cached)" }
            return context.stringResource(MR.strings.jellyfin_scan_requires_admin)
        }

        return when (val result = jellyfin.api.triggerLibraryScan(serverUrl)) {
            is JellyfinApi.LibraryScanResult.Success -> {
                logcat(LogPriority.INFO) { "Triggered Jellyfin library scan after sync" }
                null
            }
            is JellyfinApi.LibraryScanResult.Forbidden -> {
                // Admin status may have changed since login — update cache
                trackPreferences.jellyfinIsAdmin().set(false)
                logcat(LogPriority.WARN) { "Library scan denied — user is not admin (updated cache)" }
                context.stringResource(MR.strings.jellyfin_scan_requires_admin)
            }
            is JellyfinApi.LibraryScanResult.Error -> {
                logcat(LogPriority.WARN) { "Library scan failed: ${result.message}" }
                context.stringResource(MR.strings.jellyfin_scan_failed, result.message)
            }
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    fun markPreviousChapterRead(pointer: Chapter) {
        val manga = successState?.manga ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }
        val prevChapters = if (manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        screenModelScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )

            if (!read || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = getTracks.await(mangaId)
            val maxChapterNumber = chapters.maxOf { it.chapterNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastChapterRead }

            if (!shouldPromptTrackingUpdate) return@launchIO
            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackChapter.await(context, mangaId, maxChapterNumber)
                withUIContext {
                    context.toast(context.stringResource(MR.strings.trackers_updated_summary, maxChapterNumber.toInt()))
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.confirm_tracker_update, maxChapterNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackChapter.await(context, mangaId, maxChapterNumber)
            }
        }
    }

    private suspend fun refreshTrackers(
        refreshTracks: RefreshTracks = Injekt.get(),
    ) {
        refreshTracks.await(mangaId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                val errorDetail = when (e) {
                    is HttpException -> "HTTP ${e.code}"
                    is java.net.SocketTimeoutException -> "timeout"
                    is java.net.UnknownHostException -> "no connection"
                    else -> e.message ?: "unknown error"
                }
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data mangaId=$mangaId for ${track!!.name}: $errorDetail"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track!!.name,
                            errorDetail,
                        ),
                    )
                }
            }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(chapters: List<Chapter>) {
        val manga = successState?.manga ?: return
        downloadManager.downloadChapters(manga, chapters)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteChapters(
                        chapters,
                        state.manga,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            val manga = successState?.manga ?: return@launchNonCancellable
            val chaptersToDownload = filterChaptersForDownload.await(manga, chapters)

            if (chaptersToDownload.isNotEmpty()) {
                downloadChapters(chaptersToDownload)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val selectedIndex = successState.processedChapters.indexOfFirst { it.id == item.chapter.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.id, selected)

                if (selected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (!fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(chapters = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val manga = successState?.manga ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(manga.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { mangaTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                val supportedTrackers = loggedInTrackers.filter { (it as? EnhancedTracker)?.accept(source!!) ?: true }
                val supportedTrackerIds = supportedTrackers.mapTo(HashSet()) { it.id }
                val supportedTrackerTracks = mangaTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks.size to supportedTrackers.isNotEmpty()
            }
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { (trackingCount, hasLoggedInTrackers) ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingCount,
                            hasLoggedInTrackers = hasLoggedInTrackers,
                        )
                    }
                }
        }
    }

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        data class SetFetchInterval(val manga: Manga) : Dialog
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
        data object EditMetadata : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    fun showEditMetadataDialog() {
        updateSuccessState { it.copy(dialog = Dialog.EditMetadata) }
    }

    /**
     * Applies a metadata field update and auto-locks the field (Jellyfin behavior:
     * manual edits are automatically protected from authority overwrite).
     * When the manga is linked to a Jellyfin authority, also pushes the metadata
     * change back to the Jellyfin server (bidirectional sync).
     */
    private fun editFieldAndLock(
        lockField: Long,
        buildUpdate: (mangaId: Long, newLockedFields: Long) -> tachiyomi.domain.manga.model.MangaUpdate,
    ) {
        val manga = successState?.manga ?: return
        screenModelScope.launchIO {
            updateManga.await(
                buildUpdate(manga.id, manga.lockedFields or lockField),
            )
            // Push metadata to Jellyfin if linked via "jf" canonical prefix
            pushMetadataToJellyfinIfLinked(manga)
        }
    }

    /**
     * Pushes updated metadata to the Jellyfin server when the manga is linked
     * to Jellyfin as its authority source. This enables bidirectional metadata sync —
     * edits in the app are reflected on the server.
     */
    private suspend fun pushMetadataToJellyfinIfLinked(manga: Manga) {
        val canonicalId = manga.canonicalId ?: return
        if (!canonicalId.startsWith("jf:")) return
        val jellyfin = trackerManager.jellyfin
        if (!jellyfin.isLoggedIn) return

        // Find the Jellyfin track for this manga to get the tracking URL
        val tracks = getTracks.await(manga.id)
        val jellyfinTrack = tracks.firstOrNull {
            it.trackerId == trackerManager.jellyfin.id
        } ?: return

        // Re-read the manga to get the latest values after the update
        val updated = getMangaAndChapters.awaitManga(manga.id)

        jellyfin.pushMetadataToServer(
            trackingUrl = jellyfinTrack.remoteUrl,
            title = updated.title,
            description = updated.description,
            genres = updated.genre,
            author = updated.author,
            artist = updated.artist,
        )
    }

    /**
     * Marks or unmarks the manga as a favorite on the Jellyfin server when it's
     * linked to Jellyfin as its authority source. Jellyfin "favorite" maps to
     * the "in library" concept — adding to library marks favorite on server,
     * removing from library unmarks it.
     *
     * Checks both canonicalId ("jf:" prefix) and track existence because
     * canonicalId may be set without a track (e.g., during initial matching)
     * or a track may exist without canonicalId (manual Jellyfin bind).
     * The tracking URL from the track record is needed to resolve the server URL and item ID.
     */
    private suspend fun markJellyfinFavoriteIfLinked(manga: Manga, favorite: Boolean) {
        val canonicalId = manga.canonicalId ?: return
        if (!canonicalId.startsWith("jf:")) return
        val jellyfin = trackerManager.jellyfin
        if (!jellyfin.isLoggedIn) return
        if (!libraryPreferences.jellyfinSyncEnabled().get()) return

        val tracks = getTracks.await(manga.id)
        val jellyfinTrack = tracks.firstOrNull {
            it.trackerId == trackerManager.jellyfin.id
        } ?: return

        try {
            val serverUrl = jellyfin.resolveServerUrl(jellyfinTrack.remoteUrl)
            val itemId = jellyfin.api.getItemIdFromUrl(jellyfinTrack.remoteUrl)
            val userId = trackPreferences.jellyfinUserId().get()
            if (userId.isNotBlank()) {
                jellyfin.api.markFavorite(serverUrl, userId, itemId, favorite)
                logcat(LogPriority.INFO) { "Jellyfin favorite=$favorite for item $itemId" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to mark Jellyfin favorite for ${manga.title}" }
        }
    }

    fun editTitle(value: String) {
        val manga = successState?.manga ?: return
        if (value == manga.title) return
        editFieldAndLock(tachiyomi.domain.manga.model.LockedField.TITLE) { id, locked ->
            tachiyomi.domain.manga.model.MangaUpdate(id = id, title = value, lockedFields = locked)
        }
    }

    fun editAuthor(value: String) {
        val manga = successState?.manga ?: return
        if (value == (manga.author ?: "")) return
        editFieldAndLock(tachiyomi.domain.manga.model.LockedField.AUTHOR) { id, locked ->
            tachiyomi.domain.manga.model.MangaUpdate(id = id, author = value, lockedFields = locked)
        }
    }

    fun editArtist(value: String) {
        val manga = successState?.manga ?: return
        if (value == (manga.artist ?: "")) return
        editFieldAndLock(tachiyomi.domain.manga.model.LockedField.ARTIST) { id, locked ->
            tachiyomi.domain.manga.model.MangaUpdate(id = id, artist = value, lockedFields = locked)
        }
    }

    fun editDescription(value: String) {
        val manga = successState?.manga ?: return
        if (value == (manga.description ?: "")) return
        editFieldAndLock(tachiyomi.domain.manga.model.LockedField.DESCRIPTION) { id, locked ->
            tachiyomi.domain.manga.model.MangaUpdate(id = id, description = value, lockedFields = locked)
        }
    }

    fun editStatus(value: Long) {
        val manga = successState?.manga ?: return
        if (value == manga.status) return
        editFieldAndLock(tachiyomi.domain.manga.model.LockedField.STATUS) { id, locked ->
            tachiyomi.domain.manga.model.MangaUpdate(id = id, status = value, lockedFields = locked)
        }
    }

    fun editGenres(value: List<String>) {
        val manga = successState?.manga ?: return
        if (value == manga.genre) return
        editFieldAndLock(tachiyomi.domain.manga.model.LockedField.GENRE) { id, locked ->
            tachiyomi.domain.manga.model.MangaUpdate(id = id, genre = value, lockedFields = locked)
        }
    }

    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(target = manga, current = duplicate)) }
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        screenModelScope.launchIO {
            setExcludedScanlators.await(mangaId, excludedScanlators)
        }
    }

    /**
     * Resolves the canonical ID for this manga by checking tracker bindings and searching
     * tracker APIs. Shows a snackbar with the result.
     * If no trackers are available for search (Phase 2), guides the user to enable one.
     * This is the per-manga version of the bulk "Resolve all unlinked" operation.
     */
    fun resolveCanonicalId() {
        val manga = successState?.manga ?: return
        if (manga.canonicalId != null) {
            screenModelScope.launch {
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.manga_already_linked),
                )
            }
            return
        }
        screenModelScope.launchIO {
            try {
                val matchUnlinkedManga: MatchUnlinkedManga = Injekt.get()
                val result = matchUnlinkedManga.awaitSingle(manga)
                if (result != null) {
                    // Refresh manga from DB so UI reflects the new canonical ID
                    // (e.g. hides the "Link to authority" toolbar button, shows updated metadata)
                    try {
                        val updatedManga = mangaRepository.getMangaById(mangaId)
                        updateSuccessState { it.copy(manga = updatedManga) }
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) { "Failed to refresh manga state after linking" }
                    }
                    withUIContext {
                        snackbarHostState.showSnackbar(
                            context.stringResource(MR.strings.manga_linked_success, result),
                        )
                    }
                } else {
                    withUIContext {
                        if (!matchUnlinkedManga.hasQueryableTracker()) {
                            // No trackers available for search — guide user to settings
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.no_tracker_for_linking),
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.manga_linked_no_match),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to resolve canonical ID" }
                withUIContext {
                    val message = if (e is IOException) {
                        context.stringResource(MR.strings.manga_linked_error)
                    } else {
                        context.stringResource(MR.strings.manga_linked_no_match)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    /**
     * Removes the authority link (canonicalId) from this manga, clears all per-field
     * locks, and refreshes the UI state. After unlinking, the user can re-identify
     * via the "Identify" button in the metadata editor.
     */
    fun unlinkAuthority() {
        val manga = successState?.manga ?: return
        if (manga.canonicalId == null) return
        screenModelScope.launchIO {
            mangaRepository.clearCanonicalId(manga.id)
            // Refresh in-memory state so the UI reflects the change
            try {
                val updatedManga = mangaRepository.getMangaById(mangaId)
                updateSuccessState { it.copy(manga = updatedManga) }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to reload manga after unlinking authority" }
            }
        }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: Source,
            val isFromSource: Boolean,
            val chapters: List<ChapterList.Item>,
            val availableScanlators: Set<String>,
            val excludedScanlators: Set<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val hideMissingChapters: Boolean = false,
            val metadataSourceName: String? = null,
        ) : State {
            val processedChapters by lazy {
                chapters.applyFilters(manga).toList()
            }

            val isAnySelected by lazy {
                chapters.fastAny { it.selected }
            }

            val chapterListItems by lazy {
                if (hideMissingChapters) {
                    return@lazy processedChapters
                }

                processedChapters.insertSeparators { before, after ->
                    val (lowerChapter, higherChapter) = if (manga.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherChapter == null) return@insertSeparators null

                    if (lowerChapter == null) {
                        floor(higherChapter.chapter.chapterNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateChapterGap(higherChapter.chapter, lowerChapter.chapter)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            ChapterList.MissingCount(
                                id = "${lowerChapter?.id}-${higherChapter.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.any { it in availableScanlators }

            val filterActive: Boolean
                get() = scanlatorFilterActive || manga.chaptersFiltered()

            /**
             * Whether this manga is linked to a Jellyfin server.
             * Computed from the canonical ID prefix set during Jellyfin tracker binding.
             */
            val isJellyfinLinked: Boolean
                get() = manga.canonicalId?.startsWith("jf:") == true

            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val isLocalManga = manga.isLocal()
                val unreadFilter = manga.unreadFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                    .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
            }
        }
    }
}

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
    ) : ChapterList() {
        val id = chapter.id
        val isDownloaded = downloadState == Download.State.DOWNLOADED
    }
}
