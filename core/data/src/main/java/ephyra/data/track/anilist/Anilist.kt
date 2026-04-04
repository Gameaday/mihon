package ephyra.data.track.anilist

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.data.database.models.Track as DbTrack
import ephyra.data.track.BaseTracker
import ephyra.data.track.anilist.dto.ALOAuth
import ephyra.data.track.model.TrackSearch
import ephyra.data.track.model.toDomainTrackSearch
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.service.TrackPreferences
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.toDomainTrack

class Anilist(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val json: Json,
) : BaseTracker(id, "AniList", context, trackPreferences, networkService, addTracks, insertTrack) {

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REPEATING = 6L

        const val POINT_10 = "POINT_10"
        const val POINT_10_DECIMAL = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"
        const val POINT_100 = "POINT_100"
    }

    private val interceptor by lazy { AnilistInterceptor(this, getUsernameSync()) }
    private val api = AnilistApi(client, interceptor, json)

    override val supportsReadingDates: Boolean = true

    override fun getLogo() = R.drawable.brand_anilist

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REPEATING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REPEATING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = REPEATING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> {
        return when (runBlocking { trackPreferences.anilistScoreType().get() }) {
            POINT_10 -> IntRange(1, 10).map { it.toString() }
            POINT_10_DECIMAL -> IntRange(1, 100).map { (it / 10.0).toString() }
            POINT_5 -> listOf("★", "★★", "★★★", "★★★★", "★★★★★")
            POINT_3 -> listOf("🙁", "😐", "😊")
            POINT_100 -> IntRange(1, 100).map { it.toString() }
            else -> emptyList()
        }
    }

    override fun indexToScore(index: Int): Double {
        return when (runBlocking { trackPreferences.anilistScoreType().get() }) {
            POINT_10 -> (index + 1).toDouble()
            POINT_10_DECIMAL -> (index + 1) / 10.0
            POINT_5 -> (index + 1).toDouble()
            POINT_3 -> (index + 1).toDouble()
            POINT_100 -> (index + 1).toDouble()
            else -> 0.0
        }
    }

    override fun displayScore(track: Track): String {
        val score = track.score
        if (score <= 0) return ""
        return when (runBlocking { trackPreferences.anilistScoreType().get() }) {
            POINT_10_DECIMAL -> score.toString()
            POINT_10 -> score.toInt().toString()
            POINT_5 -> "★".repeat(score.toInt())
            POINT_3 -> when (score.toInt()) {
                1 -> "🙁"
                2 -> "😐"
                3 -> "😊"
                else -> ""
            }
            POINT_100 -> score.toInt().toString()
            else -> ""
        }
    }

    override suspend fun updateInternal(track: DbTrack, didReadChapter: Boolean): DbTrack {
        return api.updateLibManga(track)
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        val remoteTrack = api.findLibManga(track, getUsername().toInt())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REPEATING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            updateInternal(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            api.addLibManga(track)
        }
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        return api.search(query).map { it.toDomainTrackSearch() }
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        return api.findLibManga(track, getUsername().toInt()) ?: updateInternal(track)
    }

    override suspend fun login(username: String, password: String) {
        trackPreferences.setCredentials(this, username, password)
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
    }

    fun saveOAuth(oAuth: ALOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): ALOAuth? {
        return try {
            json.decodeFromString<ALOAuth>(runBlocking { trackPreferences.trackToken(this@Anilist).get() })
        } catch (e: Exception) {
            null
        }
    }
}
