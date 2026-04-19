package ephyra.data.chapter

import ephyra.core.common.util.system.logcat
import ephyra.data.room.daos.ChapterDao
import ephyra.data.room.entities.ChapterEntity
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority

class ChapterRepositoryImpl(
    private val chapterDao: ChapterDao,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            chapters.map { chapter ->
                val entity = ChapterEntity(
                    id = 0,
                    mangaId = chapter.mangaId,
                    url = chapter.url,
                    name = chapter.name,
                    scanlator = chapter.scanlator,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead.toInt(),
                    chapterNumber = chapter.chapterNumber,
                    sourceOrder = chapter.sourceOrder.toInt(),
                    dateFetch = chapter.dateFetch,
                    dateUpload = chapter.dateUpload,
                    lastModifiedAt = chapter.lastModifiedAt,
                    version = chapter.version,
                    isSyncing = false,
                )
                val id = chapterDao.insert(entity)
                chapter.copy(id = id)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        // In a real implementation with Room, partial updates are handled via @Update or custom @Query.
        // For simplicity and matching current logic, we fetch, update, and save.
        // A better modern approach is a dedicated @Query for each update case.
        chapterUpdates.forEach { chapterUpdate ->
            val existing = chapterDao.getChapterById(chapterUpdate.id) ?: return@forEach
            val updated = existing.copy(
                mangaId = chapterUpdate.mangaId ?: existing.mangaId,
                url = chapterUpdate.url ?: existing.url,
                name = chapterUpdate.name ?: existing.name,
                scanlator = chapterUpdate.scanlator ?: existing.scanlator,
                read = chapterUpdate.read ?: existing.read,
                bookmark = chapterUpdate.bookmark ?: existing.bookmark,
                lastPageRead = chapterUpdate.lastPageRead?.toInt() ?: existing.lastPageRead,
                chapterNumber = chapterUpdate.chapterNumber ?: existing.chapterNumber,
                sourceOrder = chapterUpdate.sourceOrder?.toInt() ?: existing.sourceOrder,
                dateFetch = chapterUpdate.dateFetch ?: existing.dateFetch,
                dateUpload = chapterUpdate.dateUpload ?: existing.dateUpload,
                version = chapterUpdate.version ?: existing.version,
                isSyncing = false,
            )
            chapterDao.update(updated)
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            chapterDao.removeChaptersWithIds(chapterIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return chapterDao.getChaptersByMangaId(mangaId, applyScanlatorFilter).map(ChapterMapper::mapChapter)
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return chapterDao.getScanlatorsByMangaId(mangaId)
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return chapterDao.getScanlatorsByMangaIdAsFlow(mangaId)
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return chapterDao.getBookmarkedChaptersByMangaId(mangaId).map(ChapterMapper::mapChapter)
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return chapterDao.getChapterById(id)?.let(ChapterMapper::mapChapter)
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return chapterDao.getChaptersByMangaIdAsFlow(mangaId, applyScanlatorFilter).map { chapters ->
            chapters.map(ChapterMapper::mapChapter)
        }
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return chapterDao.getChapterByUrlAndMangaId(url, mangaId)?.let(ChapterMapper::mapChapter)
    }

    override suspend fun getChapterByUrl(url: String): Chapter? {
        return chapterDao.getChapterByUrl(url)?.let(ChapterMapper::mapChapter)
    }
}
