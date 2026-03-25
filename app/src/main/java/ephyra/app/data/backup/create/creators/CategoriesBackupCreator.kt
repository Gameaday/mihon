package ephyra.app.data.backup.create.creators

import ephyra.app.data.backup.models.BackupCategory
import ephyra.app.data.backup.models.backupCategoryMapper
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.model.Category
class CategoriesBackupCreator(
    private val getCategories: GetCategories,
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getCategories.await()
            .filterNot(Category::isSystemCategory)
            .map(backupCategoryMapper)
    }
}
