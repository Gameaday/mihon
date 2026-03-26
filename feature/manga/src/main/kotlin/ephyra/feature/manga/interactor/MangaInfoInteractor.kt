package ephyra.feature.manga.interactor

import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import org.koin.core.annotation.Factory
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.category.interactor.SetMangaCategories

@Factory
class MangaInfoInteractor(
    private val updateManga: UpdateManga,
    private val mangaRepository: MangaRepository,
    private val refreshCanonical: RefreshCanonicalMetadata,
    private val matchUnlinkedManga: MatchUnlinkedManga,
    private val syncJellyfin: SyncJellyfin,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val setMangaCategories: SetMangaCategories,
) {
    suspend fun updateManga(update: MangaUpdate) {
        updateManga.await(update)
    }

    suspend fun refreshFromAuthority(manga: Manga) {
        if (manga.canonicalId != null) {
            refreshCanonical.await(manga)
        }
    }

    suspend fun unlinkAuthority(manga: Manga) {
        mangaRepository.clearCanonicalId(manga.id)
    }

    suspend fun pushMetadataToJellyfinIfLinked(manga: Manga) {
        val updated = mangaRepository.getMangaById(manga.id)
        syncJellyfin.pushMetadataToJellyfinIfLinked(updated)
    }

    suspend fun updateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        return updateManga.awaitUpdateFavorite(mangaId, favorite)
    }

    suspend fun updateCoverLastModified(mangaId: Long) {
        updateManga.awaitUpdateCoverLastModified(mangaId)
    }

    suspend fun updateFetchInterval(manga: Manga): Boolean {
        return updateManga.awaitUpdateFetchInterval(manga)
    }

    suspend fun markJellyfinFavoriteIfLinked(manga: Manga, favorite: Boolean) {
        syncJellyfin.markJellyfinFavoriteIfLinked(manga, favorite)
    }

    suspend fun updateUpdateStrategy(manga: Manga, networkManga: eu.kanade.tachiyomi.source.model.SManga?, manualFetch: Boolean) {
        updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch)
    }

    suspend fun setExcludedScanlators(mangaId: Long, excludedScanlators: Set<String>) {
        setExcludedScanlators.await(mangaId, excludedScanlators)
    }

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        setMangaCategories.await(mangaId, categoryIds)
    }

    suspend fun getMangaById(mangaId: Long): Manga {
        return mangaRepository.getMangaById(mangaId)
    }
}
