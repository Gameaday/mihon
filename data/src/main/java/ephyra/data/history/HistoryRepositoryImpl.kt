package ephyra.data.history

import ephyra.data.room.daos.HistoryDao
import ephyra.data.room.entities.HistoryEntity
import ephyra.domain.history.model.History
import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.history.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepositoryImpl(
    private val historyDao: HistoryDao,
) : HistoryRepository {

    override fun getHistory(query: String): Flow<List<HistoryWithRelations>> {
        return historyDao.getHistory(query).map { list -> list.map(HistoryMapper::mapHistoryWithRelations) }
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return historyDao.getLatestHistory()?.let(HistoryMapper::mapHistoryWithRelations)
    }

    override suspend fun getTotalReadDuration(): Long {
        return historyDao.getTotalReadDuration()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return historyDao.getHistoryByMangaId(mangaId).map(HistoryMapper::mapHistory)
    }

    override suspend fun resetHistory(historyId: Long) {
        historyDao.resetHistory(historyId)
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) {
        historyDao.resetHistoryByMangaId(mangaId)
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            historyDao.removeAll()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        val entity = HistoryEntity(
            id = 0,
            chapterId = historyUpdate.chapterId,
            lastRead = historyUpdate.readAt,
            timeRead = historyUpdate.sessionReadDuration,
        )
        historyDao.upsert(entity)
    }

    override suspend fun getHistoryByChapterId(chapterId: Long): History? {
        return historyDao.getHistoryByChapterId(chapterId)?.let(HistoryMapper::mapHistory)
    }

    override suspend fun removeResettedHistory() {
        historyDao.removeResettedHistory()
    }
}
