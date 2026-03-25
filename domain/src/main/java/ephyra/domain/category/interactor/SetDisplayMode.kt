package ephyra.domain.category.interactor

import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.service.LibraryPreferences

class SetDisplayMode(
    private val preferences: LibraryPreferences,
) {

    fun await(display: LibraryDisplayMode) {
        preferences.displayMode().set(display)
    }
}
