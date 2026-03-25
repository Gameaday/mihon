package ephyra.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository

class GetManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long): Manga? {
        return try {
            mangaRepository.getMangaById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun isFavorite(id: Long): Boolean {
        return try {
            mangaRepository.isMangaFavorite(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    suspend fun subscribe(id: Long): Flow<Manga> {
        return mangaRepository.getMangaByIdAsFlow(id)
    }

    fun subscribe(url: String, sourceId: Long): Flow<Manga?> {
        return mangaRepository.getMangaByUrlAndSourceIdAsFlow(url, sourceId)
    }
}
