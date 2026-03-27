package ephyra.data.chapter

import ephyra.data.room.entities.ChapterEntity
import ephyra.domain.chapter.model.Chapter

object ChapterMapper {
    fun mapChapter(
        id: Long,
        mangaId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
    ): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )

    fun mapChapter(entity: ChapterEntity): Chapter = mapChapter(
        id = entity.id,
        mangaId = entity.mangaId,
        url = entity.url,
        name = entity.name,
        scanlator = entity.scanlator,
        read = entity.read,
        bookmark = entity.bookmark,
        lastPageRead = entity.lastPageRead.toLong(),
        chapterNumber = entity.chapterNumber,
        sourceOrder = entity.sourceOrder.toLong(),
        dateFetch = entity.dateFetch,
        dateUpload = entity.dateUpload,
        lastModifiedAt = entity.lastModifiedAt,
        version = entity.version,
        isSyncing = if (entity.isSyncing) 1L else 0L,
    )
}
