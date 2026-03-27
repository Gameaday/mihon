package ephyra.data.history

import ephyra.domain.history.model.History
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.data.room.entities.HistoryEntity
import ephyra.data.room.views.HistoryView
import ephyra.domain.manga.model.MangaCover
import java.util.Date

object HistoryMapper {
    fun mapHistory(
        id: Long,
        chapterId: Long,
        readAt: Date?,
        readDuration: Long,
    ): History = History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )

    fun mapHistory(entity: HistoryEntity): History = mapHistory(
        id = entity.id,
        chapterId = entity.chapterId,
        readAt = entity.lastRead,
        readDuration = entity.timeRead,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        mangaId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        chapterNumber: Double,
        readAt: Date?,
        readDuration: Long,
    ): HistoryWithRelations = HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        readAt = readAt,
        readDuration = readDuration,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )

    fun mapHistoryWithRelations(view: HistoryView): HistoryWithRelations = mapHistoryWithRelations(
        historyId = view.id,
        mangaId = view.mangaId,
        chapterId = view.chapterId,
        title = view.title,
        thumbnailUrl = view.thumbnailUrl,
        sourceId = view.source,
        isFavorite = view.favorite,
        coverLastModified = view.cover_last_modified,
        chapterNumber = view.chapterNumber,
        readAt = view.readAt,
        readDuration = view.readDuration,
    )
}
