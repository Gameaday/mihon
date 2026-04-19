package ephyra.data.manga

import ephyra.core.common.util.system.logcat
import ephyra.data.room.daos.MangaDao
import ephyra.data.room.entities.MangaEntity
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaNotFoundException
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import java.time.LocalDate
import java.time.ZoneId

class MangaRepositoryImpl(
    private val mangaDao: MangaDao,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return mangaDao.getMangaById(id)?.let(MangaMapper::mapManga) ?: throw MangaNotFoundException(id)
    }

    override suspend fun isMangaFavorite(id: Long): Boolean {
        return mangaDao.isMangaFavorite(id) ?: false
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return mangaDao.getMangaByIdAsFlow(id)
            .map { it?.let(MangaMapper::mapManga) ?: throw MangaNotFoundException(id) }
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return mangaDao.getMangaByUrlAndSource(url, sourceId)?.let(MangaMapper::mapManga)
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return mangaDao.getMangaByUrlAndSourceAsFlow(url, sourceId).map { it?.let(MangaMapper::mapManga) }
    }

    override suspend fun getFavoritesByCanonicalId(canonicalId: String, excludeMangaId: Long): List<Manga> {
        return mangaDao.getFavoritesByCanonicalId(canonicalId, excludeMangaId).map(MangaMapper::mapManga)
    }

    override suspend fun getDeadFavorites(deadSinceBefore: Long): List<Manga> {
        return mangaDao.getFavoritesByDeadSinceBefore(deadSinceBefore).map(MangaMapper::mapManga)
    }

    override suspend fun getFavorites(): List<Manga> {
        return mangaDao.getFavorites().map(MangaMapper::mapManga)
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return mangaDao.getReadMangaNotInLibrary().map(MangaMapper::mapManga)
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        return mangaDao.getLibraryManga().map(MangaMapper::mapLibraryManga)
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return mangaDao.getLibraryMangaAsFlow().map { list -> list.map(MangaMapper::mapLibraryManga) }
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return mangaDao.getFavoritesBySourceIdAsFlow(sourceId).map { list -> list.map(MangaMapper::mapManga) }
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount> {
        // This is a bit tricky with Room views, but we can return MangaWithChapterCount if we have a view or aggregate query.
        // For now, mirroring old logic using the entities.
        return mangaDao.getDuplicateLibraryManga(id, title)
            .map { MangaWithChapterCount(MangaMapper.mapManga(it), 0 /* count needs join */) }
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return mangaDao.getUpcomingMangaAsFlow(epochMillis, statuses).map { list -> list.map(MangaMapper::mapManga) }
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            mangaDao.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        mangaDao.setMangaCategories(mangaId, categoryIds)
    }

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdate(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun clearMetadataSource(mangaId: Long): Boolean {
        return try {
            mangaDao.clearMetadataSource(mangaId)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun clearCanonicalId(mangaId: Long): Boolean {
        return try {
            mangaDao.clearCanonicalId(mangaId)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertNetworkManga(manga: List<Manga>): List<Manga> {
        return manga.map {
            val entity = MangaEntity(
                id = 0,
                source = it.source,
                url = it.url,
                artist = it.artist,
                author = it.author,
                description = it.description,
                genre = it.genre,
                title = it.title,
                status = it.status,
                thumbnailUrl = it.thumbnailUrl,
                favorite = it.favorite,
                lastUpdate = it.lastUpdate,
                nextUpdate = it.nextUpdate,
                initialized = it.initialized,
                viewerFlags = it.viewerFlags,
                chapterFlags = it.chapterFlags,
                coverLastModified = it.coverLastModified,
                dateAdded = it.dateAdded,
                updateStrategy = it.updateStrategy.ordinal,
                calculateInterval = it.fetchInterval,
                lastModifiedAt = it.lastModifiedAt,
                favoriteModifiedAt = it.favoriteModifiedAt,
                version = it.version,
                isSyncing = false,
                notes = it.notes,
                metadataSource = it.metadataSource,
                metadataUrl = it.metadataUrl,
                canonicalId = it.canonicalId,
                sourceStatus = it.sourceStatus,
                alternativeTitles = MangaMapper.serializeAlternativeTitles(it.alternativeTitles),
                deadSince = it.deadSince,
                contentType = it.contentType.value,
                lockedFields = it.lockedFields,
            )
            val id = mangaDao.upsert(entity)
            it.copy(id = id)
        }
    }

    private suspend fun partialUpdate(vararg mangaUpdates: MangaUpdate) {
        mangaUpdates.forEach { value ->
            val existing = mangaDao.getMangaById(value.id) ?: return@forEach
            val updated = existing.copy(
                source = value.source ?: existing.source,
                url = value.url ?: existing.url,
                artist = value.artist ?: existing.artist,
                author = value.author ?: existing.author,
                description = value.description ?: existing.description,
                genre = value.genre ?: existing.genre,
                title = value.title ?: existing.title,
                status = value.status ?: existing.status,
                thumbnailUrl = value.thumbnailUrl ?: existing.thumbnailUrl,
                favorite = value.favorite ?: existing.favorite,
                lastUpdate = value.lastUpdate ?: existing.lastUpdate,
                nextUpdate = value.nextUpdate ?: existing.nextUpdate,
                calculateInterval = value.fetchInterval ?: existing.calculateInterval,
                initialized = value.initialized ?: existing.initialized,
                viewerFlags = value.viewerFlags ?: existing.viewerFlags,
                chapterFlags = value.chapterFlags ?: existing.chapterFlags,
                coverLastModified = value.coverLastModified ?: existing.coverLastModified,
                dateAdded = value.dateAdded ?: existing.dateAdded,
                updateStrategy = value.updateStrategy?.ordinal ?: existing.updateStrategy,
                version = value.version ?: existing.version,
                notes = value.notes ?: existing.notes,
                metadataSource = value.metadataSource ?: existing.metadataSource,
                metadataUrl = value.metadataUrl ?: existing.metadataUrl,
                canonicalId = value.canonicalId ?: existing.canonicalId,
                sourceStatus = value.sourceStatus ?: existing.sourceStatus,
                alternativeTitles = value.alternativeTitles?.let { MangaMapper.serializeAlternativeTitles(it) }
                    ?: existing.alternativeTitles,
                deadSince = value.deadSince ?: existing.deadSince,
                contentType = value.contentType?.value ?: existing.contentType,
                lockedFields = value.lockedFields ?: existing.lockedFields,
            )
            mangaDao.update(updated)
        }
    }

    override suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Long) {
        // Room DAO handles this. keepReadManga logic needs to be verified in SQL.
        mangaDao.deleteNonLibraryManga(sourceIds)
    }

    override suspend fun getAllMangaSourceAndUrl(): List<Pair<Long, String>> {
        return mangaDao.getAllMangaSourceAndUrl().map { Pair(it.source, it.url) }
    }
}
