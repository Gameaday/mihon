package ephyra.data.track.myanimelist

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.data.track.BaseTracker
import ephyra.domain.track.service.DeletableTracker
import ephyra.data.track.myanimelist.dto.MALOAuth
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.data.track.toDbTrack
import ephyra.data.track.toDomainTrack
import ephyra.domain.track.service.TrackPreferences
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import ephyra.data.database.models.Track as DbTrack

class MyAnimeList(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val json: Json,
) : BaseTracker(id, "MyAnimeList", context, trackPreferences, networkService, addTracks, insertTrack),
    DeletableTracker {

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 6L
        const val REREADING = 7L

        private const val SEARCH_ID_PREFIX = "id:"
        private const val SEARCH_LIST_PREFIX = "my:"

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
    }

    private val interceptor by lazy { MyAnimeListInterceptor(this, json) }
    private val api by lazy { MyAnimeListApi(id, client, interceptor, json) }

    override val oauthUrl: String get() = MyAnimeListApi.authUrl().toString()

    override val supportsReadingDates: Boolean = true

    override fun getLogo() = R.drawable.brand_myanimelist

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = REREADING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: Track): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: DbTrack): DbTrack {
        return api.updateItem(track)
    }

    override suspend fun updateInternal(track: DbTrack, didReadChapter: Boolean): DbTrack {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateItem(track)
    }

    override suspend fun delete(track: Track) {
        api.deleteItem(track)
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        val remoteTrack = api.findListItem(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            updateInternal(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        val results = if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).toIntOrNull()?.let { id ->
                listOf(api.getMangaDetails(id))
            } ?: emptyList()
        } else if (query.startsWith(SEARCH_LIST_PREFIX)) {
            query.substringAfter(SEARCH_LIST_PREFIX).let { title ->
                api.findListItems(title)
            }
        } else {
            api.search(query)
        }

        return results.map { it.toDomainTrackSearch() }
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        return api.findListItem(track) ?: add(track)
    }

    suspend fun getUserFullList() = api.getUserFullList()

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(authCode: String) {
        try {
            val oauth = api.getAccessToken(authCode)
            interceptor.setAuth(oauth)
            val username = api.getCurrentUser()
            saveCredentials(username, oauth.accessToken)
        } catch (e: Throwable) {
            logout()
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    suspend fun getIfAuthExpired(): Boolean {
        return trackPreferences.trackAuthExpired(this).get()
    }

    fun getIfAuthExpiredSync(): Boolean {
        return trackPreferences.trackAuthExpired(this@MyAnimeList).getSync()
    }

    fun setAuthExpired() {
        trackPreferences.trackAuthExpired(this).set(true)
    }

    fun saveOAuth(oAuth: MALOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    suspend fun loadOAuth(): MALOAuth? {
        return try {
            json.decodeFromString<MALOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }

    fun loadOAuthSync(): MALOAuth? {
        return try {
            json.decodeFromString<MALOAuth>(trackPreferences.trackToken(this@MyAnimeList).getSync())
        } catch (e: Exception) {
            null
        }
    }
}

// Extension to convert data TrackSearch to domain TrackSearch
fun ephyra.data.track.model.TrackSearch.toDomainTrackSearch() = TrackSearch(
    remote_id = remote_id,
    title = title,
    tracking_url = tracking_url,
    summary = summary ?: "",
    authors = authors ?: emptyList(),
    artists = artists ?: emptyList(),
    cover_url = cover_url ?: "",
    publishing_type = publishing_type ?: "",
    alternative_titles = alternative_titles ?: emptyList(),
)
