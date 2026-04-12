package ephyra.data.track

import ephyra.data.room.entities.TrackEntity
import ephyra.domain.track.model.Track

object TrackMapper {
    fun mapTrack(
        id: Long,
        mangaId: Long,
        syncId: Long,
        remoteId: Long,
        libraryId: Long?,
        title: String,
        lastChapterRead: Double,
        totalChapters: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
        isPrivate: Boolean,
    ): Track = Track(
        id = id,
        mangaId = mangaId,
        trackerId = syncId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
        isPrivate = isPrivate,
    )

    fun mapTrack(entity: TrackEntity): Track = mapTrack(
        id = entity.id,
        mangaId = entity.mangaId,
        syncId = entity.syncId,
        remoteId = entity.remoteId,
        libraryId = entity.libraryId,
        title = entity.title,
        lastChapterRead = entity.lastChapterRead,
        totalChapters = entity.totalChapters,
        status = entity.status,
        score = entity.score,
        remoteUrl = entity.remoteUrl,
        startDate = entity.startDate,
        finishDate = entity.finishDate,
        isPrivate = entity.isPrivate,
    )
}
