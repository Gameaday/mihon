package ephyra.app.data.track.jellyfin

import ephyra.app.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import ephyra.core.common.util.lang.withIOContext

/**
 * Jellyfin REST API client.
 *
 * Supports server info, library browsing, series search, read-progress sync,
 * metadata push, and content download URL generation.
 * Reference: https://api.jellyfin.org/
 */
class JellyfinApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    private val json: Json,
) {


    // -- Image URL helpers (Jellyfin-style: quality params for caching) --

    /**
     * Builds an image URL for a Jellyfin item with optional quality/size parameters.
     * Jellyfin serves images at `/Items/{id}/Images/{type}` with query params for
     * scaling and quality.
     *
     * Falls back through image types: Primary → Thumb → Backdrop.
     * Returns empty string only if no image type is available — avoiding blank covers
     * in search results (Jellyfin's cover search never shows blanks).
     *
     * @param serverUrl Base server URL
     * @param item The Jellyfin item to get an image for
     * @param maxWidth Maximum width in pixels (null = original size)
     * @param quality JPEG quality 0-100 (null = server default)
     */
    fun buildImageUrl(
        serverUrl: String,
        item: JellyfinItem,
        maxWidth: Int? = null,
        quality: Int? = null,
    ): String {
        // Determine best available image type.
        // "Backdrop/0" uses a URL path segment (Items/{id}/Images/Backdrop/0)
        // to select the first backdrop image — this is Jellyfin's standard URL format.
        val imageType = when {
            item.imageTags?.containsKey("Primary") == true -> "Primary"
            item.imageTags?.containsKey("Thumb") == true -> "Thumb"
            !item.backdropImageTags.isNullOrEmpty() -> "Backdrop/0"
            else -> return "" // No image available
        }

        val url = "$serverUrl/Items/${item.id}/Images/$imageType".toHttpUrl().newBuilder()
        if (maxWidth != null) url.addQueryParameter("maxWidth", maxWidth.toString())
        if (quality != null) url.addQueryParameter("quality", quality.toString())
        return url.build().toString()
    }

    /**
     * Builds a cover image URL optimized for list/grid display (max 400px, 90% quality).
     * Reduces bandwidth for LAN connections while maintaining visual quality.
     */
    fun buildCoverUrl(serverUrl: String, item: JellyfinItem): String {
        return buildImageUrl(serverUrl, item, maxWidth = COVER_MAX_WIDTH, quality = COVER_QUALITY)
    }

    /**
     * Returns the direct download URL for a Jellyfin item (chapter/book).
     * Uses Jellyfin's `/Items/{id}/Download` endpoint which returns the original file.
     *
     * Authentication is handled by [JellyfinInterceptor] adding the X-Emby-Token header.
     */
    fun getItemDownloadUrl(serverUrl: String, itemId: String): String {
        return "$serverUrl/Items/$itemId/Download"
    }

    /**
     * Validates the server connection by fetching system info.
     */
    suspend fun getSystemInfo(serverUrl: String): JellyfinSystemInfo = withIOContext {
        client.newCall(GET("$serverUrl/System/Info/Public"))
            .awaitSuccess()
            .let { with(json) { it.parseAs<JellyfinSystemInfo>() } }
    }

    /**
     * Authenticates a user by name and password using Jellyfin's
     * `/Users/AuthenticateByName` endpoint. Returns an access token that
     * can be used for subsequent API calls instead of a global API key.
     *
     * This is the preferred login method because:
     * 1. It authenticates as a specific user (not an admin-level API key)
     * 2. The access token is scoped to that user's permissions
     * 3. The server returns the user ID and server ID alongside the token
     *
     * The caller must add an `X-Emby-Authorization` header with client info
     * since the user is not yet authenticated at this point.
     *
     * Reference: POST /Users/AuthenticateByName
     */
    suspend fun authenticateByName(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthByNameResponse = withIOContext {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "Username" to kotlinx.serialization.json.JsonPrimitive(username),
                    "Pw" to kotlinx.serialization.json.JsonPrimitive(password),
                ),
            ),
        ).toByteArray().toRequestBody("application/json".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("$serverUrl/Users/AuthenticateByName")
            .post(body)
            .build()

        client.newCall(request)
            .awaitSuccess()
            .let { with(json) { it.parseAs<JellyfinAuthByNameResponse>() } }
    }

    /**
     * Returns libraries ("views") visible to the authenticated user.
     */
    suspend fun getLibraries(serverUrl: String, userId: String): List<JellyfinLibrary> =
        withIOContext {
            val response = client
                .newCall(GET("$serverUrl/Users/$userId/Views"))
                .awaitSuccess()
            with(json) {
                response.parseAs<JellyfinItemsResponse>()
            }.items.map { JellyfinLibrary(it.id, it.name, it.type) }
        }

    /**
     * Searches for series in a user's library matching the given query.
     * When [parentId] is provided, scopes the search to that library — improving
     * matching accuracy when the server has multiple libraries.
     * Results without any available cover image are sorted to the end,
     * matching Jellyfin's search behavior where items with images appear first.
     */
    suspend fun searchSeries(
        serverUrl: String,
        userId: String,
        query: String,
        parentId: String? = null,
    ): List<TrackSearch> = withIOContext {
        val url = "$serverUrl/Users/$userId/Items".toHttpUrl().newBuilder()
            .addQueryParameter("searchTerm", query)
            .addQueryParameter("IncludeItemTypes", "Series")
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("Fields", SERIES_FIELDS)
            .addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
            .addQueryParameter("Limit", "20")
        if (!parentId.isNullOrBlank()) {
            url.addQueryParameter("ParentId", parentId)
        }

        val response = client.newCall(GET(url.build().toString()))
            .awaitSuccess()

        with(json) {
            response.parseAs<JellyfinItemsResponse>()
        }.items
            .distinctBy { it.id } // Deduplicate items that appear in multiple libraries
            .sortedByDescending { it.hasImage() } // Items with covers first
            .map { item -> item.toTrackSearch(trackId, serverUrl, this@JellyfinApi) }
    }

    /**
     * Gets a specific series by its Jellyfin item ID.
     * Includes image types for cover fallback and Studios for author/artist.
     */
    suspend fun getSeries(
        serverUrl: String,
        userId: String,
        itemId: String,
    ): TrackSearch = withIOContext {
        val url = "$serverUrl/Users/$userId/Items/$itemId".toHttpUrl().newBuilder()
            .addQueryParameter("Fields", SERIES_FIELDS)
            .addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
            .build()

        val response = client.newCall(GET(url.toString()))
            .awaitSuccess()

        val item = with(json) { response.parseAs<JellyfinItem>() }
        item.toTrackSearch(trackId, serverUrl, this@JellyfinApi)
    }

    /**
     * Gets child items (chapters/volumes) of a series to compute read progress.
     * Includes Path and MediaSources fields for content file access.
     */
    suspend fun getSeriesChildren(
        serverUrl: String,
        userId: String,
        itemId: String,
    ): List<JellyfinItem> = withIOContext {
        val url = "$serverUrl/Users/$userId/Items".toHttpUrl().newBuilder()
            .addQueryParameter("ParentId", itemId)
            .addQueryParameter("Fields", "UserData,Path,MediaSources")
            .addQueryParameter("SortBy", "SortName")
            .addQueryParameter("SortOrder", "Ascending")
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("IncludeItemTypes", "Book")
            .build()

        val response = client.newCall(GET(url.toString()))
            .awaitSuccess()

        with(json) { response.parseAs<JellyfinItemsResponse>() }.items
    }

    /**
     * Marks an item as played/unplayed on the Jellyfin server.
     */
    suspend fun markPlayed(
        serverUrl: String,
        userId: String,
        itemId: String,
        played: Boolean,
    ) = withIOContext {
        val method = if (played) "POST" else "DELETE"
        val url = "$serverUrl/Users/$userId/PlayedItems/$itemId"
        val request = okhttp3.Request.Builder()
            .url(url)
            .method(method, if (method == "POST") ByteArray(0).toRequestBody() else null)
            .build()
        client.newCall(request).awaitSuccess()
    }

    /**
     * Extracts the Jellyfin item ID from a tracking URL.
     *
     * Tracking URLs have two formats:
     * - Legacy: `{serverUrl}/Items/{itemId}` (contains full server URL)
     * - New: bare item ID string (server URL resolved from preferences)
     */
    fun getItemIdFromUrl(trackingUrl: String): String {
        return if (trackingUrl.contains("/Items/")) {
            trackingUrl.substringAfterLast("/Items/").substringBefore("?")
        } else {
            // New format: tracking URL IS the item ID
            trackingUrl.substringBefore("?")
        }
    }

    /**
     * Extracts the server URL from a tracking URL.
     *
     * For legacy tracking URLs that embed the server URL (`{serverUrl}/Items/{itemId}`),
     * this extracts the server portion. For new-style bare item ID URLs, this returns
     * null — callers should fall back to the stored server URL preference.
     */
    fun getServerUrlFromTrackUrl(trackingUrl: String): String? {
        return if (trackingUrl.contains("/Items/")) {
            trackingUrl.substringBefore("/Items/")
        } else {
            null
        }
    }

    /**
     * Marks or unmarks an item as a favorite on the Jellyfin server.
     * Favorites in Jellyfin map to the "in library" concept in the app.
     */
    suspend fun markFavorite(
        serverUrl: String,
        userId: String,
        itemId: String,
        favorite: Boolean,
    ) = withIOContext {
        val method = if (favorite) "POST" else "DELETE"
        val url = "$serverUrl/Users/$userId/FavoriteItems/$itemId"
        val request = okhttp3.Request.Builder()
            .url(url)
            .method(method, if (method == "POST") ByteArray(0).toRequestBody() else null)
            .build()
        client.newCall(request).awaitSuccess()
    }

    /**
     * Updates metadata fields on a Jellyfin item.
     * Mirrors Jellyfin's "Edit Metadata" → Save workflow.
     *
     * Only non-null fields are included in the update payload.
     * Studios and Tags use Jellyfin's `NamedItem` format (`[{"Name": "value"}]`)
     * matching the Jellyfin API's expected object structure.
     * Reference: POST /Items/{itemId}
     */
    suspend fun updateItemMetadata(
        serverUrl: String,
        itemId: String,
        name: String? = null,
        overview: String? = null,
        genres: List<String>? = null,
        communityRating: Double? = null,
        productionYear: Int? = null,
        studios: List<String>? = null,
        tags: List<String>? = null,
    ) = withIOContext {
        val jsonFields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        jsonFields["Id"] = kotlinx.serialization.json.JsonPrimitive(itemId)
        if (name != null) jsonFields["Name"] = kotlinx.serialization.json.JsonPrimitive(name)
        if (overview != null) jsonFields["Overview"] = kotlinx.serialization.json.JsonPrimitive(overview)
        if (genres != null) {
            jsonFields["Genres"] = kotlinx.serialization.json.JsonArray(
                genres.map { kotlinx.serialization.json.JsonPrimitive(it) },
            )
        }
        if (communityRating != null) {
            jsonFields["CommunityRating"] = kotlinx.serialization.json.JsonPrimitive(communityRating)
        }
        if (productionYear != null) {
            jsonFields["ProductionYear"] = kotlinx.serialization.json.JsonPrimitive(productionYear)
        }
        // Studios and Tags use Jellyfin's NamedItem format: [{"Name": "value"}]
        if (studios != null) {
            jsonFields["Studios"] = kotlinx.serialization.json.JsonArray(
                studios.map { studio ->
                    kotlinx.serialization.json.JsonObject(
                        mapOf("Name" to kotlinx.serialization.json.JsonPrimitive(studio)),
                    )
                },
            )
        }
        if (tags != null) {
            jsonFields["Tags"] = kotlinx.serialization.json.JsonArray(
                tags.map { tag ->
                    kotlinx.serialization.json.JsonObject(
                        mapOf("Name" to kotlinx.serialization.json.JsonPrimitive(tag)),
                    )
                },
            )
        }

        val jsonBody = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(jsonFields),
        )
        val body = jsonBody.toByteArray().toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/Items/$itemId")
            .post(body)
            .build()
        client.newCall(request).awaitSuccess()
    }

    // -- Jellyfin discovery & recommendation endpoints --

    /**
     * Fetches items similar to the given item from the Jellyfin server.
     * Jellyfin uses its internal algorithm to find related content based on
     * genre, tags, studios, and other metadata.
     *
     * Reference: GET /Items/{itemId}/Similar
     */
    suspend fun getSimilarItems(
        serverUrl: String,
        userId: String,
        itemId: String,
        limit: Int = 10,
    ): List<TrackSearch> = withIOContext {
        val url = "$serverUrl/Items/$itemId/Similar".toHttpUrl().newBuilder()
            .addQueryParameter("UserId", userId)
            .addQueryParameter("Limit", limit.toString())
            .addQueryParameter("Fields", SERIES_FIELDS)
            .addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
            .build()

        val response = client.newCall(GET(url.toString()))
            .awaitSuccess()

        with(json) {
            response.parseAs<JellyfinItemsResponse>()
        }.items
            .distinctBy { it.id }
            .sortedByDescending { it.hasImage() }
            .map { item -> item.toTrackSearch(trackId, serverUrl, this@JellyfinApi) }
    }

    /**
     * Fetches the user's "Continue Reading" items (resume list).
     * This is the Jellyfin equivalent of "recently read" — items the user has
     * partially consumed and can resume. Matches Jellyfin's home screen
     * "Continue Reading" row.
     *
     * Reference: GET /Users/{userId}/Items/Resume
     */
    suspend fun getResumeItems(
        serverUrl: String,
        userId: String,
        limit: Int = 12,
        parentId: String? = null,
    ): List<TrackSearch> = withIOContext {
        val url = "$serverUrl/Users/$userId/Items/Resume".toHttpUrl().newBuilder()
            .addQueryParameter("Limit", limit.toString())
            .addQueryParameter("Fields", SERIES_FIELDS)
            .addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
            .addQueryParameter("IncludeItemTypes", "Series")
        if (!parentId.isNullOrBlank()) {
            url.addQueryParameter("ParentId", parentId)
        }

        val response = client.newCall(GET(url.build().toString()))
            .awaitSuccess()

        with(json) {
            response.parseAs<JellyfinItemsResponse>()
        }.items
            .distinctBy { it.id }
            .map { item -> item.toTrackSearch(trackId, serverUrl, this@JellyfinApi) }
    }

    /**
     * Fetches the latest items added to the server (or a specific library).
     * Matches Jellyfin's "Latest" section on the home screen — shows recently
     * added content that the user may want to read.
     *
     * Reference: GET /Users/{userId}/Items/Latest
     */
    suspend fun getLatestItems(
        serverUrl: String,
        userId: String,
        limit: Int = 16,
        parentId: String? = null,
    ): List<TrackSearch> = withIOContext {
        val url = "$serverUrl/Users/$userId/Items/Latest".toHttpUrl().newBuilder()
            .addQueryParameter("Limit", limit.toString())
            .addQueryParameter("Fields", SERIES_FIELDS)
            .addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
            .addQueryParameter("IncludeItemTypes", "Series")
        if (!parentId.isNullOrBlank()) {
            url.addQueryParameter("ParentId", parentId)
        }

        val response = client.newCall(GET(url.build().toString()))
            .awaitSuccess()

        with(json) {
            response.parseAs<List<JellyfinItem>>()
        }
            .distinctBy { it.id }
            .sortedByDescending { it.hasImage() }
            .map { item -> item.toTrackSearch(trackId, serverUrl, this@JellyfinApi) }
    }

    /**
     * Fetches the user's next-up items — series that the user has started
     * but not finished, showing the next episode/chapter to read.
     * This is Jellyfin's primary "what to read next" recommendation.
     *
     * Reference: GET /Shows/NextUp
     */
    suspend fun getNextUp(
        serverUrl: String,
        userId: String,
        limit: Int = 12,
        parentId: String? = null,
    ): List<TrackSearch> = withIOContext {
        val url = "$serverUrl/Shows/NextUp".toHttpUrl().newBuilder()
            .addQueryParameter("UserId", userId)
            .addQueryParameter("Limit", limit.toString())
            .addQueryParameter("Fields", SERIES_FIELDS)
            .addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
        if (!parentId.isNullOrBlank()) {
            url.addQueryParameter("ParentId", parentId)
        }

        val response = client.newCall(GET(url.build().toString()))
            .awaitSuccess()

        with(json) {
            response.parseAs<JellyfinItemsResponse>()
        }.items
            .distinctBy { it.id }
            .map { item -> item.toTrackSearch(trackId, serverUrl, this@JellyfinApi) }
    }

    /**
     * Lightweight pre-flight check to verify the Jellyfin server is reachable.
     * Uses the public system info endpoint (no auth required, minimal payload).
     *
     * Returns `true` if the server responds successfully, `false` on any error.
     * This should be called before starting expensive sync operations to
     * fail fast with a clear "server unreachable" message.
     */
    suspend fun checkServerReachable(serverUrl: String): Boolean = withIOContext {
        try {
            client.newCall(GET("$serverUrl/System/Info/Public"))
                .awaitSuccess()
                .close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Triggers a Jellyfin library scan so newly downloaded files are discovered.
     *
     * Uses the `/Library/Refresh` endpoint which initiates a scan of all libraries.
     * Requires administrator privileges to succeed.
     *
     * Returns a [LibraryScanResult] indicating success, permission failure (403),
     * or other errors — so callers can show appropriate user-facing messages.
     *
     * Reference: POST /Library/Refresh
     */
    suspend fun triggerLibraryScan(serverUrl: String): LibraryScanResult = withIOContext {
        try {
            val request = okhttp3.Request.Builder()
                .url("$serverUrl/Library/Refresh")
                .post(ByteArray(0).toRequestBody())
                .build()
            val response = client.newCall(request).execute()
            when {
                response.isSuccessful -> LibraryScanResult.Success
                response.code == 401 || response.code == 403 -> {
                    response.close()
                    LibraryScanResult.Forbidden
                }
                else -> {
                    val msg = "HTTP ${response.code}"
                    response.close()
                    LibraryScanResult.Error(msg)
                }
            }
        } catch (e: java.io.IOException) {
            LibraryScanResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Result of a Jellyfin library scan trigger.
     */
    sealed class LibraryScanResult {
        data object Success : LibraryScanResult()
        data object Forbidden : LibraryScanResult()
        data class Error(val message: String) : LibraryScanResult()
    }

    companion object {
        /** Maximum cover image width for list/grid display. */
        const val COVER_MAX_WIDTH = 400

        /** JPEG quality percentage for cover images. */
        const val COVER_QUALITY = 90

        /** Fields requested from the Jellyfin API for series metadata. */
        private const val SERIES_FIELDS =
            "Overview,Genres,CommunityRating,ProductionYear,RecursiveItemCount,Studios,Tags,DateCreated,SortName,ExternalUrls,ProviderIds"

        /**
         * Converts a [JellyfinItem] to a [TrackSearch] for the tracker system.
         * Uses [api] for image URL construction with quality parameters and
         * cover fallback through Primary → Thumb → Backdrop image types.
         */
        private fun JellyfinItem.toTrackSearch(
            trackId: Long,
            serverUrl: String,
            api: JellyfinApi,
        ): TrackSearch {
            return TrackSearch.create(trackId).also { track ->
                track.title = name
                track.summary = overview.orEmpty()
                track.cover_url = api.buildCoverUrl(serverUrl, this)
                track.tracking_url = "$serverUrl/Items/$id"
                track.total_chapters = (recursiveItemCount ?: childCount ?: 0).toLong()
                track.score = communityRating ?: 0.0
                track.start_year = productionYear ?: 0
                if (!genres.isNullOrEmpty()) {
                    track.genres = genres
                }
                track.publishing_status = status.orEmpty()
                track.publishing_type = type

                // Map Jellyfin Studios to authors (book/comic creator convention)
                val creators = getCreators()
                if (creators.isNotEmpty()) {
                    track.authors = creators
                }

                // Compute read progress from user data
                val totalChildren = recursiveItemCount ?: childCount ?: 0
                val unplayed = userData?.unplayedItemCount ?: totalChildren
                val readCount = totalChildren - unplayed

                track.last_chapter_read = readCount.toDouble()
                track.status = when {
                    userData?.played == true -> Jellyfin.COMPLETED
                    readCount > 0 -> Jellyfin.READING
                    else -> Jellyfin.UNREAD
                }
            }
        }
    }
}
