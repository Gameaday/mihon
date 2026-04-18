package ephyra.domain.manga.interactor

import ephyra.domain.manga.repository.ExcludedScanlatorRepository

class SetExcludedScanlators(
    private val repository: ExcludedScanlatorRepository,
) {

    suspend fun await(mangaId: Long, excludedScanlators: Set<String>) {
        repository.setExcludedScanlators(mangaId, excludedScanlators)
    }
}
