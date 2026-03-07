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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
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
            val primary = manga.title
            val existing = manga.alternativeTitles.toMutableList()
            val previousSize = existing.size

            // Add new titles, excluding the primary title, blanks, and case-insensitive duplicates
            val existingLower = existing.map { it.lowercase() }.toMutableSet()
            val primaryLower = primary.lowercase()
            for (title in newTitles) {
                if (title.isBlank()) continue
                val titleLower = title.lowercase()
                if (titleLower == primaryLower) continue
                if (!existingLower.add(titleLower)) continue
                existing.add(title)
            }
            if (existing.size == previousSize) return // No new titles to add

            mangaRepository.update(
                MangaUpdate(id = mangaId, alternativeTitles = existing),
            )
            logcat(LogPriority.INFO) {
                "Updated alternative_titles for ${manga.title}: +${existing.size - previousSize} titles"
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
        )

        /**
         * Tracker IDs whose search API works without user authentication.
         * These trackers can be used in Discover search even when not logged in.
         */
        val TRACKERS_WITH_PUBLIC_SEARCH = setOf(
            MANGAUPDATES_ID, // MangaUpdates search is unauthenticated
        )
    }
}
