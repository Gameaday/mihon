package ephyra.app.data.track.kavita

import dev.icerock.moko.resources.StringResource
import ephyra.app.R
import ephyra.app.data.database.models.Track
import ephyra.app.data.track.BaseTracker
import ephyra.app.data.track.EnhancedTracker
import ephyra.app.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.i18n.MR
import ephyra.domain.track.model.Track as DomainTrack
import android.app.Application
import ephyra.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import java.security.MessageDigest

class Kavita(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val sourceManager: SourceManager,
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

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainTrack): String = ""

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
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

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        // Kavita is a self-hosted tracker that auto-binds via enhanced matching.
        // It does not support general title search — return empty so callers don't crash.
        return emptyList()
    }

    override suspend fun refresh(track: Track): Track {
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

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: Source?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: Source): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }

    fun loadOAuth() {
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
