package ephyra.domain.track.interactor

import ephyra.core.common.util.lang.convertEpochMillisZone
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.mergedAlternativeTitles
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.interactor.AddTracks.Companion.TRACKER_CANONICAL_PREFIXES
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.EnhancedTracker
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import eu.kanade.tachiyomi.source.Source
import logcat.LogPriority
import java.time.ZoneOffset

class AddTracks(
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getHistory: GetHistory,
    private val trackerManager: TrackerManager,
    private val mangaRepository: MangaRepository,
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: Tracker, item: Track, mangaId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getChaptersByMangaId.await(mangaId)
            val hasReadChapters = allChapters.any { it.read }
            tracker.bind(item, hasReadChapters)

            insertTrack.await(item)

            // Set canonical_id from tracker remote_id if not already set.
            // This links the manga's identity to the tracker's ID (e.g. "al:21" for AniList).
            // Only set on first tracker link — subsequent trackers don't overwrite.
            setCanonicalIdIfAbsent(mangaId, tracker.id, item.remoteId)

            // Collect alternative titles from the tracker search result.
            // These enable cross-source matching even when canonical IDs differ.
            // Only TrackSearch instances carry alt titles — enhanced trackers pass plain Track.
            // NOTE: Since Track is now a data class, we can't just check if it's TrackSearch.
            // But we can check if it came from a search result that had alt titles if we had that info.
            // For now, assume if it has remoteId it might have alt titles elsewhere, or we skip this here.

            var currentTrack = item

            // TODO: merge into [SyncChapterProgressWithTrack]?
            // Update chapter progress if newer chapters marked read locally
            if (hasReadChapters) {
                val latestLocalReadChapterNumber = allChapters
                    .sortedBy { it.chapterNumber }
                    .takeWhile { it.read }
                    .lastOrNull()
                    ?.chapterNumber ?: -1.0

                if (latestLocalReadChapterNumber > currentTrack.lastChapterRead) {
                    currentTrack = currentTrack.copy(
                        lastChapterRead = latestLocalReadChapterNumber,
                    )
                    tracker.setRemoteLastChapterRead(currentTrack, latestLocalReadChapterNumber.toInt())
                }

                if (currentTrack.startDate <= 0) {
                    val firstReadChapterDate = getHistory.await(mangaId)
                        .sortedBy { it.readAt }
                        .firstOrNull()
                        ?.readAt

                    firstReadChapterDate?.let {
                        val startDate = firstReadChapterDate.time.convertEpochMillisZone(
                            ZoneOffset.systemDefault(),
                            ZoneOffset.UTC,
                        )
                        currentTrack = currentTrack.copy(
                            startDate = startDate,
                        )
                        tracker.setRemoteStartDate(currentTrack, startDate)
                    }
                }
            }

            syncChapterProgressWithTrack.await(mangaId, currentTrack, tracker)
        }
    }

    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) = withNonCancellableContext {
        withIOContext {
            trackerManager.loggedInTrackers()
                .filterIsInstance<EnhancedTracker>()
                .filter { it.accept(source) }
                .forEach { service ->
                    try {
                        service.match(manga)?.let { searchResult ->
                            val track = Track(
                                id = -1,
                                mangaId = manga.id,
                                trackerId = service.id,
                                remoteId = searchResult.remote_id,
                                libraryId = null,
                                title = searchResult.title,
                                lastChapterRead = 0.0,
                                totalChapters = 0,
                                status = 0,
                                score = -1.0,
                                remoteUrl = searchResult.tracking_url,
                                startDate = 0,
                                finishDate = 0,
                                isPrivate = false,
                            )
                            val boundTrack = (service as Tracker).bind(track)
                            insertTrack.await(boundTrack)

                            // Also set canonical_id for enhanced tracker bindings
                            setCanonicalIdIfAbsent(manga.id, service.id, boundTrack.remoteId)

                            syncChapterProgressWithTrack.await(
                                manga.id,
                                boundTrack,
                                service,
                            )
                        }
                    } catch (e: Exception) {
                        logcat(
                            LogPriority.WARN,
                            e,
                        ) { "Could not match manga: ${manga.title} with service $service" }
                    }
                }
        }
    }

    /**
     * Sets the canonical_id on a manga from a tracker's remote_id, if not already set.
     * Format: "al:21" for AniList, "mal:13" for MyAnimeList, "mu:abc123" for MangaUpdates.
     * Only authoritative trackers (AniList, MAL, MangaUpdates) produce canonical IDs.
     * Once set, canonical_id is not overwritten by subsequent tracker bindings.
     */
    internal suspend fun setCanonicalIdIfAbsent(mangaId: Long, trackerId: Long, remoteId: Long) {
        if (remoteId <= 0) return

        val prefix = TRACKER_CANONICAL_PREFIXES[trackerId] ?: return

        try {
            val manga = mangaRepository.getMangaById(mangaId)
            if (manga.canonicalId != null) return

            val canonicalId = "$prefix:$remoteId"
            mangaRepository.update(MangaUpdate(id = mangaId, canonicalId = canonicalId))
            logcat(LogPriority.INFO) { "Set canonical_id=$canonicalId for manga ${manga.title}" }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to set canonical_id for manga $mangaId" }
        }
    }

    /**
     * Merges new alternative titles into the manga's existing alternative title list.
     * Deduplicates and filters blanks. New titles are additive — existing titles are never removed.
     * Also adds the tracker's title as an alternative if it differs from the manga's primary title.
     */
    internal suspend fun mergeAlternativeTitles(mangaId: Long, newTitles: List<String>) {
        try {
            val manga = mangaRepository.getMangaById(mangaId)
            val merged = manga.mergedAlternativeTitles(newTitles) ?: return

            mangaRepository.update(
                MangaUpdate(id = mangaId, alternativeTitles = merged),
            )
            logcat(LogPriority.INFO) {
                "Updated alternative_titles for ${manga.title}: +${merged.size - manga.alternativeTitles.size} titles"
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to update alternative_titles for manga $mangaId" }
        }
    }

    companion object {
        /**
         * Mapping of tracker IDs to canonical ID prefixes.
         * Only authoritative metadata trackers are included.
         * Uses TrackerManager constants where available.
         */
        private const val MYANIMELIST_ID = 1L
        private const val MANGAUPDATES_ID = 7L

        val TRACKER_CANONICAL_PREFIXES = mapOf(
            MYANIMELIST_ID to "mal", // MyAnimeList
            TrackerManager.ANILIST to "al", // AniList
            MANGAUPDATES_ID to "mu", // MangaUpdates
            TrackerManager.JELLYFIN to "jf", // Jellyfin
        )

        /**
         * Tracker IDs whose search API works without user authentication.
         * These trackers can be used in Discover search even when not logged in.
         */
        val TRACKERS_WITH_PUBLIC_SEARCH = setOf(
            MANGAUPDATES_ID, // MangaUpdates search is unauthenticated
        )

        /**
         * Content types supported by each canonical tracker.
         *
         * Used to:
         * - Filter which trackers to suggest for a given content type.
         * - Determine which trackers to search when the user's content type is known.
         * - Future: filter Discover search results by content type.
         *
         * All current canonical trackers (MAL, AniList, MangaUpdates) support both
         * MANGA and NOVEL. Only trackers listed in [TRACKER_CANONICAL_PREFIXES] are
         * included here — self-hosted trackers (Kavita, Suwayomi, Komga) are not
         * canonical authority sources and handle content type internally.
         */
        val TRACKER_CONTENT_TYPES: Map<Long, Set<ContentType>> = mapOf(
            MYANIMELIST_ID to setOf(ContentType.MANGA, ContentType.NOVEL),
            TrackerManager.ANILIST to setOf(ContentType.MANGA, ContentType.NOVEL),
            MANGAUPDATES_ID to setOf(ContentType.MANGA, ContentType.NOVEL),
            TrackerManager.JELLYFIN to setOf(ContentType.MANGA, ContentType.NOVEL, ContentType.BOOK),
        )

        /**
         * Returns tracker IDs that support the given content type.
         * If [contentType] is [ContentType.UNKNOWN], returns all canonical trackers.
         */
        fun trackersForContentType(contentType: ContentType): Set<Long> {
            if (contentType == ContentType.UNKNOWN) return TRACKER_CANONICAL_PREFIXES.keys
            return TRACKER_CONTENT_TYPES.entries
                .filter { contentType in it.value }
                .map { it.key }
                .toSet()
        }
    }
}
