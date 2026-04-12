package ephyra.data.track

import ephyra.data.room.daos.TrackDao
import ephyra.data.room.entities.TrackEntity
import ephyra.domain.track.model.Track
import ephyra.domain.track.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackRepositoryImpl(
    private val trackDao: TrackDao,
) : TrackRepository {

    override suspend fun getTrackById(id: Long): Track? {
        return trackDao.getTrackById(id)?.let(TrackMapper::mapTrack)
    }

    override suspend fun getTracksByMangaId(mangaId: Long): List<Track> {
        return trackDao.getTracksByMangaId(mangaId).map(TrackMapper::mapTrack)
    }

    override fun getTracksAsFlow(): Flow<List<Track>> {
        return trackDao.getTracksAsFlow().map { list -> list.map(TrackMapper::mapTrack) }
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>> {
        return trackDao.getTracksByMangaIdAsFlow(mangaId).map { list -> list.map(TrackMapper::mapTrack) }
    }

    override suspend fun delete(mangaId: Long, trackerId: Long) {
        trackDao.delete(mangaId, trackerId)
    }

    override suspend fun insert(track: Track) {
        insertValues(track)
    }

    override suspend fun insertAll(tracks: List<Track>) {
        insertValues(*tracks.toTypedArray())
    }

    private suspend fun insertValues(vararg tracks: Track) {
        tracks.forEach { mangaTrack ->
            val entity = TrackEntity(
                id = mangaTrack.id,
                mangaId = mangaTrack.mangaId,
                syncId = mangaTrack.trackerId,
                remoteId = mangaTrack.remoteId,
                libraryId = mangaTrack.libraryId,
                title = mangaTrack.title,
                lastChapterRead = mangaTrack.lastChapterRead,
                totalChapters = mangaTrack.totalChapters,
                status = mangaTrack.status,
                score = mangaTrack.score,
                remoteUrl = mangaTrack.remoteUrl,
                startDate = mangaTrack.startDate,
                finishDate = mangaTrack.finishDate,
                isPrivate = mangaTrack.isPrivate,
            )
            trackDao.insert(entity)
        }
    }
}
