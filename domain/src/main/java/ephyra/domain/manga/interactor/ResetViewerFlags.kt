package ephyra.domain.manga.interactor

import ephyra.domain.manga.repository.MangaRepository

class ResetViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): Boolean {
        return mangaRepository.resetViewerFlags()
    }
}
