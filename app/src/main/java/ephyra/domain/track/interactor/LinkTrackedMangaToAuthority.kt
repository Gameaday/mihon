package ephyra.domain.track.interactor

import logcat.LogPriority
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.interactor.GetTracks

/**
 * Retroactively assigns canonical IDs to existing library manga based on their tracker bindings.
 *
 * Manga added to the library before the authority model was introduced may have trackers bound
 * but no canonical ID set. This interactor finds all such manga and sets their canonical ID from
 * the first authoritative tracker binding found (MAL, AniList, or MangaUpdates).
 *
 * This implements the migration path described in the "Migrator" user story: users who already
 * have manga tracked can link them to the authority model without re-importing from scratch.
 */
class LinkTrackedMangaToAuthority(
    private val mangaRepository: MangaRepository,
    private val getTracks: GetTracks,
) {

    /**
     * Links all unlinked library manga to the authority model.
     *
     * @return the number of manga that were newly linked.
     */
    suspend fun await(): Int = withIOContext {
        var linked = 0
        val favorites = mangaRepository.getFavorites()
        for (manga in favorites) {
            if (manga.canonicalId != null) continue
            val canonicalId = resolveCanonicalId(manga) ?: continue
            try {
                mangaRepository.update(MangaUpdate(id = manga.id, canonicalId = canonicalId))
                logcat(LogPriority.INFO) {
                    "Linked '${manga.title}' → canonical_id=$canonicalId"
                }
                linked++
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to link manga ${manga.id}" }
            }
        }
        linked
    }

    /**
     * Resolves the canonical ID for a manga from its tracker bindings.
     * Returns null if no authoritative tracker binding is found.
     */
    private suspend fun resolveCanonicalId(manga: Manga): String? {
        val tracks = getTracks.await(manga.id)
        for (track in tracks) {
            if (track.remoteId <= 0) continue
            val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[track.trackerId] ?: continue
            return "$prefix:${track.remoteId}"
        }
        return null
    }
}
