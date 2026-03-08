package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.model.ContentType
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.mergedAlternativeTitles
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZoneOffset

class AddTracks(
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackerManager: TrackerManager,
    private val mangaRepository: MangaRepository = Injekt.get(),
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: Tracker, item: Track, mangaId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getChaptersByMangaId.await(mangaId)
            val hasReadChapters = allChapters.any { it.read }
            tracker.bind(item, hasReadChapters)

            var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // Set canonical_id from tracker remote_id if not already set.
            // This links the manga's identity to the tracker's ID (e.g. "al:21" for AniList).
            // Only set on first tracker link — subsequent trackers don't overwrite.
            setCanonicalIdIfAbsent(mangaId, tracker.id, track.remoteId)

            // Collect alternative titles from the tracker search result.
            // These enable cross-source matching even when canonical IDs differ.
            // Only TrackSearch instances carry alt titles — enhanced trackers pass plain Track.
            if (item is TrackSearch && item.alternative_titles.isNotEmpty()) {
                mergeAlternativeTitles(mangaId, item.alternative_titles)
            }

            // TODO: merge into [SyncChapterProgressWithTrack]?
            // Update chapter progress if newer chapters marked read locally
            if (hasReadChapters) {
                val latestLocalReadChapterNumber = allChapters
                    .sortedBy { it.chapterNumber }
                    .takeWhile { it.read }
                    .lastOrNull()
                    ?.chapterNumber ?: -1.0

                if (latestLocalReadChapterNumber > track.lastChapterRead) {
                    track = track.copy(
                        lastChapterRead = latestLocalReadChapterNumber,
                    )
                    tracker.setRemoteLastChapterRead(track.toDbTrack(), latestLocalReadChapterNumber.toInt())
                }

                if (track.startDate <= 0) {
                    val firstReadChapterDate = Injekt.get<GetHistory>().await(mangaId)
                        .sortedBy { it.readAt }
                        .firstOrNull()
                        ?.readAt

                    firstReadChapterDate?.let {
                        val startDate = firstReadChapterDate.time.convertEpochMillisZone(
                            ZoneOffset.systemDefault(),
                            ZoneOffset.UTC,
                        )
                        track = track.copy(
                            startDate = startDate,
                        )
                        tracker.setRemoteStartDate(track.toDbTrack(), startDate)
                    }
                }
            }

            syncChapterProgressWithTrack.await(mangaId, track, tracker)
        }
    }

    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) = withNonCancellableContext {
        withIOContext {
            trackerManager.loggedInTrackers()
                .filterIsInstance<EnhancedTracker>()
                .filter { it.accept(source) }
                .forEach { service ->
                    try {
                        service.match(manga)?.let { track ->
                            track.manga_id = manga.id
                            (service as Tracker).bind(track)
                            val domainTrack = track.toDomainTrack(idRequired = false)
                                ?: return@let
                            insertTrack.await(domainTrack)

                            // Also set canonical_id for enhanced tracker bindings
                            setCanonicalIdIfAbsent(manga.id, service.id, domainTrack.remoteId)

                            syncChapterProgressWithTrack.await(
                                manga.id,
                                domainTrack,
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
