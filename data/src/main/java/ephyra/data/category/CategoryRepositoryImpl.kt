package ephyra.data.category

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ephyra.data.room.daos.CategoryDao
import ephyra.data.room.entities.CategoryEntity
import ephyra.domain.category.model.Category
import ephyra.domain.category.model.CategoryUpdate
import ephyra.domain.category.repository.CategoryRepository

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return categoryDao.getCategories().find { it.id == id }?.let(CategoryMapper::mapCategory)
    }

    override suspend fun getAll(): List<Category> {
        return categoryDao.getCategories().map(CategoryMapper::mapCategory)
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return categoryDao.getCategoriesAsFlow().map { list -> list.map(CategoryMapper::mapCategory) }
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return categoryDao.getCategoriesByMangaId(mangaId).map(CategoryMapper::mapCategory)
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return categoryDao.getCategoriesByMangaIdAsFlow(mangaId).map { list -> list.map(CategoryMapper::mapCategory) }
    }

    override suspend fun insert(category: Category) {
        val entity = CategoryEntity(
            id = 0,
            name = category.name,
            sort = category.order.toInt(),
            flags = category.flags,
        )
        categoryDao.insert(entity)
    }

    override suspend fun updatePartial(update: CategoryUpdate) {
        updatePartialBlocking(update)
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        for (update in updates) {
            updatePartialBlocking(update)
        }
    }

    private suspend fun updatePartialBlocking(update: CategoryUpdate) {
        val existing = get(update.id) ?: return
        val updated = CategoryEntity(
            id = update.id,
            name = update.name ?: existing.name,
            sort = (update.order ?: existing.order).toInt(),
            flags = update.flags ?: existing.flags,
        )
        categoryDao.update(updated)
    }

    override suspend fun updateAllFlags(flags: Long?) {
        categoryDao.updateAllFlags(flags)
    }

    override suspend fun delete(categoryId: Long) {
        categoryDao.delete(categoryId)
    }
}
