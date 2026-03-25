package ephyra.app.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALMediaResult(
    val data: ALMediaData,
)

@Serializable
data class ALMediaData(
    @SerialName("Media")
    val media: ALSearchItem,
)
