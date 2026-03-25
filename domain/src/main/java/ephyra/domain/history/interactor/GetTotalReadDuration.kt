package ephyra.domain.history.interactor

import ephyra.domain.history.repository.HistoryRepository

class GetTotalReadDuration(
    private val repository: HistoryRepository,
) {

    suspend fun await(): Long {
        return repository.getTotalReadDuration()
    }
}
