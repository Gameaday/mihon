package ephyra.feature.library

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.service.LibraryPreferences

sealed interface LibrarySettingsScreenEvent {
    data class ToggleFilter(val preference: (LibraryPreferences) -> Preference<TriState>) : LibrarySettingsScreenEvent
    data class ToggleTracker(val id: Int) : LibrarySettingsScreenEvent
    data class SetDisplayMode(val mode: LibraryDisplayMode) : LibrarySettingsScreenEvent
    data class SetSort(
        val category: Category?,
        val mode: LibrarySort.Type,
        val direction: LibrarySort.Direction,
    ) : LibrarySettingsScreenEvent
}
