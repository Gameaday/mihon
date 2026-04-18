package ephyra.feature.library

import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.presentation.core.util.manga.DownloadAction

/**
 * All user intents originating from the Library screen.
 *
 * [LibraryScreenModel.onEvent] is the single entry-point for all mutations.
 * Public value-returning accessors ([LibraryScreenModel.getDisplayMode],
 * [LibraryScreenModel.getColumnsForOrientation], [LibraryScreenModel.getRandomLibraryItemForCurrentCategory],
 * [LibraryScreenModel.getNextUnreadChapter]) remain public because they return live/reactive values.
 */
sealed interface LibraryScreenEvent {
    data class Search(val query: String?) : LibraryScreenEvent
    data class UpdateActiveCategoryIndex(val index: Int) : LibraryScreenEvent
    data class ToggleSelection(val category: Category, val manga: LibraryManga) : LibraryScreenEvent
    data class ToggleRangeSelection(val category: Category, val manga: LibraryManga) : LibraryScreenEvent
    data object SelectAll : LibraryScreenEvent
    data object InvertSelection : LibraryScreenEvent
    data object ClearSelection : LibraryScreenEvent
    data class PerformDownloadAction(val action: DownloadAction) : LibraryScreenEvent
    data class MarkReadSelection(val read: Boolean) : LibraryScreenEvent
    data class RemoveMangas(
        val mangas: List<Manga>,
        val deleteFromLibrary: Boolean,
        val deleteChapters: Boolean,
    ) : LibraryScreenEvent
    data class SetMangaCategories(
        val mangaList: List<Manga>,
        val addCategories: List<Long>,
        val removeCategories: List<Long>,
    ) : LibraryScreenEvent
    data object ShowSettingsDialog : LibraryScreenEvent
    data object EnableHealthFilter : LibraryScreenEvent
    data object OpenChangeCategoryDialog : LibraryScreenEvent
    data object OpenDeleteMangaDialog : LibraryScreenEvent
    data object CloseDialog : LibraryScreenEvent
}
