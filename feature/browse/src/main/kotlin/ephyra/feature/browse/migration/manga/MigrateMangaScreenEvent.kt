package ephyra.feature.browse.migration.manga

import ephyra.domain.manga.model.Manga

sealed interface MigrateMangaScreenEvent {
    data class ToggleSelection(val item: Manga) : MigrateMangaScreenEvent
    data object ClearSelection : MigrateMangaScreenEvent
}
