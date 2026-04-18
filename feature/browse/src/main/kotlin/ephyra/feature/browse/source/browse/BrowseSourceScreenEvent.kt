package ephyra.feature.browse.source.browse

import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * All user intents originating from the Browse Source screen.
 * [BrowseSourceScreenModel.onEvent] is the single mutation entry-point.
 *
 * Public properties/methods that return values remain public:
 *   • `source`, `displayMode`, `mangaPagerFlowFlow` — reactive state read by Compose
 *   • `getColumnsPreference()` — returns GridCells consumed inline
 *   • `getCategories()` / `getDuplicateLibraryManga()` — suspend value-returning helpers
 */
sealed interface BrowseSourceScreenEvent {
    data object ResetFilters : BrowseSourceScreenEvent
    data class SetListing(val listing: BrowseSourceScreenModel.Listing) : BrowseSourceScreenEvent
    data class SetFilters(val filters: FilterList) : BrowseSourceScreenEvent
    data class Search(val query: String? = null, val filters: FilterList? = null) : BrowseSourceScreenEvent
    data class SearchGenre(val genreName: String) : BrowseSourceScreenEvent
    data class ChangeMangaFavorite(val manga: Manga) : BrowseSourceScreenEvent
    data class AddFavorite(val manga: Manga) : BrowseSourceScreenEvent
    data class MoveMangaToCategories(val manga: Manga, val categoryIds: List<Long>) : BrowseSourceScreenEvent
    data object OpenFilterSheet : BrowseSourceScreenEvent
    data class SetDialog(val dialog: BrowseSourceScreenModel.Dialog?) : BrowseSourceScreenEvent
    data class SetToolbarQuery(val query: String?) : BrowseSourceScreenEvent
}
