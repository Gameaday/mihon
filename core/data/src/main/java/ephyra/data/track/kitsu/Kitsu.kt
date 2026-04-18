package ephyra.data.track.kitsu

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.core.common.util.system.logcat
import ephyra.data.track.BaseTracker
import ephyra.domain.track.service.DeletableTracker
import ephyra.data.track.kitsu.dto.KitsuOAuth
import ephyra.data.track.model.TrackSearch
import ephyra.data.track.model.toDomainTrackSearch
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.TrackPreferences
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import logcat.LogPriority
import java.text.DecimalFormat
import ephyra.data.database.models.Track as DbTrack

class Kitsu(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val json: Json,
) : BaseTracker(id, "Kitsu", context, trackPreferences, networkService, addTracks, insertTrack), DeletableTracker {

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
    }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val interceptor by lazy { KitsuInterceptor(this, json) }

    private val api by lazy { KitsuApi(client, interceptor, json) }

    override fun getLogo() = R.drawable.brand_kitsu

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> {
        val df = DecimalFormat("0.#")
        return (listOf("0") + IntRange(2, 20).map { df.format(it / 2f) })
    }

    override fun indexToScore(index: Int): Double {
        return if (index > 0) (index + 1) / 2.0 else 0.0
    }

    override fun displayScore(track: Track): String {
        val df = DecimalFormat("0.#")
        return df.format(track.score)
    }

    private suspend fun add(track: DbTrack): DbTrack {
        return api.addLibManga(track, getUserId())
    }

    override suspend fun updateInternal(track: DbTrack, didReadChapter: Boolean): DbTrack {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun delete(track: Track) {
        // api.removeLibManga takes DomainTrack
        api.removeLibManga(track)
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        val remoteTrack = api.findLibManga(track, getUserId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.remote_id = remoteTrack.remote_id
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else track.status
            }

            updateInternal(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        return api.search(query).map { it.toDomainTrackSearch() }
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        val remoteTrack = api.getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        val token = api.login(username, password)
        interceptor.newAuth(token)
        val userId = api.getCurrentUser()
        saveCredentials(username, userId)
    }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    private suspend fun getUserId(): String {
        return getPassword()
    }

    fun saveToken(oauth: KitsuOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): KitsuOAuth? {
        return try {
            json.decodeFromString<KitsuOAuth>(trackPreferences.trackToken(this@Kitsu).getSync())
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Failed to restore Kitsu OAuth token from preferences; user may need to log in again" }
            null
        }
    }
}
