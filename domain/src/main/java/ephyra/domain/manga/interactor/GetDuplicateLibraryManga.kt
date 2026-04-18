package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.repository.MangaRepository

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    /** Finds library manga with a matching title, excluding [manga] itself. */
    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
    }

    /**
     * Finds library manga with a matching [title] without excluding any specific entry.
     *
     * Used when searching for potential duplicates before inserting a new entry that
     * does not yet exist in the database (so there is no local manga ID to exclude).
     */
    suspend fun invoke(title: String): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(id = -1L, title = title.lowercase())
    }
}
