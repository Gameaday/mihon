package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository

class UpdateMangaNotes(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(mangaId: Long, notes: String): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = mangaId,
                notes = notes,
            ),
        )
    }
}
