package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.ResetCategoryFlags
import ephyra.domain.category.model.Category
import ephyra.domain.library.service.LibraryPreferences
import kotlinx.coroutines.flow.Flow

class SettingsLibraryScreenModel(
    val libraryPreferences: LibraryPreferences,
    private val getCategories: GetCategories,
    val resetCategoryFlags: ResetCategoryFlags,
) : ScreenModel {

    fun getCategories(): Flow<List<Category>> {
        return getCategories.subscribe()
    }
}
