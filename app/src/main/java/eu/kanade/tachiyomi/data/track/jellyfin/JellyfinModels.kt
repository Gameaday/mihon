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
    @SerialName("Studios") val studios: List<JellyfinNamedItem>? = null,
    @SerialName("Tags") val tags: List<String>? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("ExternalUrls") val externalUrls: List<JellyfinExternalUrl>? = null,
    @SerialName("ProviderIds") val providerIds: Map<String, String>? = null,
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

    /**
     * Returns the author/artist names extracted from Jellyfin's Studios field.
     * In Jellyfin, book/comic creators are stored as studio entries.
     */
    fun getCreators(): List<String> {
        return studios?.map { it.name } ?: emptyList()
    }
}

/**
 * Jellyfin named item DTO — used for Studios and similar fields
 * that Jellyfin stores as objects with a Name property.
 */
@Serializable
data class JellyfinNamedItem(
    @SerialName("Name") val name: String,
    @SerialName("Id") val id: String? = null,
)

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
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
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

/**
 * Jellyfin external URL DTO — provides links to external metadata providers
 * (e.g., AniList, MAL, MangaUpdates URLs configured in Jellyfin's metadata plugins).
 */
@Serializable
data class JellyfinExternalUrl(
    @SerialName("Name") val name: String,
    @SerialName("Url") val url: String,
)
