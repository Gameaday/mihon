package ephyra.domain.history.interactor

import kotlinx.coroutines.flow.Flow
import ephyra.domain.history.model.History
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.history.repository.HistoryRepository

class GetHistory(
    private val repository: HistoryRepository,
) {

    suspend fun await(mangaId: Long): List<History> {
        return repository.getHistoryByMangaId(mangaId)
    }

    fun subscribe(query: String): Flow<List<HistoryWithRelations>> {
        return repository.getHistory(query)
    }
}
