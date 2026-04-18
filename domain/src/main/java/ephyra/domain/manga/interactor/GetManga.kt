package ephyra.domain.manga.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaNotFoundException
import ephyra.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import logcat.LogPriority

class GetManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long): Manga? {
        return try {
            mangaRepository.getMangaById(id)
        } catch (e: MangaNotFoundException) {
            null
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

    /**
     * Returns a [Flow] that emits the [Manga] for [id] whenever it changes in
     * the database.  If the manga is deleted (or was never present) the flow
     * terminates with [MangaNotFoundException] so that callers can navigate away
     * instead of being left with a stale / frozen screen.
     */
    suspend fun subscribe(id: Long): Flow<Manga> {
        return mangaRepository.getMangaByIdAsFlow(id)
            .catch { e ->
                if (e !is MangaNotFoundException) {
                    logcat(LogPriority.ERROR, e) { "Unexpected error in getMangaByIdAsFlow(id=$id)" }
                }
                throw e
            }
    }

    fun subscribe(url: String, sourceId: Long): Flow<Manga?> {
        return mangaRepository.getMangaByUrlAndSourceIdAsFlow(url, sourceId)
    }
}
