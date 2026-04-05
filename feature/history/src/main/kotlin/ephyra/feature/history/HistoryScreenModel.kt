package ephyra.feature.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.preference.CheckboxState
import ephyra.core.common.preference.mapAsCheckboxState
import ephyra.core.common.util.insertSeparators
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.toLocalDate
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.AddTracks
import ephyra.feature.history.HistoryUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import org.koin.core.annotation.Factory

@Factory
class HistoryScreenModel(
    private val addTracks: AddTracks,
    private val getCategories: GetCategories,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getHistory: GetHistory,
    private val getManga: GetManga,
    private val getNextChapters: GetNextChapters,
    private val libraryPreferences: LibraryPreferences,
    private val removeHistory: RemoveHistory,
    private val setMangaCategories: SetMangaCategories,
    private val updateManga: UpdateManga,
    private val sourceManager: SourceManager,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<HistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            state.map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    getHistory.subscribe(query ?: "")
                        .distinctUntilChanged()
                        .catch { error ->
                            logcat(LogPriority.ERROR, error)
                            _events.send(Event.InternalError)
                        }
                        .map { it.toHistoryUiModels() }
                        .flowOn(Dispatchers.IO)
                }
                .collect { newList -> mutableState.update { it.copy(list = newList) } }
        }
    }

    private fun List<HistoryWithRelations>.toHistoryUiModels(): List<HistoryUiModel> {
        return map { HistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    suspend fun getNextChapter(): Chapter? {
        return withIOContext { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        screenModelScope.launchIO {
            sendNextChapterEvent(getNextChapters.await(mangaId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(mangaId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(mangaId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun addFavorite(mangaId: Long) {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId) ?: return@launchIO

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                mutableState.update { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                return@launchIO
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
        }
    }

    fun showMigrateDialog(target: Manga, current: Manga) {
        mutableState.update { currentState ->
            currentState.copy(dialog = Dialog.Migrate(target = target, current = current))
        }
    }

    fun showChangeCategoryDialog(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            mutableState.update { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<HistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: HistoryWithRelations) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog

        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
