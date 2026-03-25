package ephyra.domain.chapter.interactor

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.repository.ChapterRepository

class GetChapterByUrlAndMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(url: String, sourceId: Long): Chapter? {
        return try {
            chapterRepository.getChapterByUrlAndMangaId(url, sourceId)
        } catch (e: Exception) {
            null
        }
    }
}
