package ephyra.feature.manga

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.CheckboxState
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.mapAsCheckboxState
import ephyra.core.common.util.addOrRemove
import ephyra.core.common.util.insertSeparators
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.lang.withUIContext
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadCache
import ephyra.core.download.DownloadManager
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.model.Download
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.applyFilter
import ephyra.domain.manga.model.chaptersFiltered
import ephyra.domain.manga.model.downloadedFilter
import ephyra.domain.manga.model.toSManga
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.feature.manga.presentation.components.ChapterDownloadAction
import ephyra.i18n.MR
import ephyra.presentation.core.util.asState
import ephyra.presentation.core.util.manga.DownloadAction
import ephyra.presentation.core.util.manga.removeCovers
import ephyra.presentation.core.util.system.toast
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam
import java.time.Instant

@Factory
class MangaScreenModel(
    @InjectedParam private val mangaId: Long,
    private val getManga: ephyra.domain.manga.interactor.GetManga,
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
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
    private val basePreferences: ephyra.domain.base.BasePreferences,
    private val coverCache: ephyra.data.cache.CoverCache,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<MangaScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val skipRead by readerPreferences.skipRead().asState(screenModelScope)
    private val skipFiltered by readerPreferences.skipFiltered().asState(screenModelScope)
    private val skipDupe by readerPreferences.skipDupe().asState(screenModelScope)

    private val selectedChapterIds = HashSet<Long>()

    init {
        screenModelScope.launch {
            getManga.subscribe(mangaId)
                .distinctUntilChanged()
                .collect { manga ->
                    if (manga == null) return@collect
                    mutableState.update { state ->
                        when (state) {
                            is State.Loading -> State.Success(
                                manga = manga,
                                source = sourceManager.getOrStub(manga.source),
                                isAnySelected = false,
                                chapterListItems = emptyList(),
                            )
                            is State.Success -> state.copy(manga = manga)
                        }
                    }
                }
        }

        screenModelScope.launch {
            combine(
                getMangaAndChapters.subscribe(mangaId),
                downloadCache.changes,
                downloadManager.queueState,
                libraryPreferences.swipeToEndAction().changes(),
                libraryPreferences.swipeToStartAction().changes(),
            ) { (manga, chapters), _, queue, swipeStart, swipeEnd ->

                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(
                        manga = manga,
                        chapters = chapters,
                        chapterListItems = chapters.toChapterListItems(manga, queue),
                        chapterSwipeStartAction = swipeStart,
                        chapterSwipeEndAction = swipeEnd,
                    )
                }
            }.collect {}
        }
    }

    fun toggleFavorite() {
        val manga = manga ?: return
        screenModelScope.launchIO {
            val favorite = !manga.favorite
            if (mangaInfoInteractor.updateFavorite(manga.id, favorite)) {
                mangaInfoInteractor.markJellyfinFavoriteIfLinked(manga, favorite)
            }
        }
    }

    fun onEvent(event: MangaScreenEvent) {
        when (event) {
            is MangaScreenEvent.ToggleSelection -> {
                selectedChapterIds.addOrRemove(event.item.id, event.selected)
                updateSelectionState()
            }
            is MangaScreenEvent.ToggleAllSelection -> {
                val success = successState ?: return
                success.chapters.forEach { chapter ->
                    selectedChapterIds.addOrRemove(chapter.id!!, event.selected)
                }
                updateSelectionState()
            }
            is MangaScreenEvent.ClearSelection -> {
                selectedChapterIds.clear()
                updateSelectionState()
            }
            is MangaScreenEvent.InvertSelection -> {
                val success = successState ?: return
                success.chapters.forEach { chapter ->
                    selectedChapterIds.addOrRemove(chapter.id!!, chapter.id !in selectedChapterIds)
                }
                updateSelectionState()
            }
            MangaScreenEvent.DismissDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = null)
                }
            }
            MangaScreenEvent.ShowSettingsDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.SettingsSheet)
                }
            }
            MangaScreenEvent.ShowTrackDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.TrackSheet)
                }
            }
            MangaScreenEvent.ShowCoverDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.FullCover)
                }
            }
            MangaScreenEvent.ShowEditMetadataDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.EditMetadata)
                }
            }
            is MangaScreenEvent.ShowDeleteChapterDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.DeleteChapters(event.chapters))
                }
            }
            is MangaScreenEvent.ShowMigrateDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.Migrate(current = success.manga, target = event.duplicate))
                }
            }
            MangaScreenEvent.ShowChangeCategoryDialog -> {
                screenModelScope.launchIO {
                    val manga = manga ?: return@launchIO
                    val categories = getCategories.await()
                    val selection = categories.map { category ->
                        CheckboxState.State.None(category) as CheckboxState<Category>
                    }
                    mutableState.update { state ->
                        val success = state as? State.Success ?: return@update state
                        success.copy(dialog = Dialog.ChangeCategory(manga, selection))
                    }
                }
            }
            MangaScreenEvent.ShowSetFetchIntervalDialog -> {
                mutableState.update { state ->
                    val success = state as? State.Success ?: return@update state
                    success.copy(dialog = Dialog.SetFetchInterval(success.manga))
                }
            }
            else -> {}
        }
    }

    private fun updateSelectionState() {
        mutableState.update { state ->
            val success = state as? State.Success ?: return@update state
            success.copy(
                isAnySelected = selectedChapterIds.isNotEmpty(),
                chapterListItems = success.chapterListItems.map { item ->
                    if (item is ChapterList.Item) {
                        item.copy(selected = item.id in selectedChapterIds)
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun List<Chapter>.toChapterListItems(manga: Manga, queue: List<Download>): List<ChapterList> {
        val items = map { chapter ->
            val download = queue.find { it.chapter.id == chapter.id }
            ChapterList.Item(
                chapter = chapter,
                downloadState = download?.status ?: if (
                    downloadManager.isChapterDownloaded(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        manga.title,
                        manga.source,
                    )
                ) {
                    Download.State.DOWNLOADED
                } else {
                    Download.State.NOT_DOWNLOADED
                },
                downloadProgress = download?.progress ?: 0,
                selected = chapter.id!! in selectedChapterIds,
            )
        }

        return items.insertSeparators { before, after ->
            // TODO: missing chapters logic
            null
        }
    }

    // Skeleton implementations for missing methods to fix build
    fun getNextUnreadChapter(): Chapter? {
        val success = successState ?: return null
        return success.chapterListItems.filterIsInstance<ChapterList.Item>().getNextUnread(success.manga)
    }

    private fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
        // Simple implementation for now
        return find { !it.chapter.read }?.chapter
    }

    sealed class State {
        data object Loading : State()
        data class Success(
            val manga: Manga,
            val source: Source,
            val chapters: List<Chapter> = emptyList(),
            val chapterListItems: List<ChapterList> = emptyList(),
            val isAnySelected: Boolean = false,
            val filterActive: Boolean = false,
            val scanlatorFilterActive: Boolean = false,
            val availableScanlators: Set<String> = emptySet(),
            val excludedScanlators: Set<String> = emptySet(),
            val isRefreshingData: Boolean = false,
            val isJellyfinLinked: Boolean = false,
            val isUpdateIntervalEnabled: Boolean = false,
            val trackingCount: Int = 0,
            val metadataSourceName: String? = null,
            val jellyfinServerUrl: String? = null,
            val imagesInDescription: Boolean = false,
            val isFromSource: Boolean = false,
            val chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction =
                LibraryPreferences.ChapterSwipeAction.ToggleRead,
            val chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction =
                LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
            val dialog: Dialog? = null,
        ) : State()
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
        data object EditMetadata : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DuplicateManga(val duplicates: List<MangaWithChapterCount>) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class Migrate(val current: Manga, val target: Manga) : Dialog
        data class SetFetchInterval(val manga: Manga) : Dialog
    }
}

sealed class ChapterList {
    abstract val id: Long

    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State = Download.State.NOT_DOWNLOADED,
        val downloadProgress: Int = 0,
        val selected: Boolean = false,
    ) : ChapterList() {
        override val id: Long = chapter.id!!
    }

    data class MissingCount(
        override val id: Long,
        val count: Int,
    ) : ChapterList()
}
