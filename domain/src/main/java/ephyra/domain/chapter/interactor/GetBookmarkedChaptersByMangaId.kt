package ephyra.domain.chapter.interactor

import logcat.LogPriority
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.repository.ChapterRepository

class GetBookmarkedChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): List<Chapter> {
        return try {
            chapterRepository.getBookmarkedChaptersByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
