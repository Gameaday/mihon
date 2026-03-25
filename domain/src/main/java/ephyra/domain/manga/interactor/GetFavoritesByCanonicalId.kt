package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository

/**
 * Finds library manga that share the same canonical identity.
 * Used for cross-source resolution: if manga A has canonical_id "al:21" and
 * manga B in a different source also has "al:21", they are the same series.
 * This enables zero-API-call matching during migration and source failover.
 */
class GetFavoritesByCanonicalId(
    private val mangaRepository: MangaRepository,
) {
    /**
     * @param canonicalId The canonical identity to search for (e.g. "al:21")
     * @param excludeMangaId The manga ID to exclude from results (usually the source manga)
     * @return Library manga entries sharing this canonical ID
     */
    suspend fun await(canonicalId: String, excludeMangaId: Long): List<Manga> {
        return mangaRepository.getFavoritesByCanonicalId(canonicalId, excludeMangaId)
    }
}
