package ephyra.feature.manga

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.feature.manga.ChapterList
import ephyra.feature.manga.presentation.DownloadAction
import ephyra.domain.library.service.LibraryPreferences

sealed interface MangaScreenEvent {
    data class FetchAllFromSource(val manualFetch: Boolean = false) : MangaScreenEvent
    data class SetMetadataSource(val sourceId: Long, val mangaUrl: String) : MangaScreenEvent
    data class ToggleLockedField(val field: Long) : MangaScreenEvent
    data class SetLockedFields(val mask: Long) : MangaScreenEvent
    data object RefreshFromAuthority : MangaScreenEvent
    data class ToggleFavorite(val checkDuplicate: Boolean = true) : MangaScreenEvent
    data object ShowChangeCategoryDialog : MangaScreenEvent
    data object ShowSetFetchIntervalDialog : MangaScreenEvent
    data class SetFetchInterval(val manga: Manga, val interval: Int) : MangaScreenEvent
    data class MoveMangaToCategoriesAndAddToLibrary(val manga: Manga, val categories: List<Long>) : MangaScreenEvent

    data class ChapterSwipe(val chapterItem: ChapterList.Item, val swipeAction: LibraryPreferences.ChapterSwipeAction) : MangaScreenEvent
    data class RunChapterDownloadActions(val items: List<ChapterList.Item>, val action: ephyra.feature.manga.presentation.components.ChapterDownloadAction) : MangaScreenEvent
    data class RunDownloadAction(val action: DownloadAction) : MangaScreenEvent
    data class MarkPreviousChapterRead(val pointer: Chapter) : MangaScreenEvent
    data class MarkChaptersRead(val chapters: List<Chapter>, val read: Boolean) : MangaScreenEvent
    data class BookmarkChapters(val chapters: List<Chapter>, val bookmarked: Boolean) : MangaScreenEvent
    data class DeleteChapters(val chapters: List<Chapter>) : MangaScreenEvent
    data class SetUnreadFilter(val state: ephyra.core.common.preference.TriState) : MangaScreenEvent
    data class SetDownloadedFilter(val state: ephyra.core.common.preference.TriState) : MangaScreenEvent
    data class SetBookmarkedFilter(val state: ephyra.core.common.preference.TriState) : MangaScreenEvent
    data class SetDisplayMode(val mode: Long) : MangaScreenEvent
    data class SetSorting(val sort: Long) : MangaScreenEvent
    data class SetCurrentSettingsAsDefault(val applyToExisting: Boolean) : MangaScreenEvent
    data object ResetToDefaultSettings : MangaScreenEvent
    data class ToggleSelection(val item: ChapterList.Item, val selected: Boolean, val fromLongPress: Boolean = false) : MangaScreenEvent
    data class ToggleAllSelection(val selected: Boolean) : MangaScreenEvent
    data object InvertSelection : MangaScreenEvent
    data object DismissDialog : MangaScreenEvent
    data class ShowDeleteChapterDialog(val chapters: List<Chapter>) : MangaScreenEvent
    data object ShowSettingsDialog : MangaScreenEvent
    data object ShowTrackDialog : MangaScreenEvent
    data object ShowCoverDialog : MangaScreenEvent
    data object ShowEditMetadataDialog : MangaScreenEvent
    data class EditTitle(val value: String) : MangaScreenEvent
    data class EditAuthor(val value: String) : MangaScreenEvent
    data class EditArtist(val value: String) : MangaScreenEvent
    data class EditDescription(val value: String) : MangaScreenEvent
    data class EditStatus(val value: Long) : MangaScreenEvent
    data class EditGenres(val value: List<String>) : MangaScreenEvent
    data class ShowMigrateDialog(val duplicate: Manga) : MangaScreenEvent
    data class SetExcludedScanlators(val excludedScanlators: Set<String>) : MangaScreenEvent
    data object ResolveCanonicalId : MangaScreenEvent
    data object UnlinkAuthority : MangaScreenEvent
}
