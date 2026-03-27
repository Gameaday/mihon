package ephyra.data.room.daos

import androidx.room.*
import ephyra.data.room.entities.HistoryEntity
import ephyra.data.room.entities.TrackEntity
import ephyra.data.room.views.HistoryView
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @androidx.room.Query("SELECT * FROM historyView WHERE readAt > 0 AND title LIKE '%' || :query || '%' ORDER BY readAt DESC")
    fun getHistory(query: String): kotlinx.coroutines.flow.Flow<List<HistoryView>>

    @androidx.room.Query("SELECT * FROM historyView WHERE readAt > 0 ORDER BY readAt DESC LIMIT 1")
    suspend fun getLatestHistory(): HistoryView?

    @androidx.room.Query("SELECT sum(time_read) FROM history")
    suspend fun getTotalReadDuration(): Long

    @androidx.room.Query("SELECT history.* FROM history JOIN chapters ON history.chapter_id = chapters._id WHERE chapters.manga_id = :mangaId")
    suspend fun getHistoryByMangaId(mangaId: Long): List<HistoryEntity>

    @androidx.room.Query("UPDATE history SET last_read = 0, time_read = 0 WHERE _id = :id")
    suspend fun resetHistory(id: Long)

    @androidx.room.Query("UPDATE history SET last_read = 0, time_read = 0 WHERE _id IN (SELECT history._id FROM history JOIN chapters ON history.chapter_id = chapters._id WHERE chapters.manga_id = :mangaId)")
    suspend fun resetHistoryByMangaId(mangaId: Long)

    @androidx.room.Query("DELETE FROM history")
    suspend fun removeAll()

    @androidx.room.Query("DELETE FROM history WHERE last_read = 0")
    suspend fun removeResettedHistory()
    
    @androidx.room.Query("SELECT * FROM history WHERE chapter_id = :chapterId")
    suspend fun getHistoryByChapterId(chapterId: Long): HistoryEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity): Long

    @androidx.room.Update
    suspend fun update(history: HistoryEntity)

    @androidx.room.Transaction
    suspend fun upsert(history: HistoryEntity): Long {
        val existing = getHistoryByChapterId(history.chapterId)
        return if (existing != null) {
            update(history.copy(id = existing.id))
            existing.id
        } else {
            insert(history)
        }
    }
}

@Dao
interface TrackDao {

    @Query("SELECT * FROM manga_sync WHERE _id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM manga_sync")
    fun getTracksAsFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM manga_sync WHERE manga_id = :mangaId")
    suspend fun getTracksByMangaId(mangaId: Long): List<TrackEntity>

    @Query("SELECT * FROM manga_sync WHERE manga_id = :mangaId")
    fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Query("DELETE FROM manga_sync WHERE manga_id = :mangaId AND sync_id = :syncId")
    suspend fun delete(mangaId: Long, syncId: Long)
}
