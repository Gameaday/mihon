package ephyra.domain.manga.repository

import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun isMangaFavorite(id: Long): Boolean

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>

    suspend fun getFavoritesByCanonicalId(canonicalId: String, excludeMangaId: Long): List<Manga>

    suspend fun getDeadFavorites(deadSinceBefore: Long): List<Manga>

    suspend fun getFavorites(): List<Manga>

    suspend fun getReadMangaNotInLibrary(): List<Manga>

    suspend fun getLibraryManga(): List<LibraryManga>

    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount>

    suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean

    suspend fun clearMetadataSource(mangaId: Long): Boolean

    suspend fun clearCanonicalId(mangaId: Long): Boolean

    suspend fun insertNetworkManga(manga: List<Manga>): List<Manga>

    suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Long)

    /**
     * Returns a lightweight list of (source, url) pairs for all known manga.
     * Used by the backup restorer to sort incoming mangas by whether they are new
     * to this device without loading full [Manga] objects.
     */
    suspend fun getAllMangaSourceAndUrl(): List<Pair<Long, String>>
}
