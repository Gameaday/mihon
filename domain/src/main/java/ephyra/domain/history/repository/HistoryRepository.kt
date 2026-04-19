package ephyra.domain.history.repository

import ephyra.domain.history.model.History
import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.history.model.HistoryWithRelations
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {

    fun getHistory(query: String): Flow<List<HistoryWithRelations>>

    suspend fun getLastHistory(): HistoryWithRelations?

    suspend fun getTotalReadDuration(): Long

    suspend fun getHistoryByMangaId(mangaId: Long): List<History>

    suspend fun resetHistory(historyId: Long)

    suspend fun resetHistoryByMangaId(mangaId: Long)

    suspend fun deleteAllHistory(): Boolean

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)

    suspend fun getHistoryByChapterId(chapterId: Long): History?

    suspend fun removeResettedHistory()
}
