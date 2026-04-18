package ephyra.data.room.daos

import androidx.room.*
import ephyra.data.room.entities.ExcludedScanlatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedScanlatorDao {

    @Query("SELECT scanlator FROM excluded_scanlators WHERE manga_id = :mangaId")
    suspend fun getExcludedScanlators(mangaId: Long): List<String>

    @Query("SELECT scanlator FROM excluded_scanlators WHERE manga_id = :mangaId")
    fun getExcludedScanlatorsAsFlow(mangaId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<ExcludedScanlatorEntity>)

    @Query("DELETE FROM excluded_scanlators WHERE manga_id = :mangaId AND scanlator IN (:scanlators)")
    suspend fun deleteByMangaIdAndScanlators(mangaId: Long, scanlators: List<String>)

    @Query("DELETE FROM excluded_scanlators WHERE manga_id = :mangaId")
    suspend fun deleteAllForManga(mangaId: Long)
}
