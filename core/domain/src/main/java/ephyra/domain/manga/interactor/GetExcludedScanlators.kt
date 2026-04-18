package ephyra.domain.manga.interactor

import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import kotlinx.coroutines.flow.Flow

class GetExcludedScanlators(
    private val repository: ExcludedScanlatorRepository,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return repository.getExcludedScanlators(mangaId)
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return repository.subscribeExcludedScanlators(mangaId)
    }
}
