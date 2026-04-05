package ephyra.data.room.daos

import androidx.paging.PagingSource
import androidx.room.*
import ephyra.data.room.entities.CategoryEntity
import ephyra.data.room.entities.MangaCategoryEntity
import ephyra.data.room.entities.MangaEntity
import ephyra.data.room.views.LibraryView
import kotlinx.coroutines.flow.Flow

data class SourceWithCountRecord(
    val source: Long,
    val count: Long,
)

@Dao
interface MangaDao {

    @Query("SELECT * FROM mangas WHERE _id = :id")
    suspend fun getMangaById(id: Long): MangaEntity?

    @Query("SELECT * FROM mangas WHERE _id = :id")
    fun getMangaByIdAsFlow(id: Long): Flow<MangaEntity?>

    @Query("SELECT favorite FROM mangas WHERE _id = :id")
    suspend fun isMangaFavorite(id: Long): Boolean?

    @Query("SELECT * FROM mangas WHERE url = :url AND source = :source LIMIT 1")
    suspend fun getMangaByUrlAndSource(url: String, source: Long): MangaEntity?

    @Query("SELECT * FROM mangas WHERE url = :url AND source = :source LIMIT 1")
    fun getMangaByUrlAndSourceAsFlow(url: String, source: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM mangas WHERE favorite = 1")
    suspend fun getFavorites(): List<MangaEntity>

    @Query("SELECT * FROM libraryView")
    fun getLibraryMangaAsFlow(): Flow<List<LibraryView>>

    @Query("SELECT * FROM libraryView")
    suspend fun getLibraryManga(): List<LibraryView>

    @Query("SELECT * FROM mangas WHERE favorite = 1 AND canonical_id = :canonicalId AND _id != :excludeMangaId")
    suspend fun getFavoritesByCanonicalId(canonicalId: String, excludeMangaId: Long): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE favorite = 1 AND dead_since < :deadSinceBefore")
    suspend fun getFavoritesByDeadSinceBefore(deadSinceBefore: Long): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE favorite = 0 AND _id IN (SELECT DISTINCT manga_id FROM chapters WHERE read = 1)")
    suspend fun getReadMangaNotInLibrary(): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE favorite = 1 AND source = :sourceId")
    fun getFavoritesBySourceIdAsFlow(sourceId: Long): Flow<List<MangaEntity>>

    @Query("SELECT * FROM mangas WHERE _id != :id AND title = :title AND favorite = 1")
    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE next_update > 0 AND next_update < :epochMillis AND status IN (:statuses)")
    fun getUpcomingMangaAsFlow(epochMillis: Long, statuses: Set<Long>): Flow<List<MangaEntity>>

    @Query("UPDATE mangas SET viewer = 0")
    suspend fun resetViewerFlags()

    @Query("UPDATE mangas SET metadata_source = NULL, metadata_url = NULL WHERE _id = :mangaId")
    suspend fun clearMetadataSource(mangaId: Long)

    @Query("UPDATE mangas SET canonical_id = NULL WHERE _id = :mangaId")
    suspend fun clearCanonicalId(mangaId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: MangaEntity): Long

    @Update
    suspend fun update(manga: MangaEntity)

    @Query("DELETE FROM mangas WHERE favorite = 0 AND source IN (:sourceIds)")
    suspend fun deleteNonLibraryManga(sourceIds: List<Long>)

    @Query("DELETE FROM mangas_categories WHERE manga_id = :mangaId")
    suspend fun deleteMangaCategoriesByMangaId(mangaId: Long)

    @Insert
    suspend fun insertMangaCategory(mangaCategory: MangaCategoryEntity)

    @Transaction
    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        deleteMangaCategoriesByMangaId(mangaId)
        categoryIds.forEach { categoryId ->
            insertMangaCategory(MangaCategoryEntity(0, mangaId, categoryId))
        }
    }

    @Query("SELECT source, count(*) as count FROM mangas WHERE favorite = 1 GROUP BY source")
    fun getSourceIdWithFavoriteCount(): Flow<List<SourceWithCountRecord>>

    @Query(
        "SELECT source, count(*) as count FROM mangas WHERE favorite = 0 AND _id IN (SELECT DISTINCT manga_id FROM chapters WHERE read = 1) GROUP BY source",
    )
    fun getSourceIdsWithNonLibraryManga(): Flow<List<SourceWithCountRecord>>

    @Transaction
    suspend fun upsert(manga: MangaEntity): Long {
        val id = getMangaByUrlAndSource(manga.url, manga.source)?.id
        return if (id != null) {
            update(manga.copy(id = id))
            id
        } else {
            insert(manga)
        }
    }
}
