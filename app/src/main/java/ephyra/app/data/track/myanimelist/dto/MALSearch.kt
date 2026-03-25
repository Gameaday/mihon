package ephyra.app.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALSearchResult(
    val data: List<MALSearchResultNode>,
    val paging: MALSearchPaging,
)

@Serializable
data class MALSearchResultNode(
    val node: MALManga,
    @SerialName("list_status")
    val listStatus: MALListItemStatus? = null,
)

@Serializable
data class MALSearchPaging(
    val next: String?,
)
