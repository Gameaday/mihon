package ephyra.feature.manga

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
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam
import com.hippo.unifile.UniFile
import ephyra.core.preference.asState
import ephyra.core.util.addOrRemove
import ephyra.core.util.insertSeparators
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.chaptersFiltered
import ephyra.domain.manga.model.downloadedFilter
import ephyra.domain.manga.model.toSManga
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.model.AutoTrackState
import ephyra.domain.track.service.TrackPreferences
import ephyra.feature.manga.presentation.DownloadAction
import ephyra.feature.manga.presentation.components.ChapterDownloadAction
import ephyra.presentation.util.formattedMessage
import ephyra.core.download.DownloadCache
import ephyra.core.download.DownloadManager
import ephyra.core.download.DownloadProvider
import ephyra.core.download.model.Download
import ephyra.app.data.track.EnhancedTracker
import ephyra.app.data.track.TrackerManager
import ephyra.app.data.track.jellyfin.JellyfinApi
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import ephyra.app.ui.reader.setting.ReaderPreferences
import ephyra.app.util.chapter.getNextUnread
import ephyra.app.util.removeCovers
import ephyra.app.util.storage.DiskUtil
import ephyra.app.util.system.toast
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
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.CheckboxState
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.mapAsCheckboxState
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.lang.withUIContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.model.NoChaptersException
import ephyra.domain.chapter.service.calculateChapterGap
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.applyFilter
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.GetTracks
import ephyra.i18n.MR
import ephyra.source.local.isLocal
import java.io.IOException
import kotlin.math.floor

@Factory
class MangaScreenModel(
    private val context: Context,
    @InjectedParam private val lifecycle: Lifecycle,
    @InjectedParam private val mangaId: Long,
    @InjectedParam private val isFromSource: Boolean,
    private val basePreferences: ephyra.domain.base.BasePreferences,
    private val uiPreferences: ephyra.domain.ui.UiPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val getMangaAndChapters: GetMangaWithChapters,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val getCategories: GetCategories,
    private val sourceManager: SourceManager,
    private val mangaInfoInteractor: ephyra.feature.manga.interactor.MangaInfoInteractor,
    private val mangaChapterInteractor: ephyra.feature.manga.interactor.MangaChapterInteractor,
    private val mangaTrackInteractor: ephyra.feature.manga.interactor.MangaTrackInteractor,
    private val syncJellyfin: ephyra.domain.jellyfin.interactor.SyncJellyfin,
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

    private val skipFiltered by readerPreferences.skipFiltered().asState(screenModelScope)

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
                libraryPreferences.swipeToEndAction().changes(),
                libraryPreferences.swipeToStartAction().changes(),
                mangaTrackInteractor.autoUpdateTrackOnMarkRead().changes(),
            ) { mangaAndChapters, _, _, swipeStart, swipeEnd, autoTrack ->
                Triple(mangaAndChapters, swipeStart, swipeEnd) to autoTrack
            }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (triple, autoTrack) ->
                    val (mangaAndChapters, swipeStart, swipeEnd) = triple
                    val (manga, chapters) = mangaAndChapters
                    val metadataSourceName = manga.metadataSource?.takeIf { it > 0 }?.let { id ->
                        sourceManager.get(id)?.name
                    }
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = chapters.toChapterListItems(manga),
                            metadataSourceName = metadataSourceName,
                            chapterSwipeStartAction = swipeStart,
                            chapterSwipeEndAction = swipeEnd,
                            autoTrackState = autoTrack,
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
                mangaChapterInteractor.resetToDefaultSettings(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
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
                    jellyfinServerUrl = mangaTrackInteractor.jellyfin.getServerUrl(),
                    imagesInDescription = uiPreferences.imagesInDescription().get(),
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                    hideMissingChapters = libraryPreferences.hideMissingChapters().getSync(),
                    chapterSwipeStartAction = libraryPreferences.swipeToEndAction().getSync(),
                    chapterSwipeEndAction = libraryPreferences.swipeToStartAction().getSync(),
                    autoTrackState = mangaTrackInteractor.autoUpdateTrackOnMarkRead().getSync(),
                    isUpdateIntervalEnabled = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateMangaRestrictions().getSync(),
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

    fun onEvent(event: MangaScreenEvent) {
        when (event) {
            is MangaScreenEvent.FetchAllFromSource -> fetchAllFromSource(event.manualFetch)
            is MangaScreenEvent.SetMetadataSource -> setMetadataSource(event.sourceId, event.mangaUrl)
            is MangaScreenEvent.ToggleLockedField -> toggleLockedField(event.field)
            is MangaScreenEvent.SetLockedFields -> setLockedFields(event.mask)
            is MangaScreenEvent.RefreshFromAuthority -> refreshFromAuthority()
            is MangaScreenEvent.ToggleFavorite -> toggleFavorite(onRemoved = {}, checkDuplicate = event.checkDuplicate)
            is MangaScreenEvent.ShowChangeCategoryDialog -> showChangeCategoryDialog()
            is MangaScreenEvent.ShowSetFetchIntervalDialog -> showSetFetchIntervalDialog()
            is MangaScreenEvent.SetFetchInterval -> setFetchInterval(event.manga, event.interval)
            is MangaScreenEvent.MoveMangaToCategoriesAndAddToLibrary -> moveMangaToCategoriesAndAddToLibrary(event.manga, event.categories)
            is MangaScreenEvent.ChapterSwipe -> chapterSwipe(event.chapterItem, event.swipeAction)
            is MangaScreenEvent.RunChapterDownloadActions -> runChapterDownloadActions(event.items, event.action)
            is MangaScreenEvent.RunDownloadAction -> runDownloadAction(event.action)
            is MangaScreenEvent.MarkPreviousChapterRead -> markPreviousChapterRead(event.pointer)
            is MangaScreenEvent.MarkChaptersRead -> markChaptersRead(event.chapters, event.read)
            is MangaScreenEvent.BookmarkChapters -> bookmarkChapters(event.chapters, event.bookmarked)
            is MangaScreenEvent.DeleteChapters -> deleteChapters(event.chapters)
            is MangaScreenEvent.SetUnreadFilter -> setUnreadFilter(event.state)
            is MangaScreenEvent.SetDownloadedFilter -> setDownloadedFilter(event.state)
            is MangaScreenEvent.SetBookmarkedFilter -> setBookmarkedFilter(event.state)
            is MangaScreenEvent.SetDisplayMode -> setDisplayMode(event.mode)
            is MangaScreenEvent.SetSorting -> setSorting(event.sort)
            is MangaScreenEvent.SetCurrentSettingsAsDefault -> setCurrentSettingsAsDefault(event.applyToExisting)
            is MangaScreenEvent.ResetToDefaultSettings -> resetToDefaultSettings()
            is MangaScreenEvent.ToggleSelection -> toggleSelection(event.item, event.selected, event.fromLongPress)
            is MangaScreenEvent.ToggleAllSelection -> toggleAllSelection(event.selected)
            is MangaScreenEvent.InvertSelection -> invertSelection()
            is MangaScreenEvent.DismissDialog -> dismissDialog()
            is MangaScreenEvent.ShowDeleteChapterDialog -> showDeleteChapterDialog(event.chapters)
            is MangaScreenEvent.ShowSettingsDialog -> showSettingsDialog()
            is MangaScreenEvent.ShowTrackDialog -> showTrackDialog()
            is MangaScreenEvent.ShowCoverDialog -> showCoverDialog()
            is MangaScreenEvent.ShowEditMetadataDialog -> showEditMetadataDialog()
            is MangaScreenEvent.EditTitle -> editTitle(event.value)
            is MangaScreenEvent.EditAuthor -> editAuthor(event.value)
            is MangaScreenEvent.EditArtist -> editArtist(event.value)
            is MangaScreenEvent.EditDescription -> editDescription(event.value)
            is MangaScreenEvent.EditStatus -> editStatus(event.value)
            is MangaScreenEvent.EditGenres -> editGenres(event.value)
            is MangaScreenEvent.ShowMigrateDialog -> showMigrateDialog(event.duplicate)
            is MangaScreenEvent.SetExcludedScanlators -> setExcludedScanlators(event.excludedScanlators)
            is MangaScreenEvent.ResolveCanonicalId -> resolveCanonicalId()
            is MangaScreenEvent.UnlinkAuthority -> unlinkAuthority()
        }
    }

    private fun fetchAllFromSource(manualFetch: Boolean = true) {
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
                    manga.source == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID

                // Authority-only manga have no content source — refresh from canonical tracker only.
                if (isAuthorityOnly) {
                    if (manga.canonicalId != null) {
                        mangaTrackInteractor.refreshCanonical(manga)
                    }
                    return@withIOContext
                }

                val metadataSourceId = manga.metadataSource?.takeIf { it > 0 }
                val metadataUrl = manga.metadataUrl?.takeIf { it.isNotEmpty() }

                var networkManga: SManga? = null

                // Try metadata source first if configured
                if (metadataSourceId != null && metadataUrl != null) {
                    try {
                        val metaSrc = sourceManager.getOrStub(metadataSourceId)
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

                mangaInfoInteractor.updateUpdateStrategy(manga, networkManga, manualFetch)

                // On manual refresh, also refresh metadata from the canonical tracker source
                // to capture updates (status changes, new cover art, description updates).
                // Re-read the manga from the DB to ensure comparisons in RefreshCanonicalMetadata
                // use the latest stored values (e.g. thumbnailUrl may have just been updated by
                // awaitUpdateFromSource). Using the pre-update object would cause false "changed"
                // detections and alternating cover flicker.
                if (manualFetch && manga.canonicalId != null) {
                    try {
                        val freshManga = mangaInfoInteractor.getMangaById(manga.id)
                        mangaTrackInteractor.refreshCanonical(freshManga)
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
    private fun setMetadataSource(sourceId: Long, mangaUrl: String) {
        val manga = successState?.manga ?: return
        screenModelScope.launchIO {
            mangaInfoInteractor.updateManga(
                ephyra.domain.manga.model.MangaUpdate(
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
    private fun toggleLockedField(field: Long) {
        val manga = successState?.manga ?: return
        val newLocked = manga.lockedFields xor field
        screenModelScope.launchIO {
            mangaInfoInteractor.updateManga(
                ephyra.domain.manga.model.MangaUpdate(
                    id = manga.id,
                    lockedFields = newLocked,
                ),
            )
        }
    }

    private fun setLockedFields(mask: Long) {
        val manga = successState?.manga ?: return
        screenModelScope.launchIO {
            mangaInfoInteractor.updateManga(
                ephyra.domain.manga.model.MangaUpdate(
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
    private fun refreshFromAuthority() {
        val manga = successState?.manga ?: return
        if (manga.canonicalId == null) return
        screenModelScope.launchIO {
            try {
                mangaTrackInteractor.refreshCanonical(manga)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "Authority-only refresh failed for ${manga.title}"
                }
            }
        }
    }

    private fun toggleFavorite() {
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
    private fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val manga = state.manga

            if (isFavorited) {
                // Remove from library
                if (mangaInfoInteractor.updateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.removeCovers() != manga) {
                        mangaInfoInteractor.updateCoverLastModified(manga.id)
                    }
                    // Jellyfin: unmark favorite on server when removing from library
                    mangaInfoInteractor.markJellyfinFavoriteIfLinked(manga, favorite = false)
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
                        val result = mangaInfoInteractor.updateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = mangaInfoInteractor.updateFavorite(manga.id, true)
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
                        mangaTrackInteractor.matchUnlinkedManga(manga)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) { "Auto-link on add-to-library skipped" }
                    }
                }

                // Jellyfin: mark as favorite on server when adding to library
                // (Jellyfin "favorite" ≈ "in library" concept)
                mangaInfoInteractor.markJellyfinFavoriteIfLinked(manga, favorite = true)
            }
        }
    }

    private fun showChangeCategoryDialog() {
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

    private fun showSetFetchIntervalDialog() {
        val manga = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetFetchInterval(manga))
        }
    }

    private fun setFetchInterval(manga: Manga, interval: Int) {
        screenModelScope.launchIO {
            if (
                mangaInfoInteractor.updateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = mangaInfoInteractor.getMangaById(manga.id)
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

    private fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            mangaInfoInteractor.updateFavorite(manga.id, true)
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
            mangaInfoInteractor.setMangaCategories(mangaId, categoryIds)
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
        if (state.manga.source == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID) return
        try {
            withIOContext {
                val chapters = state.source.getChapterList(state.manga.toSManga())

                val newChapters = mangaChapterInteractor.syncChaptersWithSource(
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
            val newManga = mangaInfoInteractor.getMangaById(mangaId)
            updateSuccessState { it.copy(manga = newManga, isRefreshingData = false) }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
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

    private fun runChapterDownloadActions(
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

    private fun runDownloadAction(action: DownloadAction) {
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

        val syncAction = when (action) {
            DownloadAction.SYNC_ALL_TO_JELLYFIN -> ephyra.domain.jellyfin.interactor.SyncJellyfin.SyncAction.SYNC_ALL_TO_JELLYFIN
            DownloadAction.SYNC_READ_TO_JELLYFIN -> ephyra.domain.jellyfin.interactor.SyncJellyfin.SyncAction.SYNC_READ_TO_JELLYFIN
            DownloadAction.SYNC_TO_JELLYFIN -> ephyra.domain.jellyfin.interactor.SyncJellyfin.SyncAction.SYNC_UNREAD_TO_JELLYFIN
            else -> return
        }

        screenModelScope.launchIO {
            val allChapterItems = allChapters.orEmpty()
            val chapters = allChapterItems.map { it.chapter }
            val downloadStates = allChapterItems.associate { it.id to (it.downloadState == Download.State.DOWNLOADED) }
            syncJellyfin.syncToJellyfin(manga, chapters, downloadStates, syncAction)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    private fun markPreviousChapterRead(pointer: Chapter) {
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
    private fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        screenModelScope.launchIO {
            mangaChapterInteractor.markChaptersRead(chapters, read)

            if (!read || successState?.hasLoggedInTrackers == false || mangaTrackInteractor.isAutoTrackStateNever()) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = mangaTrackInteractor.getTracks(mangaId)
            val maxChapterNumber = chapters.maxOf { it.chapterNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastChapterRead }

            if (!shouldPromptTrackingUpdate) return@launchIO
            if (mangaTrackInteractor.isAutoTrackStateAlways()) {
                mangaTrackInteractor.trackChapter(context, mangaId, maxChapterNumber)
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
                mangaTrackInteractor.trackChapter(context, mangaId, maxChapterNumber)
            }
        }
    }

    private suspend fun refreshTrackers() {
        mangaTrackInteractor.refreshTracks(mangaId)
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
        mangaChapterInteractor.downloadChapters(chapters, manga)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    private fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            mangaChapterInteractor.bookmarkChapters(chapters, bookmarked)
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    private fun deleteChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    mangaChapterInteractor.deleteChapters(
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
            val chaptersToDownload = mangaChapterInteractor.filterChaptersForDownload(manga, chapters)

            if (chaptersToDownload.isNotEmpty()) {
                downloadChapters(chaptersToDownload)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    private fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.setUnreadFilter(manga, state)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    private fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.setDownloadedFilter(manga, state)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    private fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.setBookmarkedFilter(manga, state)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    private fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.setDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    private fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.setSorting(manga, sort)
        }
    }

    private fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.setCurrentSettingsAsDefault(manga, applyToExisting)
            snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
        }
    }

    private fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            mangaChapterInteractor.resetToDefaultSettings(manga)
        }
    }

    private fun toggleSelection(
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

    private fun toggleAllSelection(selected: Boolean) {
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

    private fun invertSelection() {
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
                mangaTrackInteractor.subscribeTracks(manga.id).catch { logcat(LogPriority.ERROR, it) },
                mangaTrackInteractor.loggedInTrackersFlow(),
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

    private fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    private fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    private fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    private fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    private fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    private fun showEditMetadataDialog() {
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
        buildUpdate: (mangaId: Long, newLockedFields: Long) -> ephyra.domain.manga.model.MangaUpdate,
    ) {
        val manga = successState?.manga ?: return
        screenModelScope.launchIO {
            mangaInfoInteractor.updateManga(
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
        val jellyfin = mangaTrackInteractor.jellyfin
        if (!jellyfin.isLoggedIn) return

        // Find the Jellyfin track for this manga to get the tracking URL
        val tracks = mangaTrackInteractor.getTracks(manga.id)
        val jellyfinTrack = tracks.firstOrNull {
            it.trackerId == mangaTrackInteractor.jellyfin.id
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
        val jellyfin = mangaTrackInteractor.jellyfin
        if (!jellyfin.isLoggedIn) return
        if (!libraryPreferences.jellyfinSyncEnabled().get()) return

        val tracks = mangaTrackInteractor.getTracks(manga.id)
        val jellyfinTrack = tracks.firstOrNull {
            it.trackerId == mangaTrackInteractor.jellyfin.id
        } ?: return

        try {
            val serverUrl = jellyfin.resolveServerUrl(jellyfinTrack.remoteUrl)
            val itemId = jellyfin.api.getItemIdFromUrl(jellyfinTrack.remoteUrl)
            val userId = mangaTrackInteractor.jellyfinUserId().get()
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

    private fun editTitle(value: String) {
        val manga = successState?.manga ?: return
        if (value == manga.title) return
        editFieldAndLock(ephyra.domain.manga.model.LockedField.TITLE) { id, locked ->
            ephyra.domain.manga.model.MangaUpdate(id = id, title = value, lockedFields = locked)
        }
    }

    private fun editAuthor(value: String) {
        val manga = successState?.manga ?: return
        if (value == (manga.author ?: "")) return
        editFieldAndLock(ephyra.domain.manga.model.LockedField.AUTHOR) { id, locked ->
            ephyra.domain.manga.model.MangaUpdate(id = id, author = value, lockedFields = locked)
        }
    }

    private fun editArtist(value: String) {
        val manga = successState?.manga ?: return
        if (value == (manga.artist ?: "")) return
        editFieldAndLock(ephyra.domain.manga.model.LockedField.ARTIST) { id, locked ->
            ephyra.domain.manga.model.MangaUpdate(id = id, artist = value, lockedFields = locked)
        }
    }

    private fun editDescription(value: String) {
        val manga = successState?.manga ?: return
        if (value == (manga.description ?: "")) return
        editFieldAndLock(ephyra.domain.manga.model.LockedField.DESCRIPTION) { id, locked ->
            ephyra.domain.manga.model.MangaUpdate(id = id, description = value, lockedFields = locked)
        }
    }

    private fun editStatus(value: Long) {
        val manga = successState?.manga ?: return
        if (value == manga.status) return
        editFieldAndLock(ephyra.domain.manga.model.LockedField.STATUS) { id, locked ->
            ephyra.domain.manga.model.MangaUpdate(id = id, status = value, lockedFields = locked)
        }
    }

    private fun editGenres(value: List<String>) {
        val manga = successState?.manga ?: return
        if (value == manga.genre) return
        editFieldAndLock(ephyra.domain.manga.model.LockedField.GENRE) { id, locked ->
            ephyra.domain.manga.model.MangaUpdate(id = id, genre = value, lockedFields = locked)
        }
    }

    private fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(target = manga, current = duplicate)) }
    }

    private fun setExcludedScanlators(excludedScanlators: Set<String>) {
        screenModelScope.launchIO {
            mangaInfoInteractor.setExcludedScanlators(mangaId, excludedScanlators)
        }
    }

    /**
     * Resolves the canonical ID for this manga by checking tracker bindings and searching
     * tracker APIs. Shows a snackbar with the result.
     * If no trackers are available for search (Phase 2), guides the user to enable one.
     * This is the per-manga version of the bulk "Resolve all unlinked" operation.
     */
    private fun resolveCanonicalId() {
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
                val result = mangaTrackInteractor.matchUnlinkedManga(manga)
                if (result != null) {
                    // Refresh manga from DB so UI reflects the new canonical ID
                    // (e.g. hides the "Link to authority" toolbar button, shows updated metadata)
                    try {
                        val updatedManga = mangaInfoInteractor.getMangaById(mangaId)
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
                        if (!mangaTrackInteractor.hasQueryableTracker()) {
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
    private fun unlinkAuthority() {
        val manga = successState?.manga ?: return
        if (manga.canonicalId == null) return
        screenModelScope.launchIO {
            mangaInfoInteractor.unlinkAuthority(manga)
            // Refresh in-memory state so the UI reflects the change
            try {
                val updatedManga = mangaInfoInteractor.getMangaById(mangaId)
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
            val jellyfinServerUrl: String? = null,
            val imagesInDescription: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val hideMissingChapters: Boolean = false,
            val metadataSourceName: String? = null,
            val chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction = LibraryPreferences.ChapterSwipeAction.Disabled,
            val chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction = LibraryPreferences.ChapterSwipeAction.Disabled,
            val autoTrackState: Boolean = false,
            val isUpdateIntervalEnabled: Boolean = false,
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
                get() = scanlatorFilterActive || manga.chaptersFiltered(basePreferences)

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
                val downloadedFilter = manga.downloadedFilter(basePreferences)
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
