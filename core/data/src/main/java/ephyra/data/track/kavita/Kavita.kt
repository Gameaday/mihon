package ephyra.data.track.kavita

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
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import ephyra.data.database.models.Track as DbTrack

class Kavita(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val sourceManager: SourceManager,
    private val json: Json,
) : BaseTracker(id, "Kavita", context, trackPreferences, networkService, addTracks, insertTrack), EnhancedTracker {

    companion object {
        const val UNREAD = 1L
        const val READING = 2L
        const val COMPLETED = 3L
    }

    var authentications: OAuth? = null

    private val interceptor by lazy { KavitaInterceptor(this) }
    val api by lazy { KavitaApi(client, interceptor, json) }

    override fun getLogo(): Int = R.drawable.brand_kavita

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
        return api.updateProgress(track)
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        return track
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        // Kavita is a self-hosted tracker that auto-binds via enhanced matching.
        // It does not support general title search — return empty so callers don't crash.
        return emptyList()
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    // [Tracker].isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("ephyra.app.extension.all.kavita.Kavita")

    override fun accept(source: Source): Boolean {
        return source.javaClass.name in getAcceptedSources()
    }

    override suspend fun match(manga: Manga): ephyra.domain.track.model.TrackSearch? =
        try {
            api.getTrackSearch(manga.url)?.toDomainTrackSearch()
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

    suspend fun loadOAuth() {
        val oauth = OAuth()
        for (id in 1..3) {
            val authentication = oauth.authentications[id - 1]
            val sourceId by lazy {
                val key = "kavita_$id/all/1" // Hardcoded versionID to 1
                val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
                (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                    .reduce(Long::or) and Long.MAX_VALUE
            }
            val preferences = (sourceManager.get(sourceId) as ConfigurableSource).sourcePreferences()

            val prefApiUrl = preferences.getString("APIURL", "")
            val prefApiKey = preferences.getString("APIKEY", "")
            if (prefApiUrl.isNullOrEmpty() || prefApiKey.isNullOrEmpty()) {
                // Source not configured. Skip
                continue
            }

            val token = api.getNewToken(apiUrl = prefApiUrl, apiKey = prefApiKey)
            if (token.isNullOrEmpty()) {
                // Source is not accessible. Skip
                continue
            }

            authentication.apiUrl = prefApiUrl
            authentication.jwtToken = token
        }
        authentications = oauth
    }
}
