package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository

/**
 * Finds library manga that have been persistently DEAD (source returning 0 chapters)
 * since before the given timestamp. Used by the bulk migration prompt to identify
 * manga that should be migrated to a working source.
 */
class GetDeadFavorites(
    private val mangaRepository: MangaRepository,
) {
    /**
     * @param deadSinceBefore Timestamp cutoff — returns manga whose dead_since is before this value.
     *   Typically calculated as `System.currentTimeMillis() - DEAD_MIGRATION_THRESHOLD_MS`.
     * @return Library manga entries that have been DEAD since before the cutoff.
     */
    suspend fun await(deadSinceBefore: Long): List<Manga> {
        return mangaRepository.getDeadFavorites(deadSinceBefore)
    }
}
