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
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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

    private val libraryPreferences: LibraryPreferences by lazy { Injekt.get() }

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

            // Auto-populate user ID from the API key's user list
            if (trackPreferences.jellyfinUserId().get().isBlank()) {
                try {
                    val users = tempApi.getUsers(serverUrl)
                    if (users.isNotEmpty()) {
                        trackPreferences.jellyfinUserId().set(users.first().id)
                        logcat(LogPriority.INFO) { "Auto-selected Jellyfin user: ${users.first().name}" }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Could not auto-populate Jellyfin user ID" }
                }
            }

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
            // Exact title match preferred, then normalized match, then first result
            results.firstOrNull {
                it.title.equals(manga.title, ignoreCase = true)
            } ?: results.firstOrNull {
                normalizeTitle(it.title) == normalizeTitle(manga.title)
            } ?: results.firstOrNull()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin match failed for: ${manga.title}" }
            null
        }
    }

    /**
     * Normalizes a title for matching by removing common punctuation and
     * collapsing whitespace. This improves matching between slightly different
     * title formats (e.g., "Series: Part 1" vs "Series - Part 1").
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[\\-–—:,!?.'\"()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
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
        val serverUrl = getUsername().trimEnd('/')
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
     * @param trackingUrl The Jellyfin tracking URL containing server URL and item ID
     * @param title Updated title (null = no change)
     * @param description Updated description (null = no change)
     * @param genres Updated genres list (null = no change)
     * @param rating Updated community rating (null = no change)
     * @param year Updated production year (null = no change)
     */
    suspend fun pushMetadataToServer(
        trackingUrl: String,
        title: String? = null,
        description: String? = null,
        genres: List<String>? = null,
        rating: Double? = null,
        year: Int? = null,
    ) {
        if (!isLoggedIn || !libraryPreferences.jellyfinSyncEnabled().get()) return
        val serverUrl = api.getServerUrlFromTrackUrl(trackingUrl)
        val itemId = api.getItemIdFromUrl(trackingUrl)
        try {
            api.updateItemMetadata(
                serverUrl = serverUrl,
                itemId = itemId,
                name = title,
                overview = description,
                genres = genres,
                communityRating = rating,
                productionYear = year,
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
        val serverUrl = api.getServerUrlFromTrackUrl(trackingUrl)
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
        val serverUrl = api.getServerUrlFromTrackUrl(trackingUrl)
        return api.getItemDownloadUrl(serverUrl, childItemId)
    }

    // -- Private helpers --

    /**
     * Syncs read progress back to Jellyfin by marking children as played/unplayed.
     * Only syncs items that have changed to minimize API calls (Jellyfin-friendly:
     * bandwidth to LAN server is cheap, but reducing round-trips improves responsiveness).
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

    /**
     * Pulls read progress from Jellyfin when the server is ahead of the local track.
     * Used by both [bind] (initial link) and [refresh] (periodic updates).
     *
     * When [remoteTrack] is null, fetches the series from the server first.
     */
    private suspend fun pullRemoteProgress(track: Track, remoteTrack: TrackSearch? = null) {
        val remote = remoteTrack ?: run {
            val serverUrl = api.getServerUrlFromTrackUrl(track.tracking_url)
            val itemId = api.getItemIdFromUrl(track.tracking_url)
            val userId = trackPreferences.jellyfinUserId().get()
            if (userId.isBlank()) return
            try {
                api.getSeries(serverUrl, userId, itemId)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to pull Jellyfin progress" }
                return
            }
        }

        if (remote.last_chapter_read > track.last_chapter_read) {
            track.last_chapter_read = remote.last_chapter_read
            track.status = remote.status
        }
    }
}
