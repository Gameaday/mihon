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
 * Supports server info, library browsing, series search, and read-progress sync.
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
     * Filters to "books" collection type items (manga/comics).
     */
    suspend fun searchSeries(
        serverUrl: String,
        userId: String,
        query: String,
    ): List<TrackSearch> = withIOContext {
        val url = "$serverUrl/Users/$userId/Items".toHttpUrl().newBuilder()
            .addQueryParameter("searchTerm", query)
            .addQueryParameter("IncludeItemTypes", "Series")
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("Fields", "Overview,Genres,CommunityRating,ProductionYear")
            .addQueryParameter("Limit", "20")
            .build()

        val response = client.newCall(GET(url.toString()))
            .awaitSuccess()

        with(json) {
            response.parseAs<JellyfinItemsResponse>()
        }.items.map { item ->
            item.toTrackSearch(trackId, serverUrl)
        }
    }

    /**
     * Gets a specific series by its Jellyfin item ID.
     */
    suspend fun getSeries(
        serverUrl: String,
        userId: String,
        itemId: String,
    ): TrackSearch = withIOContext {
        val url = "$serverUrl/Users/$userId/Items/$itemId".toHttpUrl().newBuilder()
            .addQueryParameter("Fields", "Overview,Genres,CommunityRating,ProductionYear,RecursiveItemCount")
            .build()

        val response = client.newCall(GET(url.toString()))
            .awaitSuccess()

        val item = with(json) { response.parseAs<JellyfinItem>() }
        item.toTrackSearch(trackId, serverUrl)
    }

    /**
     * Gets child items (chapters/volumes) of a series to compute read progress.
     */
    suspend fun getSeriesChildren(
        serverUrl: String,
        userId: String,
        itemId: String,
    ): List<JellyfinItem> = withIOContext {
        val url = "$serverUrl/Users/$userId/Items".toHttpUrl().newBuilder()
            .addQueryParameter("ParentId", itemId)
            .addQueryParameter("Fields", "UserData")
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
    ) = withIOContext {
        val fields = mutableMapOf<String, Any>()
        fields["Id"] = itemId
        if (name != null) fields["Name"] = name
        if (overview != null) fields["Overview"] = overview
        if (genres != null) fields["Genres"] = genres
        if (communityRating != null) fields["CommunityRating"] = communityRating
        if (productionYear != null) fields["ProductionYear"] = productionYear

        val jsonBody = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(
                fields.mapValues { (_, value) ->
                    when (value) {
                        is String -> kotlinx.serialization.json.JsonPrimitive(value)
                        is Int -> kotlinx.serialization.json.JsonPrimitive(value)
                        is Double -> kotlinx.serialization.json.JsonPrimitive(value)
                        is List<*> -> kotlinx.serialization.json.JsonArray(
                            value.map { kotlinx.serialization.json.JsonPrimitive(it as String) },
                        )
                        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
                    }
                },
            ),
        )
        val body = jsonBody.toByteArray().toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/Items/$itemId")
            .post(body)
            .build()
        client.newCall(request).awaitSuccess()
    }

    companion object {
        /**
         * Converts a [JellyfinItem] to a [TrackSearch] for the tracker system.
         */
        private fun JellyfinItem.toTrackSearch(trackId: Long, serverUrl: String): TrackSearch {
            return TrackSearch.create(trackId).also { track ->
                track.title = name
                track.summary = overview.orEmpty()
                track.cover_url = if (imageTags?.containsKey("Primary") == true) {
                    "$serverUrl/Items/$id/Images/Primary"
                } else {
                    ""
                }
                track.tracking_url = "$serverUrl/Items/$id"
                track.total_chapters = (recursiveItemCount ?: childCount ?: 0).toLong()
                track.score = communityRating ?: 0.0
                track.start_year = productionYear ?: 0
                if (!genres.isNullOrEmpty()) {
                    track.genres = genres
                }
                track.publishing_status = status.orEmpty()
                track.publishing_type = type

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
