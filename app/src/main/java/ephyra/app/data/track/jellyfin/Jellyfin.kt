package ephyra.app.data.track.jellyfin

import dev.icerock.moko.resources.StringResource
import ephyra.app.R
import ephyra.app.data.database.models.Track
import ephyra.app.data.track.BaseTracker
import ephyra.app.data.track.DeletableTracker
import ephyra.app.data.track.EnhancedTracker
import ephyra.app.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import okhttp3.Dns
import ephyra.domain.track.model.Track as DomainTrack
import kotlin.time.Duration.Companion.seconds
import android.app.Application
import okhttp3.OkHttpClient
import ephyra.core.common.util.system.logcat
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack

/**
 * Jellyfin tracker for syncing read progress with a Jellyfin media server.
 *
 * This is an [EnhancedTracker] that auto-binds manga via library matching
 * (no manual search needed). It syncs read progress back to Jellyfin so that
 * chapters read in the app are reflected on the server.
 *
 * Configuration:
 * - Server URL stored in `jellyfinServerUrl` preference
 * - Access token stored in tracker password field (from AuthenticateByName)
 * - Jellyfin user ID stored in `jellyfinUserId` preference
 * - Jellyfin server ID stored in `jellyfinServerId` preference (stable across moves)
 * - Jellyfin display username stored in `jellyfinUsername` preference
 * - Tracker username field stores server URL for BaseTracker.isLoggedIn compatibility
 *
 * Server migration: When the server moves to a new address (dynamic IP, domain change),
 * use [updateServerUrl] to update. Tracking URLs store only item IDs, so they survive
 * server address changes without migration. The server ID is verified to prevent
 * accidentally pointing to a different Jellyfin instance.
 *
 * Jellyfin Bookshelf plugin compatibility:
 * - Recognizes series organized in Jellyfin hierarchy
 */
class Jellyfin(
    id: Long,
    context: Application,
    trackPreferences: TrackPreferences,
    networkService: NetworkHelper,
    addTracks: AddTracks,
    insertTrack: InsertTrack,
    private val libraryPreferences: LibraryPreferences,
) : BaseTracker(id, "Jellyfin", context, trackPreferences, networkService, addTracks, insertTrack), EnhancedTracker, DeletableTracker {

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
     * Guards [syncReadProgressToServer] and [pullRemoteProgress] so that
     * only one sync operation runs at a time per Jellyfin tracker instance.
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

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        // On bind, pull Jellyfin's played state when the local track
        // has no read progress yet — adopts server-side read count.
        // Also push local progress to server when we already have reads
        // (edge case: user reads offline, then links to Jellyfin later).
        if (track.last_chapter_read < 1.0) {
            pullRemoteProgress(track)
        } else if (libraryPreferences.jellyfinSyncEnabled().get()) {
            // Push existing local progress to the server on initial bind
            try {
                syncReadProgressToServer(track)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to push progress on Jellyfin bind" }
            }
        }
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (!isLoggedIn) return emptyList()
        val serverUrl = getServerUrl()
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        val libraryId = libraryPreferences.jellyfinLibraryId().get().takeIf { it.isNotBlank() }
        return try {
            // Support direct ID lookup: "id:itemId"
            if (query.startsWith("id:")) {
                val itemId = query.removePrefix("id:")
                val result = api.getSeries(serverUrl, userId, itemId)
                listOf(result)
            } else {
                api.searchSeries(serverUrl, userId, query, parentId = libraryId)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: Track): Track {
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
            } else if (track.last_chapter_read > remoteTrack.last_chapter_read &&
                libraryPreferences.jellyfinSyncEnabled().get()
            ) {
                // Local is ahead — push progress to server
                try {
                    syncReadProgressToServer(track)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to push local progress during refresh" }
                }
            }
            track
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin refresh failed for ${track.tracking_url}" }
            track
        }
    }

    // -- Authentication --
    // Server URL = jellyfinServerUrl preference
    // Access token = password field (from AuthenticateByName)
    // User info = jellyfinUserId, jellyfinUsername, jellyfinServerId preferences

    /**
     * Returns the stored Jellyfin server URL, falling back to the username field
     * for backward compatibility with pre-migration credentials.
     */
    fun getServerUrl(): String {
        val stored = trackPreferences.jellyfinServerUrl().get()
        if (stored.isNotBlank()) return stored.trimEnd('/')
        // Legacy fallback: server URL was stored in the username field
        val legacy = getUsername()
        if (legacy.isNotBlank() && legacy != NOOP_CREDENTIAL && legacy.startsWith("http")) {
            return legacy.trimEnd('/')
        }
        return ""
    }

    /**
     * Resolves the server URL for a given tracking URL.
     *
     * New-style tracking URLs are bare item IDs — the server URL comes from
     * the [jellyfinServerUrl] preference. Legacy tracking URLs embed the server
     * URL and are parsed directly, falling back to the preference if parsing fails.
     */
    fun resolveServerUrl(trackingUrl: String): String {
        // Try to extract from legacy URL format first
        val fromUrl = api.getServerUrlFromTrackUrl(trackingUrl)
        if (fromUrl != null) return fromUrl
        // New format: resolve from stored preference
        return getServerUrl()
    }

    /**
     * Creates a temporary OkHttp client with the Jellyfin auth header for pre-login requests.
     * This client is used during AuthenticateByName since the user doesn't have an access token yet.
     */
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

    /**
     * BaseTracker login interface. Delegates to [loginWithCredentials].
     * The [username] parameter is the server URL, [password] is unused
     * since the actual Jellyfin username/password come from the 3-field dialog.
     */
    override suspend fun login(username: String, password: String) {
        // Not directly callable for proper auth — use loginWithCredentials instead
        loginWithCredentials(username, "", "")
    }

    /**
     * Login with server URL, Jellyfin username, and Jellyfin password as separate parameters.
     * This is the primary login method called from the 3-field login dialog.
     *
     * Flow:
     * 1. Validate server connectivity via public system info endpoint
     * 2. Authenticate by username/password via Jellyfin's AuthenticateByName endpoint
     * 3. Store access token, server URL, server ID, user ID, and display username
     *
     * @param serverUrl The Jellyfin server URL (e.g., `http://192.168.1.100:8096`)
     * @param jellyfinUser The Jellyfin username to authenticate as
     * @param jellyfinPassword The Jellyfin password for that user
     */
    suspend fun loginWithCredentials(
        serverUrl: String,
        jellyfinUser: String,
        jellyfinPassword: String,
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val tempClient = buildPreAuthClient()
        val tempApi = JellyfinApi(id, tempClient)

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

    /**
     * Updates the Jellyfin server URL when the server moves to a new address.
     * Verifies that the new URL points to the same Jellyfin instance by comparing
     * server IDs, preventing accidental connection to a different server.
     *
     * @param newServerUrl The new server URL (e.g., after dynamic IP change)
     * @throws IllegalStateException if the new URL points to a different server
     */
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
        saveCredentials(cleanUrl, getPassword())
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

    /**
     * Accept all sources — Jellyfin matching works by title, not by source.
     */
    override fun accept(source: Source): Boolean = true

    override suspend fun match(manga: Manga): TrackSearch? {
        if (!isLoggedIn) return null
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
            if (exactMatch != null) return exactMatch

            // Tier 2: Normalized title match (punctuation-insensitive)
            val normalizedMatch = results.firstOrNull {
                normalizeTitle(it.title) == normalizedMangaTitle
            }
            if (normalizedMatch != null) return normalizedMatch

            // Tier 3: Try alternative titles as search queries
            for (altTitle in manga.alternativeTitles) {
                if (altTitle.isBlank()) continue
                val altResults = api.searchSeries(serverUrl, userId, altTitle, parentId = libraryId)
                val altExact = altResults.firstOrNull {
                    it.title.equals(altTitle, ignoreCase = true)
                }
                if (altExact != null) return altExact
                val altNormalized = altResults.firstOrNull {
                    normalizeTitle(it.title) == normalizeTitle(altTitle)
                }
                if (altNormalized != null) return altNormalized
            }

            // Tier 4: First result from primary search as fallback
            results.firstOrNull()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin match failed for: ${manga.title}" }
            null
        }
    }

    /**
     * Normalizes a title for matching by removing common punctuation and
     * collapsing whitespace. This improves matching between slightly different
     * title formats (e.g., "Series: Part 1" vs "Series - Part 1").
     * Uses pre-compiled [PUNCTUATION_REGEX] and [WHITESPACE_REGEX] for performance.
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
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

    // -- Public utilities --

    /**
     * Checks the Jellyfin server connection and returns server info.
     * Returns null if the connection fails.
     */
    suspend fun getServerInfo(): JellyfinSystemInfo? {
        if (!isLoggedIn) return null
        val serverUrl = getServerUrl()
        return try {
            api.getSystemInfo(serverUrl)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin server connection check failed" }
            null
        }
    }

    /**
     * Pushes local metadata edits back to the Jellyfin server.
     * This enables bidirectional metadata sync — edits made in the app
     * are reflected on the Jellyfin server.
     *
     * Author and artist are mapped to Jellyfin's Studios field,
     * following the convention for book/comic series creators. Tags are
     * pushed as Jellyfin Tags for additional metadata context.
     *
     * @param trackingUrl The Jellyfin tracking URL containing server URL and item ID
     * @param title Updated title (null = no change)
     * @param description Updated description (null = no change)
     * @param genres Updated genres list (null = no change)
     * @param rating Updated community rating (null = no change)
     * @param year Updated production year (null = no change)
     * @param author Updated author name (null = no change)
     * @param artist Updated artist name (null = no change)
     */
    suspend fun pushMetadataToServer(
        trackingUrl: String,
        title: String? = null,
        description: String? = null,
        genres: List<String>? = null,
        rating: Double? = null,
        year: Int? = null,
        author: String? = null,
        artist: String? = null,
    ) {
        if (!isLoggedIn || !libraryPreferences.jellyfinSyncEnabled().get()) return
        val serverUrl = resolveServerUrl(trackingUrl)
        val itemId = api.getItemIdFromUrl(trackingUrl)
        // Map author/artist to Studios (Jellyfin's creator convention for books/comics)
        val studios = buildList {
            if (author != null && author.isNotBlank()) add(author)
            if (artist != null && artist.isNotBlank() && artist != author) add(artist)
        }.takeIf { it.isNotEmpty() }
        try {
            api.updateItemMetadata(
                serverUrl = serverUrl,
                itemId = itemId,
                name = title,
                overview = description,
                genres = genres,
                communityRating = rating,
                productionYear = year,
                studios = studios,
            )
            logcat(LogPriority.INFO) { "Pushed metadata to Jellyfin for item $itemId" }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to push metadata to Jellyfin for item $itemId" }
        }
    }

    /**
     * Returns the list of child items (chapters/books) for the series
     * linked to this track. Useful for identifying missing chapters.
     */
    suspend fun getChaptersFromServer(trackingUrl: String): List<JellyfinItem> {
        if (!isLoggedIn) return emptyList()
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

    /**
     * Returns the download URL for a specific Jellyfin chapter/book item.
     * The URL can be used with the authenticated client to download the file.
     */
    fun getChapterDownloadUrl(trackingUrl: String, childItemId: String): String {
        val serverUrl = resolveServerUrl(trackingUrl)
        return api.getItemDownloadUrl(serverUrl, childItemId)
    }

    // -- Jellyfin discovery features --

    /**
     * Fetches items similar to a given series from the Jellyfin server.
     * Jellyfin's recommendation algorithm uses genre, tags, studios, and
     * other metadata to find related content — enabling a "More Like This"
     * section (Jellyfin's series detail page pattern).
     */
    suspend fun getSimilarItems(trackingUrl: String): List<TrackSearch> {
        if (!isLoggedIn) return emptyList()
        val serverUrl = resolveServerUrl(trackingUrl)
        val itemId = api.getItemIdFromUrl(trackingUrl)
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        return try {
            api.getSimilarItems(serverUrl, userId, itemId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin similar items for $trackingUrl" }
            emptyList()
        }
    }

    /**
     * Fetches the user's "Continue Reading" items from the Jellyfin server.
     * This mirrors Jellyfin's home screen "Continue Reading" row — series that
     * the user has started but not finished.
     */
    suspend fun getResumeItems(): List<TrackSearch> {
        if (!isLoggedIn) return emptyList()
        val serverUrl = getServerUrl()
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        val libraryId = libraryPreferences.jellyfinLibraryId().get().takeIf { it.isNotBlank() }
        return try {
            api.getResumeItems(serverUrl, userId, parentId = libraryId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin resume items" }
            emptyList()
        }
    }

    /**
     * Fetches the latest items added to the Jellyfin server.
     * Mirrors Jellyfin's "Latest" section — recently added content.
     */
    suspend fun getLatestItems(): List<TrackSearch> {
        if (!isLoggedIn) return emptyList()
        val serverUrl = getServerUrl()
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        val libraryId = libraryPreferences.jellyfinLibraryId().get().takeIf { it.isNotBlank() }
        return try {
            api.getLatestItems(serverUrl, userId, parentId = libraryId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin latest items" }
            emptyList()
        }
    }

    /**
     * Fetches the user's "Next Up" items from the Jellyfin server.
     * Mirrors Jellyfin's "Next Up" section — the next chapter to read
     * in each series the user is currently reading.
     */
    suspend fun getNextUp(): List<TrackSearch> {
        if (!isLoggedIn) return emptyList()
        val serverUrl = getServerUrl()
        val userId = trackPreferences.jellyfinUserId().get()
        if (userId.isBlank()) return emptyList()
        val libraryId = libraryPreferences.jellyfinLibraryId().get().takeIf { it.isNotBlank() }
        return try {
            api.getNextUp(serverUrl, userId, parentId = libraryId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get Jellyfin next up items" }
            emptyList()
        }
    }

    // -- Private helpers --

    /**
     * Syncs read progress back to Jellyfin by marking children as played/unplayed.
     * Only syncs items that have changed to minimize API calls (Jellyfin-friendly:
     * bandwidth to LAN server is cheap, but reducing round-trips improves responsiveness).
     *
     * Protected by [syncMutex] to prevent concurrent syncs from interleaving
     * mark-played calls, which could result in incorrect progress state.
     */
    private suspend fun syncReadProgressToServer(track: Track) = syncMutex.withLock {
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

    /**
     * Pulls read progress from Jellyfin when the server is ahead of the local track.
     * Used by both [bind] (initial link) and [refresh] (periodic updates).
     *
     * When [remoteTrack] is null, fetches the series from the server first.
     *
     * Protected by [syncMutex] to prevent concurrent pulls from racing with
     * pushes in [syncReadProgressToServer].
     */
    private suspend fun pullRemoteProgress(track: Track, remoteTrack: TrackSearch? = null) = syncMutex.withLock {
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
