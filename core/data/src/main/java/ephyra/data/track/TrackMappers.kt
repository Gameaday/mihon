package ephyra.data.track

import ephyra.data.database.models.Track as DbTrack
import ephyra.domain.track.model.Track

/**
 * Mapper functions between the domain [Track] model and the legacy SQLDelight [DbTrack] model.
 *
 * These live in `:data` (not `:core:domain`) because they depend on `ephyra.data.database.models`,
 * which is part of the data layer.  Only data-layer classes should reference these mappers.
 */
fun Track.toDbTrack(): DbTrack = DbTrack.create(trackerId).also {
    it.id = id
    it.manga_id = mangaId
    it.remote_id = remoteId
    it.library_id = libraryId
    it.title = title
    it.last_chapter_read = lastChapterRead
    it.total_chapters = totalChapters
    it.status = status
    it.score = score
    it.tracking_url = remoteUrl
    it.started_reading_date = startDate
    it.finished_reading_date = finishDate
    it.isPrivate = isPrivate
}

fun DbTrack.toDomainTrack(idRequired: Boolean = true): Track? {
    val trackId = id ?: if (!idRequired) -1 else return null
    return Track(
        id = trackId,
        mangaId = manga_id,
        trackerId = tracker_id,
        remoteId = remote_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read,
        totalChapters = total_chapters,
        status = status,
        score = score,
        remoteUrl = tracking_url,
        startDate = started_reading_date,
        finishDate = finished_reading_date,
        isPrivate = isPrivate,
    )
}
