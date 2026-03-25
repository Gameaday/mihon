package ephyra.domain.category.interactor

import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.library.model.plus
import ephyra.domain.library.service.LibraryPreferences

class ResetCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.sortingMode().get()
        categoryRepository.updateAllFlags(sort.type + sort.direction)
    }
}
