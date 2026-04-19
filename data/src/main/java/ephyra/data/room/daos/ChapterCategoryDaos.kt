package ephyra.data.room.daos

import androidx.room.*
import ephyra.data.room.entities.CategoryEntity
import ephyra.data.room.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE _id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Query(
        """
        SELECT * FROM chapters
        WHERE manga_id = :mangaId
        AND (:applyScanlatorFilter = 0 OR scanlator IS NULL OR scanlator NOT IN (
            SELECT scanlator FROM excluded_scanlators WHERE manga_id = :mangaId
        ))
    """,
    )
    fun getChaptersByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<ChapterEntity>>

    @Query(
        """
        SELECT * FROM chapters
        WHERE manga_id = :mangaId
        AND (:applyScanlatorFilter = 0 OR scanlator IS NULL OR scanlator NOT IN (
            SELECT scanlator FROM excluded_scanlators WHERE manga_id = :mangaId
        ))
    """,
    )
    suspend fun getChaptersByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<ChapterEntity>

    @Query("SELECT DISTINCT scanlator FROM chapters WHERE manga_id = :mangaId AND scanlator IS NOT NULL")
    fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT scanlator FROM chapters WHERE manga_id = :mangaId AND scanlator IS NOT NULL")
    suspend fun getScanlatorsByMangaId(mangaId: Long): List<String>

    @Query("SELECT * FROM chapters WHERE manga_id = :mangaId AND bookmark = 1")
    suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE manga_id = :mangaId AND url = :url LIMIT 1")
    suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE url = :url LIMIT 1")
    suspend fun getChapterByUrl(url: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity): Long

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Delete
    suspend fun delete(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE _id IN (:chapterIds)")
    suspend fun removeChaptersWithIds(chapterIds: List<Long>)

    @Query("UPDATE chapters SET read = :read WHERE _id = :chapterId")
    suspend fun updateReadStatus(chapterId: Long, read: Boolean)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories")
    fun getCategoriesAsFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getCategories(): List<CategoryEntity>

    @Query(
        "SELECT * FROM categories WHERE _id IN (SELECT category_id FROM mangas_categories WHERE manga_id = :mangaId)",
    )
    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<CategoryEntity>>

    @Query(
        "SELECT * FROM categories WHERE _id IN (SELECT category_id FROM mangas_categories WHERE manga_id = :mangaId)",
    )
    suspend fun getCategoriesByMangaId(mangaId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET flags = :flags")
    suspend fun updateAllFlags(flags: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE _id = :categoryId")
    suspend fun delete(categoryId: Long)

    @Query("SELECT category_id FROM mangas_categories WHERE manga_id = :mangaId")
    suspend fun getCategoryIdsByMangaId(mangaId: Long): List<Long>
}
