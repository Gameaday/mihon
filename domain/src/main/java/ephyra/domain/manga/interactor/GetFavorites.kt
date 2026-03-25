package ephyra.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository

class GetFavorites(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaRepository.getFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Manga>> {
        return mangaRepository.getFavoritesBySourceId(sourceId)
    }
}
