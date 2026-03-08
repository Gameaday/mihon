package eu.kanade.tachiyomi.data.track.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jellyfin REST API DTOs.
 *
 * Reference: https://api.jellyfin.org/
 */

@Serializable
data class JellyfinAuthResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: JellyfinUser,
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
)

@Serializable
data class JellyfinSystemInfo(
    @SerialName("ServerName") val serverName: String,
    @SerialName("Version") val version: String,
    @SerialName("Id") val id: String,
)

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items") val items: List<JellyfinItem>,
    @SerialName("TotalRecordCount") val totalRecordCount: Int,
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: String = "",
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("Genres") val genres: List<String>? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("UserData") val userData: JellyfinUserData? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerialName("RecursiveItemCount") val recursiveItemCount: Int? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSource>? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
) {
    /**
     * Returns true if this item has at least one displayable image.
     * Checks Primary, Thumb, and Backdrop image tags.
     */
    fun hasImage(): Boolean {
        return imageTags?.containsKey("Primary") == true ||
            imageTags?.containsKey("Thumb") == true ||
            !backdropImageTags.isNullOrEmpty()
    }
}

/**
 * Jellyfin media source DTO — provides access to the physical file path
 * and container format of a media item (chapter/book).
 */
@Serializable
data class JellyfinMediaSource(
    @SerialName("Id") val id: String,
    @SerialName("Path") val path: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
)

@Serializable
data class JellyfinUserData(
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("PlayCount") val playCount: Int = 0,
)

@Serializable
data class JellyfinLibrary(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("CollectionType") val collectionType: String? = null,
)

@Serializable
data class JellyfinPlayedRequest(
    @SerialName("Played") val played: Boolean,
)
