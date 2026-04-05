package ephyra.data.track.suwayomi

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.data.track.BaseTracker
import ephyra.data.track.model.TrackSearch
import ephyra.data.track.model.toDomainTrackSearch
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.EnhancedTracker
import ephyra.domain.track.service.TrackPreferences
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import kotlinx.serialization.json.Json
import ephyra.data.database.models.Track as DbTrack

class Suwayomi(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val sourceManager: SourceManager,
    private val json: Json,
) : BaseTracker(id, "Suwayomi", context, trackPreferences, networkService, addTracks, insertTrack), EnhancedTracker {
    private val api by lazy { SuwayomiApi(id, sourceManager, json) }

    override fun getLogo() = R.drawable.brand_suwayomi

    companion object {
        const val UNREAD = 1L
        const val READING = 2L
        const val COMPLETED = 3L

        private const val TRACKER_DELETE_KEY = "Tracker Delete"
        private const val TRACKER_DELETE_DEFAULT = false
    }

    override fun getStatusList(): List<Long> = listOf(UNREAD, READING, COMPLETED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        UNREAD -> MR.strings.unread
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: Track): String = ""

    override suspend fun updateInternal(track: DbTrack, didReadChapter: Boolean): DbTrack {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = READING
                }
            }
        }

        return api.updateProgress(track, getPrefTrackerDelete())
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        return track
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        // Suwayomi is a self-hosted tracker that auto-binds via enhanced matching.
        // It does not support general title search — return empty so callers don't crash.
        return emptyList()
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        val remoteTrack = api.getTrackSearch(track.remote_id)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources(): List<String> = listOf("ephyra.app.extension.all.tachidesk.Tachidesk")

    override fun accept(source: Source): Boolean {
        return source.javaClass.name in getAcceptedSources()
    }

    override suspend fun match(manga: Manga): ephyra.domain.track.model.TrackSearch? =
        try {
            api.getTrackSearch(manga.url.getMangaId()).toDomainTrackSearch()
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: Track, manga: Manga, source: Source?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: Track, manga: Manga, newSource: Source): Track? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }

    private fun String.getMangaId(): Long =
        this.substringAfterLast('/').toLong()

    private fun getPrefTrackerDelete(): Boolean {
        val preferences = api.sourcePreferences()
        return preferences.getBoolean(TRACKER_DELETE_KEY, TRACKER_DELETE_DEFAULT)
    }
}
