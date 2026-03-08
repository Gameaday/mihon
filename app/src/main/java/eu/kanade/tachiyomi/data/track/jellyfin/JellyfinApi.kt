package eu.kanade.tachiyomi.data.track.jellyfin

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

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
) {

    private val json: Json by injectLazy()

    /**
     * Returns the base URL stored in the tracker's username credential field.
     * The URL is normalized to remove trailing slashes.
     */
    fun getServerUrl(jellyfin: Jellyfin): String {
        return jellyfin.getUsername().trimEnd('/')
    }

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
     * Authenticates and returns the user info + access token.
     */
    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthResponse = withIOContext {
        val url = "$serverUrl/Users/AuthenticateByName"
        val body = okhttp3.FormBody.Builder()
            .add("Username", username)
            .add("Pw", password)
            .build()
        client.newCall(POST(url, body = body))
            .awaitSuccess()
            .let { with(json) { it.parseAs<JellyfinAuthResponse>() } }
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
            .addQueryParameter(
                "Fields",
                "Overview,Genres,CommunityRating,ProductionYear,RecursiveItemCount,Studios,Tags,DateCreated,SortName",
            )
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
            .addQueryParameter(
                "Fields",
                "Overview,Genres,CommunityRating,ProductionYear,RecursiveItemCount,Studios,Tags,DateCreated,SortName",
            )
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
     * Tracking URLs have the format: `{serverUrl}/Items/{itemId}`
     */
    fun getItemIdFromUrl(trackingUrl: String): String {
        return trackingUrl.substringAfterLast("/Items/").substringBefore("?")
    }

    /**
     * Extracts the server URL from a tracking URL.
     */
    fun getServerUrlFromTrackUrl(trackingUrl: String): String {
        return trackingUrl.substringBefore("/Items/")
    }

    /**
     * Returns the list of users on the server. Requires admin-level API key.
     * Used during login to auto-populate the user ID preference.
     */
    suspend fun getUsers(serverUrl: String): List<JellyfinUser> = withIOContext {
        val response = client.newCall(GET("$serverUrl/Users"))
            .awaitSuccess()
        with(json) { response.parseAs<List<JellyfinUser>>() }
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

    companion object {
        /** Maximum cover image width for list/grid display. */
        const val COVER_MAX_WIDTH = 400

        /** JPEG quality percentage for cover images. */
        const val COVER_QUALITY = 90

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
