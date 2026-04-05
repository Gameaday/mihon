package ephyra.data.room.daos

import androidx.room.*
import ephyra.data.room.entities.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources")
    fun subscribeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE _id = :id")
    suspend fun getStubSource(id: Long): SourceEntity?

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Query("DELETE FROM sources WHERE _id = :id")
    suspend fun delete(id: Long)
}
