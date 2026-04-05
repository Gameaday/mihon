package ephyra.data.track.mangaupdates

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.data.track.BaseTracker
import ephyra.data.track.DeletableTracker
import ephyra.data.track.mangaupdates.dto.MUListItem
import ephyra.data.track.mangaupdates.dto.MURating
import ephyra.data.track.mangaupdates.dto.copyTo
import ephyra.data.track.mangaupdates.dto.toTrackSearch
import ephyra.data.track.model.TrackSearch
import ephyra.data.track.model.toDomainTrackSearch
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.TrackPreferences
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ephyra.data.database.models.Track as DbTrack

class MangaUpdates(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val json: Json,
) : BaseTracker(id, "MangaUpdates", context, trackPreferences, networkService, addTracks, insertTrack),
    DeletableTracker {

    companion object {
        const val READING_LIST = 0L
        const val WISH_LIST = 1L
        const val COMPLETE_LIST = 2L
        const val UNFINISHED_LIST = 3L
        const val ON_HOLD_LIST = 4L

        private const val SEARCH_ID_PREFIX = "id:"

        private val SCORE_LIST = (0..10)
            .flatMap { decimal ->
                when (decimal) {
                    0 -> listOf("-")
                    10 -> listOf("10.0")
                    else -> (0..9).map { fraction ->
                        "$decimal.$fraction"
                    }
                }
            }
    }

    private val interceptor by lazy { MangaUpdatesInterceptor(this) }

    private val api by lazy { MangaUpdatesApi(interceptor, client, json) }

    override fun getLogo(): Int = R.drawable.brand_mangaupdates

    override fun getStatusList(): List<Long> {
        return listOf(READING_LIST, COMPLETE_LIST, ON_HOLD_LIST, UNFINISHED_LIST, WISH_LIST)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING_LIST -> MR.strings.reading_list
        WISH_LIST -> MR.strings.wish_list
        COMPLETE_LIST -> MR.strings.complete_list
        ON_HOLD_LIST -> MR.strings.on_hold_list
        UNFINISHED_LIST -> MR.strings.unfinished_list
        else -> null
    }

    override fun getReadingStatus(): Long = READING_LIST

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETE_LIST

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun indexToScore(index: Int): Double = if (index == 0) 0.0 else SCORE_LIST[index].toDouble()

    override fun displayScore(track: Track): String = track.score.toString()

    override suspend fun updateInternal(track: DbTrack, didReadChapter: Boolean): DbTrack {
        if (track.status != COMPLETE_LIST && didReadChapter) {
            track.status = READING_LIST
        }
        api.updateSeriesListItem(track)
        return track
    }

    override suspend fun delete(track: Track) {
        api.deleteSeriesFromList(track)
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        return try {
            val (series, rating) = api.getSeriesListItem(track)
            track.copyFrom(series, rating)
        } catch (e: Exception) {
            track.score = 0.0
            api.addSeriesToList(track, hasReadChapters)
            track
        }
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).toLongOrNull()?.let { seriesId ->
                val record = api.getSeriesById(seriesId)
                return listOfNotNull(record?.toTrackSearch(id)?.toDomainTrackSearch())
            }
        }

        return api.search(query)
            .map {
                it.toTrackSearch(id).toDomainTrackSearch()
            }
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        val (series, rating) = api.getSeriesListItem(track)
        return track.copyFrom(series, rating)
    }

    private fun DbTrack.copyFrom(item: MUListItem, rating: MURating?): DbTrack = apply {
        item.copyTo(this)
        score = rating?.rating ?: 0.0
    }

    override suspend fun login(username: String, password: String) {
        val authenticated = api.authenticate(username, password) ?: throw Throwable("Unable to login")
        saveCredentials(authenticated.uid.toString(), authenticated.sessionToken)
        interceptor.newAuth(authenticated.sessionToken)
    }

    fun restoreSession(): String? {
        return runBlocking { trackPreferences.trackPassword(this@MangaUpdates).get() }.ifBlank { null }
    }
}
