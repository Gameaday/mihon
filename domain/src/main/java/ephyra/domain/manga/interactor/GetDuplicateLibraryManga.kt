package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.repository.MangaRepository

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
    }
}
