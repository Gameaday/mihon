package ephyra.data.backup.restore.restorers

import ephyra.data.backup.models.BackupCategory
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.model.Category
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.library.service.LibraryPreferences

class CategoriesRestorer(
    private val categoryRepository: CategoryRepository,
    private val getCategories: GetCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                .map {
                    val dbCategory = dbCategoriesByName[it.name]
                    if (dbCategory != null) return@map dbCategory
                    val order = nextOrder++
                    val newCategory = Category(
                        id = 0,
                        name = it.name,
                        order = order,
                        flags = it.flags,
                    )
                    val id = categoryRepository.insert(newCategory)
                    it.toCategory(id).copy(order = order)
                }

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
