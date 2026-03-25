package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository

class GetMangaByUrlAndSourceId(
    private val mangaRepository: MangaRepository,
) {
    suspend fun await(url: String, sourceId: Long): Manga? {
        return mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
    }
}
