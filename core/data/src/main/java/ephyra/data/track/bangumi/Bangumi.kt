package ephyra.data.track.bangumi

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.core.common.util.system.logcat
import ephyra.data.track.BaseTracker
import ephyra.data.track.bangumi.dto.BGMOAuth
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
import ephyra.data.database.models.Track as DbTrack

class Bangumi(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val json: Json,
) : BaseTracker(id, "Bangumi", context, trackPreferences, networkService, addTracks, insertTrack) {

    private val interceptor by lazy { BangumiInterceptor(this, json) }

    private val api by lazy { BangumiApi(id, client, interceptor, json) }

    override val oauthUrl: String get() = BangumiApi.authUrl().toString()

    override val supportsPrivateTracking: Boolean = true

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: Track): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: DbTrack): DbTrack {
        return api.addLibManga(track)
    }

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

        return api.updateLibManga(track)
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        val statusTrack = api.statusLibManga(track, getUsername())
        return if (statusTrack != null) {
            track.copyPersonalFrom(statusTrack, copyRemotePrivate = false)
            track.library_id = statusTrack.library_id
            track.score = statusTrack.score
            track.last_chapter_read = statusTrack.last_chapter_read
            track.total_chapters = statusTrack.total_chapters
            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else statusTrack.status
            }

            updateInternal(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        return api.search(query).map { it.toDomainTrackSearch() }
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        val remoteStatusTrack = api.statusLibManga(track, getUsernameSync()) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteStatusTrack)
        return track
    }

    override fun getLogo() = R.drawable.brand_bangumi

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

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            // Users can set a 'username' (not nickname) once which effectively
            // replaces the stringified ID in certain queries.
            // If no username is set, the API returns the user ID as a strings
            val username = api.getUsername()
            saveCredentials(username, oauth.accessToken)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Bangumi login failed; logging out" }
            logout()
        }
    }

    fun saveToken(oauth: BGMOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): BGMOAuth? {
        return try {
            json.decodeFromString<BGMOAuth>(trackPreferences.trackToken(this@Bangumi).getSync())
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) {
                "Failed to restore Bangumi OAuth token from preferences; user may need to log in again"
            }
            null
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.newAuth(null)
    }

    companion object {
        const val PLAN_TO_READ = 1L
        const val COMPLETED = 2L
        const val READING = 3L
        const val ON_HOLD = 4L
        const val DROPPED = 5L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
    }
}
