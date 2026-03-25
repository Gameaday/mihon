package ephyra.domain.history.interactor

import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.history.repository.HistoryRepository

class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        historyRepository.upsertHistory(historyUpdate)
    }
}
