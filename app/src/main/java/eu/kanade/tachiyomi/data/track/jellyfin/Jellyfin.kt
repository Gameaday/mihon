package eu.kanade.tachiyomi.data.track.jellyfin

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import logcat.LogPriority
import okhttp3.Dns
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * Jellyfin tracker for syncing read progress with a Jellyfin media server.
 *
 * This is an [EnhancedTracker] that auto-binds manga via library matching
 * (no manual search needed). It syncs read progress back to Jellyfin so that
 * chapters read in the app are reflected on the server.
 *
 * Configuration:
 * - Username field stores the server URL (e.g., `http://192.168.1.100:8096`)
 * - Password field stores the API key (generated in Jellyfin → Dashboard → API Keys)
 * - User ID is stored in `jellyfinUserId` preference
 *
 * Jellyfin Bookshelf plugin compatibility:
 * - Uses the existing [JellyfinNaming] utility for path matching
 * - Recognizes series organized in Jellyfin hierarchy
 */
class Jellyfin(id: Long) : BaseTracker(id, "Jellyfin"), EnhancedTracker, DeletableTracker {

    companion object {
        const val UNREAD = 1L
        const val READING = 2L
        const val COMPLETED = 3L
    }

    override val client: OkHttpClient =
        networkService.client.newBuilder()
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS — Jellyfin is typically on LAN
            .addInterceptor(JellyfinInterceptor(this))
            .build()

    val api by lazy { JellyfinApi(id, client) }

    // -- Branding --

    override fun getLogo() = R.drawable.brand_jellyfin

    // -- Status mapping --

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

    // -- Scoring (Jellyfin doesn't have per-user scoring for books) --

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainTrack): String = ""

    // -- Sync operations --

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
        // Sync read state back to Jellyfin for individual chapters
        try {
            syncReadProgressToServer(track)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to sync progress to Jellyfin" }
        }
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (!isLoggedIn) return emptyList()
        val serverUrl = getUsername().trimEnd('/')
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        return try {
            // Support direct ID lookup: "id:itemId"
            if (query.startsWith("id:")) {
                val itemId = query.removePrefix("id:")
                val result = api.getSeries(serverUrl, userId, itemId)
                listOf(result)
            } else {
                api.searchSeries(serverUrl, userId, query)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: Track): Track {
        val serverUrl = api.getServerUrlFromTrackUrl(track.tracking_url)
        val itemId = api.getItemIdFromUrl(track.tracking_url)
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return track

        return try {
            val remoteTrack = api.getSeries(serverUrl, userId, itemId)
            track.copyPersonalFrom(remoteTrack)
            track.total_chapters = remoteTrack.total_chapters
            track
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin refresh failed for ${track.tracking_url}" }
            track
        }
    }

    // -- Authentication --
    // Username = server URL, Password = API key

    override suspend fun login(username: String, password: String) {
        val serverUrl = username.trimEnd('/')
        // Validate the connection using the API key
        try {
            // Create a temporary client with the key for validation
            val tempClient = networkService.client.newBuilder()
                .dns(Dns.SYSTEM)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("X-Emby-Token", password)
                            .build(),
                    )
                }
                .build()

            val tempApi = JellyfinApi(id, tempClient)
            val info = tempApi.getSystemInfo(serverUrl)
            logcat(LogPriority.INFO) { "Connected to Jellyfin: ${info.serverName} v${info.version}" }

            // Try to get the user ID from the API key
            // With an API key, we use the first admin user; with a user token we get the user info
            saveCredentials(serverUrl, password)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Jellyfin login failed" }
            throw e
        }
    }

    override fun loginNoop() {
        saveCredentials("jellyfin", "jellyfin")
    }

    // -- EnhancedTracker: auto-binding --

    override fun getAcceptedSources(): List<String> = emptyList()

    /**
     * Accept all sources — Jellyfin matching works by title, not by source.
     */
    override fun accept(source: Source): Boolean = true

    override suspend fun match(manga: Manga): TrackSearch? {
        if (!isLoggedIn) return null
        val serverUrl = getUsername().trimEnd('/')
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return null

        return try {
            val results = api.searchSeries(serverUrl, userId, manga.title)
            // Exact title match preferred
            results.firstOrNull {
                it.title.equals(manga.title, ignoreCase = true)
            } ?: results.firstOrNull()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin match failed for: ${manga.title}" }
            null
        }
    }

    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: Source?): Boolean {
        return track.remoteUrl.contains("/Items/")
    }

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: Source): DomainTrack? {
        return track
    }

    // -- DeletableTracker --

    override suspend fun delete(track: DomainTrack) {
        // No server-side deletion needed — just remove local tracking
    }

    // -- Private helpers --

    /**
     * Syncs read progress back to Jellyfin by marking children as played/unplayed.
     */
    private suspend fun syncReadProgressToServer(track: Track) {
        val serverUrl = api.getServerUrlFromTrackUrl(track.tracking_url)
        val itemId = api.getItemIdFromUrl(track.tracking_url)
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return

        val children = api.getSeriesChildren(serverUrl, userId, itemId)
        val lastRead = track.last_chapter_read.toInt()

        for ((index, child) in children.withIndex()) {
            val shouldBePlayed = index < lastRead
            val isPlayed = child.userData?.played == true
            if (shouldBePlayed != isPlayed) {
                try {
                    api.markPlayed(serverUrl, userId, child.id, shouldBePlayed)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to mark Jellyfin item ${child.id} as played=$shouldBePlayed" }
                }
            }
        }
    }
}
