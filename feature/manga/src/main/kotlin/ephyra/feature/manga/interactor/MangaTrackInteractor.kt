package ephyra.feature.manga.interactor

import android.content.Context
import ephyra.app.data.track.EnhancedTracker
import ephyra.app.data.track.TrackerManager
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.model.AutoTrackState
import ephyra.domain.manga.model.Manga
import org.koin.core.annotation.Factory
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import ephyra.domain.track.model.Track
import ephyra.app.data.track.Tracker

@Factory
class MangaTrackInteractor(
    private val getTracks: GetTracks,
    private val addTracks: AddTracks,
    private val refreshCanonical: RefreshCanonicalMetadata,
    private val matchUnlinkedManga: MatchUnlinkedManga,
    private val trackChapter: TrackChapter,
    private val trackPreferences: TrackPreferences,
    private val trackerManager: TrackerManager,
    private val refreshTracks: RefreshTracks,
) {

    fun loggedInTrackersFlow() = trackerManager.loggedInTrackersFlow()
    fun getTracker(id: Long): Tracker? = trackerManager.get(id)

    suspend fun getTracks(mangaId: Long): List<Track> = getTracks.await(mangaId)

    fun hasQueryableTracker(): Boolean = matchUnlinkedManga.hasQueryableTracker()

    suspend fun matchUnlinkedManga(manga: Manga): String? = matchUnlinkedManga.awaitSingle(manga)

    fun subscribeTracks(mangaId: Long): Flow<List<Track>> = getTracks.subscribe(mangaId)

    suspend fun refreshCanonical(manga: Manga) {
        refreshCanonical.await(manga)
    }

    suspend fun refreshTracks(mangaId: Long) = refreshTracks.await(mangaId)

    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) {
        addTracks.bindEnhancedTrackers(manga, source)
    }

    suspend fun trackChapter(context: Context, mangaId: Long, maxChapterNumber: Double) {
        trackChapter.await(context, mangaId, maxChapterNumber)
    }

    fun isAutoTrackStateAlways(): Boolean = trackPreferences.autoUpdateTrackOnMarkRead().get() == AutoTrackState.ALWAYS
    fun isAutoTrackStateNever(): Boolean = trackPreferences.autoUpdateTrackOnMarkRead().get() == AutoTrackState.NEVER
    fun autoUpdateTrackOnMarkRead() = trackPreferences.autoUpdateTrackOnMarkRead()

    val jellyfin = trackerManager.jellyfin

    fun jellyfinIsAdmin() = trackPreferences.jellyfinIsAdmin()

    fun jellyfinUserId() = trackPreferences.jellyfinUserId()
}
