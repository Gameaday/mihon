package ephyra.domain.category.repository

import ephyra.domain.category.model.Category
import ephyra.domain.category.model.CategoryUpdate
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun insert(category: Category): Long

    suspend fun updatePartial(update: CategoryUpdate)

    suspend fun updatePartial(updates: List<CategoryUpdate>)

    suspend fun updateAllFlags(flags: Long?)

    suspend fun delete(categoryId: Long)
}
