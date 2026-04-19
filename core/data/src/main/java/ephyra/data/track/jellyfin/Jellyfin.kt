package ephyra.data.track.jellyfin

import android.app.Application
import dev.icerock.moko.resources.StringResource
import ephyra.app.core.common.R
import ephyra.core.common.util.system.logcat
import ephyra.data.track.BaseTracker
import ephyra.data.track.model.TrackSearch
import ephyra.data.track.model.toDomainTrackSearch
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import ephyra.domain.track.service.DeletableTracker
import ephyra.domain.track.service.EnhancedTracker
import ephyra.domain.track.service.TrackPreferences
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Dns
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds
import ephyra.data.database.models.Track as DbTrack

/**
 * Jellyfin tracker for syncing read progress with a Jellyfin media server.
 */
class Jellyfin(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val libraryPreferences: LibraryPreferences,
    private val json: Json,
) : BaseTracker(id, "Jellyfin", context, trackPreferences, networkService, addTracks, insertTrack),
    EnhancedTracker,
    DeletableTracker {

    companion object {
        const val UNREAD = 1L
        const val READING = 2L
        const val COMPLETED = 3L

        /** Placeholder credential used by [loginNoop] — not a valid server URL. */
        const val NOOP_CREDENTIAL = "jellyfin"

        /** Client identification header sent during AuthenticateByName (pre-auth requests). */
        private const val AUTH_HEADER =
            "MediaBrowser Client=\"Ephyra\", Device=\"Android\", DeviceId=\"ephyra\", Version=\"1.0\""

        /** Regex for stripping punctuation during title normalization. */
        private val PUNCTUATION_REGEX = Regex("[\\-–—:,!?.'\"()\\[\\]{}]")

        /** Regex for collapsing multiple whitespace into a single space. */
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    override val client: OkHttpClient =
        networkService.client.newBuilder()
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS — Jellyfin is typically on LAN
            .addInterceptor(JellyfinInterceptor(this))
            .rateLimit(permits = 10, period = 1.seconds) // self-hosted; generous for LAN use
            .build()

    val api by lazy { JellyfinApi(id, client, json) }

    /**
     * Mutex to prevent concurrent sync operations from corrupting read progress.
     */
    private val syncMutex = Mutex()

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

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: Track): String = ""

    // -- Sync operations --

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
        // Sync read state back to Jellyfin only when sync is enabled
        if (libraryPreferences.jellyfinSyncEnabled().get()) {
            try {
                syncReadProgressToServer(track)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to sync progress to Jellyfin" }
            }
        }
        return track
    }

    override suspend fun bindInternal(track: DbTrack, hasReadChapters: Boolean): DbTrack {
        if (track.last_chapter_read < 1.0) {
            pullRemoteProgress(track)
        } else {
            if (libraryPreferences.jellyfinSyncEnabled().get()) {
                try {
                    syncReadProgressToServer(track)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to push progress on Jellyfin bind" }
                }
            }
        }
        return track
    }

    override suspend fun search(query: String): List<ephyra.domain.track.model.TrackSearch> {
        if (!isLoggedIn()) return emptyList()
        val serverUrl = getServerUrl()
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        val libraryId = libraryPreferences.jellyfinLibraryId().get().takeIf { it.isNotBlank() }
        return try {
            // Support direct ID lookup: "id:itemId"
            if (query.startsWith("id:")) {
                val itemId = query.removePrefix("id:")
                val result = api.getSeries(serverUrl, userId, itemId)
                listOf(result.toDomainTrackSearch())
            } else {
                api.searchSeries(serverUrl, userId, query, parentId = libraryId).map { it.toDomainTrackSearch() }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin search failed" }
            emptyList()
        }
    }

    override suspend fun refreshInternal(track: DbTrack): DbTrack {
        val serverUrl = resolveServerUrl(track.tracking_url)
        val itemId = api.getItemIdFromUrl(track.tracking_url)
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return track

        return try {
            val remoteTrack = api.getSeries(serverUrl, userId, itemId)
            track.copyPersonalFrom(remoteTrack)
            track.total_chapters = remoteTrack.total_chapters

            // Bidirectional sync: pull when server ahead, push when local ahead
            if (remoteTrack.last_chapter_read > track.last_chapter_read) {
                // Server is ahead — adopt server's progress
                track.last_chapter_read = remoteTrack.last_chapter_read
                track.status = remoteTrack.status
            } else if (track.last_chapter_read > remoteTrack.last_chapter_read) {
                if (libraryPreferences.jellyfinSyncEnabled().get()) {
                    // Local is ahead — push progress to server
                    try {
                        syncReadProgressToServer(track)
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to push local progress during refresh" }
                    }
                }
            }
            track
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin refresh failed for ${track.tracking_url}" }
            track
        }
    }

    // -- Authentication --

    fun getServerUrl(): String {
        val stored = trackPreferences.jellyfinServerUrl().getSync()
        if (stored.isNotBlank()) return stored.trimEnd('/')
        // Legacy fallback: server URL was stored in the username field
        val legacy = getUsernameSync()
        if (legacy.isNotBlank() && legacy != NOOP_CREDENTIAL && legacy.startsWith("http")) {
            return legacy.trimEnd('/')
        }
        return ""
    }

    fun resolveServerUrl(trackingUrl: String): String {
        // Try to extract from legacy URL format first
        val fromUrl = api.getServerUrlFromTrackUrl(trackingUrl)
        if (fromUrl != null) return fromUrl
        // New format: resolve from stored preference
        return getServerUrl()
    }

    private fun buildPreAuthClient(): OkHttpClient {
        return networkService.client.newBuilder()
            .dns(Dns.SYSTEM)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-Emby-Authorization", AUTH_HEADER)
                        .build(),
                )
            }
            .build()
    }

    override suspend fun login(username: String, password: String) {
        // Not directly callable for proper auth — use loginWithCredentials instead
        loginWithCredentials(username, "", "")
    }

    suspend fun loginWithCredentials(
        serverUrl: String,
        jellyfinUser: String,
        jellyfinPassword: String,
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val tempClient = buildPreAuthClient()
        val tempApi = JellyfinApi(id, tempClient, json)

        try {
            // Validate server connectivity
            val info = tempApi.getSystemInfo(cleanUrl)
            logcat(LogPriority.INFO) { "Connected to Jellyfin: ${info.serverName} v${info.version}" }

            // Authenticate by username/password
            val authResponse = tempApi.authenticateByName(cleanUrl, jellyfinUser, jellyfinPassword)

            // Persist all Jellyfin-specific preferences
            trackPreferences.jellyfinServerUrl().set(cleanUrl)
            trackPreferences.jellyfinServerId().set(info.id)
            trackPreferences.jellyfinServerName().set(info.serverName)
            trackPreferences.jellyfinUserId().set(authResponse.user.id)
            trackPreferences.jellyfinUsername().set(authResponse.user.name)

            // Cache admin status from user policy (gates library scan feature)
            val isAdmin = authResponse.user.policy?.isAdministrator == true
            trackPreferences.jellyfinIsAdmin().set(isAdmin)
            logcat(LogPriority.INFO) { "Jellyfin user admin status: $isAdmin" }

            // BaseTracker credentials: server URL in username, access token in password
            saveCredentials(cleanUrl, authResponse.accessToken)
            logcat(LogPriority.INFO) { "Authenticated as Jellyfin user: ${authResponse.user.name}" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Jellyfin login failed" }
            throw e
        }
    }

    suspend fun updateServerUrl(newServerUrl: String) {
        val cleanUrl = newServerUrl.trimEnd('/')
        val info = api.getSystemInfo(cleanUrl)
        val storedServerId = trackPreferences.jellyfinServerId().get()

        if (storedServerId.isNotBlank() && info.id != storedServerId) {
            throw IllegalStateException(
                "Server ID mismatch: expected $storedServerId but got ${info.id}. " +
                    "This appears to be a different Jellyfin server.",
            )
        }

        trackPreferences.jellyfinServerUrl().set(cleanUrl)
        trackPreferences.jellyfinServerName().set(info.serverName)
        // Update the username field too (used by BaseTracker.isLoggedIn)
        saveCredentials(cleanUrl, getPasswordSync())
        logcat(LogPriority.INFO) { "Updated Jellyfin server URL to: $cleanUrl" }
    }

    override fun loginNoop() {
        saveCredentials(NOOP_CREDENTIAL, NOOP_CREDENTIAL)
    }

    override fun logout() {
        super.logout()
        // Clear Jellyfin-specific preferences
        trackPreferences.jellyfinServerUrl().set("")
        trackPreferences.jellyfinServerId().set("")
        trackPreferences.jellyfinServerName().set("")
        trackPreferences.jellyfinUserId().set("")
        trackPreferences.jellyfinUsername().set("")
        trackPreferences.jellyfinIsAdmin().set(false)
    }

    // -- EnhancedTracker: auto-binding --

    override fun getAcceptedSources(): List<String> = emptyList()

    override fun accept(source: Source): Boolean = true

    override suspend fun match(manga: ephyra.domain.manga.model.Manga): ephyra.domain.track.model.TrackSearch? {
        if (!isLoggedIn()) return null
        val serverUrl = getServerUrl()
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return null
        val libraryId = libraryPreferences.jellyfinLibraryId().get().takeIf { it.isNotBlank() }

        return try {
            val results = api.searchSeries(serverUrl, userId, manga.title, parentId = libraryId)
            val normalizedMangaTitle = normalizeTitle(manga.title)

            // Tier 1: Exact title match
            val exactMatch = results.firstOrNull {
                it.title.equals(manga.title, ignoreCase = true)
            }
            if (exactMatch != null) return exactMatch.toDomainTrackSearch()

            // Tier 2: Normalized title match (punctuation-insensitive)
            val normalizedMatch = results.firstOrNull {
                normalizeTitle(it.title) == normalizedMangaTitle
            }
            if (normalizedMatch != null) return normalizedMatch.toDomainTrackSearch()

            // Tier 3: Try alternative titles as search queries
            for (altTitle in manga.alternativeTitles) {
                if (altTitle.isBlank()) continue
                val altResults = api.searchSeries(serverUrl, userId, altTitle, parentId = libraryId)
                val altExact = altResults.firstOrNull {
                    it.title.equals(altTitle, ignoreCase = true)
                }
                if (altExact != null) return altExact.toDomainTrackSearch()
                val altNormalized = altResults.firstOrNull {
                    normalizeTitle(it.title) == normalizeTitle(altTitle)
                }
                if (altNormalized != null) return altNormalized.toDomainTrackSearch()
            }

            // Tier 4: First result from primary search as fallback
            results.firstOrNull()?.toDomainTrackSearch()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin match failed for: ${manga.title}" }
            null
        }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    override fun isTrackFrom(track: Track, manga: ephyra.domain.manga.model.Manga, source: Source?): Boolean {
        return track.remoteUrl.contains("/Items/")
    }

    override fun migrateTrack(track: Track, manga: ephyra.domain.manga.model.Manga, newSource: Source): Track {
        return track
    }

    // -- DeletableTracker --

    override suspend fun delete(track: Track) {
        // No server-side deletion needed — just remove local tracking
    }

    // -- Public utilities --

    suspend fun getServerInfo(): JellyfinSystemInfo? {
        if (!isLoggedIn()) return null
        val serverUrl = getServerUrl()
        return try {
            api.getSystemInfo(serverUrl)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin server connection check failed" }
            null
        }
    }

    suspend fun getChaptersFromServer(trackingUrl: String): List<JellyfinItem> {
        if (!isLoggedIn()) return emptyList()
        val serverUrl = resolveServerUrl(trackingUrl)
        val itemId = api.getItemIdFromUrl(trackingUrl)
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        return try {
            api.getSeriesChildren(serverUrl, userId, itemId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin chapters for $trackingUrl" }
            emptyList()
        }
    }

    fun getChapterDownloadUrl(trackingUrl: String, childItemId: String): String {
        val serverUrl = resolveServerUrl(trackingUrl)
        return api.getItemDownloadUrl(serverUrl, childItemId)
    }

    // -- Private helpers --

    private suspend fun syncReadProgressToServer(track: DbTrack) = syncMutex.withLock {
        val serverUrl = resolveServerUrl(track.tracking_url)
        val itemId = api.getItemIdFromUrl(track.tracking_url)
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return@withLock

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

    private suspend fun pullRemoteProgress(track: DbTrack, remoteTrack: TrackSearch? = null) = syncMutex.withLock {
        val remote = remoteTrack ?: run {
            val serverUrl = resolveServerUrl(track.tracking_url)
            val itemId = api.getItemIdFromUrl(track.tracking_url)
            val userId = trackPreferences.jellyfinUserId().get()
            if (userId.isBlank()) return@withLock
            try {
                api.getSeries(serverUrl, userId, itemId)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to pull Jellyfin progress" }
                return@withLock
            }
        }

        if (remote.last_chapter_read > track.last_chapter_read) {
            track.last_chapter_read = remote.last_chapter_read
            track.status = remote.status
        }
    }
}
